package com.omino.shopai.app.service.chat;

import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 用 Redis Stream 保存一轮的事件。
 */
@Service
public class ChatEventServiceImpl implements IChatEventService {

    private final StringRedisTemplate redis;

    /**
     * 注入 Redis。事件、取消标记和属主都写在这里。
     *
     * @param redis 字符串模板
     */
    public ChatEventServiceImpl(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * 把事件追加进该 run 的流，供任意副本的 SSE 读取。
     *
     * @param runId 这一轮的编号
     * @param event 事件名
     * @param data 事件正文，可空
     * @return 已写入的事件
     */
    @Override
    public ChatEvents.Event append(String runId, String event, String data) {
        return appendEvent(runId, event, data);
    }

    /**
     * 从给定序号之后读取，泵中断后可以续上。
     *
     * @param runId 这一轮的编号
     * @param afterId 已读到的序号，空表示从头
     * @return 序号更大的事件
     */
    @Override
    public List<ChatEvents.Event> after(String runId, String afterId) {
        return readAfter(runId, afterId);
    }

    /**
     * 记下取消标记。
     *
     * @param runId 这一轮的编号
     */
    @Override
    public void markCancel(String runId) {
        writeCancel(runId);
    }

    /**
     * 这一轮是否已被取消。
     *
     * @param runId 这一轮的编号
     * @return 已取消时为真
     */
    @Override
    public boolean cancelled(String runId) {
        return readCancelled(runId);
    }

    /**
     * 流里是否已有终态事件。
     *
     * @param runId 这一轮的编号
     * @return 已结束时为真
     */
    @Override
    public boolean terminal(String runId) {
        return after(runId, null).stream().anyMatch(event -> ChatEvents.TERMINAL.contains(event.event()));
    }

    /**
     * 终态后给流一个过期时间，避免 Redis 被旧 run 撑满。
     *
     * @param runId 这一轮的编号
     */
    @Override
    public void expire(String runId) {
        expireRun(runId);
    }

    /**
     * 读剩余存活时间。没有键则按已结束处理。
     *
     * @param runId 这一轮的编号
     * @return 剩余时间；没有键则空
     */
    @Override
    public Duration ttl(String runId) {
        return readTtl(runId);
    }

    /**
     * 记下这轮属于哪个用户。
     *
     * @param runId 这一轮的编号
     * @param userId 用户主键
     */
    @Override
    public void bind(String runId, String userId) {
        bindOwner(runId, userId);
    }

    /**
     * 读取这轮的属主。
     *
     * @param runId 这一轮的编号
     * @return 用户主键；没有则空
     */
    @Override
    public Optional<String> owner(String runId) {
        return readOwner(runId);
    }

    /**
     * 把事件追加进该 run 的流。
     *
     * @param runId 这一轮的编号
     * @param event 事件名
     * @param data 事件正文，可空
     * @return 已写入的事件
     */
    protected ChatEvents.Event appendEvent(String runId, String event, String data) {
        ChatEvents.check(event);
        Long seq = redis.opsForValue().increment(seqKey(runId));
        String id = Long.toString(seq == null ? 1 : seq);
        String body = data == null ? "" : data;
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("id", id);
        fields.put("event", event);
        fields.put("data", body);
        streams().add(StreamRecords.mapBacked(fields).withStreamKey(streamKey(runId)));
        return new ChatEvents.Event(id, event, body);
    }

    /**
     * 从给定序号之后读取流里的事件。
     *
     * @param runId 这一轮的编号
     * @param afterId 已读到的序号，空表示从头
     * @return 序号更大的事件
     */
    protected List<ChatEvents.Event> readAfter(String runId, String afterId) {
        long after = afterId == null || afterId.isBlank() ? 0 : Long.parseLong(afterId);
        List<MapRecord<String, String, String>> records = streams().range(streamKey(runId), Range.unbounded());
        List<ChatEvents.Event> events = new ArrayList<>();
        if (records == null) {
            return events;
        }
        for (MapRecord<String, String, String> record : records) {
            String id = record.getValue().get("id");
            if (id == null || Long.parseLong(id) <= after) {
                continue;
            }
            events.add(new ChatEvents.Event(id, record.getValue().get("event"), record.getValue().get("data")));
        }
        return events;
    }

    /**
     * 写下取消标记，并给它终态过期时间。
     *
     * @param runId 这一轮的编号
     */
    protected void writeCancel(String runId) {
        redis.opsForValue().set(cancelKey(runId), "1", ChatEvents.TERMINAL_TTL);
    }

    /**
     * 读取取消标记。
     *
     * @param runId 这一轮的编号
     * @return 已取消时为真
     */
    protected boolean readCancelled(String runId) {
        return "1".equals(redis.opsForValue().get(cancelKey(runId)));
    }

    /**
     * 给流、取消标记、属主和序号同一个过期时间。
     *
     * @param runId 这一轮的编号
     */
    protected void expireRun(String runId) {
        redis.expire(streamKey(runId), ChatEvents.TERMINAL_TTL);
        redis.expire(cancelKey(runId), ChatEvents.TERMINAL_TTL);
        redis.expire(ownerKey(runId), ChatEvents.TERMINAL_TTL);
        redis.expire(seqKey(runId), ChatEvents.TERMINAL_TTL);
    }

    /**
     * 读取流键还剩多久过期。
     *
     * @param runId 这一轮的编号
     * @return 剩余时间；没有键则空
     */
    protected Duration readTtl(String runId) {
        Long seconds = redis.getExpire(streamKey(runId));
        if (seconds == null || seconds < 0) {
            return null;
        }
        return Duration.ofSeconds(seconds);
    }

    /**
     * 记下这轮属于哪个用户。
     *
     * @param runId 这一轮的编号
     * @param userId 用户主键
     */
    protected void bindOwner(String runId, String userId) {
        redis.opsForValue().set(ownerKey(runId), userId);
    }

    /**
     * 读取这轮的属主。
     *
     * @param runId 这一轮的编号
     * @return 用户主键；没有则空
     */
    protected Optional<String> readOwner(String runId) {
        return Optional.ofNullable(redis.opsForValue().get(ownerKey(runId)));
    }

    /**
     * 取 Redis Stream 操作。模板的泛型在这里收窄。
     *
     * @return 字符串流操作
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private StreamOperations<String, String, String> streams() {
        return (StreamOperations) redis.opsForStream();
    }

    /**
     * 这一轮事件流的键。
     *
     * @param runId 这一轮的编号
     * @return Redis 键
     */
    private static String streamKey(String runId) {
        return "chat:run:" + runId;
    }

    /**
     * 这一轮取消标记的键。
     *
     * @param runId 这一轮的编号
     * @return Redis 键
     */
    private static String cancelKey(String runId) {
        return "chat:run:" + runId + ":cancel";
    }

    /**
     * 这一轮属主的键。
     *
     * @param runId 这一轮的编号
     * @return Redis 键
     */
    private static String ownerKey(String runId) {
        return "chat:run:" + runId + ":owner";
    }

    /**
     * 这一轮事件序号的键。
     *
     * @param runId 这一轮的编号
     * @return Redis 键
     */
    private static String seqKey(String runId) {
        return "chat:run:" + runId + ":seq";
    }
}
