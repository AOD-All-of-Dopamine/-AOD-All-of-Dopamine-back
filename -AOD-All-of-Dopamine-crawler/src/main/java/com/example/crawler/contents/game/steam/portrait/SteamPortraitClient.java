package com.example.crawler.contents.game.steam.portrait;

import com.example.crawler.contents.game.steam.SteamRateLimiter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Steam 세로 표지(라이브러리 캡슐 600×900) 조회 — 공개 API {@code IStoreBrowseService/GetItems}(키 없음).
 * 설계: 프론트 docs/superpowers/specs/2026-10-01-game-portrait-cover-design.md "SteamPortraitClient".
 *
 * <p>표지 주소는 해시가 낀 경로라 규칙으로 만들 수 없다 — {@code assets.asset_url_format} 의 {@code ${FILENAME}} 자리에
 * {@code assets.library_capsule}(1x) 를 넣는다. {@code language=koreana} 면 한국어 표지가 있으면 그것을 준다.
 */
@Component
public class SteamPortraitClient {

    /** 한 번에 묻는 최대 개수 — input_json 이 URL 에 들어가 200개면 약 6KB. */
    public static final int MAX_BATCH = 200;
    static final String ENDPOINT = "https://api.steampowered.com/IStoreBrowseService/GetItems/v1";
    static final String ASSET_BASE = "https://shared.akamai.steamstatic.com/store_item_assets/";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final SteamRateLimiter rateLimiter;

    @Autowired
    public SteamPortraitClient(ObjectMapper objectMapper, SteamRateLimiter rateLimiter) {
        // 공용 RestTemplate 은 읽기 타임아웃이 없다 — 응답이 멈추면 동기화가 영영 끝나지 않는다
        this(timeoutRestTemplate(), objectMapper, rateLimiter);
    }

    SteamPortraitClient(RestTemplate restTemplate, ObjectMapper objectMapper, SteamRateLimiter rateLimiter) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.rateLimiter = rateLimiter;
    }

    private static RestTemplate timeoutRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(30_000);
        return new RestTemplate(factory);
    }

    /** 앱 하나의 결과. {@code portraitUrl} 이 null 이면 표지 없음(없는 앱 · 숨김 앱 포함) — 그래도 "확인함"이다. */
    public record Portrait(long appId, String portraitUrl) { }

    /**
     * 묻고 답을 appid 로 돌려준다. 응답에 빠진 appid 는 결과에 없다(확인 안 함 — 다음에 다시).
     *
     * @throws SteamPortraitException 호출 실패 · 타임아웃 · 4xx/5xx · JSON 오류 — 묶음 전체를 확인 안 한 것으로 둔다
     */
    public Map<Long, Portrait> fetch(List<Long> appIds) {
        if (appIds.isEmpty()) return Map.of();
        if (appIds.size() > MAX_BATCH) throw new IllegalArgumentException("최대 " + MAX_BATCH + "개: " + appIds.size());
        String body;
        try {
            rateLimiter.acquirePermit();
            body = restTemplate.getForObject(uri(appIds), String.class);
        } catch (Exception e) {
            throw new SteamPortraitException("GetItems 호출 실패: " + e.getMessage(), e);
        }
        return parse(body);
    }

    URI uri(List<Long> appIds) {
        ObjectNode input = objectMapper.createObjectNode();
        ArrayNode ids = input.putArray("ids");
        for (Long id : appIds) ids.addObject().put("appid", id);
        input.putObject("context").put("language", "koreana").put("country_code", "KR");
        input.putObject("data_request").put("include_assets", true);
        // 문자열 URL 에 JSON 을 그대로 넣으면 {…} 가 URI 변수로 해석된다 — 변수로 넘겨 값 전체를 인코딩한다
        return UriComponentsBuilder.fromHttpUrl(ENDPOINT)
                .queryParam("input_json", "{input}")
                .encode()
                .buildAndExpand(input.toString())
                .toUri();
    }

    /** 넓은 배경 그림 한 장(홈 "오늘의 작품" 히어로). {@code heroUrl} 이 null 이면 없다고 확인. */
    public record Assets(long appId, String heroUrl) { }

    /**
     * 히어로 배경 — {@code assets.library_hero}(1x 1920×620). 세로 표지 {@link #fetch} 와 같은 호출 · 같은 규칙.
     * 응답에 빠진 appid 는 결과에 없다(확인 안 함).
     *
     * @throws SteamPortraitException 호출 실패 — 묶음 전체를 확인 안 한 것으로 둔다
     */
    public Map<Long, Assets> fetchAssets(List<Long> appIds) {
        if (appIds.isEmpty()) return Map.of();
        if (appIds.size() > MAX_BATCH) throw new IllegalArgumentException("최대 " + MAX_BATCH + "개: " + appIds.size());
        String body;
        try {
            rateLimiter.acquirePermit();
            body = restTemplate.getForObject(uri(appIds), String.class);
        } catch (Exception e) {
            throw new SteamPortraitException("GetItems 호출 실패: " + e.getMessage(), e);
        }
        Map<Long, Assets> out = new HashMap<>();
        for (JsonNode item : items(body)) {
            long id = item.path("id").asLong(0);
            if (id <= 0) continue;
            out.put(id, new Assets(id, item.path("success").asInt() == 1 ? assetUrl(item.path("assets"), "library_hero") : null));
        }
        return out;
    }

    private JsonNode items(String body) {
        JsonNode items;
        try {
            items = objectMapper.readTree(body).path("response").path("store_items");
        } catch (Exception e) {
            throw new SteamPortraitException("GetItems 응답 해석 실패: " + e.getMessage(), e);
        }
        if (!items.isArray()) throw new SteamPortraitException("GetItems 응답에 store_items 가 없다", null);
        return items;
    }

    Map<Long, Portrait> parse(String body) {
        JsonNode items;
        try {
            items = objectMapper.readTree(body).path("response").path("store_items");
        } catch (Exception e) {
            throw new SteamPortraitException("GetItems 응답 해석 실패: " + e.getMessage(), e);
        }
        if (!items.isArray()) throw new SteamPortraitException("GetItems 응답에 store_items 가 없다", null);
        Map<Long, Portrait> out = new HashMap<>();
        for (JsonNode item : items) {
            // 없는 앱은 appid 가 0 으로 온다 — 요청한 값은 id 에 있다
            long id = item.path("id").asLong(0);
            if (id <= 0) continue;
            out.put(id, new Portrait(id, item.path("success").asInt() == 1 ? capsuleUrl(item.path("assets")) : null));
        }
        return out;
    }

    static String capsuleUrl(JsonNode assets) {
        return assetUrl(assets, "library_capsule");
    }

    static String assetUrl(JsonNode assets, String key) {
        String format = assets.path("asset_url_format").asText("");
        String file = assets.path(key).asText("");
        if (format.isBlank() || file.isBlank() || !format.contains("${FILENAME}")) return null;
        return ASSET_BASE + format.replace("${FILENAME}", file);
    }

    public static class SteamPortraitException extends RuntimeException {
        public SteamPortraitException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
