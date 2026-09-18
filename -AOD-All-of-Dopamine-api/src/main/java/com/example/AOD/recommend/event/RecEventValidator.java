package com.example.AOD.recommend.event;

import com.example.AOD.recommend.context.RecContext;
import com.example.AOD.recommend.log.EventLogRecord;
import com.example.AOD.recommend.log.RecEventRecorder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.UUID;

/** 클라이언트 이벤트 1건 검증 → EventLogRecord (REC_TAB_DESIGN §4-1·§6-2). */
@Component
public class RecEventValidator {

    public static final int MAX_EVENTS = 50;
    public static final int MAX_PAYLOAD_CHARS = 4096;

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

        String payloadJson = "{}";
        if (item.payload() != null) {
            try {
                payloadJson = objectMapper.writeValueAsString(item.payload());
            } catch (JsonProcessingException e) {
                return new Rejected("bad_payload");
            }
            if (payloadJson.length() > MAX_PAYLOAD_CHARS) return new Rejected("payload_too_large");
        }

        return new Accepted(new EventLogRecord(
                eventId, header.serverTs(), item.type(), RecEventRecorder.ORIGIN_CLIENT,
                header.userId(), header.anonId(), header.sessionId(), item.contentId(),
                RecContext.parseUuid(item.requestId()), RecContext.parseUuid(item.impressionId()),
                item.surface(), payloadJson, clientTs, header.appVersion(), header.device()));
    }
}
