package com.example.AOD.common.service;

import com.example.AOD.Novel.NaverSeriesNovel.repository.NaverSeriesNovelRepository;
import com.example.AOD.common.commonDomain.*;
import com.example.AOD.common.config.ContentIntegrationConfig;
import com.example.AOD.common.config.CustomFieldCalculation;
import com.example.AOD.common.config.FieldMapping;
import com.example.AOD.common.repository.*;
import com.example.AOD.game.StreamAPI.repository.GameRepository;
import com.example.AOD.movie.CGV.repository.MovieRepository;
import com.example.AOD.OTT.Netflix.repository.NetflixContentRepository;
import com.example.AOD.Webtoon.NaverWebtoon.repository.WebtoonRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ContentIntegrationService {

    // Configuration 리포지토리
    private final ContentIntegrationConfigRepository configRepository;
    private final FieldMappingRepository fieldMappingRepository;
    private final CustomFieldCalculationRepository customFieldCalculationRepository;

    // 플랫폼별 리포지토리
    private final NaverSeriesNovelRepository naverSeriesNovelRepository;
    private final MovieRepository movieRepository;
    private final NetflixContentRepository netflixContentRepository;
    private final WebtoonRepository webtoonRepository;
    private final GameRepository steamGameRepository;

    // Common 엔티티 리포지토리
    private final NovelCommonRepository novelCommonRepository;
    private final MovieCommonRepository movieCommonRepository;
    private final OTTCommonRepository ottCommonRepository;
    private final WebtoonCommonRepository webtoonCommonRepository;
    private final GameCommonRepository gameCommonRepository;

    // PlatformMapping 리포지토리
    private final NovelPlatformMappingRepository novelMappingRepository;
    private final MoviePlatformMappingRepository movieMappingRepository;
    private final OTTPlatformMappingRepository ottMappingRepository;
    private final WebtoonPlatformMappingRepository webtoonMappingRepository;
    private final GamePlatformMappingRepository gameMappingRepository;

    /**
     * 콘텐츠 통합 메인 메서드
     */
    @Transactional
    public List<Object> integrateContent(Long configId, List<Long> sourceIds) {
        ContentIntegrationConfig config = configRepository.findById(configId)
                .orElseThrow(() -> new RuntimeException("Configuration not found"));

        String contentType = config.getContentType();
        List<Object> results = new ArrayList<>();

        if (sourceIds == null || sourceIds.isEmpty()) {
            return results;
        }

        // 각 소스 ID에 대해 개별적으로 통합 처리
        for (Long sourceId : sourceIds) {
            try {
                Object result = integrateSingleContent(config, contentType, sourceId);
                if (result != null) {
                    results.add(result);
                }
            } catch (Exception e) {
                log.error("Failed to integrate content with ID {}: {}", sourceId, e.getMessage());
                // 개별 실패는 전체 프로세스를 중단하지 않음
            }
        }

        return results;
    }

    /**
     * 단일 콘텐츠 통합
     */
    @Transactional
    protected Object integrateSingleContent(ContentIntegrationConfig config, String contentType, Long sourceId) {
        // 플랫폼 데이터 로드
        Map<String, Object> sourceData = loadSingleSourceData(contentType, sourceId);
        if (sourceData.isEmpty()) {
            return null;
        }

        // 플랫폼 정보 추출
        String platform = sourceData.keySet().iterator().next().split("_")[0];
        Object platformContent = sourceData.values().iterator().next();

        // 제목 추출
        String title = extractTitle(platformContent);

        // 기존 Common 엔티티 확인 또는 생성
        Object commonEntity = findOrCreateCommonEntity(contentType, title);

        // 필드 매핑 적용
        List<FieldMapping> fieldMappings = fieldMappingRepository.findByConfigIdOrderByPriorityAsc(config.getId());
        applyFieldMappings(commonEntity, fieldMappings, sourceData);

        // 커스텀 계산 적용
        List<CustomFieldCalculation> customCalculations = customFieldCalculationRepository.findByConfigId(config.getId());
        applyCustomCalculations(commonEntity, customCalculations, sourceData);

        // Common 엔티티 저장
        Object savedCommon = saveCommonEntity(contentType, commonEntity);

        // PlatformMapping 업데이트
        updatePlatformMapping(contentType, savedCommon, platform, sourceId);

        return savedCommon;
    }

    /**
     * Common 엔티티 찾기 또는 생성
     */
    private Object findOrCreateCommonEntity(String contentType, String title) {
        switch (contentType) {
            case "novel":
                List<NovelCommon> novels = novelCommonRepository.findByTitleIgnoreCase(title);
                return novels.isEmpty() ? new NovelCommon() : novels.get(0);
            case "movie":
                List<MovieCommon> movies = movieCommonRepository.findByTitleIgnoreCase(title);
                return movies.isEmpty() ? new MovieCommon() : movies.get(0);
            case "ott":
                List<OTTCommon> otts = ottCommonRepository.findByTitleIgnoreCase(title);
                return otts.isEmpty() ? new OTTCommon() : otts.get(0);
            case "webtoon":
                List<WebtoonCommon> webtoons = webtoonCommonRepository.findByTitleIgnoreCase(title);
                return webtoons.isEmpty() ? new WebtoonCommon() : webtoons.get(0);
            case "game":
                List<GameCommon> games = gameCommonRepository.findByTitleIgnoreCase(title);
                return games.isEmpty() ? new GameCommon() : games.get(0);
            default:
                throw new IllegalArgumentException("Unsupported content type: " + contentType);
        }
    }

    /**
     * PlatformMapping 업데이트
     */
    @Transactional
    protected void updatePlatformMapping(String contentType, Object commonEntity, String platform, Long platformId) {
        switch (contentType) {
            case "novel":
                updateNovelPlatformMapping((NovelCommon) commonEntity, platform, platformId);
                break;
            case "movie":
                updateMoviePlatformMapping((MovieCommon) commonEntity, platform, platformId);
                break;
            case "ott":
                updateOTTPlatformMapping((OTTCommon) commonEntity, platform, platformId);
                break;
            case "webtoon":
                updateWebtoonPlatformMapping((WebtoonCommon) commonEntity, platform, platformId);
                break;
            case "game":
                updateGamePlatformMapping((GameCommon) commonEntity, platform, platformId);
                break;
        }
    }

    private void updateNovelPlatformMapping(NovelCommon novel, String platform, Long platformId) {
        NovelPlatformMapping mapping = novel.getPlatformMapping();
        if (mapping == null) {
            mapping = new NovelPlatformMapping();
            mapping.setNovelCommon(novel);
            novel.setPlatformMapping(mapping);
        }

        switch (platform.toLowerCase()) {
            case "naver":
            case "naverseries":
                mapping.setNaverSeriesNovel(platformId);
                break;
            case "kakao":
            case "kakaopage":
                mapping.setKakaoPageNovel(platformId);
                break;
            case "ridibooks":
                mapping.setRidibooksNovel(platformId);
                break;
        }

        novelMappingRepository.save(mapping);
    }

    private void updateMoviePlatformMapping(MovieCommon movie, String platform, Long platformId) {
        MoviePlatformMapping mapping = movie.getPlatformMapping();
        if (mapping == null) {
            mapping = new MoviePlatformMapping();
            mapping.setMovieCommon(movie);
            movie.setPlatformMapping(mapping);
        }

        switch (platform.toLowerCase()) {
            case "cgv":
                mapping.setCgvMovie(platformId);
                break;
            case "megabox":
                mapping.setMegaboxMovie(platformId);
                break;
            case "lottecinema":
                mapping.setLotteCinemaMovie(platformId);
                break;
        }

        movieMappingRepository.save(mapping);
    }

    private void updateOTTPlatformMapping(OTTCommon ott, String platform, Long platformId) {
        OTTPlatformMapping mapping = ott.getPlatformMapping();
        if (mapping == null) {
            mapping = new OTTPlatformMapping();
            mapping.setOttCommon(ott);
            ott.setPlatformMapping(mapping);
        }

        switch (platform.toLowerCase()) {
            case "netflix":
                mapping.setNetflixContent(platformId);
                break;
            case "disneyplus":
                mapping.setDisneyPlusContent(platformId);
                break;
            case "watcha":
                mapping.setWatchaContent(platformId);
                break;
            case "wavve":
                mapping.setWavveContent(platformId);
                break;
        }

        ottMappingRepository.save(mapping);
    }

    private void updateWebtoonPlatformMapping(WebtoonCommon webtoon, String platform, Long platformId) {
        WebtoonPlatformMapping mapping = webtoon.getPlatformMapping();
        if (mapping == null) {
            mapping = new WebtoonPlatformMapping();
            mapping.setWebtoonCommon(webtoon);
            webtoon.setPlatformMapping(mapping);
        }

        switch (platform.toLowerCase()) {
            case "naver":
                mapping.setNaverWebtoon(platformId);
                break;
            case "kakao":
                mapping.setKakaoWebtoon(platformId);
                break;
        }

        webtoonMappingRepository.save(mapping);
    }

    private void updateGamePlatformMapping(GameCommon game, String platform, Long platformId) {
        GamePlatformMapping mapping = game.getPlatformMapping();
        if (mapping == null) {
            mapping = new GamePlatformMapping();
            mapping.setGameCommon(game);
            game.setPlatformMapping(mapping);
        }

        switch (platform.toLowerCase()) {
            case "steam":
                mapping.setSteamGame(platformId);
                break;
            case "epic":
                mapping.setEpicGame(platformId);
                break;
            case "gog":
                mapping.setGogGame(platformId);
                break;
        }

        gameMappingRepository.save(mapping);
    }

    /**
     * 제목 추출
     */
    private String extractTitle(Object source) {
        try {
            Field titleField = findField(source.getClass(), "title");
            if (titleField != null) {
                titleField.setAccessible(true);
                return (String) titleField.get(source);
            }
        } catch (Exception e) {
            log.error("Failed to extract title: {}", e.getMessage());
        }
        return "Unknown";
    }

    /**
     * 중복 콘텐츠 확인
     */
    public Map<Long, String> checkDuplicateContent(String contentType, List<Long> sourceIds) {
        if (sourceIds == null || sourceIds.isEmpty()) {
            return new HashMap<>();
        }

        Map<Long, String> duplicates = new HashMap<>();
        Map<Long, String> sourceTitles = getSourceTitles(contentType, sourceIds);

        switch (contentType) {
            case "novel":
                for (Map.Entry<Long, String> entry : sourceTitles.entrySet()) {
                    // 플랫폼 ID로 먼저 확인
                    Optional<NovelPlatformMapping> mapping = novelMappingRepository.findByNaverSeriesId(entry.getKey());
                    if (mapping.isPresent()) {
                        duplicates.put(entry.getKey(), entry.getValue());
                    }
                }
                break;
            case "movie":
                for (Map.Entry<Long, String> entry : sourceTitles.entrySet()) {
                    Optional<MoviePlatformMapping> mapping = movieMappingRepository.findByCgvId(entry.getKey());
                    if (mapping.isPresent()) {
                        duplicates.put(entry.getKey(), entry.getValue());
                    }
                }
                break;
            // 다른 콘텐츠 타입도 유사하게 구현
        }

        return duplicates;
    }

    /**
     * 필드 매핑 적용
     */
    private void applyFieldMappings(Object target, List<FieldMapping> mappings, Map<String, Object> sourceData) {
        Map<String, List<FieldMapping>> fieldMappingGroups = mappings.stream()
                .collect(Collectors.groupingBy(FieldMapping::getCommonField));

        for (Map.Entry<String, List<FieldMapping>> entry : fieldMappingGroups.entrySet()) {
            String fieldName = entry.getKey();
            List<FieldMapping> mappingsForField = entry.getValue();

            mappingsForField.sort(Comparator.comparing(FieldMapping::getPriority));

            for (FieldMapping mapping : mappingsForField) {
                String platform = mapping.getPlatform();
                String platformField = mapping.getPlatformField();

                for (Map.Entry<String, Object> sourceEntry : sourceData.entrySet()) {
                    String sourceKey = sourceEntry.getKey();
                    Object sourceObj = sourceEntry.getValue();

                    if (sourceKey.startsWith(platform)) {
                        try {
                            Field sourceObjField = findField(sourceObj.getClass(), platformField);
                            if (sourceObjField != null) {
                                sourceObjField.setAccessible(true);
                                Object value = sourceObjField.get(sourceObj);

                                Field targetField = findField(target.getClass(), fieldName);
                                if (targetField != null && value != null) {
                                    targetField.setAccessible(true);
                                    value = convertSpecialTypes(value);
                                    targetField.set(target, value);
                                    break;
                                }
                            }
                        } catch (Exception e) {
                            log.error("Error mapping field {}: {}", fieldName, e.getMessage());
                        }
                    }
                }
            }
        }
    }

    /**
     * 커스텀 계산 적용
     */
    private void applyCustomCalculations(Object target, List<CustomFieldCalculation> calculations, Map<String, Object> sourceData) {
        for (CustomFieldCalculation calc : calculations) {
            String targetField = calc.getTargetField();
            String calcType = calc.getCalculationType();

            try {
                Field field = findField(target.getClass(), targetField);
                if (field != null) {
                    field.setAccessible(true);

                    switch (calcType) {
                        case "AVERAGE":
                            applyAverageCalculation(target, field, calc, sourceData);
                            break;
                        case "MAX":
                            applyMaxCalculation(target, field, calc, sourceData);
                            break;
                        case "MIN":
                            applyMinCalculation(target, field, calc, sourceData);
                            break;
                        case "CONCAT":
                            applyConcatCalculation(target, field, calc, sourceData);
                            break;
                        case "CUSTOM":
                            applyCustomExpression(target, field, calc, sourceData);
                            break;
                    }
                }
            } catch (Exception e) {
                log.error("Error applying calculation for field {}: {}", targetField, e.getMessage());
                if (calc.getIsRequired()) {
                    throw new RuntimeException("Required calculation failed: " + e.getMessage());
                }
            }
        }
    }

    // 헬퍼 메서드들

    private Object convertSpecialTypes(Object value) {
        if (value instanceof List && !((List<?>) value).isEmpty()) {
            Object firstItem = ((List<?>) value).get(0);
            String className = firstItem.getClass().getSimpleName();

            // 엔티티 리스트를 String 리스트로 변환
            if (className.contains("Genre") || className.contains("Author") ||
                    className.contains("Actor") || className.contains("Publisher") ||
                    className.contains("Developer")) {
                return ((List<?>) value).stream()
                        .map(item -> {
                            try {
                                Field nameField = findField(item.getClass(), "name");
                                if (nameField == null) {
                                    nameField = findField(item.getClass(), "genre");
                                }
                                if (nameField != null) {
                                    nameField.setAccessible(true);
                                    return nameField.get(item);
                                }
                            } catch (Exception e) {
                                return null;
                            }
                            return item.toString();
                        })
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
            }
        }
        return value;
    }

    private Field findField(Class<?> clazz, String fieldName) {
        try {
            return clazz.getDeclaredField(fieldName);
        } catch (NoSuchFieldException e) {
            if (clazz.getSuperclass() != null) {
                return findField(clazz.getSuperclass(), fieldName);
            }
            return null;
        }
    }

    private Map<String, Object> loadSingleSourceData(String contentType, Long sourceId) {
        Map<String, Object> sourceData = new HashMap<>();

        if (sourceId == null) {
            return sourceData;
        }

        switch (contentType) {
            case "novel":
                naverSeriesNovelRepository.findById(sourceId).ifPresent(
                        novel -> sourceData.put("naver_" + novel.getId(), novel));
                break;
            case "movie":
                movieRepository.findById(sourceId).ifPresent(
                        movie -> sourceData.put("cgv_" + movie.getId(), movie));
                break;
            case "ott":
                netflixContentRepository.findById(sourceId).ifPresent(
                        content -> sourceData.put("netflix_" + content.getContentId(), content));
                break;
            case "webtoon":
                webtoonRepository.findById(sourceId).ifPresent(
                        webtoon -> sourceData.put("naver_" + webtoon.getId(), webtoon));
                break;
            case "game":
                steamGameRepository.findById(sourceId).ifPresent(
                        game -> sourceData.put("steam_" + game.getId(), game));
                break;
        }

        return sourceData;
    }

    private Object saveCommonEntity(String contentType, Object entity) {
        switch (contentType) {
            case "novel":
                return novelCommonRepository.save((NovelCommon) entity);
            case "movie":
                return movieCommonRepository.save((MovieCommon) entity);
            case "ott":
                return ottCommonRepository.save((OTTCommon) entity);
            case "webtoon":
                return webtoonCommonRepository.save((WebtoonCommon) entity);
            case "game":
                return gameCommonRepository.save((GameCommon) entity);
            default:
                throw new IllegalArgumentException("Unsupported content type: " + contentType);
        }
    }

    private Map<Long, String> getSourceTitles(String contentType, List<Long> sourceIds) {
        Map<Long, String> titles = new HashMap<>();

        switch (contentType) {
            case "novel":
                naverSeriesNovelRepository.findAllById(sourceIds).forEach(
                        novel -> titles.put(novel.getId(), novel.getTitle()));
                break;
            case "movie":
                movieRepository.findAllById(sourceIds).forEach(
                        movie -> titles.put(movie.getId(), movie.getTitle()));
                break;
            case "ott":
                netflixContentRepository.findAllById(sourceIds).forEach(
                        content -> {
                            try {
                                Long id = content.getContentId();
                                titles.put(id, content.getTitle());
                            } catch (NumberFormatException e) {
                                log.error("Cannot parse contentId to Long: {}", content.getContentId());
                            }
                        });
                break;
            case "webtoon":
                webtoonRepository.findAllById(sourceIds).forEach(
                        webtoon -> titles.put(webtoon.getId(), webtoon.getTitle()));
                break;
            case "game":
                steamGameRepository.findAllById(sourceIds).forEach(
                        game -> titles.put(game.getId(), game.getTitle()));
                break;
        }

        return titles;
    }

    // 계산 메서드들
    private void applyAverageCalculation(Object target, Field field, CustomFieldCalculation calc, Map<String, Object> sourceData) {
        // 구현 필요
    }

    private void applyMaxCalculation(Object target, Field field, CustomFieldCalculation calc, Map<String, Object> sourceData) {
        // 구현 필요
    }

    private void applyMinCalculation(Object target, Field field, CustomFieldCalculation calc, Map<String, Object> sourceData) {
        // 구현 필요
    }

    private void applyConcatCalculation(Object target, Field field, CustomFieldCalculation calc, Map<String, Object> sourceData) {
        // 구현 필요
    }

    private void applyCustomExpression(Object target, Field field, CustomFieldCalculation calc, Map<String, Object> sourceData) {
        // 구현 필요
    }

    /**
     * 통합 상태 확인
     */
    public Map<String, Object> getIntegrationStatus(String contentType) {
        Map<String, Object> status = new HashMap<>();

        switch (contentType.toLowerCase()) {
            case "novel":
                status.put("commonCount", novelCommonRepository.count());
                status.put("mappingCount", novelMappingRepository.count());
                status.put("naverSeriesCount", novelMappingRepository.findByNaverSeriesAvailable().size());
                break;
            case "movie":
                status.put("commonCount", movieCommonRepository.count());
                status.put("mappingCount", movieMappingRepository.count());
                status.put("cgvCount", movieMappingRepository.findByCgvAvailable().size());
                break;
            // 다른 타입들도 추가
        }

        status.put("contentType", contentType);
        status.put("timestamp", new Date());

        return status;
    }
}