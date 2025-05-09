package com.example.AOD.common.controller;

import com.example.AOD.common.dto.FieldInfoDTO;
import com.example.AOD.common.dto.PlatformInfoDTO;
import com.example.AOD.common.service.MetadataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

@Controller
@RequestMapping("/admin/metadata")
public class MetadataController {

    private final MetadataService metadataService;

    @Autowired
    public MetadataController(MetadataService metadataService) {
        this.metadataService = metadataService;
    }

    @GetMapping("/platforms/{contentType}")
    @ResponseBody
    public List<PlatformInfoDTO> getPlatformsForContentType(@PathVariable String contentType) {
        return metadataService.getPlatformsForContentType(contentType);
    }

    @GetMapping("/common-fields/{contentType}")
    @ResponseBody
    public List<FieldInfoDTO> getCommonFieldsForContentType(@PathVariable String contentType) {
        return metadataService.getCommonFieldsForContentType(contentType);
    }

    @GetMapping("/platform-fields/{contentType}/{platformId}")
    @ResponseBody
    public List<FieldInfoDTO> getPlatformFields(
            @PathVariable String contentType,
            @PathVariable String platformId) {
        return metadataService.getPlatformFields(contentType, platformId);
    }
}