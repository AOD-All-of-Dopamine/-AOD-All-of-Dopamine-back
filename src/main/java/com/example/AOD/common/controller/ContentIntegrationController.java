package com.example.AOD.common.controller;

import com.example.AOD.common.dto.ConfigurationDTO;
import com.example.AOD.common.dto.ContentIntegrationRequestDTO;
import com.example.AOD.common.dto.PlatformInfoDTO;
import com.example.AOD.common.service.ContentIntegrationService;
import com.example.AOD.common.service.IntegrationConfigService;
import com.example.AOD.common.service.MetadataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

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
            Model model) {

        List<Object> results = contentIntegrationService.integrateContent(request.getConfigId(), request.getSourceIds());

        model.addAttribute("contentType", contentType);
        model.addAttribute("results", results); // 복수형으로 변경
        model.addAttribute("result", results.isEmpty() ? null : results.get(0)); // 기존 템플릿 호환성 유지

        return "admin/integration/content-integration-result";
    }

    // 플랫폼별 콘텐츠 목록 로딩
    private Map<String, List<?>> loadPlatformContents(String contentType, List<PlatformInfoDTO> platforms) {
        Map<String, List<?>> platformContents = new HashMap<>();

        // 각 플랫폼별로 콘텐츠 로드
        for (PlatformInfoDTO platform : platforms) {
            List<?> contents = metadataService.getContentsForPlatform(contentType, platform.getId());
            platformContents.put(platform.getId(), contents);
        }

        return platformContents;
    }

    // 기존 유형별 통합 컨트롤러 메서드 - 호환성을 위해 유지
    @GetMapping("/novel/integrate")
    public String showNovelIntegrationForm(Model model) {
        return showIntegrationForm("novel", model);
    }

    @PostMapping("/novel/integrate")
    public String integrateNovel(@ModelAttribute ContentIntegrationRequestDTO request, Model model) {
        return integrateContent("novel", request, model);
    }

    // 다른 콘텐츠 유형에 대한 메서드들...
}