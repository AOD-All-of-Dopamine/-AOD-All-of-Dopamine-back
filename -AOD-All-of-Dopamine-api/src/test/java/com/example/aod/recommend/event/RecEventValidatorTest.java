package com.example.AOD.recommend.event;

import com.example.AOD.recommend.log.EventLogRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecEventValidatorTest {

    private final RecEventValidator validator = new RecEventValidator(new ObjectMapper());
    private final RecEventValidator.BatchHeader header = new RecEventValidator.BatchHeader(
            UUID.randomUUID(), UUID.randomUUID(), 7L, "1.2.3", "mobile",
            OffsetDateTime.of(2026, 9, 18, 0, 0, 0, 0, ZoneOffset.UTC));

    private RecEventItem item(String type, String eventId, String clientTs, Map<String, Object> payload) {
        return new RecEventItem(eventId, type, clientTs, null, UUID.randomUUID().toString(), 123L, "for_you", payload);
    }

    @Test
    void acceptsWhitelistedEventAndFillsBatchFields() {
        String eventId = UUID.randomUUID().toString();
        RecEventValidator.Outcome out = validator.validate(
                item("impression_viewed", eventId, "2026-09-18T00:00:01Z", Map.of("visible_ms", 1200)), header);

        EventLogRecord r = assertInstanceOf(RecEventValidator.Accepted.class, out).record();
        assertEquals(UUID.fromString(eventId), r.eventId());
        assertEquals("client", r.origin());
        assertEquals(7L, r.userId());
        assertEquals(header.anonId(), r.anonId());
        assertEquals(123L, r.contentId());
        assertEquals("mobile", r.device());
        assertTrue(r.payloadJson().contains("\"visible_ms\":1200"));
        assertEquals(header.serverTs(), r.serverTs());
    }

    @Test
    void rejectsServerOnlyAndUnknownTypes() {
        String id = UUID.randomUUID().toString();
        assertEquals("type_not_allowed", reason(validator.validate(item("reaction_changed", id, "2026-09-18T00:00:01Z", null), header)));
        assertEquals("type_not_allowed", reason(validator.validate(item("anything", id, "2026-09-18T00:00:01Z", null), header)));
    }

    @Test
    void rejectsBadIdsTimestampsAndOversizedPayload() {
        String id = UUID.randomUUID().toString();
        assertEquals("bad_event_id", reason(validator.validate(item("card_clicked", "nope", "2026-09-18T00:00:01Z", null), header)));
        assertEquals("bad_client_ts", reason(validator.validate(item("card_clicked", id, "yesterday", null), header)));
        assertEquals("bad_client_ts", reason(validator.validate(item("card_clicked", id, null, null), header)));
        assertEquals("payload_too_large", reason(validator.validate(
                item("card_clicked", id, "2026-09-18T00:00:01Z", Map.of("blob", "x".repeat(5000))), header)));
        assertEquals("null_event", reason(validator.validate(null, header)));
    }

    @Test
    void rejectsValuesPostgresWouldRefuse() {
        String id = UUID.randomUUID().toString();
        assertEquals("bad_payload", reason(validator.validate(
                item("card_clicked", id, "2026-09-18T00:00:01Z", Map.of("k", "a\0b")), header)));
        assertEquals("client_ts_out_of_range", reason(validator.validate(
                item("card_clicked", id, "+999999999-01-01T00:00:00Z", null), header)));
        assertEquals("client_ts_out_of_range", reason(validator.validate(
                item("card_clicked", id, "2026-09-01T00:00:00Z", null), header)));   // 17일 전
    }

    @Test
    void cleansNulAndOverlongFreeText() {
        assertEquals("ab", RecEventValidator.clean("a\0b", 64));
        assertEquals("abc", RecEventValidator.clean("abcdef", 3));
        assertEquals(null, RecEventValidator.clean(null, 3));
    }

    private static String reason(RecEventValidator.Outcome out) {
        return assertInstanceOf(RecEventValidator.Rejected.class, out).reason();
    }
}
