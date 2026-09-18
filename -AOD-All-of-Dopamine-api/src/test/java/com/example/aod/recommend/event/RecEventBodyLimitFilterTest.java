package com.example.AOD.recommend.event;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class RecEventBodyLimitFilterTest {

    private final RecEventBodyLimitFilter filter = new RecEventBodyLimitFilter();

    private MockHttpServletRequest post(String uri, byte[] body) {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", uri);
        req.setRequestURI(uri);
        if (body != null) req.setContent(body);
        return req;
    }

    @Test
    void passesNormalBody() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(post("/api/rec-events", new byte[1024]), res, chain);
        assertNotNull(chain.getRequest(), "체인이 이어져야 한다");
        assertEquals(200, res.getStatus());
    }

    @Test
    void rejectsOversizedBodyWith413() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(post("/api/rec-events", new byte[(int) RecEventBodyLimitFilter.MAX_BODY_BYTES + 1]), res, chain);
        assertEquals(413, res.getStatus());
        assertNull(chain.getRequest(), "본문을 읽기 전에 끊는다");
    }

    @Test
    void rejectsUnknownLengthWith411() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(post("/api/rec-events", null), res, chain);
        assertEquals(411, res.getStatus());
        assertNull(chain.getRequest());
    }

    @Test
    void ignoresOtherPaths() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(post("/api/works/1/like", null), res, chain);
        assertNotNull(chain.getRequest());
    }
}
