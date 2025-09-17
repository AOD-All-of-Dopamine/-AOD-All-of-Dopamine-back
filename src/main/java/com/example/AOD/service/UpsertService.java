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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

        String platformName = (String) platform.get("platformName");
        if (domain == Domain.AV && "TMDB".equals(platformName)) {
            String posterPath = (String) master.get("poster_image_url");
            if (posterPath != null && !posterPath.isBlank() && !posterPath.startsWith("http")) {
                master.put("poster_image_url", "https://image.tmdb.org/t/p/w500" + posterPath);
            }
        }

        String masterTitle = (String) master.get("master_title");
        if (masterTitle == null || masterTitle.isBlank()) {
            log.warn("Master title이 비어있어 해당 항목을 건너뜁니다. PlatformSpecificId: {}", platformSpecificId);
            return null;
        }

        Integer initialYear = (master.get("release_year") instanceof Number) ? ((Number) master.get("release_year")).intValue() : null;
        if ((domain == Domain.AV || domain == Domain.GAME) && domainDoc.get("release_date") instanceof String dateStr) {
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
                    c.setReleaseYear(finalReleaseYear);
                    c.setPosterImageUrl((String) master.get("poster_image_url"));
                    c.setSynopsis((String) master.get("synopsis"));
                    return contentRepo.save(c);
                });

        if (content.getReleaseYear() == null && finalReleaseYear != null) {
            content.setReleaseYear(finalReleaseYear);
        }
        if (content.getOriginalTitle() == null)
            content.setOriginalTitle((String) master.getOrDefault("original_title", content.getOriginalTitle()));
        if (content.getPosterImageUrl() == null)
            content.setPosterImageUrl((String) master.getOrDefault("poster_image_url", content.getPosterImageUrl()));
        if (content.getSynopsis() == null)
            content.setSynopsis((String) master.getOrDefault("synopsis", content.getSynopsis()));


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
                        savePlatformData(content, providerName, platformSpecificId, providerUrl, Map.of("provider_info", provider));
                    }
                }
            }
        } else {
            savePlatformData(content, platformName, platformSpecificId, url, (Map<String, Object>) platform.get("attributes"));
        }

        domainCoreUpsert.upsert(domain, content, domainDoc);

        return content.getContentId();
    }

    private void savePlatformData(Content content, String platformName, String platformSpecificId, String url, Map<String, Object> attributes) {
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
        if (dateString == null || dateString.isBlank()) {
            return null;
        }
        // [수정] "YYYY년 MM월 dd일" 형식 또는 "YYYY-MM-DD" 형식 모두 처리
        Matcher matcher = Pattern.compile("^(\\d{4})").matcher(dateString.trim());
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}