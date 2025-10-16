package com.example.AOD.service;

import com.example.AOD.domain.Content;
import com.example.AOD.domain.entity.*;
import com.example.AOD.repo.*;
import com.example.AOD.rules.MappingRule;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class DomainCoreUpsertService {

    // 범용 Upserter 주입
    private final GenericDomainUpserter genericUpserter;

    // 엔티티를 찾기 위한 각 도메인의 Repository는 여전히 필요
    private final GameContentRepository gameRepo;
    private final WebtoonContentRepository webtoonRepo;
    private final WebnovelContentRepository webnovelRepo;
    private final AvContentRepository avRepo;

    @Transactional
    public void upsert(Domain domain, Content content, Map<String, Object> domainDoc, MappingRule rule) {
        if (domainDoc == null || domainDoc.isEmpty()) return;

        // 1. 도메인에 맞는 엔티티를 찾거나 새로 생성 (이 부분은 하드코딩이 불가피)
        Object domainEntity = findOrCreateDomainEntity(domain, content);
        if (domainEntity == null) return; // 지원하지 않는 도메인

        // 2. Generic Upserter를 호출하여 필드 값을 동적으로 채움
        genericUpserter.upsert(domainEntity, domainDoc, rule.getDomainObjectMappings());

        // 3. 엔티티 저장
        saveDomainEntity(domain, domainEntity);
    }

    private Object findOrCreateDomainEntity(Domain domain, Content content) {
        Long contentId = content.getContentId();
        return switch (domain) {
            case AV -> avRepo.findById(contentId).orElseGet(() -> new AvContent(content));
            case GAME -> gameRepo.findById(contentId).orElseGet(() -> new GameContent(content));
            case WEBTOON -> webtoonRepo.findById(contentId).orElseGet(() -> new WebtoonContent(content));
            case WEBNOVEL -> webnovelRepo.findById(contentId).orElseGet(() -> new WebnovelContent(content));
            default -> null;
        };
    }

    private void saveDomainEntity(Domain domain, Object entity) {
        switch (domain) {
            case AV -> avRepo.save((AvContent) entity);
            case GAME -> gameRepo.save((GameContent) entity);
            case WEBTOON -> webtoonRepo.save((WebtoonContent) entity);
            case WEBNOVEL -> webnovelRepo.save((WebnovelContent) entity);
        }
    }
}