package com.example.crawler.service;


import com.example.shared.entity.Content;
import com.example.shared.entity.Domain;
import com.example.shared.entity.PlatformData;
import com.example.shared.repository.PlatformDataRepository;
import com.example.crawler.rules.MappingRule;
import com.example.crawler.service.similarity.ContentMergeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
@Service
public class UpsertService {
    private final PlatformDataRepository platformRepo;
    private final DomainCoreUpsertService domainCoreUpsert;
    private final ContentUpsertService contentUpsertService;
    private final ContentMergeService contentMergeService;

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
            return null;
        }

        // 1. Content 엔티티 처리 (일단 임시로 생성, 아직 저장 안 함)
        Content newContent = contentUpsertService.buildContent(domain, master);
        
        // 2. 도메인별 상세 정보 구성 (아직 저장 안 함)
        Object domainSpecificData = domainCoreUpsert.buildDomainData(domain, newContent, domainDoc, rule);
        
        // 3. PlatformData 구성 (아직 저장 안 함)
        String platformName = (String) platform.get("platformName");
        @SuppressWarnings("unchecked")
        Map<String, Object> attributes = (Map<String, Object>) platform.get("attributes");
        PlatformData newPlatformData = buildPlatformData(platformName, platformSpecificId, url, attributes);
        
        // 4. 중복 체크 및 병합 (domainDoc와 domainMappings 전달)
        Content existingContent = contentMergeService.findAndMergeDuplicate(
                newContent, 
                domainSpecificData, 
                newPlatformData,
                domainDoc,
                rule.getDomainObjectMappings()
        );
        
        if (existingContent != null) {
            // 중복 발견 -> 기존 작품에 병합됨
            log.info("중복 작품으로 병합됨: {}", existingContent.getContentId());
            return existingContent.getContentId();
        }
        
        // 5. 중복 없음 -> 새로 저장
        Content savedContent = contentUpsertService.saveContent(newContent);
        savePlatformData(savedContent, platformName, platformSpecificId, url, attributes);
        domainCoreUpsert.saveDomainData(domain, savedContent, domainDoc, rule);
        
        log.info("새 작품 저장: {}", savedContent.getContentId());
        return savedContent.getContentId();
    }

    private PlatformData buildPlatformData(String platformName, String platformSpecificId, 
                                          String url, Map<String, Object> attributes) {
        PlatformData pd = new PlatformData();
        pd.setPlatformName(platformName);
        pd.setPlatformSpecificId(platformSpecificId);
        pd.setUrl(url);
        pd.setAttributes(attributes != null ? attributes : Map.of());
        pd.setLastSeenAt(Instant.now());
        return pd;
    }

    private void savePlatformData(Content content, String platformName, String platformSpecificId, 
                                 String url, Map<String, Object> attributes) {
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


