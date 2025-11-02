package com.example.AOD.service.similarity;

import com.example.AOD.domain.Content;
import com.example.AOD.domain.entity.*;
import com.example.AOD.repo.*;
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

    private static final double SIMILARITY_THRESHOLD = 0.85;

    /**
     * 중복 가능성이 있는 작품을 찾아서 병합
     * @param newContent 새로 추가하려는 작품
     * @param domainSpecificData 도메인별 상세 정보 (GameContent, WebtoonContent 등)
     * @param platformData 플랫폼 데이터
     * @return 병합된 작품 (기존 작품) 또는 null (중복 없음)
     */
    @Transactional
    public Content findAndMergeDuplicate(Content newContent, 
                                         Object domainSpecificData,
                                         PlatformData platformData) {
        
        log.info("🔍 중복 검사 시작: 제목='{}', Domain={}", newContent.getMasterTitle(), newContent.getDomain());
        
        List<Content> candidates = findDuplicateCandidates(newContent, domainSpecificData);
        
        if (candidates.isEmpty()) {
            log.info("   ℹ️  중복 후보 없음 - 새 작품으로 저장");
            return null;
        }

        log.info("   📋 중복 후보 {}개 발견 - 유사도 검사 시작", candidates.size());

        // 유사도 검사
        for (Content candidate : candidates) {
            double similarity = similarityService.calculateSimilarity(
                    newContent.getMasterTitle(), 
                    candidate.getMasterTitle()
            );
            
            log.info("🔍 중복 검사: '{}' vs '{}' = {}% (임계값: {}%)", 
                    newContent.getMasterTitle(), 
                    candidate.getMasterTitle(), 
                    String.format("%.2f", similarity * 100),
                    (int)(SIMILARITY_THRESHOLD * 100));
            
            if (similarity >= SIMILARITY_THRESHOLD) {
                log.warn("⚠️  중복 작품 발견! 같은 작품으로 판단됨 - 유사도: {}%", 
                        String.format("%.2f", similarity * 100));
                log.warn("    기존 작품: ID={}, 제목='{}', Domain={}", 
                        candidate.getContentId(), 
                        candidate.getMasterTitle(),
                        candidate.getDomain());
                log.warn("    신규 데이터: 제목='{}', Domain={}", 
                        newContent.getMasterTitle(),
                        newContent.getDomain());
                log.info("🔄 병합 진행: '{}' 데이터를 기존 작품(ID={})에 추가", 
                        newContent.getMasterTitle(), 
                        candidate.getContentId());
                
                mergeContent(candidate, newContent, domainSpecificData, platformData);
                
                log.info("✅ 중복 작품 병합 완료: 기존 ID={}", candidate.getContentId());
                return candidate; // 기존 작품 반환
            }
        }
        
        log.info("   ❌ 유사도 임계값 미달 - 중복 없음으로 판단");
        return null; // 중복 없음
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
                            PlatformData newPlatformData) {
        
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
        
        // 3. 도메인별 상세 정보 병합
        mergeDomainSpecificData(existingContent, domainSpecificData);
        
        log.info("✅ 작품 병합 완료: ID={}, 최종 제목='{}'", 
                existingContent.getContentId(),
                existingContent.getMasterTitle());
    }

    /**
     * 도메인별 상세 정보 병합
     */
    private void mergeDomainSpecificData(Content existingContent, Object newDomainData) {
        Domain domain = existingContent.getDomain();
        log.debug("   🔧 도메인별 상세 정보 병합 시작: {}", domain);
        
        switch (domain) {
            case GAME:
                if (newDomainData instanceof GameContent) {
                    GameContent newGame = (GameContent) newDomainData;
                    GameContent existingGame = gameContentRepository.findById(existingContent.getContentId())
                            .orElse(null);
                    
                    if (existingGame != null) {
                        boolean domainUpdated = false;
                        if (existingGame.getPublisher() == null && newGame.getPublisher() != null) {
                            existingGame.setPublisher(newGame.getPublisher());
                            log.info("      ➕ [GAME] publisher 추가: '{}'", newGame.getPublisher());
                            domainUpdated = true;
                        }
                        if (existingGame.getReleaseDate() == null && newGame.getReleaseDate() != null) {
                            existingGame.setReleaseDate(newGame.getReleaseDate());
                            log.info("      ➕ [GAME] releaseDate 추가: {}", newGame.getReleaseDate());
                            domainUpdated = true;
                        }
                        // 플랫폼 정보 병합 (Map)
                        if (newGame.getPlatforms() != null) {
                            Map<String, Object> existingPlatforms = existingGame.getPlatforms();
                            if (existingPlatforms == null) {
                                existingGame.setPlatforms(newGame.getPlatforms());
                                log.info("      ➕ [GAME] platforms 추가: {} 항목", newGame.getPlatforms().size());
                                domainUpdated = true;
                            } else {
                                int beforeSize = existingPlatforms.size();
                                existingPlatforms.putAll(newGame.getPlatforms());
                                int afterSize = existingPlatforms.size();
                                if (afterSize > beforeSize) {
                                    log.info("      ➕ [GAME] platforms 병합: {}개 추가 (총 {}개)", 
                                            afterSize - beforeSize, afterSize);
                                    domainUpdated = true;
                                }
                            }
                        }
                        // 장르 정보 병합
                        if (newGame.getGenres() != null) {
                            Map<String, Object> existingGenres = existingGame.getGenres();
                            if (existingGenres == null) {
                                existingGame.setGenres(newGame.getGenres());
                                log.info("      ➕ [GAME] genres 추가: {} 항목", newGame.getGenres().size());
                                domainUpdated = true;
                            } else {
                                int beforeSize = existingGenres.size();
                                existingGenres.putAll(newGame.getGenres());
                                int afterSize = existingGenres.size();
                                if (afterSize > beforeSize) {
                                    log.info("      ➕ [GAME] genres 병합: {}개 추가 (총 {}개)", 
                                            afterSize - beforeSize, afterSize);
                                    domainUpdated = true;
                                }
                            }
                        }
                        if (domainUpdated) {
                            gameContentRepository.save(existingGame);
                            log.debug("      💾 GameContent 저장 완료");
                        }
                    }
                }
                break;
                
            case WEBTOON:
                if (newDomainData instanceof WebtoonContent) {
                    WebtoonContent newWebtoon = (WebtoonContent) newDomainData;
                    WebtoonContent existingWebtoon = webtoonContentRepository.findById(existingContent.getContentId())
                            .orElse(null);
                    
                    if (existingWebtoon != null) {
                        boolean domainUpdated = false;
                        if (existingWebtoon.getIllustrator() == null && newWebtoon.getIllustrator() != null) {
                            existingWebtoon.setIllustrator(newWebtoon.getIllustrator());
                            log.info("      ➕ [WEBTOON] illustrator 추가: '{}'", newWebtoon.getIllustrator());
                            domainUpdated = true;
                        }
                        if (existingWebtoon.getStatus() == null && newWebtoon.getStatus() != null) {
                            existingWebtoon.setStatus(newWebtoon.getStatus());
                            log.info("      ➕ [WEBTOON] status 추가: '{}'", newWebtoon.getStatus());
                            domainUpdated = true;
                        }
                        if (existingWebtoon.getStartedAt() == null && newWebtoon.getStartedAt() != null) {
                            existingWebtoon.setStartedAt(newWebtoon.getStartedAt());
                            log.info("      ➕ [WEBTOON] startedAt 추가: {}", newWebtoon.getStartedAt());
                            domainUpdated = true;
                        }
                        if (newWebtoon.getGenres() != null) {
                            Map<String, Object> existingGenres = existingWebtoon.getGenres();
                            if (existingGenres == null) {
                                existingWebtoon.setGenres(newWebtoon.getGenres());
                                log.info("      ➕ [WEBTOON] genres 추가: {} 항목", newWebtoon.getGenres().size());
                                domainUpdated = true;
                            } else {
                                int beforeSize = existingGenres.size();
                                existingGenres.putAll(newWebtoon.getGenres());
                                int afterSize = existingGenres.size();
                                if (afterSize > beforeSize) {
                                    log.info("      ➕ [WEBTOON] genres 병합: {}개 추가 (총 {}개)", 
                                            afterSize - beforeSize, afterSize);
                                    domainUpdated = true;
                                }
                            }
                        }
                        if (domainUpdated) {
                            webtoonContentRepository.save(existingWebtoon);
                            log.debug("      💾 WebtoonContent 저장 완료");
                        }
                    }
                }
                break;
                
            case WEBNOVEL:
                if (newDomainData instanceof WebnovelContent) {
                    WebnovelContent newNovel = (WebnovelContent) newDomainData;
                    WebnovelContent existingNovel = webnovelContentRepository.findById(existingContent.getContentId())
                            .orElse(null);
                    
                    if (existingNovel != null) {
                        boolean domainUpdated = false;
                        if (existingNovel.getPublisher() == null && newNovel.getPublisher() != null) {
                            existingNovel.setPublisher(newNovel.getPublisher());
                            log.info("      ➕ [WEBNOVEL] publisher 추가: '{}'", newNovel.getPublisher());
                            domainUpdated = true;
                        }
                        if (existingNovel.getAgeRating() == null && newNovel.getAgeRating() != null) {
                            existingNovel.setAgeRating(newNovel.getAgeRating());
                            log.info("      ➕ [WEBNOVEL] ageRating 추가: '{}'", newNovel.getAgeRating());
                            domainUpdated = true;
                        }
                        if (existingNovel.getStartedAt() == null && newNovel.getStartedAt() != null) {
                            existingNovel.setStartedAt(newNovel.getStartedAt());
                            log.info("      ➕ [WEBNOVEL] startedAt 추가: {}", newNovel.getStartedAt());
                            domainUpdated = true;
                        }
                        if (newNovel.getGenres() != null && !newNovel.getGenres().isEmpty()) {
                            if (existingNovel.getGenres() == null) {
                                existingNovel.setGenres(newNovel.getGenres());
                                log.info("      ➕ [WEBNOVEL] genres 추가: {} 항목", newNovel.getGenres().size());
                                domainUpdated = true;
                            } else {
                                // List 병합 (중복 제거)
                                List<String> merged = new ArrayList<>(existingNovel.getGenres());
                                int beforeSize = merged.size();
                                for (String genre : newNovel.getGenres()) {
                                    if (!merged.contains(genre)) {
                                        merged.add(genre);
                                    }
                                }
                                int afterSize = merged.size();
                                if (afterSize > beforeSize) {
                                    existingNovel.setGenres(merged);
                                    log.info("      ➕ [WEBNOVEL] genres 병합: {}개 추가 (총 {}개)", 
                                            afterSize - beforeSize, afterSize);
                                    domainUpdated = true;
                                }
                            }
                        }
                        if (domainUpdated) {
                            webnovelContentRepository.save(existingNovel);
                            log.debug("      💾 WebnovelContent 저장 완료");
                        }
                    }
                }
                break;
                
            default:
                break;
        }
    }
}
