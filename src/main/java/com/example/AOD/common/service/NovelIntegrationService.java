package com.example.AOD.common.service;

import com.example.AOD.Novel.NaverSeriesNovel.domain.NaverSeriesNovel;
import com.example.AOD.Novel.NaverSeriesNovel.domain.NaverSeriesNovelGenre;
import com.example.AOD.Novel.NaverSeriesNovel.repository.NaverSeriesNovelRepository;
import com.example.AOD.common.commonDomain.NovelCommon;
import com.example.AOD.common.config.ContentIntegrationConfig;
import com.example.AOD.common.config.CustomFieldCalculation;
import com.example.AOD.common.config.FieldMapping;
import com.example.AOD.common.repository.ContentIntegrationConfigRepository;
import com.example.AOD.common.repository.CustomFieldCalculationRepository;
import com.example.AOD.common.repository.FieldMappingRepository;
import com.example.AOD.common.repository.NovelCommonRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class NovelIntegrationService {

    private final ContentIntegrationConfigRepository configRepository;
    private final FieldMappingRepository fieldMappingRepository;
    private final CustomFieldCalculationRepository customFieldCalculationRepository;
    private final NaverSeriesNovelRepository naverSeriesNovelRepository;
    private final NovelCommonRepository novelCommonRepository;

    @Autowired
    public NovelIntegrationService(
            ContentIntegrationConfigRepository configRepository,
            FieldMappingRepository fieldMappingRepository,
            CustomFieldCalculationRepository customFieldCalculationRepository,
            NaverSeriesNovelRepository naverSeriesNovelRepository,
            NovelCommonRepository novelCommonRepository) {
        this.configRepository = configRepository;
        this.fieldMappingRepository = fieldMappingRepository;
        this.customFieldCalculationRepository = customFieldCalculationRepository;
        this.naverSeriesNovelRepository = naverSeriesNovelRepository;
        this.novelCommonRepository = novelCommonRepository;
    }

    @Transactional
    public NovelCommon integrateNovelData(Long configId, List<Long> sourceIds) {
        ContentIntegrationConfig config = configRepository.findById(configId)
                .orElseThrow(() -> new RuntimeException("Configuration not found"));

        if (!"novel".equals(config.getContentType())) {
            throw new IllegalArgumentException("Configuration is not for novels");
        }

        // 소스 데이터 로드
        Map<String, Object> sourceData = new HashMap<>();

        // 네이버 시리즈 소설 데이터 로드
        List<NaverSeriesNovel> naverNovels = naverSeriesNovelRepository.findAllById(
                sourceIds.stream()
                        .filter(id -> id != null)
                        .collect(Collectors.toList())
        );

        for (NaverSeriesNovel novel : naverNovels) {
            sourceData.put("naver_" + novel.getId(), novel);
        }

        // Common 엔티티 생성
        NovelCommon novelCommon = new NovelCommon();

        // 필드 매핑 적용
        List<FieldMapping> fieldMappings = fieldMappingRepository.findByConfigIdOrderByPriorityAsc(configId);
        applyFieldMappings(novelCommon, fieldMappings, sourceData);

        // 커스텀 계산 적용
        List<CustomFieldCalculation> customCalculations = customFieldCalculationRepository.findByConfigId(configId);
        applyCustomCalculations(novelCommon, customCalculations, sourceData);

        // 저장 및 반환
        return novelCommonRepository.save(novelCommon);
    }

    private void applyFieldMappings(NovelCommon target, List<FieldMapping> mappings, Map<String, Object> sourceData) {
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

                                    // 특수 처리: NaverSeriesNovelGenre -> String으로 변환
                                    if (value instanceof List && !((List<?>) value).isEmpty() && ((List<?>) value).get(0) instanceof NaverSeriesNovelGenre) {
                                        List<String> genreNames = ((List<NaverSeriesNovelGenre>) value).stream()
                                                .map(NaverSeriesNovelGenre::getName)
                                                .collect(Collectors.toList());
                                        targetField.set(target, genreNames);
                                    } else {
                                        targetField.set(target, value);
                                    }
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

    private void applyCustomCalculations(NovelCommon target, List<CustomFieldCalculation> calculations, Map<String, Object> sourceData) {
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
                            // 복잡한 커스텀 계산 로직은 별도의 서비스에서 구현
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

    private void applyAverageCalculation(NovelCommon target, Field field, CustomFieldCalculation calc, Map<String, Object> sourceData) {
        // 평균 계산 로직 구현
        // 실제 구현은 계산 대상 필드와 데이터 유형에 따라 달라집니다.
    }

    private void applyMaxCalculation(NovelCommon target, Field field, CustomFieldCalculation calc, Map<String, Object> sourceData) {
        // 최대값 계산 로직 구현
        // 실제 구현은 계산 대상 필드와 데이터 유형에 따라 달라집니다.
    }

    private void applyCustomExpression(NovelCommon target, Field field, CustomFieldCalculation calc, Map<String, Object> sourceData) {
        // 커스텀 계산 로직 구현
        // 실제 구현은 계산식과 데이터 유형에 따라 달라집니다.
        // 예: 스크립트 엔진을 사용하여 동적으로 표현식 평가
    }
}