package com.example.AOD.common.service;

import com.example.AOD.Novel.NaverSeriesNovel.repository.NaverSeriesNovelRepository;
import com.example.AOD.common.commonDomain.GameCommon;
import com.example.AOD.common.commonDomain.MovieCommon;
import com.example.AOD.common.commonDomain.NovelCommon;
import com.example.AOD.common.commonDomain.OTTCommon;
import com.example.AOD.common.commonDomain.WebtoonCommon;
import com.example.AOD.common.config.ContentIntegrationConfig;
import com.example.AOD.common.config.CustomFieldCalculation;
import com.example.AOD.common.config.FieldMapping;
import com.example.AOD.common.repository.*;
import com.example.AOD.common.repository.GameCommonRepository;
import com.example.AOD.common.repository.OTTCommonRepository;
import com.example.AOD.common.repository.WebtoonCommonRepository;
import com.example.AOD.game.StreamAPI.repository.GameRepository;
import com.example.AOD.movie.CGV.repository.MovieRepository;
import com.example.AOD.OTT.Netflix.repository.NetflixContentRepository;
import com.example.AOD.Webtoon.NaverWebtoon.repository.WebtoonRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ContentIntegrationService {

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

    @Autowired
    public ContentIntegrationService(
            ContentIntegrationConfigRepository configRepository,
            FieldMappingRepository fieldMappingRepository,
            CustomFieldCalculationRepository customFieldCalculationRepository,
            NaverSeriesNovelRepository naverSeriesNovelRepository,
            MovieRepository movieRepository,
            NetflixContentRepository netflixContentRepository,
            WebtoonRepository webtoonRepository,
            GameRepository steamGameRepository,
            NovelCommonRepository novelCommonRepository,
            MovieCommonRepository movieCommonRepository,
            OTTCommonRepository ottCommonRepository,
            WebtoonCommonRepository webtoonCommonRepository,
            GameCommonRepository gameCommonRepository) {
        this.configRepository = configRepository;
        this.fieldMappingRepository = fieldMappingRepository;
        this.customFieldCalculationRepository = customFieldCalculationRepository;
        this.naverSeriesNovelRepository = naverSeriesNovelRepository;
        this.movieRepository = movieRepository;
        this.netflixContentRepository = netflixContentRepository;
        this.webtoonRepository = webtoonRepository;
        this.steamGameRepository = steamGameRepository;
        this.novelCommonRepository = novelCommonRepository;
        this.movieCommonRepository = movieCommonRepository;
        this.ottCommonRepository = ottCommonRepository;
        this.webtoonCommonRepository = webtoonCommonRepository;
        this.gameCommonRepository = gameCommonRepository;
    }

    @Transactional
    public List<Object> integrateContent(Long configId, List<Long> sourceIds) {
        ContentIntegrationConfig config = configRepository.findById(configId)
                .orElseThrow(() -> new RuntimeException("Configuration not found"));

        String contentType = config.getContentType();
        List<Object> results = new ArrayList<>();

        // sourceIds가 없으면 빈 리스트 반환
        if (sourceIds == null || sourceIds.isEmpty()) {
            return results;
        }

        // 각 소스 ID에 대해 개별적으로 통합 처리
        for (Long sourceId : sourceIds) {
            // 각 소스에 대한 데이터 로드
            Map<String, Object> sourceData = loadSingleSourceData(contentType, sourceId);

            if (sourceData.isEmpty()) {
                continue; // 유효한 소스 데이터가 없으면 건너뛰기
            }

            // Common 엔티티 생성
            Object commonEntity = createCommonEntity(contentType);

            // ID 설정 (소스 ID를 Common 엔티티 ID로 사용)
            setEntityId(commonEntity, sourceId);

            // 필드 매핑 적용
            List<FieldMapping> fieldMappings = fieldMappingRepository.findByConfigIdOrderByPriorityAsc(configId);
            applyFieldMappings(commonEntity, fieldMappings, sourceData);

            // 커스텀 계산 적용
            List<CustomFieldCalculation> customCalculations = customFieldCalculationRepository.findByConfigId(configId);
            applyCustomCalculations(commonEntity, customCalculations, sourceData);

            // 저장 및 결과 리스트에 추가
            Object savedEntity = saveCommonEntity(contentType, commonEntity);
            results.add(savedEntity);
        }

        return results;
    }

    // ID 설정 메서드 추가
    private void setEntityId(Object entity, Long id) {
        try {
            Field idField = findField(entity.getClass(), "id");
            if (idField != null) {
                idField.setAccessible(true);
                idField.set(entity, id);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to set ID on entity", e);
        }
    }

    // 단일 소스 데이터 로드를 위한 새 메서드
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

    // 기존 메서드는 유지하되 사용하지 않음
    private Map<String, Object> loadSourceData(String contentType, List<Long> sourceIds) {
        Map<String, Object> sourceData = new HashMap<>();

        if (sourceIds == null || sourceIds.isEmpty()) {
            return sourceData;
        }

        List<Long> validIds = sourceIds.stream()
                .filter(id -> id != null)
                .collect(Collectors.toList());

        switch (contentType) {
            case "novel":
                naverSeriesNovelRepository.findAllById(validIds).forEach(
                        novel -> sourceData.put("naver_" + novel.getId(), novel));
                break;
            case "movie":
                movieRepository.findAllById(validIds).forEach(
                        movie -> sourceData.put("cgv_" + movie.getId(), movie));
                break;
            case "ott":
                netflixContentRepository.findAllById(validIds).forEach(
                        content -> sourceData.put("netflix_" + content.getContentId(), content));
                break;
            case "webtoon":
                webtoonRepository.findAllById(validIds).forEach(
                        webtoon -> sourceData.put("naver_" + webtoon.getId(), webtoon));
                break;
            case "game":
                steamGameRepository.findAllById(validIds).forEach(
                        game -> sourceData.put("steam_" + game.getId(), game));
                break;
            default:
                throw new IllegalArgumentException("Unsupported content type: " + contentType);
        }

        return sourceData;
    }

    private Object createCommonEntity(String contentType) {
        try {
            switch (contentType) {
                case "novel":
                    return new NovelCommon();
                case "movie":
                    return new MovieCommon();
                case "ott":
                    return new OTTCommon();
                case "webtoon":
                    return new WebtoonCommon();
                case "game":
                    return new GameCommon();
                default:
                    throw new IllegalArgumentException("Unsupported content type: " + contentType);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to create common entity for type: " + contentType, e);
        }
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

    private void applyFieldMappings(Object target, List<FieldMapping> mappings, Map<String, Object> sourceData) {
        // 필드별로 그룹화
        Map<String, List<FieldMapping>> fieldMappingGroups = mappings.stream()
                .collect(Collectors.groupingBy(FieldMapping::getCommonField));

        for (Map.Entry<String, List<FieldMapping>> entry : fieldMappingGroups.entrySet()) {
            String fieldName = entry.getKey();
            List<FieldMapping> mappingsForField = entry.getValue();

            // 우선순위에 따라 정렬
            mappingsForField.sort(Comparator.comparing(FieldMapping::getPriority));

            for (FieldMapping mapping : mappingsForField) {
                String platform = mapping.getPlatform();
                String platformField = mapping.getPlatformField();

                // 소스 데이터에서 일치하는 플랫폼 데이터 찾기
                for (Map.Entry<String, Object> sourceEntry : sourceData.entrySet()) {
                    String sourceKey = sourceEntry.getKey();
                    Object sourceObj = sourceEntry.getValue();

                    if (sourceKey.startsWith(platform)) {
                        try {
                            // 리플렉션을 사용하여 소스 객체에서 필드 값 가져오기
                            Field sourceObjField = findField(sourceObj.getClass(), platformField);
                            if (sourceObjField != null) {
                                sourceObjField.setAccessible(true);
                                Object value = sourceObjField.get(sourceObj);

                                // 타겟 객체에 값 설정
                                Field targetField = findField(target.getClass(), fieldName);
                                if (targetField != null && value != null) {
                                    targetField.setAccessible(true);

                                    // 특수 타입 변환 처리
                                    value = convertSpecialTypes(value);

                                    targetField.set(target, value);
                                    break; // 값을 찾았으므로 다음 필드로 이동
                                }
                            }
                        } catch (Exception e) {
                            // 예외 처리
                            System.err.println("Error mapping field " + fieldName + ": " + e.getMessage());
                        }
                    }
                }
            }
        }
    }

    private Object convertSpecialTypes(Object value) {
        // NaverSeriesNovelGenre -> String 리스트 변환 (기존 코드)
        if (value instanceof List && !((List<?>) value).isEmpty()) {
            Object firstItem = ((List<?>) value).get(0);

            // NaverSeriesNovelGenre 처리
            if (firstItem.getClass().getSimpleName().equals("NaverSeriesNovelGenre")) {
                return ((List<?>) value).stream()
                        .map(item -> {
                            try {
                                Field nameField = findField(item.getClass(), "name");
                                nameField.setAccessible(true);
                                return nameField.get(item);
                            } catch (Exception e) {
                                return null;
                            }
                        })
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
            }

            // NaverSeriesNovelAuthor 리스트 처리 추가
            if (firstItem.getClass().getSimpleName().equals("NaverSeriesNovelAuthor")) {
                return ((List<?>) value).stream()
                        .map(item -> {
                            try {
                                Field nameField = findField(item.getClass(), "name");
                                nameField.setAccessible(true);
                                return nameField.get(item);
                            } catch (Exception e) {
                                return null;
                            }
                        })
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
            }
        }

        return value;
    }
    /**
     * 특정 콘텐츠의 제목이 이미 Common 테이블에 존재하는지 확인
     * @param contentType 콘텐츠 유형
     * @param sourceIds 확인할 소스 ID 목록
     * @return 중복된 제목과 ID의 Map (없으면 빈 맵)
     */
    public Map<Long, String> checkDuplicateContent(String contentType, List<Long> sourceIds) {
        if (sourceIds == null || sourceIds.isEmpty()) {
            return new HashMap<>();
        }

        Map<Long, String> duplicates = new HashMap<>();

        // 선택된 소스에서 제목 추출
        Map<Long, String> sourceTitles = getSourceTitles(contentType, sourceIds);

        // 제목으로 중복 확인
        switch (contentType) {
            case "novel":
                for (Map.Entry<Long, String> entry : sourceTitles.entrySet()) {
                    Long id = entry.getKey();
                    String title = entry.getValue();

                    // 제목으로 기존 Common 엔티티 검색
                    List<NovelCommon> existing = novelCommonRepository.findByTitleIgnoreCase(title);
                    if (!existing.isEmpty()) {
                        duplicates.put(id, title);
                    }
                }
                break;
            case "movie":
                for (Map.Entry<Long, String> entry : sourceTitles.entrySet()) {
                    Long id = entry.getKey();
                    String title = entry.getValue();

                    List<MovieCommon> existing = movieCommonRepository.findByTitleIgnoreCase(title);
                    if (!existing.isEmpty()) {
                        duplicates.put(id, title);
                    }
                }
                break;
            case "ott":
                for (Map.Entry<Long, String> entry : sourceTitles.entrySet()) {
                    Long id = entry.getKey();
                    String title = entry.getValue();

                    List<OTTCommon> existing = ottCommonRepository.findByTitleIgnoreCase(title);
                    if (!existing.isEmpty()) {
                        duplicates.put(id, title);
                    }
                }
                break;
            case "webtoon":
                for (Map.Entry<Long, String> entry : sourceTitles.entrySet()) {
                    Long id = entry.getKey();
                    String title = entry.getValue();

                    List<WebtoonCommon> existing = webtoonCommonRepository.findByTitleIgnoreCase(title);
                    if (!existing.isEmpty()) {
                        duplicates.put(id, title);
                    }
                }
                break;
            case "game":
                for (Map.Entry<Long, String> entry : sourceTitles.entrySet()) {
                    Long id = entry.getKey();
                    String title = entry.getValue();

                    List<GameCommon> existing = gameCommonRepository.findByTitleIgnoreCase(title);
                    if (!existing.isEmpty()) {
                        duplicates.put(id, title);
                    }
                }
                break;
        }

        return duplicates;
    }

    /**
     * 소스 ID 목록에서 제목을 추출
     */
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
                                Long id = Long.parseLong(content.getContentId());
                                titles.put(id, content.getTitle());
                            } catch (NumberFormatException e) {
                                // contentId가 숫자로 변환할 수 없는 경우 처리
                                System.err.println("Cannot parse contentId to Long: " + content.getContentId());
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

    private Field findField(Class<?> clazz, String fieldName) {
        try {
            return clazz.getDeclaredField(fieldName);
        } catch (NoSuchFieldException e) {
            // 부모 클래스에서 찾기
            if (clazz.getSuperclass() != null) {
                return findField(clazz.getSuperclass(), fieldName);
            }
            return null;
        }
    }

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
                        case "CUSTOM":
                            applyCustomExpression(target, field, calc, sourceData);
                            break;
                    }
                }
            } catch (Exception e) {
                System.err.println("Error applying calculation for field " + targetField + ": " + e.getMessage());
                if (calc.getIsRequired()) {
                    throw new RuntimeException("Required calculation failed: " + e.getMessage());
                }
            }
        }
    }

    private void applyAverageCalculation(Object target, Field field, CustomFieldCalculation calc, Map<String, Object> sourceData) {
        // 평균 계산 로직 구현
        // 실제 구현은 계산 대상 필드와 데이터 유형에 따라 달라집니다.
    }

    private void applyMaxCalculation(Object target, Field field, CustomFieldCalculation calc, Map<String, Object> sourceData) {
        // 최대값 계산 로직 구현
        // 실제 구현은 계산 대상 필드와 데이터 유형에 따라 달라집니다.
    }

    private void applyCustomExpression(Object target, Field field, CustomFieldCalculation calc, Map<String, Object> sourceData) {
        // 커스텀 계산 로직 구현
        // 실제 구현은 계산식과 데이터 유형에 따라 달라집니다.
    }

    // 특정 유형 통합 메서드 - 기존 코드와의 호환성을 위해
    @Transactional
    public List<NovelCommon> integrateNovelData(Long configId, List<Long> sourceIds) {
        return integrateContent(configId, sourceIds).stream()
                .map(obj -> (NovelCommon) obj)
                .collect(Collectors.toList());
    }

    @Transactional
    public List<MovieCommon> integrateMovieData(Long configId, List<Long> sourceIds) {
        return integrateContent(configId, sourceIds).stream()
                .map(obj -> (MovieCommon) obj)
                .collect(Collectors.toList());
    }

    @Transactional
    public List<OTTCommon> integrateOTTData(Long configId, List<Long> sourceIds) {
        return integrateContent(configId, sourceIds).stream()
                .map(obj -> (OTTCommon) obj)
                .collect(Collectors.toList());
    }

    @Transactional
    public List<WebtoonCommon> integrateWebtoonData(Long configId, List<Long> sourceIds) {
        return integrateContent(configId, sourceIds).stream()
                .map(obj -> (WebtoonCommon) obj)
                .collect(Collectors.toList());
    }

    @Transactional
    public List<GameCommon> integrateGameData(Long configId, List<Long> sourceIds) {
        return integrateContent(configId, sourceIds).stream()
                .map(obj -> (GameCommon) obj)
                .collect(Collectors.toList());
    }
}