package com.example.AOD.recommend.event;

import com.example.AOD.recommend.log.EventLogRecord;
import com.example.AOD.recommend.log.RecEventRecorder;
import com.example.AOD.security.JwtTokenProvider;
import com.example.AOD.security.SecurityConfig;
import com.example.AOD.user.model.User;
import com.example.AOD.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = RecEventController.class)
@Import({SecurityConfig.class, RecEventValidator.class})
class RecEventControllerTest {

    @Autowired MockMvc mvc;
    @MockBean JwtTokenProvider jwtTokenProvider;
    @MockBean UserRepository userRepository;
    @MockBean RecEventRateLimiter rateLimiter;
    @MockBean RejectedEventSampler sampler;
    @MockBean RecEventRecorder recorder;

    private final String anonId = UUID.randomUUID().toString();
    private final String sessionId = UUID.randomUUID().toString();

    @BeforeEach
    void allowByDefault() {
        given(rateLimiter.allow(anyString())).willReturn(true);
    }

    private String batch(String eventsJson) {
        return "{\"anonId\":\"" + anonId + "\",\"sessionId\":\"" + sessionId + "\","
                + "\"appVersion\":\"1.0.0\",\"device\":\"desktop\",\"events\":[" + eventsJson + "]}";
    }

    private String event(String type) {
        return "{\"eventId\":\"" + UUID.randomUUID() + "\",\"type\":\"" + type + "\","
                + "\"clientTs\":\"2026-09-18T00:00:01Z\",\"contentId\":123,\"payload\":{\"visible_ms\":1200}}";
    }

    @Test
    void acceptsTextPlainBeaconAndCountsAcceptedAndRejected() throws Exception {
        mvc.perform(post("/api/rec-events")
                        .contentType("text/plain;charset=UTF-8")
                        .header("User-Agent", "Mozilla/5.0")
                        .content(batch(event("impression_viewed") + "," + event("reaction_changed"))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.accepted").value(1))
                .andExpect(jsonPath("$.rejected").value(1));

        ArgumentCaptor<EventLogRecord> captor = ArgumentCaptor.forClass(EventLogRecord.class);
        verify(recorder).clientEvent(captor.capture());
        assertEquals("impression_viewed", captor.getValue().eventType());
        assertEquals(UUID.fromString(anonId), captor.getValue().anonId());
        assertEquals(null, captor.getValue().userId());
        verify(sampler).maybeStore(any(), eq("type_not_allowed"));
        verify(recorder).clientAgentOnce(UUID.fromString(sessionId), "Mozilla/5.0");
    }

    @Test
    void attachesUserIdWhenTokenIsValid() throws Exception {
        User user = new User();
        user.setId(7L);
        given(jwtTokenProvider.validateToken("good")).willReturn(true);
        given(jwtTokenProvider.getUsername("good")).willReturn("tester");
        given(userRepository.findByUsername("tester")).willReturn(Optional.of(user));

        mvc.perform(post("/api/rec-events")
                        .contentType("application/json")
                        .header("Authorization", "Bearer good")
                        .content(batch(event("card_clicked"))))
                .andExpect(status().isAccepted());

        ArgumentCaptor<EventLogRecord> captor = ArgumentCaptor.forClass(EventLogRecord.class);
        verify(recorder).clientEvent(captor.capture());
        assertEquals(7L, captor.getValue().userId());
    }

    @Test
    void rejectsMalformedBodies() throws Exception {
        mvc.perform(post("/api/rec-events").contentType("text/plain").content("not json"))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/rec-events").contentType("text/plain")
                        .content("{\"anonId\":\"nope\",\"sessionId\":\"" + sessionId + "\",\"events\":[" + event("card_clicked") + "]}"))
                .andExpect(status().isBadRequest());

        StringBuilder many = new StringBuilder();
        for (int i = 0; i < 51; i++) many.append(i == 0 ? "" : ",").append(event("card_clicked"));
        mvc.perform(post("/api/rec-events").contentType("text/plain").content(batch(many.toString())))
                .andExpect(status().isBadRequest());

        verify(recorder, never()).clientEvent(any());
    }

    @Test
    void rateLimitedIs429() throws Exception {
        given(rateLimiter.allow(anonId)).willReturn(false);

        mvc.perform(post("/api/rec-events").contentType("text/plain").content(batch(event("card_clicked"))))
                .andExpect(status().isTooManyRequests());

        verify(recorder, never()).clientEvent(any());
    }
}
