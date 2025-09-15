// src/main/java/com/example/AOD/service/UpsertService.java

package com.example.AOD.service;


import com.example.AOD.domain.Content;
import com.example.AOD.domain.entity.Domain;
import com.example.AOD.domain.entity.PlatformData;
import com.example.AOD.repo.ContentRepository;
import com.example.AOD.repo.PlatformDataRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class UpsertService {
    private final ContentRepository contentRepo;
    private final PlatformDataRepository platformRepo;
    private final DomainCoreUpsertService domainCoreUpsert;

    public UpsertService(ContentRepository contentRepo,
                         PlatformDataRepository platformRepo,
                         DomainCoreUpsertService domainCoreUpsert) {
        this.contentRepo = contentRepo;
        this.platformRepo = platformRepo;
        this.domainCoreUpsert = domainCoreUpsert;
    }

    @Transactional
    public Long upsert(Domain domain,
                       Map<String,Object> master,
                       Map<String,Object> platform,
                       Map<String,Object> domainDoc,
                       String platformSpecificId,
                       String url) {

        // --- [ 1. 이 부분을 메서드 상단으로 이동 및 수정 ] ---
        // poster_path를 완전한 URL로 먼저 조립
        String platformName = (String) platform.get("platformName");
        if (domain == Domain.AV && "TMDB".equals(platformName)) {
            String posterPath = (String) master.get("poster_image_url");
            if (posterPath != null && !posterPath.isBlank() && !posterPath.startsWith("http")) {
                master.put("poster_image_url", "https://image.tmdb.org/t/p/w500" + posterPath);
            }
        }
        // ---

        String masterTitle = (String) master.get("master_title");
        if (masterTitle == null || masterTitle.isBlank()) {
            log.warn("Master title이 비어있어 해당 항목을 건너뜁니다. PlatformSpecificId: {}", platformSpecificId);
            return null;
        }

        Integer initialYear = (master.get("release_year") instanceof Number) ? ((Number) master.get("release_year")).intValue() : null;
        if (domain == Domain.AV && domainDoc.get("release_date") instanceof String dateStr) {
            initialYear = parseYearFromDateString(dateStr);
        }
        final Integer finalReleaseYear = initialYear;

        Content content = contentRepo
                .findFirstByDomainAndMasterTitleAndReleaseYear(domain,
                        masterTitle,
                        finalReleaseYear)
                .orElseGet(() -> {
                    Content c = new Content();
                    c.setDomain(domain);
                    c.setMasterTitle(masterTitle);
                    c.setOriginalTitle((String) master.get("original_title"));
                    c.setReleaseYear(finalReleaseYear); // 생성 시점에 연도 저장
                    c.setPosterImageUrl((String) master.get("poster_image_url")); // 생성 시점에 포스터 URL 저장
                    c.setSynopsis((String) master.get("synopsis"));
                    return contentRepo.save(c);
                });

        // --- [ 2. 이 부분을 추가/수정 ] ---
        // 기존 콘텐츠의 정보가 null일 경우에만 업데이트
        if (content.getReleaseYear() == null && finalReleaseYear != null) {
            content.setReleaseYear(finalReleaseYear);
        }
        if (content.getOriginalTitle() == null)
            content.setOriginalTitle((String) master.getOrDefault("original_title", content.getOriginalTitle()));
        if (content.getPosterImageUrl() == null)
            content.setPosterImageUrl((String) master.getOrDefault("poster_image_url", content.getPosterImageUrl()));
        if (content.getSynopsis() == null)
            content.setSynopsis((String) master.getOrDefault("synopsis", content.getSynopsis()));
        // ---



        // TMDB에서 수집된 AV 콘텐츠인 경우, Watch Provider를 별도의 PlatformData로 저장
        if (domain == Domain.AV && "TMDB".equals(platformName)) {
            Map<String, Object> attributes = (Map<String, Object>) platform.getOrDefault("attributes", Map.of());
            Map<String, Map<String, Object>> watchProviders = (Map<String, Map<String, Object>>) attributes.get("watch_providers");

            if (watchProviders != null && watchProviders.containsKey("KR")) {
                Map<String, Object> krProviders = watchProviders.get("KR");
                List<Map<String, Object>> flatrate = (List<Map<String, Object>>) krProviders.get("flatrate");

                if (flatrate != null) {
                    for (Map<String, Object> provider : flatrate) {
                        String providerName = (String) provider.get("provider_name");
                        String providerUrl = (String) krProviders.get("link");
                        // 각 OTT 플랫폼을 별도의 PlatformData로 저장
                        savePlatformData(content, providerName, platformSpecificId, providerUrl, Map.of("provider_info", provider));
                    }
                }
            }
        } else {
            // TMDB가 아닌 다른 플랫폼은 기존 방식대로 저장
            savePlatformData(content, platformName, platformSpecificId, url, (Map<String, Object>) platform.get("attributes"));
        }

        domainCoreUpsert.upsert(domain, content, domainDoc);

        return content.getContentId();
    }

    // PlatformData 저장을 위한 헬퍼 메서드
    private void savePlatformData(Content content, String platformName, String platformSpecificId, String url, Map<String, Object> attributes) {
        // 동일한 콘텐츠에 동일한 플랫폼 정보가 중복 저장되는 것을 방지
        Optional<PlatformData> existing = platformRepo.findByPlatformNameAndPlatformSpecificId(platformName, platformSpecificId);

        PlatformData pd = existing.orElseGet(PlatformData::new);

        pd.setContent(content);
        pd.setPlatformName(platformName);
        pd.setPlatformSpecificId(platformSpecificId);
        pd.setUrl(url);
        pd.setAttributes(attributes != null ? attributes : Map.of());
        pd.setLastSeenAt(Instant.now());

        platformRepo.save(pd);
    }

    private Integer parseYearFromDateString(String dateString) {
        if (dateString == null || dateString.length() < 4) {
            return null;
        }
        try {
            return Integer.parseInt(dateString.substring(0, 4));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}