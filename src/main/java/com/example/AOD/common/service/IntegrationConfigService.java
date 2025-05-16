package com.example.AOD.common.service;

import com.example.AOD.common.config.ContentIntegrationConfig;
import com.example.AOD.common.config.CustomFieldCalculation;
import com.example.AOD.common.config.FieldMapping;
import com.example.AOD.common.dto.ConfigurationDTO;
import com.example.AOD.common.dto.CustomFieldCalculationDTO;
import com.example.AOD.common.dto.FieldMappingDTO;
import com.example.AOD.common.repository.ContentIntegrationConfigRepository;
import com.example.AOD.common.repository.CustomFieldCalculationRepository;
import com.example.AOD.common.repository.FieldMappingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class IntegrationConfigService {

    private final ContentIntegrationConfigRepository configRepository;
    private final FieldMappingRepository fieldMappingRepository;
    private final CustomFieldCalculationRepository customFieldCalculationRepository;

    @Autowired
    public IntegrationConfigService(
            ContentIntegrationConfigRepository configRepository,
            FieldMappingRepository fieldMappingRepository,
            CustomFieldCalculationRepository customFieldCalculationRepository) {
        this.configRepository = configRepository;
        this.fieldMappingRepository = fieldMappingRepository;
        this.customFieldCalculationRepository = customFieldCalculationRepository;
    }

    @Transactional(readOnly = true)
    public List<ConfigurationDTO> getConfigurationsForContentType(String contentType) {
        List<ContentIntegrationConfig> configs = configRepository.findByContentTypeAndIsActiveTrue(contentType);
        return configs.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ConfigurationDTO getConfigurationById(Long id) {
        ContentIntegrationConfig config = configRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Configuration not found"));
        return convertToDTO(config);
    }

    @Transactional
    public ConfigurationDTO createConfiguration(ConfigurationDTO configDTO) {
        ContentIntegrationConfig config = new ContentIntegrationConfig();
        config.setContentType(configDTO.getContentType());
        config.setName(configDTO.getName());
        config.setDescription(configDTO.getDescription());
        config.setActive(configDTO.isActive());

        ContentIntegrationConfig savedConfig = configRepository.save(config);

        // 필드 매핑 저장
        if (configDTO.getFieldMappings() != null) {
            for (FieldMappingDTO mappingDTO : configDTO.getFieldMappings()) {
                FieldMapping mapping = new FieldMapping();
                mapping.setConfig(savedConfig);
                mapping.setCommonField(mappingDTO.getCommonField());
                mapping.setPlatform(mappingDTO.getPlatform());
                mapping.setPlatformField(mappingDTO.getPlatformField());
                mapping.setPriority(mappingDTO.getPriority());
                fieldMappingRepository.save(mapping);
            }
        }

        // 커스텀 계산 저장
        if (configDTO.getCustomCalculations() != null) {
            for (CustomFieldCalculationDTO calcDTO : configDTO.getCustomCalculations()) {
                CustomFieldCalculation calc = new CustomFieldCalculation();
                calc.setConfig(savedConfig);
                calc.setTargetField(calcDTO.getTargetField());
                calc.setCalculationType(calcDTO.getCalculationType());
                calc.setCalculationExpression(calcDTO.getCalculationExpression());
                calc.setIsRequired(calcDTO.getIsRequired());
                customFieldCalculationRepository.save(calc);
            }
        }

        return getConfigurationById(savedConfig.getId());
    }

    @Transactional
    public ConfigurationDTO updateConfiguration(Long id, ConfigurationDTO configDTO) {
        ContentIntegrationConfig config = configRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Configuration not found"));

        config.setName(configDTO.getName());
        config.setDescription(configDTO.getDescription());
        config.setActive(configDTO.isActive());

        // 필드 매핑 업데이트 (기존 매핑 삭제 후 새로 추가)]]
        List<FieldMapping> existingMappings = fieldMappingRepository.findByConfigIdOrderByPriorityAsc(id);
        fieldMappingRepository.deleteAll(existingMappings);

        if (configDTO.getFieldMappings() != null) {
            for (FieldMappingDTO mappingDTO : configDTO.getFieldMappings()) {
                FieldMapping mapping = new FieldMapping();
                mapping.setConfig(config);
                mapping.setCommonField(mappingDTO.getCommonField());
                mapping.setPlatform(mappingDTO.getPlatform());
                mapping.setPlatformField(mappingDTO.getPlatformField());
                mapping.setPriority(mappingDTO.getPriority());
                fieldMappingRepository.save(mapping);
            }
        }

        // 커스텀 계산 업데이트 (기존 계산 삭제 후 새로 추가)
        List<CustomFieldCalculation> existingCalcs = customFieldCalculationRepository.findByConfigId(id);
        customFieldCalculationRepository.deleteAll(existingCalcs);

        if (configDTO.getCustomCalculations() != null) {
            for (CustomFieldCalculationDTO calcDTO : configDTO.getCustomCalculations()) {
                CustomFieldCalculation calc = new CustomFieldCalculation();
                calc.setConfig(config);
                calc.setTargetField(calcDTO.getTargetField());
                calc.setCalculationType(calcDTO.getCalculationType());
                calc.setCalculationExpression(calcDTO.getCalculationExpression());
                calc.setIsRequired(calcDTO.getIsRequired());
                customFieldCalculationRepository.save(calc);
            }
        }

        return getConfigurationById(id);
    }

    @Transactional
    public void deleteConfiguration(Long id) {
        configRepository.deleteById(id);
    }

    private ConfigurationDTO convertToDTO(ContentIntegrationConfig config) {
        ConfigurationDTO dto = new ConfigurationDTO();
        dto.setId(config.getId());
        dto.setContentType(config.getContentType());
        dto.setName(config.getName());
        dto.setDescription(config.getDescription());
        dto.setActive(config.isActive());

        // 필드 매핑 변환
        List<FieldMapping> mappings = fieldMappingRepository.findByConfigIdOrderByPriorityAsc(config.getId());
        dto.setFieldMappings(mappings.stream().map(mapping -> {
            FieldMappingDTO mappingDTO = new FieldMappingDTO();
            mappingDTO.setId(mapping.getId());
            mappingDTO.setCommonField(mapping.getCommonField());
            mappingDTO.setPlatform(mapping.getPlatform());
            mappingDTO.setPlatformField(mapping.getPlatformField());
            mappingDTO.setPriority(mapping.getPriority());
            return mappingDTO;
        }).collect(Collectors.toList()));

        // 커스텀 계산 변환
        List<CustomFieldCalculation> calcs = customFieldCalculationRepository.findByConfigId(config.getId());
        dto.setCustomCalculations(calcs.stream().map(calc -> {
            CustomFieldCalculationDTO calcDTO = new CustomFieldCalculationDTO();
            calcDTO.setId(calc.getId());
            calcDTO.setTargetField(calc.getTargetField());
            calcDTO.setCalculationType(calc.getCalculationType());
            calcDTO.setCalculationExpression(calc.getCalculationExpression());
            calcDTO.setIsRequired(calc.getIsRequired());
            return calcDTO;
        }).collect(Collectors.toList()));

        return dto;
    }
}