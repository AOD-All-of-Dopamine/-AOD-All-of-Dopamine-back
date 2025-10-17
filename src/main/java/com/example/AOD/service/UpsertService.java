package com.example.AOD.service;


import com.example.AOD.domain.Content;
import com.example.AOD.domain.entity.Domain;
import com.example.AOD.domain.entity.PlatformData;
import com.example.AOD.repo.PlatformDataRepository;
import com.example.AOD.rules.MappingRule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RequiredArgsConstructor // 생성자 주입을 위해 변경
@Service
public class UpsertService {
    private final PlatformDataRepository platformRepo;
    private final DomainCoreUpsertService domainCoreUpsert;
    private final ContentUpsertService contentUpsertService;

    @Transactional
    public Long upsert(Domain domain,
                       Map<String,Object> master,
                       Map<String,Object> platform,
                       Map<String,Object> domainDoc,
                       String platformSpecificId,
                       String url,
                       MappingRule rule) {

        String masterTitle = (String) master.get("master_title");
        if (masterTitle == null || masterTitle.isBlank()) {
            log.warn("Master title이 비어있어 해당 항목을 건너뜁니다. PlatformSpecificId: {}", platformSpecificId);
            return null; // 제목이 없으면 처리하지 않음
        }

        // 1. Content 엔티티 처리 로직을 ContentUpsertService에 위임
        Content content = contentUpsertService.findOrCreateContent(domain, master);

        // 2. PlatformData 저장
        String platformName = (String) platform.get("platformName");
        @SuppressWarnings("unchecked")
        Map<String, Object> attributes = (Map<String, Object>) platform.get("attributes");
        savePlatformData(content, platformName, platformSpecificId, url, attributes);

        // 3. 도메인별 상세 정보 저장
        domainCoreUpsert.upsert(domain, content, domainDoc, rule);

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
}