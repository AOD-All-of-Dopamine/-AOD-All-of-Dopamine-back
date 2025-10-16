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

        // 3. 엔티티 저장은 Dirty Checking에 의해 자동으로 처리되므로 명시적 save 호출 제거
    }

    private Object findOrCreateDomainEntity(Domain domain, Content content) {
        Long contentId = content.getContentId();
        return switch (domain) {
            case AV -> avRepo.findById(contentId).orElseGet(() -> {
                AvContent entity = new AvContent(content);
                return avRepo.save(entity);
            });
            case GAME -> gameRepo.findById(contentId).orElseGet(() -> {
                GameContent entity = new GameContent(content);
                return gameRepo.save(entity);
            });
            case WEBTOON -> webtoonRepo.findById(contentId).orElseGet(() -> {
                WebtoonContent entity = new WebtoonContent(content);
                return webtoonRepo.save(entity);
            });
            case WEBNOVEL -> webnovelRepo.findById(contentId).orElseGet(() -> {
                WebnovelContent entity = new WebnovelContent(content);
                return webnovelRepo.save(entity);
            });
            default -> null;
        };
    }
}