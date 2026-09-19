package com.example.AOD.recommend.router;

import com.example.AOD.recommend.router.dto.RouterItem;
import com.example.AOD.recommend.router.dto.RouterRequest;
import com.example.AOD.recommend.router.dto.RouterResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.net.SocketTimeoutException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RecRouterClientTest {

    /** 반열림 시험(30초 대기)을 기다리지 않고 돌리려고 시계를 손으로 민다. */
    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-09-19T12:00:00Z");

        void advanceMillis(long millis) { now = now.plusMillis(millis); }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private static final String URL = "http://router.test:18080/v1/recommend";
    private static final String OK_BODY = """
            {"items":[{"platform":"steam","key":"240","rank":0,"dominantSeed":"730",
                       "candidateSource":"content_sim","isExploration":false,"propensity":1.0,
                       "score":{"final":1.19,"sim":0.58,"factors":{"rec_pct":0.99}},
                       "factorSchema":"steam.v1"}],
             "exhausted":{"steam":false},"droppedSeeds":{"steam":["999"]},"partial":["tmdb"],
             "versions":{"router":"c8ec317","engines":{"steam":{"sha":"c8ec317","config":"a10c","corpus":"tags_full"}}}}
            """;

    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private RecCircuitBreaker breaker;
    private RecRouterClient client;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.createServer(restTemplate);
        breaker = new RecCircuitBreaker();
        client = new RecRouterClient(restTemplate, breaker, "http://router.test:18080/", 20);
    }

    private static RouterRequest request() {
        return new RouterRequest("all", 20, 30,
                Map.of("steam", List.of("730")), Map.of(), Map.of(), Map.of());
    }

    @Test
    void parsesCamelCaseResponse() {
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.tab").value("all"))
                .andExpect(jsonPath("$.k").value(20))
                .andExpect(jsonPath("$.buffer").value(30))
                .andExpect(jsonPath("$.seeds.steam[0]").value("730"))
                .andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));

        RouterResult result = client.recommend(request());
        server.verify();

        assertTrue(result.ok());
        assertNull(result.failure());
        RouterResponse body = result.response();
        RouterItem item = body.items().get(0);
        assertEquals("steam", item.platform());
        assertEquals("240", item.key());
        assertEquals("730", item.dominantSeed());
        assertEquals("content_sim", item.candidateSource());
        assertEquals("steam.v1", item.factorSchema());
        assertEquals(1.19, item.score().finalScore(), 1e-9);
        assertEquals(0.99, item.score().factors().get("rec_pct"), 1e-9);
        assertEquals(false, body.exhausted().get("steam"));
        assertEquals(List.of("999"), body.droppedSeeds().get("steam"));
        assertEquals(List.of("tmdb"), body.partial());
        assertEquals("c8ec317", body.versions().router());
        assertEquals("tags_full", body.versions().engines().get("steam").corpus());
    }

    @Test
    void unknownFieldsDoNotBreakParsing() {
        server.expect(requestTo(URL)).andRespond(withSuccess(
                "{\"items\":[],\"exhausted\":{},\"versions\":{\"router\":\"x\"},\"somethingNew\":1}",
                MediaType.APPLICATION_JSON));

        assertTrue(client.recommend(request()).ok());
    }

    @Test
    void enginesUnavailable503IsServiceError() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":\"engines_unavailable\",\"partial\":[\"steam\"]}"));

        RouterResult result = client.recommend(request());

        assertFalse(result.ok());
        assertEquals(RouterResult.SERVICE_ERROR, result.failure());
    }

    @Test
    void readTimeoutIsTimeout() {
        server.expect(requestTo(URL)).andRespond(request -> {
            throw new SocketTimeoutException("Read timed out");
        });

        assertEquals(RouterResult.TIMEOUT, client.recommend(request()).failure());
    }

    @Test
    void connectFailureIsServiceError() {
        server.expect(requestTo(URL)).andRespond(request -> {
            throw new java.net.ConnectException("Connection refused");
        });

        assertEquals(RouterResult.SERVICE_ERROR, client.recommend(request()).failure());
    }

    @Test
    void malformedBodyIsServiceError() {
        server.expect(requestTo(URL)).andRespond(withSuccess("not json at all", MediaType.APPLICATION_JSON));

        assertEquals(RouterResult.SERVICE_ERROR, client.recommend(request()).failure());
    }

    @Test
    void openCircuitShortCircuitsWithoutCallingRouter() {
        for (int i = 0; i < RecCircuitBreaker.FAILURE_THRESHOLD; i++) breaker.recordFailure();

        RouterResult result = client.recommend(request());

        assertEquals(RouterResult.CIRCUIT_OPEN, result.failure());
        server.verify();   // 아무 요청도 기대하지 않았고, 아무것도 가지 않았다
    }

    @Test
    void clientErrorIsServiceErrorButNeverTripsTheCircuit() {
        for (int i = 0; i < RecCircuitBreaker.FAILURE_THRESHOLD; i++) {
            server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"detail\":\"seeds: too many keys\"}"));
        }

        for (int i = 0; i < RecCircuitBreaker.FAILURE_THRESHOLD; i++) {
            assertEquals(RouterResult.SERVICE_ERROR, client.recommend(request()).failure());
        }

        assertFalse(breaker.isOpen(), "4xx 는 우리 요청 모양이 틀린 것 — 라우터를 죽었다고 보면 안 된다");
        server.verify();
    }

    @Test
    void unexpectedRuntimeExceptionIsServiceErrorAndReleasesTheHalfOpenProbe() {
        MutableClock clock = new MutableClock();
        RecCircuitBreaker clocked = new RecCircuitBreaker(clock);
        RecRouterClient probing = new RecRouterClient(restTemplate, clocked, "http://router.test:18080", 20);
        for (int i = 0; i < RecCircuitBreaker.FAILURE_THRESHOLD; i++) clocked.recordFailure();
        assertTrue(clocked.isOpen());

        // MockRestServiceServer 는 첫 요청 뒤에 기대를 더 걸 수 없어 두 건을 먼저 선언한다(순서대로 소비된다).
        server.expect(requestTo(URL)).andRespond(request -> {
            throw new IllegalStateException("요청 직렬화 폭발");
        });
        server.expect(requestTo(URL)).andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));

        // 반열림 시험 1건이 RestClientException 이 아닌 예외로 죽는다
        clock.advanceMillis(RecCircuitBreaker.OPEN_MS);
        assertEquals(RouterResult.SERVICE_ERROR, probing.recommend(request()).failure());

        // 시험권이 새면 여기서부터 영원히 CIRCUIT_OPEN 이 된다
        clock.advanceMillis(RecCircuitBreaker.OPEN_MS);
        assertTrue(probing.recommend(request()).ok(), "30초 뒤에는 다시 시험할 수 있어야 한다");
        server.verify();
    }

    @Test
    void clientErrorDuringTheProbeGivesTheTestTicketBack() {
        MutableClock clock = new MutableClock();
        RecCircuitBreaker clocked = new RecCircuitBreaker(clock);
        RecRouterClient probing = new RecRouterClient(restTemplate, clocked, "http://router.test:18080", 20);
        for (int i = 0; i < RecCircuitBreaker.FAILURE_THRESHOLD; i++) clocked.recordFailure();

        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY).body("{}"));
        server.expect(requestTo(URL)).andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));

        clock.advanceMillis(RecCircuitBreaker.OPEN_MS);
        assertEquals(RouterResult.SERVICE_ERROR, probing.recommend(request()).failure());

        clock.advanceMillis(RecCircuitBreaker.OPEN_MS);
        assertTrue(probing.recommend(request()).ok(), "판정 없이 끝난 시험도 시험권을 돌려준다");
        server.verify();
    }

    @Test
    void noPermitIsServiceErrorAndDoesNotTripTheCircuit() {
        RecRouterClient saturated = new RecRouterClient(restTemplate, breaker, "http://router.test:18080", 0);

        RouterResult result = saturated.recommend(request());

        assertEquals(RouterResult.SERVICE_ERROR, result.failure());
        assertFalse(breaker.isOpen(), "라우터에 닿지도 못한 건 라우터 장애가 아니다");
        server.verify();
    }
}
