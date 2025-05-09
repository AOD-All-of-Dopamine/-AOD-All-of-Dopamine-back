//package com.example.AOD.common.controller;
//
//import com.example.AOD.Novel.NaverSeriesNovel.domain.NaverSeriesNovel;
//import com.example.AOD.Novel.NaverSeriesNovel.repository.NaverSeriesNovelRepository;
//import com.example.AOD.common.commonDomain.NovelCommon;
//import com.example.AOD.common.dto.ConfigurationDTO;
//import com.example.AOD.common.dto.ContentIntegrationRequestDTO;
//import com.example.AOD.common.service.IntegrationConfigService;
//import com.example.AOD.common.service.NovelIntegrationService;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.stereotype.Controller;
//import org.springframework.ui.Model;
//import org.springframework.web.bind.annotation.*;
//
//import java.util.List;
//
//@Controller
//@RequestMapping("/admin/integration/novel")
//public class NovelIntegrationController {
//
//    //private final NovelIntegrationService novelIntegrationService;
//    private final IntegrationConfigService configService;
//    private final NaverSeriesNovelRepository naverNovelRepository;
//
//    @Autowired
//    public NovelIntegrationController(
//            //NovelIntegrationService novelIntegrationService,
//            IntegrationConfigService configService,
//            NaverSeriesNovelRepository naverNovelRepository) {
//        this.novelIntegrationService = novelIntegrationService;
//        this.configService = configService;
//        this.naverNovelRepository = naverNovelRepository;
//    }
//
//    @GetMapping("/integrate")
//    public String showIntegrationForm(Model model) {
//        List<ConfigurationDTO> configs = configService.getConfigurationsForContentType("novel");
//        List<NaverSeriesNovel> naverNovels = naverNovelRepository.findAll();
//
//        model.addAttribute("configs", configs);
//        model.addAttribute("naverNovels", naverNovels);
//        model.addAttribute("integrationRequest", new ContentIntegrationRequestDTO());
//
//        return "admin/integration/novel-integration-form";
//    }
//
//    @PostMapping("/integrate")
//    public String integrateNovel(@ModelAttribute ContentIntegrationRequestDTO request, Model model) {
//        NovelCommon result = novelIntegrationService.integrateNovelData(
//                request.getConfigId(),
//                request.getSourceIds()
//        );
//
//        model.addAttribute("result", result);
//        return "admin/integration/novel-integration-result";
//    }
//}