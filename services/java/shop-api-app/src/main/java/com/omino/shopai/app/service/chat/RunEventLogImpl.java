package com.omino.shopai.app.service.chat;

import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
/**
 * 用 Redis Stream 保存一轮的事件。
 */

@Repository
public class RunEventLogImpl implements IRunEventLog {

    private final StringRedisTemplate redis;

    public RunEventLogImpl(StringRedisTemplate redis) {
        this.redis = redis;
    }
/**
 * 把事件追加进该 run 的流，供任意副本的 SSE 读取。
 */

    @Override
    public ChatEvents.Event append(String runId, String event, String data) {
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
 * 从给定序号之后读，泵中断后可以续上。
 */

    @Override
    public List<ChatEvents.Event> after(String runId, String afterId) {
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

    @Override
    public void markCancel(String runId) {
        redis.opsForValue().set(cancelKey(runId), "1", ChatEvents.TERMINAL_TTL);
    }

    @Override
    public boolean cancelled(String runId) {
        return "1".equals(redis.opsForValue().get(cancelKey(runId)));
    }

    @Override
    public boolean terminal(String runId) {
        return after(runId, null).stream().anyMatch(event -> ChatEvents.TERMINAL.contains(event.event()));
    }
/**
 * 终态后给流一个过期时间，避免 Redis 被旧 run 撑满。
 */

    @Override
    public void expire(String runId) {
        redis.expire(streamKey(runId), ChatEvents.TERMINAL_TTL);
        redis.expire(cancelKey(runId), ChatEvents.TERMINAL_TTL);
        redis.expire(ownerKey(runId), ChatEvents.TERMINAL_TTL);
        redis.expire(seqKey(runId), ChatEvents.TERMINAL_TTL);
    }
/**
 * 读剩余存活时间。没有键则按已结束处理。
 */

    @Override
    public Duration ttl(String runId) {
        Long seconds = redis.getExpire(streamKey(runId));
        if (seconds == null || seconds < 0) {
            return null;
        }
        return Duration.ofSeconds(seconds);
    }

    @Override
    public void bind(String runId, String userId) {
        redis.opsForValue().set(ownerKey(runId), userId);
    }

    @Override
    public Optional<String> owner(String runId) {
        return Optional.ofNullable(redis.opsForValue().get(ownerKey(runId)));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private StreamOperations<String, String, String> streams() {
        return (StreamOperations) redis.opsForStream();
    }

    private static String streamKey(String runId) {
        return "chat:run:" + runId;
    }

    private static String cancelKey(String runId) {
        return "chat:run:" + runId + ":cancel";
    }

    private static String ownerKey(String runId) {
        return "chat:run:" + runId + ":owner";
    }

    private static String seqKey(String runId) {
        return "chat:run:" + runId + ":seq";
    }
}
