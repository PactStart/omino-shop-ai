package com.omino.shopai.app.service.chat;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omino.shopai.api.ErrorCodes;
import com.omino.shopai.app.dto.ConversationViewDto;
import com.omino.shopai.app.dto.TurnAcceptedDto;
import com.omino.shopai.dto.PageDto;
import com.omino.shopai.entity.Conversation;
import com.omino.shopai.entity.ConversationMessage;
import com.omino.shopai.exception.ApiBusinessException;
import com.omino.shopai.mapper.ConversationMapper;
import com.omino.shopai.mapper.ConversationMessageMapper;
import com.omino.shopai.support.Ids;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * 开一轮对话。积分不足时不把工人丢进线程池。
 */
@Service
public class ChatTurnServiceImpl implements IChatTurnService {

    private final ConversationMapper conversations;
    private final ConversationMessageMapper messages;
    private final ObjectMapper objectMapper;
    private final IChatCancelService cancels;
    private final IChatWorker worker;
    private final Executor executor;
    private final Clock clock;
    private final Duration timeout;
    private final ICreditGate credits;

    /**
     * 注入会话表、取消、工人和时钟。积分门默认放行。
     *
     * @param conversations 会话表
     * @param messages 消息表
     * @param objectMapper 消息载荷解析
     * @param cancels 取消与属主
     * @param worker 真正跑这一轮的人
     * @param chatWorkerExecutor 工人线程
     * @param clock 截止时间
     * @param chatRunTimeout 这一轮允许跑多久
     */
    public ChatTurnServiceImpl(
            ConversationMapper conversations,
            ConversationMessageMapper messages,
            ObjectMapper objectMapper,
            IChatCancelService cancels,
            IChatWorker worker,
            Executor chatWorkerExecutor,
            Clock clock,
            Duration chatRunTimeout) {
        this(conversations, messages, objectMapper, cancels, worker, chatWorkerExecutor, clock, chatRunTimeout, ICreditGate.ALLOW);
    }

    /**
     * 注入会话表、取消、工人和积分门。开 run 不在控制器里做。
     *
     * @param conversations 会话表
     * @param messages 消息表
     * @param objectMapper 消息载荷解析
     * @param cancels 取消与属主
     * @param worker 真正跑这一轮的人
     * @param chatWorkerExecutor 工人线程
     * @param clock 截止时间
     * @param chatRunTimeout 这一轮允许跑多久
     * @param credits 开 run 前的积分门
     */
    @Autowired
    public ChatTurnServiceImpl(
            ConversationMapper conversations,
            ConversationMessageMapper messages,
            ObjectMapper objectMapper,
            IChatCancelService cancels,
            IChatWorker worker,
            Executor chatWorkerExecutor,
            Clock clock,
            Duration chatRunTimeout,
            ICreditGate credits) {
        this.conversations = conversations;
        this.messages = messages;
        this.objectMapper = objectMapper;
        this.cancels = cancels;
        this.worker = worker;
        this.executor = chatWorkerExecutor;
        this.clock = clock;
        this.timeout = chatRunTimeout;
        this.credits = credits;
    }

    /**
     * 校验文本后按 clientMsgId 幂等。已受理则返回原来的 runId。
     *
     * @param userId 用户主键
     * @param conversationId 会话主键，空则新建
     * @param clientMsgId 客户端消息号
     * @param text 用户文本
     * @return 已受理的 run
     */
    @Override
    public TurnAcceptedDto open(String userId, String conversationId, String clientMsgId, String text) {
        return open(userId, new TurnCommand(conversationId, clientMsgId, "text", text, null, null, null, List.of()));
    }

    /**
     * 按更新时间倒序列出当前用户的会话。页码和页大小先收进合法范围。
     *
     * @param userId 用户主键
     * @param page 页码，从 1 起
     * @param size 页大小
     * @return 会话页
     */
    @Override
    public PageDto<ConversationViewDto> list(String userId, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 20);
        List<ConversationViewDto> rows = listConversations(userId, safePage, safeSize).stream()
                .map(row -> new ConversationViewDto(row.getId(), row.getTitle(), row.getUpdatedAt()))
                .toList();
        return new PageDto<>(rows, safePage, safeSize, countConversations(userId));
    }

    /**
     * 按输入类型校验后受理。已受理则返回原来的 runId。
     *
     * @param userId 用户主键
     * @param command 这一轮的输入
     * @return 已受理的 run
     */
    @Override
    public TurnAcceptedDto open(String userId, TurnCommand command) {
        if (command.clientMsgId() == null || command.clientMsgId().isBlank() || command.clientMsgId().length() > 64) {
            throw new ApiBusinessException(ErrorCodes.VALIDATION, "clientMsgId 不能为空");
        }
        String trimmed = validate(command);
        Conversation conversation = resolveConversation(userId, command.conversationId(), trimmed);
        return findRunId(conversation.getId(), command.clientMsgId())
                .map(runId -> new TurnAcceptedDto(conversation.getId(), runId, command.clientMsgId()))
                .orElseGet(() -> start(userId, conversation.getId(), command, trimmed));
    }

    /**
     * 文本和语音在没有恢复动作时必须有 1 到 2000 字。链接必须是 http(s)。图片必须有键。
     *
     * @param command 这一轮的输入
     * @return 修剪后的正文，可以是空串
     */
    private static String validate(TurnCommand command) {
        String type = command.inputType() == null || command.inputType().isBlank() ? "text" : command.inputType();
        boolean resume = command.hitl() != null && !command.hitl().isEmpty();
        String text = command.text() == null ? "" : command.text().trim();
        if (("text".equals(type) || "voice_transcript".equals(type)) && !resume) {
            if (text.isBlank() || text.length() > 2000) {
                throw new ApiBusinessException(ErrorCodes.VALIDATION, "text 不能为空");
            }
        } else if (!text.isEmpty() && text.length() > 2000) {
            throw new ApiBusinessException(ErrorCodes.VALIDATION, "text 不能为空");
        }
        if ("goods_url".equals(type)) {
            String url = command.sourceUrl();
            if (url == null || !(url.startsWith("https://") || url.startsWith("http://"))) {
                throw new ApiBusinessException(ErrorCodes.VALIDATION, "链接不能为空");
            }
        }
        if ("image".equals(type) && (command.imageObjectKey() == null || command.imageObjectKey().isBlank())) {
            throw new ApiBusinessException(ErrorCodes.VALIDATION, "图片不能为空");
        }
        return text;
    }

    /**
     * 写入用户消息并立刻返回 runId。积分不足时不把工人丢进线程池。
     *
     * @param userId 用户主键
     * @param conversationId 会话主键
     * @param command 这一轮的输入
     * @param text 已修剪的用户文本
     * @return 已受理的 run
     */
    private TurnAcceptedDto start(String userId, String conversationId, TurnCommand command, String text) {
        String runId = Ids.ulid();
        insertUserMessage(conversationId, command.clientMsgId(), text, runId);
        String type = command.inputType() == null || command.inputType().isBlank() ? "text" : command.inputType();
        Map<String, Object> hitl = command.hitl() == null || command.hitl().isEmpty() ? null : command.hitl();
        List<String> installed = command.installedPlatforms() == null ? List.of() : command.installedPlatforms();
        ChatRun run = new ChatRun(
                runId, userId, conversationId, command.clientMsgId(), type, text,
                command.sourceUrl(), command.imageObjectKey(), hitl, installed, clock.instant().plus(timeout));
        cancels.remember(run);
        if (!credits.block(run)) {
            executor.execute(() -> worker.start(run));
        }
        return new TurnAcceptedDto(conversationId, runId, command.clientMsgId());
    }

    /**
     * 沿用已有会话，或按本轮文本新建一条。
     *
     * @param userId 用户主键
     * @param conversationId 会话主键，空则新建
     * @param text 已修剪的用户文本
     * @return 这一轮使用的会话
     */
    private Conversation resolveConversation(String userId, String conversationId, String text) {
        if (conversationId == null || conversationId.isBlank()) {
            String source = text.isBlank() ? "对话" : text;
            String title = source.length() <= 32 ? source : source.substring(0, 32);
            return createConversation(userId, title);
        }
        return findOwnedConversation(conversationId, userId)
                .orElseThrow(() -> new ApiBusinessException(ErrorCodes.NOT_FOUND, "会话不存在"));
    }

    /**
     * 取出该用户的一页会话，最近更新的在前。
     *
     * @param userId 用户主键
     * @param page 已校正的页码
     * @param size 已校正的页大小
     * @return 当前页
     */
    protected List<Conversation> listConversations(String userId, int page, int size) {
        return conversations.selectList(new LambdaQueryWrapper<Conversation>()
                .eq(Conversation::getUserId, userId)
                .orderByDesc(Conversation::getUpdatedAt)
                .orderByDesc(Conversation::getId)
                .last("LIMIT " + size + " OFFSET " + ((long) (page - 1) * size)));
    }

    /**
     * 数该用户的会话，供分页总数。
     *
     * @param userId 用户主键
     * @return 行数
     */
    protected long countConversations(String userId) {
        Long count = conversations.selectCount(new LambdaQueryWrapper<Conversation>().eq(Conversation::getUserId, userId));
        return count == null ? 0 : count;
    }

    /**
     * 新建一条会话。
     *
     * @param userId 用户主键
     * @param title 会话标题
     * @return 已写入的会话
     */
    protected Conversation createConversation(String userId, String title) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Conversation conversation = new Conversation();
        conversation.setId(Ids.ulid());
        conversation.setUserId(userId);
        conversation.setTitle(title);
        conversation.setCreatedAt(now);
        conversation.setUpdatedAt(now);
        conversations.insert(conversation);
        return conversation;
    }

    /**
     * 读取属于该用户的会话。别人的会话当不存在。
     *
     * @param conversationId 会话主键
     * @param userId 用户主键
     * @return 会话；不属于该用户则空
     */
    protected Optional<Conversation> findOwnedConversation(String conversationId, String userId) {
        Conversation conversation = conversations.selectById(conversationId);
        if (conversation == null || !userId.equals(conversation.getUserId())) {
            return Optional.empty();
        }
        return Optional.of(conversation);
    }

    /**
     * 写入用户消息，并把 runId 放进载荷。
     *
     * @param conversationId 会话主键
     * @param clientMsgId 客户端消息号
     * @param text 用户文本
     * @param runId 这一轮的编号
     */
    protected void insertUserMessage(String conversationId, String clientMsgId, String text, String runId) {
        ConversationMessage message = new ConversationMessage();
        message.setId(Ids.ulid());
        message.setConversationId(conversationId);
        message.setClientMsgId(clientMsgId);
        message.setRole("user");
        message.setContent(text);
        message.setPayload("{\"runId\":\"" + runId + "\"}");
        message.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        // payload 是 jsonb。BaseMapper.insert 会按字符串绑定，开一轮就会变成 50000。
        messages.insertMessage(message);
    }

    /**
     * 按客户端消息号找回已经受理的 runId。
     *
     * @param conversationId 会话主键
     * @param clientMsgId 客户端消息号
     * @return runId；没有则空
     */
    protected Optional<String> findRunId(String conversationId, String clientMsgId) {
        ConversationMessage message = messages.selectOne(new LambdaQueryWrapper<ConversationMessage>()
                .eq(ConversationMessage::getConversationId, conversationId)
                .eq(ConversationMessage::getClientMsgId, clientMsgId));
        if (message == null || message.getPayload() == null) {
            return Optional.empty();
        }
        try {
            JsonNode node = objectMapper.readTree(message.getPayload());
            JsonNode runId = node.get("runId");
            return runId == null || runId.asText().isBlank() ? Optional.empty() : Optional.of(runId.asText());
        } catch (Exception ex) {
            return Optional.empty();
        }
    }
}
