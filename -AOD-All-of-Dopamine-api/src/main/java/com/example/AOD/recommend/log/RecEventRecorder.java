package com.example.AOD.recommend.log;

import com.example.AOD.recommend.context.RecContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 이벤트를 조립해 LogQueue 에 넣는다 (REC_TAB_DESIGN §5-2).
 * 서버 이벤트는 트랜잭션 커밋 뒤에 넣는다 — 롤백된 조작이 로그에 남지 않게.
 */
@Slf4j
@Component
public class RecEventRecorder {

    public static final String ORIGIN_SERVER = "server";
    public static final String ORIGIN_CLIENT = "client";
    static final int KNOWN_SESSIONS_MAX = 10_000;

    private final LogQueue queue;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Set<UUID> knownSessions = Collections.synchronizedSet(Collections.newSetFromMap(
            new LinkedHashMap<UUID, Boolean>(256, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<UUID, Boolean> eldest) {
                    return size() > KNOWN_SESSIONS_MAX;
                }
            }));

    @Autowired
    public RecEventRecorder(LogQueue queue, ObjectMapper objectMapper) {
        this(queue, objectMapper, Clock.systemUTC());
    }

    RecEventRecorder(LogQueue queue, ObjectMapper objectMapper, Clock clock) {
        this.queue = queue;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public void reactionChanged(Long userId, Long contentId, String from, String to, RecContext ctx) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("from", from);
        payload.put("to", to);
        payload.put("source", ctx.source());
        serverEvent("reaction_changed", userId, contentId, payload, ctx);
    }

    public void bookmarkChanged(Long userId, Long contentId, boolean on, RecContext ctx) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("state", on ? "on" : "off");
        payload.put("source", ctx.source());
        serverEvent("bookmark_changed", userId, contentId, payload, ctx);
    }

    /** 관심 없음 켜기·끄기 (REC_TAB_DESIGN §2-4). 상태가 실제로 바뀔 때만 부른다. */
    public void notInterestedChanged(Long userId, Long contentId, boolean on, RecContext ctx) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("state", on ? "on" : "off");
        payload.put("source", ctx.source());
        serverEvent("not_interested_changed", userId, contentId, payload, ctx);
    }

    public void reviewSaved(Long userId, Long contentId, Double rating, boolean isNew, RecContext ctx) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("rating", rating);
        payload.put("isNew", isNew);
        payload.put("source", ctx.source());
        serverEvent("review_saved", userId, contentId, payload, ctx);
    }

    /** 검증을 통과한 클라이언트 이벤트. 트랜잭션과 무관하므로 바로 넣는다. */
    public void clientEvent(EventLogRecord record) {
        queue.offer(record);
    }

    /** user_agent 는 세션당 1회만 적재한다 (봇 판별용, 90일 보관). */
    public void clientAgentOnce(UUID sessionId, String userAgent) {
        if (sessionId == null || userAgent == null || userAgent.isBlank()) return;
        String stripped = userAgent.replace("\0", "");
        if (stripped.isBlank()) return;
        if (!knownSessions.add(sessionId)) return;
        String ua = stripped.length() > 512 ? stripped.substring(0, 512) : stripped;
        if (!queue.offer(new ClientAgentLogRecord(sessionId, OffsetDateTime.now(clock), ua))) {
            knownSessions.remove(sessionId);   // 큐가 가득 차 버려졌다 — 다음 묶음에서 다시 시도하게 한다
        }
    }

    private void serverEvent(String type, Long userId, Long contentId, Map<String, Object> payload, RecContext ctx) {
        EventLogRecord record = new EventLogRecord(
                UUID.randomUUID(), OffsetDateTime.now(clock), type, ORIGIN_SERVER,
                userId, ctx.anonId(), ctx.sessionId(), contentId, ctx.requestId(), ctx.impressionId(),
                ctx.source(), toJson(payload), null, null, null);
        offerAfterCommit(record);
    }

    private void offerAfterCommit(LogRecord record) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    queue.offer(record);
                }
            });
        } else {
            queue.offer(record);
        }
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.warn("이벤트 payload 직렬화 실패 — 빈 객체로 대체", e);
            return "{}";
        }
    }
}
