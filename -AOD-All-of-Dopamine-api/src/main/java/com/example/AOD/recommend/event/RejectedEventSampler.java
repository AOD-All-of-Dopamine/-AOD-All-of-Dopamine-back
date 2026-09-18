package com.example.AOD.recommend.event;

import com.example.AOD.recommend.log.LogQueue;
import com.example.AOD.recommend.log.RejectedEventLogRecord;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntSupplier;

/** 거절된 이벤트의 1% 를 aod_log.rejected_event 에 남긴다 (30일 보관) — 스키마 누락 원인을 추적하기 위한 표본. */
@Component
public class RejectedEventSampler {

    static final int SAMPLE_PERCENT = 1;

    private final LogQueue queue;
    private final ObjectMapper objectMapper;
    private final IntSupplier percentDie;   // 0..99
    private final Clock clock;

    @Autowired
    public RejectedEventSampler(LogQueue queue, ObjectMapper objectMapper) {
        this(queue, objectMapper, () -> ThreadLocalRandom.current().nextInt(100), Clock.systemUTC());
    }

    RejectedEventSampler(LogQueue queue, ObjectMapper objectMapper, IntSupplier percentDie, Clock clock) {
        this.queue = queue;
        this.objectMapper = objectMapper;
        this.percentDie = percentDie;
        this.clock = clock;
    }

    /** 표본에 뽑혀 저장했으면 true. raw 는 JSON 으로 직렬화할 수 있는 값(레코드·Map). */
    public boolean maybeStore(Object raw, String reason) {
        if (percentDie.getAsInt() >= SAMPLE_PERCENT) return false;
        String json;
        try {
            json = objectMapper.writeValueAsString(raw);
        } catch (JsonProcessingException e) {
            json = "{}";
        }
        if (json.contains("\\u0000")) json = "{\"note\":\"raw omitted: contains NUL\"}";   // jsonb 는 NUL 을 거부한다
        queue.offer(new RejectedEventLogRecord(json, reason, OffsetDateTime.now(clock)));
        return true;
    }
}
