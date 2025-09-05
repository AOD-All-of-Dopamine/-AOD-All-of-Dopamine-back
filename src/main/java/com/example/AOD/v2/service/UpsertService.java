package com.example.AOD.v2.service;


import com.example.AOD.v2.domain.Content;
import com.example.AOD.v2.domain.entity.Domain;
import com.example.AOD.v2.domain.entity.PlatformData;
import com.example.AOD.v2.repo.ContentRepository;
import com.example.AOD.v2.repo.PlatformDataRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

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

        // 1) contents (find-or-create)
        Content content = contentRepo
                .findFirstByDomainAndMasterTitleAndReleaseYear(domain,
                        (String) master.get("master_title"),
                        (Integer) master.get("release_year"))
                .orElseGet(() -> {
                    Content c = new Content();
                    c.setDomain(domain);
                    c.setMasterTitle((String) master.get("master_title"));
                    c.setOriginalTitle((String) master.get("original_title"));
                    c.setReleaseYear((Integer) master.get("release_year"));
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

        // 2) platform_data (UPSERT by unique (platformName, platformSpecificId))
        String platformName = (String) platform.get("platformName");
        Optional<PlatformData> existing = platformRepo.findByPlatformNameAndPlatformSpecificId(platformName, platformSpecificId);

        PlatformData pd = existing.orElseGet(PlatformData::new);
        pd.setContent(content);
        pd.setPlatformName(platformName);
        pd.setPlatformSpecificId(platformSpecificId);
        pd.setUrl(url);

        // rating/reviewCount (있으면 세팅)
        Object rating = platform.get("rating");
        if (rating instanceof Number n) pd.setRating(new BigDecimal(n.toString()));
        Object rc = platform.get("review_count");
        if (rc instanceof Number n) pd.setReviewCount(n.intValue());

        // attributes merge
        @SuppressWarnings("unchecked")
        Map<String,Object> attrs = (Map<String, Object>) platform.getOrDefault("attributes", Map.of());
        if (pd.getAttributes() == null) {
            pd.setAttributes(attrs);
        } else {
            pd.getAttributes().putAll(attrs);
        }
        pd.setLastSeenAt(Instant.now());
        // 3) 도메인 코어 upsert
        domainCoreUpsert.upsert(domain, content, domainDoc);

        platformRepo.save(pd);
        return content.getContentId();
    }
}
