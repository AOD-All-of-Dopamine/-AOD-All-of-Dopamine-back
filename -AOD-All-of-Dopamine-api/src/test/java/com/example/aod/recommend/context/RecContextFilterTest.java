package com.example.AOD.recommend.context;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class RecContextFilterTest {

    private final RecContextFilter filter = new RecContextFilter();

    @Test
    void readsHeadersDuringRequestAndClearsAfter() throws Exception {
        UUID rid = UUID.randomUUID();
        UUID anon = UUID.randomUUID();
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/works/1/like");
        req.addHeader("X-Rec-Source", "detail");
        req.addHeader("X-Rec-Request-Id", rid.toString());
        req.addHeader("X-Rec-Impression-Id", "not-a-uuid");
        req.addHeader("X-Anon-Id", anon.toString());

        AtomicReference<RecContext> seen = new AtomicReference<>();
        filter.doFilter(req, new MockHttpServletResponse(), (rq, rs) -> seen.set(RecContextHolder.current()));

        assertEquals("detail", seen.get().source());
        assertEquals(rid, seen.get().requestId());
        assertNull(seen.get().impressionId(), "형식이 틀린 uuid 는 null — 요청을 실패시키지 않는다");
        assertEquals(anon, seen.get().anonId());
        assertSame(RecContext.EMPTY, RecContextHolder.current(), "요청이 끝나면 비운다 (스레드 재사용)");
    }

    @Test
    void bodyValuesOverrideHeaders() {
        UUID iid = UUID.randomUUID();
        RecContext fromHeaders = new RecContext("detail", null, null, UUID.randomUUID(), null);

        RecContext merged = fromHeaders.withBody("rec_tab", null, iid.toString());

        assertEquals("rec_tab", merged.source());
        assertEquals(iid, merged.impressionId());
        assertEquals(fromHeaders.anonId(), merged.anonId());
    }
}
