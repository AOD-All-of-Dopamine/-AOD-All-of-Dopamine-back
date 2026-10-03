package com.example.crawler.ranking.steam;

import com.example.crawler.contents.game.steam.SteamRateLimiter;
import com.example.shared.featured.ReviewQuotes;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 홈 "오늘의 작품" 히어로용 Steam 호출 — 로고(고정 경로 HEAD) · 한국어 리뷰 목록 · 작성자 이름.
 * 설계: 프론트 docs/superpowers/specs/2026-10-03-home-featured-hero-design.md "크롤러".
 * GetItems 에는 로고 필드가 없어 옛 고정 경로를 쓴다(logo_koreana.png → logo.png).
 */
@Slf4j
@Component
public class SteamHeroClient {

    static final String LOGO_BASE = "https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/";
    static final String REVIEWS_URL = "https://store.steampowered.com/appreviews/{appId}?json=1&language=koreana&filter=all"
            + "&day_range=365&num_per_page=100&purchase_type=all";
    static final String PLAYER_URL = "https://api.steampowered.com/ISteamUser/GetPlayerSummaries/v2/?key={key}&steamids={id}";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final SteamRateLimiter rateLimiter;
    private final String apiKey;

    @Autowired
    public SteamHeroClient(ObjectMapper objectMapper, SteamRateLimiter rateLimiter, @Value("${steam.api.key:}") String apiKey) {
        this(timeoutRestTemplate(), objectMapper, rateLimiter, apiKey);
    }

    SteamHeroClient(RestTemplate restTemplate, ObjectMapper objectMapper, SteamRateLimiter rateLimiter, String apiKey) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.rateLimiter = rateLimiter;
        this.apiKey = apiKey;
    }

    private static RestTemplate timeoutRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(20_000);
        return new RestTemplate(factory);
    }

    /** 로고 — 한국어판이 있으면 그것. 둘 다 없으면 {@code Optional.empty()}(없다고 확인). 네트워크 오류는 예외. */
    public Optional<Logo> fetchLogo(long appId) {
        for (String file : List.of("logo_koreana.png", "logo.png")) {
            String url = LOGO_BASE + appId + "/" + file;
            try {
                // HEAD 는 리다이렉트를 따라가지 않는다 — 2xx 일 때만 로고로 인정(3xx 주소를 저장하지 않게)
                Boolean ok = restTemplate.execute(url, HttpMethod.HEAD, null,
                        response -> response.getStatusCode().is2xxSuccessful());
                if (Boolean.TRUE.equals(ok)) return Optional.of(new Logo(url, file.contains("koreana") ? "ko" : "other"));
            } catch (HttpClientErrorException.NotFound | HttpClientErrorException.Forbidden e) {
                // 다음 후보
            }
        }
        return Optional.empty();
    }

    public record Logo(String url, String lang) { }

    /** 한국어 리뷰 (도움 순, 최근 1년 — Steam 이 day_range 를 365 로 묶는다). 실패는 예외. */
    public List<SteamReview> fetchKoreanReviews(long appId) {
        rateLimiter.acquirePermit();
        String body = restTemplate.getForObject(REVIEWS_URL, String.class, appId);
        List<SteamReview> out = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(body);
            for (JsonNode r : root.path("reviews")) {
                out.add(new SteamReview(
                        r.path("recommendationid").asText(""),
                        r.path("author").path("steamid").asText(""),
                        r.path("review").asText(""),
                        r.path("voted_up").asBoolean(false),
                        r.path("votes_up").asInt(0),
                        r.path("votes_funny").asInt(0),
                        parseDouble(r.path("weighted_vote_score").asText("0")),
                        r.path("author").path("playtime_at_review").asInt(0)));
            }
        } catch (Exception e) {
            throw new IllegalStateException("appreviews 응답 해석 실패 appId=" + appId + ": " + e.getMessage(), e);
        }
        return out;
    }

    private static double parseDouble(String s) {
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** 작성자 이름 — 키가 없거나 실패하면 null(화면은 "Steam 사용자"). */
    public String fetchPersonaName(String steamId) {
        if (apiKey == null || apiKey.isBlank() || steamId == null || steamId.isBlank()) return null;
        try {
            String body = restTemplate.getForObject(PLAYER_URL, String.class, apiKey, steamId);
            String name = objectMapper.readTree(body).path("response").path("players").path(0).path("personaname").asText("").trim();
            if (name.isEmpty()) return null;
            return name.length() > 100 ? name.substring(0, 100) : name;
        } catch (Exception e) {
            log.debug("Steam 작성자 이름 조회 실패 steamId={}: {}", steamId, e.getMessage());
            return null;
        }
    }

    /** {@link ReviewQuotes#pickSteam} 후보. */
    public record SteamReview(String recommendationId, String steamId, String text, boolean votedUp, int votesUp,
                              int votesFunny, double weightedScore, int playtimeAtReviewMinutes)
            implements ReviewQuotes.SteamCandidate { }
}
