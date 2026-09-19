package com.example.AOD.recommend.event;

import com.example.AOD.recommend.context.RecContext;
import com.example.AOD.recommend.log.EventLogRecord;
import com.example.AOD.recommend.log.RecEventRecorder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.UUID;

/** 클라이언트 이벤트 1건 검증 → EventLogRecord (REC_TAB_DESIGN §4-1·§6-2). */
@Component
public class RecEventValidator {

    public static final int MAX_EVENTS = 50;
    public static final int MAX_PAYLOAD_CHARS = 4096;

    static final Duration MAX_CLIENT_TS_PAST = Duration.ofDays(7);
    static final Duration MAX_CLIENT_TS_FUTURE = Duration.ofDays(1);

    /** text 컬럼은 NUL(0x00)을 거부한다. 한 행이 거부되면 배치 전체(서버 이벤트 포함)가 실패하므로 여기서 지우고 길이도 자른다. */
    public static String clean(String s, int maxLength) {
        if (s == null) return null;
        String out = s.indexOf('\0') >= 0 ? s.replace("\0", "") : s;
        return out.length() > maxLength ? out.substring(0, maxLength) : out;
    }

    /** 묶음 공통 값. serverTs 는 묶음 수신 시각 하나를 모든 이벤트에 쓴다. */
    public record BatchHeader(UUID anonId, UUID sessionId, Long userId, String appVersion, String device,
                              OffsetDateTime serverTs) {
    }

    public interface Outcome { }

    public record Accepted(EventLogRecord record) implements Outcome { }

    public record Rejected(String reason) implements Outcome { }

    private final ObjectMapper objectMapper;

    public RecEventValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Outcome validate(RecEventItem item, BatchHeader header) {
        if (item == null) return new Rejected("null_event");

        UUID eventId = RecContext.parseUuid(item.eventId());
        if (eventId == null) return new Rejected("bad_event_id");

        if (item.type() == null || !RecEventTypes.CLIENT_TYPES.contains(item.type())) {
            return new Rejected("type_not_allowed");
        }

        OffsetDateTime clientTs;
        try {
            clientTs = item.clientTs() == null ? null : OffsetDateTime.parse(item.clientTs());
        } catch (DateTimeParseException e) {
            clientTs = null;
        }
        if (clientTs == null) return new Rejected("bad_client_ts");

        // timestamptz 범위를 넘는 값(예: 999999999년)은 INSERT 를 실패시킨다. 시계가 크게 틀어진 기기도 분석에 쓸모없다.
        if (clientTs.isBefore(header.serverTs().minus(MAX_CLIENT_TS_PAST))
                || clientTs.isAfter(header.serverTs().plus(MAX_CLIENT_TS_FUTURE))) {
            return new Rejected("client_ts_out_of_range");
        }

        String payloadJson = "{}";
        if (item.payload() != null) {
            try {
                payloadJson = objectMapper.writeValueAsString(item.payload());
            } catch (JsonProcessingException e) {
                return new Rejected("bad_payload");
            }
            if (payloadJson.length() > MAX_PAYLOAD_CHARS) return new Rejected("payload_too_large");
            // jsonb 는 NUL(\0) 을 거부한다 ("unsupported Unicode escape sequence")
            if (payloadJson.contains("\\u0000")) return new Rejected("bad_payload");
        }

        return new Accepted(new EventLogRecord(
                eventId, header.serverTs(), item.type(), RecEventRecorder.ORIGIN_CLIENT,
                header.userId(), header.anonId(), header.sessionId(), item.contentId(),
                RecContext.parseUuid(item.requestId()), RecContext.parseUuid(item.impressionId()),
                clean(item.surface(), 64), payloadJson, clientTs, clean(header.appVersion(), 64), clean(header.device(), 32)));
    }
}
