package com.example.AOD.common.controller;

import com.example.AOD.common.dto.ConfigurationDTO;
import com.example.AOD.common.dto.FieldInfoDTO;
import com.example.AOD.common.service.IntegrationConfigService;
import com.example.AOD.common.service.MetadataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/admin/integration/config")
public class IntegrationConfigController {

    private final IntegrationConfigService configService;
    private final MetadataService metadataService;

    @Autowired
    public IntegrationConfigController(
            IntegrationConfigService configService,
            MetadataService metadataService) {
        this.configService = configService;
        this.metadataService = metadataService;
    }

    @GetMapping("/list/{contentType}")
    public String listConfigurations(@PathVariable String contentType, Model model) {
        List<ConfigurationDTO> configs = configService.getConfigurationsForContentType(contentType);
        model.addAttribute("configs", configs);
        model.addAttribute("contentType", contentType);
        return "admin/integration/config-list";
    }

    @GetMapping("/create/{contentType}")
    public String showCreateForm(@PathVariable String contentType, Model model) {
        ConfigurationDTO config = new ConfigurationDTO();
        config.setContentType(contentType);
        model.addAttribute("config", config);
        model.addAttribute("contentType", contentType);
        model.addAttribute("platforms", metadataService.getPlatformsForContentType(contentType));
        model.addAttribute("commonFields", metadataService.getCommonFieldsForContentType(contentType));
        return "admin/integration/config-form";
    }

    @PostMapping("/create")
    public String createConfiguration(@ModelAttribute ConfigurationDTO configDTO) {
        configService.createConfiguration(configDTO);
        return "redirect:/admin/integration/config/list/" + configDTO.getContentType();
    }

    @GetMapping("/edit/{id}")
    public String showEditForm(@PathVariable Long id, Model model) {
        ConfigurationDTO config = configService.getConfigurationById(id);
        model.addAttribute("config", config);
        model.addAttribute("contentType", config.getContentType());
        model.addAttribute("platforms", metadataService.getPlatformsForContentType(config.getContentType()));
        model.addAttribute("commonFields", metadataService.getCommonFieldsForContentType(config.getContentType()));
        return "admin/integration/config-form";
    }

    @PostMapping("/edit/{id}")
    public String updateConfiguration(
            @PathVariable Long id,
            @ModelAttribute ConfigurationDTO configDTO) {
        configService.updateConfiguration(id, configDTO);
        return "redirect:/admin/integration/config/list/" + configDTO.getContentType();
    }

    @GetMapping("/delete/{id}")
    public String deleteConfiguration(@PathVariable Long id) {
        ConfigurationDTO config = configService.getConfigurationById(id);
        String contentType = config.getContentType();
        configService.deleteConfiguration(id);
        return "redirect:/admin/integration/config/list/" + contentType;
    }

    // AJAX 메서드: 플랫폼 필드 가져오기
    @GetMapping("/fields/{contentType}/{platformId}")
    @ResponseBody
    public List<FieldInfoDTO> getPlatformFields(
            @PathVariable String contentType,
            @PathVariable String platformId) {
        return metadataService.getPlatformFields(contentType, platformId);
    }
}