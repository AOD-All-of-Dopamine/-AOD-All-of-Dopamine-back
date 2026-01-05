package com.example.crawler.service;

import com.example.shared.entity.Content;
import com.example.shared.entity.*;
import com.example.shared.repository.*;
import com.example.crawler.rules.MappingRule;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.function.Supplier;

import org.springframework.data.jpa.repository.JpaRepository;

@Service
@RequiredArgsConstructor
public class DomainCoreUpsertService {

    private final GenericDomainUpserter genericUpserter;

    private final MovieContentRepository movieRepo;
    private final TvContentRepository tvRepo;
    private final GameContentRepository gameRepo;
    private final WebtoonContentRepository webtoonRepo;
    private final WebnovelContentRepository webnovelRepo;

    @Transactional
    public void upsert(Domain domain, Content content, Map<String, Object> domainDoc, MappingRule rule) {
        if (domainDoc == null || domainDoc.isEmpty()) return;

        // 1. 도메인에 맞는 엔티티를 찾거나 새로 생성 (이 부분은 하드코딩이 불가피)
        Object domainEntity = findOrCreateDomainEntity(domain, content);
        if (domainEntity == null) return; // 지원하지 않는 도메인

        // 2. Generic Upserter를 호출하여 필드 값을 동적으로 채움
        genericUpserter.upsert(domainEntity, domainDoc, rule.getDomainObjectMappings());

        // 3. 변경된 내용을 저장 (새로 생성된 엔티티는 선행 save 덕분에 INSERT 되었고, 이후 변경분은 UPDATE로 반영)
        saveDomainEntity(domain, domainEntity);
    }

    /**
     * 도메인 데이터를 구성만 하고 저장하지 않음 (중복 체크용)
     */
    public Object buildDomainData(Domain domain, Content content, Map<String, Object> domainDoc, MappingRule rule) {
        if (domainDoc == null || domainDoc.isEmpty()) return null;

        Object domainEntity = createDomainEntity(domain, content);
        if (domainEntity == null) return null;

        genericUpserter.upsert(domainEntity, domainDoc, rule.getDomainObjectMappings());
        return domainEntity;
    }

    /**
     * 도메인 데이터 저장
     */
    @Transactional
    public void saveDomainData(Domain domain, Content content, Map<String, Object> domainDoc, MappingRule rule) {
        if (domainDoc == null || domainDoc.isEmpty()) return;

        Object domainEntity = findOrCreateDomainEntity(domain, content);
        if (domainEntity == null) return;

        genericUpserter.upsert(domainEntity, domainDoc, rule.getDomainObjectMappings());
        saveDomainEntity(domain, domainEntity);
    }

    private Object createDomainEntity(Domain domain, Content content) {
        return switch (domain) {
            case MOVIE -> new MovieContent(content);
            case TV -> new TvContent(content);
            case GAME -> new GameContent(content);
            case WEBTOON -> new WebtoonContent(content);
            case WEBNOVEL -> new WebnovelContent(content);
        };
    }

    private Object findOrCreateDomainEntity(Domain domain, Content content) {
        Long contentId = content.getContentId();
        if (contentId == null) {
            throw new IllegalStateException("Content ID cannot be null when finding or creating a domain entity.");
        }

        return switch (domain) {
            case MOVIE -> findOrCreate(contentId, movieRepo, () -> new MovieContent(content));
            case TV -> findOrCreate(contentId, tvRepo, () -> new TvContent(content));
            case GAME -> findOrCreate(contentId, gameRepo, () -> new GameContent(content));
            case WEBTOON -> findOrCreate(contentId, webtoonRepo, () -> new WebtoonContent(content));
            case WEBNOVEL -> findOrCreate(contentId, webnovelRepo, () -> new WebnovelContent(content));
        };
    }

    private <T> T findOrCreate(Long id, JpaRepository<T, Long> repository, Supplier<T> creator) {
        return repository.findById(id).orElseGet(() -> repository.save(creator.get()));
    }

    private void saveDomainEntity(Domain domain, Object entity) {
        switch (domain) {
            case MOVIE -> movieRepo.save((MovieContent) entity);
            case TV -> tvRepo.save((TvContent) entity);
            case GAME -> gameRepo.save((GameContent) entity);
            case WEBTOON -> webtoonRepo.save((WebtoonContent) entity);
            case WEBNOVEL -> webnovelRepo.save((WebnovelContent) entity);
        }
    }
}


