package com.example.AOD.recommend.catalog;

import com.example.AOD.recommend.key.CorpusKey;
import com.example.AOD.recommend.key.CorpusKeyMapper;
import com.example.shared.repository.PlatformDataRepository;
import com.example.shared.repository.PlatformKeyRow;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 추천 엔진에 줄 "백엔드에 있는 작품" 키 목록 (설계 §10).
 *
 * 왜 필요한가: 코퍼스가 백엔드 카탈로그보다 훨씬 커서(로컬 실측: 코퍼스 중 DB 에 있는 비율이
 * Steam 0.51% · 웹소설 0.21% · 웹툰 2.7%) 후보 50개를 받아도 카드가 0~1장이다. 엔진이 후보를
 * 이 목록으로 좁히면 버퍼가 낭비되지 않는다.
 *
 * 엔진 컨테이너 4개가 10분마다 받아 가므로 호출당 쿼리 하나로 끝내고 60초만 캐시한다.
 * CacheConfig 의 ConcurrentMapCacheManager 는 만료가 없어 여기에 쓸 수 없고,
 * 만료 캐시를 위해 새 의존성(Caffeine 등)을 들이지 않는다 — 항목이 4개뿐이라 맵 하나면 충분하다.
 */
@Service
public class CatalogKeyService {

    static final Duration TTL = Duration.ofSeconds(60);

    private record Cached(String body, long expiresAtMs) { }

    private final PlatformDataRepository platformDataRepository;
    private final Clock clock;
    private final ConcurrentHashMap<String, Cached> cache = new ConcurrentHashMap<>();

    @Autowired
    public CatalogKeyService(PlatformDataRepository platformDataRepository) {
        this(platformDataRepository, Clock.systemUTC());
    }

    CatalogKeyService(PlatformDataRepository platformDataRepository, Clock clock) {
        this.platformDataRepository = platformDataRepository;
        this.clock = clock;
    }

    /**
     * 한 줄에 키 하나, 사전순. 성인 작품과 ID 가 빈 행은 빠진다.
     * @throws IllegalArgumentException 라우터가 모르는 platform
     */
    public String keysText(String routerPlatform) {
        List<String> platformNames = CorpusKeyMapper.platformNamesOf(routerPlatform);
        if (platformNames.isEmpty()) throw new IllegalArgumentException("unknown platform: " + routerPlatform);

        long now = clock.millis();
        Cached cached = cache.get(routerPlatform);
        if (cached != null && cached.expiresAtMs() > now) return cached.body();

        String body = build(routerPlatform, platformNames);
        cache.put(routerPlatform, new Cached(body, now + TTL.toMillis()));
        return body;
    }

    private String build(String routerPlatform, List<String> platformNames) {
        TreeSet<String> keys = new TreeSet<>();
        for (PlatformKeyRow row : platformDataRepository.findCatalogKeyRows(platformNames)) {
            CorpusKey key = CorpusKeyMapper.toKey(row.platformName(), row.platformSpecificId());
            if (key != null && key.platform().equals(routerPlatform)) keys.add(key.key());
        }
        StringBuilder sb = new StringBuilder(keys.size() * 12);
        for (String key : keys) sb.append(key).append('\n');
        return sb.toString();
    }
}
