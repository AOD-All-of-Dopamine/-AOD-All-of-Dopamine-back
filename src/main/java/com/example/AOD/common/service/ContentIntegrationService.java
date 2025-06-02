package com.example.AOD.common.service;

import com.example.AOD.Novel.NaverSeriesNovel.repository.NaverSeriesNovelRepository;
import com.example.AOD.common.commonDomain.*;
import com.example.AOD.common.config.ContentIntegrationConfig;
import com.example.AOD.common.config.CustomFieldCalculation;
import com.example.AOD.common.config.FieldMapping;
import com.example.AOD.common.dto.ManualIntegrationDTO;
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
                return novelCommonRepository.findByTitleIgnoreCase(title)
                        .stream()
                        .findFirst()
                        .orElseGet(() -> {
                            NovelCommon n = new NovelCommon();
                            n.setTitle(title);                      // NOT NULL 컬럼이면 꼭 세팅
                            return novelCommonRepository.saveAndFlush(n); // <-- INSERT + version=0
                        });

            case "movie":
                return movieCommonRepository.findByTitleIgnoreCase(title)
                        .stream()
                        .findFirst()
                        .orElseGet(() -> {
                            MovieCommon m = new MovieCommon();
                            m.setTitle(title);
                            return movieCommonRepository.saveAndFlush(m); // <-- 여기
                        });

            case "ott":
                return ottCommonRepository.findByTitleIgnoreCase(title)
                        .stream()
                        .findFirst()
                        .orElseGet(() -> {
                            OTTCommon o = new OTTCommon();
                            o.setTitle(title);
                            return ottCommonRepository.saveAndFlush(o);
                        });

            case "webtoon":
                return webtoonCommonRepository.findByTitleIgnoreCase(title)
                        .stream()
                        .findFirst()
                        .orElseGet(() -> {
                            WebtoonCommon w = new WebtoonCommon();
                            w.setTitle(title);
                            return webtoonCommonRepository.saveAndFlush(w);
                        });

            case "game":
                return gameCommonRepository.findByTitleIgnoreCase(title)
                        .stream()
                        .findFirst()
                        .orElseGet(() -> {
                            GameCommon g = new GameCommon();
                            g.setTitle(title);
                            return gameCommonRepository.saveAndFlush(g);
                        });

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


    /**
     * 수동 통합을 위한 소스 데이터 로드
     */
    public Map<String, Object> loadSourceDataForManualIntegration(String contentType, List<Long> sourceIds) {
        Map<String, Object> sourceData = new HashMap<>();

        if (sourceIds == null || sourceIds.isEmpty()) {
            return sourceData;
        }

        switch (contentType) {
            case "novel":
                for (Long id : sourceIds) {
                    naverSeriesNovelRepository.findById(id).ifPresent(
                            novel -> sourceData.put("naver_" + novel.getId(), novel));
                }
                break;
            case "movie":
                for (Long id : sourceIds) {
                    movieRepository.findById(id).ifPresent(
                            movie -> sourceData.put("cgv_" + movie.getId(), movie));
                }
                break;
            case "ott":
                for (Long id : sourceIds) {
                    netflixContentRepository.findById(id).ifPresent(
                            content -> sourceData.put("netflix_" + content.getContentId(), content));
                }
                break;
            case "webtoon":
                for (Long id : sourceIds) {
                    webtoonRepository.findById(id).ifPresent(
                            webtoon -> sourceData.put("naver_" + webtoon.getId(), webtoon));
                }
                break;
            case "game":
                for (Long id : sourceIds) {
                    steamGameRepository.findById(id).ifPresent(
                            game -> sourceData.put("steam_" + game.getId(), game));
                }
                break;
        }

        return sourceData;
    }

    /**
     * 수동 통합을 위한 필드 정보 생성
     */
    public List<Map<String, Object>> getFieldInfoForManualIntegration(String contentType, Map<String, Object> sourceData) {
        List<Map<String, Object>> fieldInfo = new ArrayList<>();

        // 공통 필드들
        addFieldInfo(fieldInfo, "title", "제목", "String", sourceData);
        addFieldInfo(fieldInfo, "imageUrl", "이미지 URL", "String", sourceData);
        addFieldInfo(fieldInfo, "genre", "장르", "List<String>", sourceData);

        // 콘텐츠 타입별 필드들
        switch (contentType) {
            case "novel":
                addFieldInfo(fieldInfo, "authors", "작가", "List<String>", sourceData);
                addFieldInfo(fieldInfo, "status", "연재상태", "String", sourceData);
                addFieldInfo(fieldInfo, "publisher", "출판사", "String", sourceData);
                addFieldInfo(fieldInfo, "ageRating", "연령등급", "String", sourceData);
                break;
            case "movie":
                addFieldInfo(fieldInfo, "director", "감독", "String", sourceData);
                addFieldInfo(fieldInfo, "actors", "출연진", "List<String>", sourceData);
                addFieldInfo(fieldInfo, "releaseDate", "개봉일", "String", sourceData);
                addFieldInfo(fieldInfo, "runningTime", "상영시간", "Integer", sourceData);
                addFieldInfo(fieldInfo, "country", "제작국가", "String", sourceData);
                addFieldInfo(fieldInfo, "movieAgeRating", "관람등급", "String", sourceData);
                addFieldInfo(fieldInfo, "totalAudience", "총관객수", "Integer", sourceData);
                addFieldInfo(fieldInfo, "summary", "줄거리", "String", sourceData);
                addFieldInfo(fieldInfo, "rating", "평점", "Double", sourceData);
                addFieldInfo(fieldInfo, "reservationRate", "예매율", "Double", sourceData);
                addFieldInfo(fieldInfo, "isRerelease", "재개봉여부", "Boolean", sourceData);
                break;
            case "ott":
                addFieldInfo(fieldInfo, "type", "콘텐츠 유형", "String", sourceData);
                addFieldInfo(fieldInfo, "creator", "제작자", "String", sourceData);
                addFieldInfo(fieldInfo, "description", "설명", "String", sourceData);
                addFieldInfo(fieldInfo, "maturityRating", "시청등급", "String", sourceData);
                addFieldInfo(fieldInfo, "releaseYear", "출시년도", "Integer", sourceData);
                addFieldInfo(fieldInfo, "features", "특징", "List<String>", sourceData);
                break;
            case "webtoon":
                addFieldInfo(fieldInfo, "authors", "작가", "List<String>", sourceData);
                addFieldInfo(fieldInfo, "publishDate", "연재시작일", "String", sourceData);
                addFieldInfo(fieldInfo, "uploadDays", "연재요일", "List<String>", sourceData);
                addFieldInfo(fieldInfo, "webtoonSummary", "줄거리", "String", sourceData);
                break;
            case "game":
                addFieldInfo(fieldInfo, "developers", "개발사", "List<String>", sourceData);
                addFieldInfo(fieldInfo, "publishers", "퍼블리셔", "List<String>", sourceData);
                addFieldInfo(fieldInfo, "requiredAge", "이용등급", "Long", sourceData);
                addFieldInfo(fieldInfo, "gameSummary", "게임설명", "String", sourceData);
                addFieldInfo(fieldInfo, "initialPrice", "정가", "Integer", sourceData);
                addFieldInfo(fieldInfo, "finalPrice", "할인가", "Integer", sourceData);
                break;
        }

        return fieldInfo;
    }

    /**
     * 필드 정보 추가 헬퍼 메서드
     */
    private void addFieldInfo(List<Map<String, Object>> fieldInfo, String fieldName, String displayName,
                              String fieldType, Map<String, Object> sourceData) {
        Map<String, Object> info = new HashMap<>();
        info.put("fieldName", fieldName);
        info.put("displayName", displayName);
        info.put("fieldType", fieldType);

        // 각 소스에서 해당 필드의 값들 추출
        Map<String, Object> sourceValues = new HashMap<>();
        for (Map.Entry<String, Object> entry : sourceData.entrySet()) {
            String sourceKey = entry.getKey();
            Object sourceObj = entry.getValue();

            Object value = extractFieldValue(sourceObj, fieldName);
            if (value != null) {
                sourceValues.put(sourceKey, value);
            }
        }

        info.put("sourceValues", sourceValues);
        fieldInfo.add(info);
    }

    /**
     * 객체에서 필드 값 추출
     */
    private Object extractFieldValue(Object source, String fieldName) {
        try {
            // 필드명 매핑 (플랫폼별로 다른 필드명을 사용할 수 있음)
            String actualFieldName = mapFieldName(source.getClass(), fieldName);

            Field field = findField(source.getClass(), actualFieldName);
            if (field != null) {
                field.setAccessible(true);
                Object value = field.get(source);
                return convertSpecialTypes(value);
            }
        } catch (Exception e) {
            log.debug("Failed to extract field {} from {}: {}", fieldName, source.getClass().getSimpleName(), e.getMessage());
        }
        return null;
    }

    /**
     * 플랫폼별 필드명 매핑
     */
    private String mapFieldName(Class<?> sourceClass, String commonFieldName) {
        // 필요에 따라 플랫폼별로 다른 필드명 매핑
        switch (commonFieldName) {
            case "authors":
                if (sourceClass.getSimpleName().contains("Webtoon")) {
                    return "webtoonAuthors";
                }
                return "authors";
            case "genre":
                if (sourceClass.getSimpleName().contains("Webtoon")) {
                    return "webtoonGenres";
                }
                return "genre";
            case "webtoonSummary":
                return "summary";
            case "gameSummary":
                return "summary";
            default:
                return commonFieldName;
        }
    }

    /**
     * 수동 통합 처리
     */
    @Transactional
    public Object processManualIntegration(ManualIntegrationDTO manualDTO) {
        String contentType = manualDTO.getContentType();

        // Common 엔티티 생성 또는 찾기
        Object commonEntity = findOrCreateCommonEntity(contentType, manualDTO.getTitle());

        // 수동으로 입력된 필드들 적용
        applyManualFields(commonEntity, manualDTO, contentType);

        // Common 엔티티 저장
        Object savedCommon = saveCommonEntity(contentType, commonEntity);

        // PlatformMapping 업데이트
        updatePlatformMappingFromManual(contentType, savedCommon, manualDTO);

        return savedCommon;
    }

    /**
     * 수동 필드 적용
     */
    private void applyManualFields(Object target, ManualIntegrationDTO manualDTO, String contentType) {
        try {
            // 공통 필드 적용
            setFieldValue(target, "title", manualDTO.getTitle());
            setFieldValue(target, "imageUrl", manualDTO.getImageUrl());
            setFieldValue(target, "genre", manualDTO.getGenre());

            // 콘텐츠 타입별 필드 적용
            switch (contentType) {
                case "novel":
                    setFieldValue(target, "authors", manualDTO.getAuthors());
                    setFieldValue(target, "status", manualDTO.getStatus());
                    setFieldValue(target, "publisher", manualDTO.getPublisher());
                    setFieldValue(target, "ageRating", manualDTO.getAgeRating());
                    break;
                case "movie":
                    setFieldValue(target, "director", manualDTO.getDirector());
                    setFieldValue(target, "actors", manualDTO.getActors());
                    setFieldValue(target, "releaseDate", manualDTO.getReleaseDate());
                    setFieldValue(target, "runningTime", manualDTO.getRunningTime());
                    setFieldValue(target, "country", manualDTO.getCountry());
                    setFieldValue(target, "ageRating", manualDTO.getMovieAgeRating());
                    setFieldValue(target, "totalAudience", manualDTO.getTotalAudience());
                    setFieldValue(target, "summary", manualDTO.getSummary());
                    setFieldValue(target, "rating", manualDTO.getRating());
                    setFieldValue(target, "reservationRate", manualDTO.getReservationRate());
                    setFieldValue(target, "isRerelease", manualDTO.getIsRerelease());
                    break;
                case "ott":
                    setFieldValue(target, "type", manualDTO.getType());
                    setFieldValue(target, "creator", manualDTO.getCreator());
                    setFieldValue(target, "description", manualDTO.getDescription());
                    setFieldValue(target, "maturityRating", manualDTO.getMaturityRating());
                    setFieldValue(target, "releaseYear", manualDTO.getReleaseYear());
                    setFieldValue(target, "features", manualDTO.getFeatures());
                    break;
                case "webtoon":
                    setFieldValue(target, "publishDate", manualDTO.getPublishDate());
                    setFieldValue(target, "uploadDay", manualDTO.getUploadDays());
                    setFieldValue(target, "summary", manualDTO.getWebtoonSummary());
                    break;
                case "game":
                    setFieldValue(target, "developers", manualDTO.getDevelopers());
                    setFieldValue(target, "publisher", manualDTO.getPublishers());
                    setFieldValue(target, "requiredAge", manualDTO.getRequiredAge());
                    setFieldValue(target, "summary", manualDTO.getGameSummary());
                    setFieldValue(target, "initialPrice", manualDTO.getInitialPrice());
                    setFieldValue(target, "finalPrice", manualDTO.getFinalPrice());
                    break;
            }
        } catch (Exception e) {
            log.error("Error applying manual fields: {}", e.getMessage());
            throw new RuntimeException("수동 필드 적용 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    /**
     * 필드 값 설정 헬퍼 메서드
     */
    private void setFieldValue(Object target, String fieldName, Object value) {
        if (value == null) return;

        try {
            Field field = findField(target.getClass(), fieldName);
            if (field != null) {
                field.setAccessible(true);
                field.set(target, value);
            }
        } catch (Exception e) {
            log.warn("Failed to set field {}: {}", fieldName, e.getMessage());
        }
    }

    /**
     * 수동 통합에서 플랫폼 매핑 업데이트
     */
    private void updatePlatformMappingFromManual(String contentType, Object commonEntity, ManualIntegrationDTO manualDTO) {
        List<Long> sourceIds = manualDTO.getSourceIds();

        if (sourceIds == null || sourceIds.isEmpty()) {
            return;
        }

        // 각 소스 ID에 대해 플랫폼 매핑 업데이트
        for (Long sourceId : sourceIds) {
            String platform = determinePlatformFromContentType(contentType);
            updatePlatformMapping(contentType, commonEntity, platform, sourceId);
        }
    }

    /**
     * 콘텐츠 타입에서 플랫폼 결정
     */
    private String determinePlatformFromContentType(String contentType) {
        switch (contentType) {
            case "novel": return "naver";
            case "movie": return "cgv";
            case "ott": return "netflix";
            case "webtoon": return "naver";
            case "game": return "steam";
            default: return "unknown";
        }
    }
}