package com.example.crawler.service.similarity;

import com.example.shared.entity.Content;
import com.example.shared.entity.*;
import com.example.shared.repository.*;
import com.example.crawler.service.GenericDomainUpserter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 중복 작품 탐지 및 병합 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContentMergeService {

    private final ContentRepository contentRepository;
    private final GameContentRepository gameContentRepository;
    private final WebtoonContentRepository webtoonContentRepository;
    private final WebnovelContentRepository webnovelContentRepository;
    private final PlatformDataRepository platformDataRepository;
    private final ContentSimilarityService similarityService;
    private final GenericDomainUpserter genericUpserter;


    /**
     * 중복 가능성이 있는 작품을 찾아서 병합
     * @param newContent 새로 추가하려는 작품
     * @param domainSpecificData 도메인별 상세 정보 (GameContent, WebtoonContent 등)
     * @param platformData 플랫폼 데이터
     * @param domainDoc 원본 도메인 데이터 (병합용)
     * @param domainMappings 도메인 필드 매핑 정보
     * @return 병합된 작품 (기존 작품) 또는 null (중복 없음)
     */
    @Transactional
    public Content findAndMergeDuplicate(Content newContent, 
                                         Object domainSpecificData,
                                         PlatformData platformData,
                                         Map<String, Object> domainDoc,
                                         Map<String, com.example.crawler.rules.DomainObjectMapping> domainMappings) {
        
        log.info("🔍 중복 검사 시작: 제목='{}', Domain={}", newContent.getMasterTitle(), newContent.getDomain());
        
        List<Content> candidates = findDuplicateCandidates(newContent, domainSpecificData);
        if (candidates.isEmpty()) {
            log.info("   ℹ️  중복 후보 없음 - 새 작품으로 저장");
            return null; // No duplicate candidates
        }

        // Iterate candidates and use title-equality (normalized) to decide merge
        for (Content candidate : candidates) {
            boolean same = similarityService.isSameTitle(
                    newContent.getMasterTitle(),
                    candidate.getMasterTitle()
            );

            log.info("🔍 중복 검사: '{}' vs '{}' => sameTitle={}",
                    newContent.getMasterTitle(),
                    candidate.getMasterTitle(),
                    same);

            if (same) {
                log.warn("⚠️  중복 작품 발견 - 제목 일치: {}", candidate.getMasterTitle());
                log.info("🔄 병합 진행: '{}' 데이터를 기존 작품(ID={})에 추가",
                        newContent.getMasterTitle(),
                        candidate.getContentId());

                mergeContent(candidate, newContent, domainSpecificData, platformData, domainDoc, domainMappings);

                log.info("✅ 중복 작품 병합 완료: 기존 ID={}", candidate.getContentId());
                return candidate; // return the merged existing content
            }
        }

        log.info("   ❌ 중복 작품 없음 - 새로운 작품으로 처리");
        return null; // No duplicates found
    }

    /**
     * 중복 후보 작품 찾기
     * - 같은 domain
     * - 같은 author/developer
     */
    private List<Content> findDuplicateCandidates(Content newContent, Object domainSpecificData) {
        List<Content> candidates = new ArrayList<>();
        
        Domain domain = newContent.getDomain();
        
        log.debug("      도메인별 중복 후보 검색 시작: Domain={}", domain);
        
        switch (domain) {
            case GAME:
                if (domainSpecificData instanceof GameContent) {
                    GameContent gameContent = (GameContent) domainSpecificData;
                    String developer = gameContent.getDeveloper();
                    
                    log.debug("      [GAME] developer: '{}'", developer);
                    
                    if (developer != null && !developer.isBlank()) {
                        List<GameContent> games = gameContentRepository.findByDeveloper(developer);
                        games.forEach(gc -> candidates.add(gc.getContent()));
                        log.debug("      [GAME] developer로 {}개 작품 발견", games.size());
                    } else {
                        log.warn("      ⚠️  [GAME] developer 정보 없음 - 중복 검사 불가");
                    }
                } else {
                    log.warn("      ⚠️  [GAME] GameContent 타입이 아님: {}", 
                            domainSpecificData != null ? domainSpecificData.getClass().getSimpleName() : "null");
                }
                break;
                
            case WEBTOON:
                if (domainSpecificData instanceof WebtoonContent) {
                    WebtoonContent webtoonContent = (WebtoonContent) domainSpecificData;
                    String author = webtoonContent.getAuthor();
                    
                    log.debug("      [WEBTOON] author: '{}'", author);
                    
                    if (author != null && !author.isBlank()) {
                        List<WebtoonContent> webtoons = webtoonContentRepository.findByAuthor(author);
                        webtoons.forEach(wc -> candidates.add(wc.getContent()));
                        log.debug("      [WEBTOON] author로 {}개 작품 발견", webtoons.size());
                    } else {
                        log.warn("      ⚠️  [WEBTOON] author 정보 없음 - 중복 검사 불가");
                    }
                } else {
                    log.warn("      ⚠️  [WEBTOON] WebtoonContent 타입이 아님: {}", 
                            domainSpecificData != null ? domainSpecificData.getClass().getSimpleName() : "null");
                }
                break;
                
            case WEBNOVEL:
                if (domainSpecificData instanceof WebnovelContent) {
                    WebnovelContent novelContent = (WebnovelContent) domainSpecificData;
                    String author = novelContent.getAuthor();
                    
                    log.debug("      [WEBNOVEL] author: '{}'", author);
                    
                    if (author != null && !author.isBlank()) {
                        List<WebnovelContent> novels = webnovelContentRepository.findByAuthor(author);
                        novels.forEach(nc -> candidates.add(nc.getContent()));
                        log.debug("      [WEBNOVEL] author로 {}개 작품 발견", novels.size());
                    } else {
                        log.warn("      ⚠️  [WEBNOVEL] author 정보 없음 - 중복 검사 불가");
                    }
                } else {
                    log.warn("      ⚠️  [WEBNOVEL] WebnovelContent 타입이 아님: {}", 
                            domainSpecificData != null ? domainSpecificData.getClass().getSimpleName() : "null");
                }
                break;
                
            default:
                log.debug("      [{}] 중복 검사 미지원 도메인", domain);
                break;
        }
        
        log.debug("   📊 중복 후보 검색 완료: {}개 (domain: {}, title: '{}')", 
                candidates.size(), domain, newContent.getMasterTitle());
        
        return candidates;
    }

    /**
     * 기존 작품에 새 정보를 병합
     * - 플랫폼 정보 추가
     * - 누락된 필드 보완
     */
    @Transactional
    public void mergeContent(Content existingContent, 
                            Content newContent,
                            Object domainSpecificData,
                            PlatformData newPlatformData,
                            Map<String, Object> domainDoc,
                            Map<String, com.example.crawler.rules.DomainObjectMapping> domainMappings) {
        
        log.info("📝 작품 병합 시작");
        log.info("   기존 작품: ID={}, 제목='{}', Domain={}", 
                existingContent.getContentId(), 
                existingContent.getMasterTitle(),
                existingContent.getDomain());
        log.info("   신규 데이터: 제목='{}', originalTitle='{}'", 
                newContent.getMasterTitle(),
                newContent.getOriginalTitle());
        
        // 1. Content 기본 정보 업데이트 (null이 아닌 값만)
        boolean updated = false;
        if (existingContent.getOriginalTitle() == null && newContent.getOriginalTitle() != null) {
            existingContent.setOriginalTitle(newContent.getOriginalTitle());
            log.info("   ➕ originalTitle 추가: '{}'", newContent.getOriginalTitle());
            updated = true;
        }
        if (existingContent.getReleaseDate() == null && newContent.getReleaseDate() != null) {
            existingContent.setReleaseDate(newContent.getReleaseDate());
            log.info("   ➕ releaseDate 추가: {}", newContent.getReleaseDate());
            updated = true;
        }
        if (existingContent.getPosterImageUrl() == null && newContent.getPosterImageUrl() != null) {
            existingContent.setPosterImageUrl(newContent.getPosterImageUrl());
            log.info("   ➕ posterImageUrl 추가");
            updated = true;
        }
        if (existingContent.getSynopsis() == null && newContent.getSynopsis() != null) {
            existingContent.setSynopsis(newContent.getSynopsis());
            log.info("   ➕ synopsis 추가");
            updated = true;
        }
        
        if (updated) {
            contentRepository.save(existingContent);
            log.info("   💾 Content 기본 정보 업데이트 완료");
        } else {
            log.debug("   ℹ️  업데이트할 기본 정보 없음 (모두 이미 존재)");
        }
        
        // 2. 플랫폼 정보 추가 (중복 체크)
        if (newPlatformData != null) {
            boolean platformExists = platformDataRepository
                    .findByPlatformNameAndPlatformSpecificId(
                            newPlatformData.getPlatformName(),
                            newPlatformData.getPlatformSpecificId()
                    )
                    .isPresent();
            
            if (!platformExists) {
                newPlatformData.setContent(existingContent);
                platformDataRepository.save(newPlatformData);
                log.info("   ➕ 새 플랫폼 정보 추가: {} (ID: {})", 
                        newPlatformData.getPlatformName(),
                        newPlatformData.getPlatformSpecificId());
            } else {
                log.debug("   ℹ️  플랫폼 정보 이미 존재: {} ({})", 
                        newPlatformData.getPlatformName(),
                        newPlatformData.getPlatformSpecificId());
            }
        }
        
        // 3. 도메인별 상세 정보 병합 (GenericDomainUpserter 사용)
        mergeDomainSpecificData(existingContent, domainDoc, domainMappings);
        
        log.info("✅ 작품 병합 완료: ID={}, 최종 제목='{}'", 
                existingContent.getContentId(),
                existingContent.getMasterTitle());
    }

    /**
     * 도메인별 상세 정보 병합 (GenericDomainUpserter 사용)
     */
    private void mergeDomainSpecificData(Content existingContent, 
                                         Map<String, Object> domainDoc,
                                         Map<String, com.example.crawler.rules.DomainObjectMapping> domainMappings) {
        
        if (domainDoc == null || domainDoc.isEmpty() || domainMappings == null) {
            log.debug("   ℹ️  병합할 도메인 데이터 없음");
            return;
        }
        
        Domain domain = existingContent.getDomain();
        log.debug("   🔧 도메인별 상세 정보 병합 시작: {}", domain);
        
        switch (domain) {
            case GAME:
                GameContent existingGame = gameContentRepository.findById(existingContent.getContentId())
                        .orElse(null);
                if (existingGame != null) {
                    genericUpserter.upsert(existingGame, domainDoc, domainMappings);
                    gameContentRepository.save(existingGame);
                    log.debug("      💾 GameContent 병합 및 저장 완료");
                }
                break;
                
            case WEBTOON:
                WebtoonContent existingWebtoon = webtoonContentRepository.findById(existingContent.getContentId())
                        .orElse(null);
                if (existingWebtoon != null) {
                    genericUpserter.upsert(existingWebtoon, domainDoc, domainMappings);
                    webtoonContentRepository.save(existingWebtoon);
                    log.debug("      💾 WebtoonContent 병합 및 저장 완료");
                }
                break;
                
            case WEBNOVEL:
                WebnovelContent existingNovel = webnovelContentRepository.findById(existingContent.getContentId())
                        .orElse(null);
                if (existingNovel != null) {
                    genericUpserter.upsert(existingNovel, domainDoc, domainMappings);
                    webnovelContentRepository.save(existingNovel);
                    log.debug("      💾 WebnovelContent 병합 및 저장 완료");
                }
                break;
                
            default:
                log.debug("      [{}] 병합 미지원 도메인", domain);
                break;
        }
    }
}



