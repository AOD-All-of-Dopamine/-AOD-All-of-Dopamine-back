package com.example.AOD.recommend.log;

import java.time.OffsetDateTime;
import java.util.UUID;

/** event_id 전역 중복 제거 대상. LogWriter 가 aod_log.event_seen 을 먼저 통과시킨 것만 본 테이블에 넣는다. */
public interface DedupedLogRecord extends LogRecord {
    UUID eventId();
    OffsetDateTime serverTs();
}
