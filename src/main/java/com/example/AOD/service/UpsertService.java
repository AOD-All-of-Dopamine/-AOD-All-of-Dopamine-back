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

        String masterTitle = (String) master.get("master_title");
        if (masterTitle == null || masterTitle.isBlank()) {
            log.warn("Master title이 비어있어 해당 항목을 건너뜁니다. PlatformSpecificId: {}", platformSpecificId);
            return null;
        }

        // [수정] 람다에서 사용할 final 변수 생성
        Integer initialYear = (Integer) master.get("release_year");
        if (domain == Domain.AV && domainDoc.get("release_date") instanceof String dateStr) {
            initialYear = parseYearFromDateString(dateStr);
        }
        final Integer finalReleaseYear = initialYear; // 이 변수는 더 이상 변경되지 않음

        // 1) contents (find-or-create)
        Content content = contentRepo
                .findFirstByDomainAndMasterTitleAndReleaseYear(domain,
                        masterTitle,
                        finalReleaseYear) // final 변수 사용
                .orElseGet(() -> {
                    Content c = new Content();
                    c.setDomain(domain);
                    c.setMasterTitle(masterTitle);
                    c.setOriginalTitle((String) master.get("original_title"));
                    c.setReleaseYear(finalReleaseYear); // final 변수 사용
                    c.setPosterImageUrl((String) master.get("poster_image_url"));
                    c.setSynopsis((String) master.get("synopsis"));
                    return contentRepo.save(c);
                });

        if (content.getOriginalTitle()==null)
            content.setOriginalTitle((String) master.getOrDefault("original_title", content.getOriginalTitle()));
        if (content.getPosterImageUrl()==null)
            content.setPosterImageUrl((String) master.getOrDefault("poster_image_url", content.getPosterImageUrl()));
        if (content.getSynopsis()==null)
            content.setSynopsis((String) master.getOrDefault("synopsis", content.getSynopsis()));

        String platformName = (String) platform.get("platformName");
        Optional<PlatformData> existing = platformRepo.findByPlatformNameAndPlatformSpecificId(platformName, platformSpecificId);

        PlatformData pd = existing.orElseGet(PlatformData::new);
        pd.setContent(content);
        pd.setPlatformName(platformName);
        pd.setPlatformSpecificId(platformSpecificId);
        pd.setUrl(url);


        @SuppressWarnings("unchecked")
        Map<String,Object> attrs = (Map<String, Object>) platform.getOrDefault("attributes", Map.of());
        if (pd.getAttributes() == null) {
            pd.setAttributes(attrs);
        } else {
            pd.getAttributes().putAll(attrs);
        }
        pd.setLastSeenAt(Instant.now());
        platformRepo.save(pd);

        domainCoreUpsert.upsert(domain, content, domainDoc);

        return content.getContentId();
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

