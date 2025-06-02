package com.example.AOD.common.controller;

import com.example.AOD.common.dto.ConfigurationDTO;
import com.example.AOD.common.dto.ContentIntegrationRequestDTO;
import com.example.AOD.common.dto.ManualIntegrationDTO;
import com.example.AOD.common.dto.PlatformInfoDTO;
import com.example.AOD.common.service.ContentIntegrationService;
import com.example.AOD.common.service.IntegrationConfigService;
import com.example.AOD.common.service.MetadataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/admin/integration")
public class ContentIntegrationController {

    private final ContentIntegrationService contentIntegrationService;
    private final IntegrationConfigService configService;
    private final MetadataService metadataService;

    @Autowired
    public ContentIntegrationController(
            ContentIntegrationService contentIntegrationService,
            IntegrationConfigService configService,
            MetadataService metadataService) {
        this.contentIntegrationService = contentIntegrationService;
        this.configService = configService;
        this.metadataService = metadataService;
    }

    @GetMapping("/{contentType}/integrate")
    public String showIntegrationForm(@PathVariable String contentType, Model model) {
        // 통합 설정 가져오기
        List<ConfigurationDTO> configs = configService.getConfigurationsForContentType(contentType);

        // 플랫폼 정보 가져오기
        List<PlatformInfoDTO> platforms = metadataService.getPlatformsForContentType(contentType);

        // 플랫폼별 콘텐츠 목록 가져오기
        Map<String, List<?>> platformContents = loadPlatformContents(contentType, platforms);

        model.addAttribute("contentType", contentType);
        model.addAttribute("configs", configs);
        model.addAttribute("platforms", platforms);
        model.addAttribute("platformContents", platformContents);
        model.addAttribute("integrationRequest", new ContentIntegrationRequestDTO());

        return "admin/integration/content-integration-form";
    }

    @PostMapping("/{contentType}/integrate")
    public String integrateContent(
            @PathVariable String contentType,
            @ModelAttribute ContentIntegrationRequestDTO request,
            @RequestParam(defaultValue = "auto") String integrationMode,
            Model model,
            RedirectAttributes redirectAttributes) {

        // 수동 통합 모드인 경우
        if ("manual".equals(integrationMode)) {
            return redirectToManualIntegration(contentType, request, redirectAttributes);
        }

        // 기존 자동 통합 로직
        Map<Long, String> duplicates = contentIntegrationService.checkDuplicateContent(
                contentType, request.getSourceIds());

        if (!duplicates.isEmpty()) {
            List<String> duplicateTitles = new ArrayList<>(duplicates.values());
            redirectAttributes.addFlashAttribute("duplicateError", true);
            redirectAttributes.addFlashAttribute("duplicateTitles", duplicateTitles);
            redirectAttributes.addFlashAttribute("integrationRequest", request);
            return "redirect:/admin/integration/" + contentType + "/integrate";
        }

        List<Object> results = contentIntegrationService.integrateContent(
                request.getConfigId(), request.getSourceIds());

        model.addAttribute("contentType", contentType);
        model.addAttribute("results", results);
        model.addAttribute("result", results.isEmpty() ? null : results.get(0));

        return "admin/integration/content-integration-result";
    }

    // 수동 통합 폼 표시
    @GetMapping("/{contentType}/manual-integrate")
    public String showManualIntegrationForm(
            @PathVariable String contentType,
            @RequestParam Long configId,
            @RequestParam List<Long> sourceIds,
            Model model) {

        // 설정 정보 가져오기
        ConfigurationDTO config = configService.getConfigurationById(configId);

        // 플랫폼 정보 가져오기
        List<PlatformInfoDTO> platforms = metadataService.getPlatformsForContentType(contentType);

        // 선택된 소스 데이터 가져오기
        Map<String, Object> sourceData = contentIntegrationService.loadSourceDataForManualIntegration(
                contentType, sourceIds);

        // 필드 정보 가져오기
        List<Map<String, Object>> fieldInfo = contentIntegrationService.getFieldInfoForManualIntegration(
                contentType, sourceData);

        ManualIntegrationDTO manualDTO = new ManualIntegrationDTO();
        manualDTO.setConfigId(configId);
        manualDTO.setContentType(contentType);
        manualDTO.setSourceIds(sourceIds);

        model.addAttribute("contentType", contentType);
        model.addAttribute("config", config);
        model.addAttribute("platforms", platforms);
        model.addAttribute("sourceData", sourceData);
        model.addAttribute("fieldInfo", fieldInfo);
        model.addAttribute("manualIntegration", manualDTO);

        return "admin/integration/manual-integration-form";
    }

    // 수동 통합 처리
    @PostMapping("/{contentType}/manual-integrate")
    public String processManualIntegration(
            @PathVariable String contentType,
            @ModelAttribute ManualIntegrationDTO manualDTO,
            Model model,
            RedirectAttributes redirectAttributes) {

        try {
            Object result = contentIntegrationService.processManualIntegration(manualDTO);

            List<Object> results = new ArrayList<>();
            results.add(result);

            model.addAttribute("contentType", contentType);
            model.addAttribute("results", results);
            model.addAttribute("result", result);

            return "admin/integration/content-integration-result";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "수동 통합 중 오류가 발생했습니다: " + e.getMessage());
            return "redirect:/admin/integration/" + contentType + "/manual-integrate" +
                    "?configId=" + manualDTO.getConfigId() +
                    "&sourceIds=" + String.join(",", manualDTO.getSourceIds().stream().map(String::valueOf).toArray(String[]::new));
        }
    }

    // 수동 통합으로 리다이렉트
    private String redirectToManualIntegration(String contentType, ContentIntegrationRequestDTO request,
                                               RedirectAttributes redirectAttributes) {
        if (request.getSourceIds() == null || request.getSourceIds().isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "통합할 콘텐츠를 선택해주세요.");
            return "redirect:/admin/integration/" + contentType + "/integrate";
        }

        String sourceIdsParam = String.join(",",
                request.getSourceIds().stream().map(String::valueOf).toArray(String[]::new));

        return "redirect:/admin/integration/" + contentType + "/manual-integrate" +
                "?configId=" + request.getConfigId() +
                "&sourceIds=" + sourceIdsParam;
    }

    // 플랫폼별 콘텐츠 목록 로딩
    private Map<String, List<?>> loadPlatformContents(String contentType, List<PlatformInfoDTO> platforms) {
        Map<String, List<?>> platformContents = new HashMap<>();

        for (PlatformInfoDTO platform : platforms) {
            List<?> contents = metadataService.getContentsForPlatform(contentType, platform.getId());
            platformContents.put(platform.getId(), contents);
        }

        return platformContents;
    }


}