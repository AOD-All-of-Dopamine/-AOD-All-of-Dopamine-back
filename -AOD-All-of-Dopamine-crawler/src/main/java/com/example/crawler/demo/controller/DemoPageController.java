package com.example.crawler.demo.controller;

import com.example.crawler.demo.dto.ContentDTO;
import com.example.crawler.demo.dto.ContentDetailDTO;
import com.example.crawler.demo.service.DemoPageService;
import com.example.shared.entity.Domain;
//import com.example.crawler.recommendation.service.TraditionalRecommendationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * 데모 페이지 렌더링을 위한 컨트롤러
 */
@Controller
@RequestMapping("/demo")
@RequiredArgsConstructor
public class DemoPageController {

    private final DemoPageService demoPageService;
    // recommendation 패키지의 TraditionalRecommendationService.java 파일의 주석을 해제해야 합니다.
    // private final TraditionalRecommendationService recommendationService;

    /**
     * 루트 URL 접속 시 추천 페이지로 리다이렉트
     */
    @GetMapping("/")
    public String root() {
        return "redirect:/demo/recommendation";
    }

    /**
     * 추천 페이지
     */
    @GetMapping("/recommendation")
    public String recommendationPage(Model model) {
        // TODO: TraditionalRecommendationService 주석 해제 후 아래 로직 활성화
        // String demoUser = "demouser"; // 데모용 가상 유저
        // Map<String, List<?>> recommendations = recommendationService.getRecommendationsForUser(demoUser);
        // model.addAttribute("recommendations", recommendations);

        // 임시로 신규 콘텐츠를 모델에 추가
        model.addAttribute("recommendations", Map.of("new", demoPageService.getNewContents(12)));
        return "demo/recommendation";
    }

    /**
     * 신작 페이지
     */
    @GetMapping("/new")
    public String newPage(Model model) {
        List<ContentDTO> newContents = demoPageService.getNewContents(20);
        model.addAttribute("contents", newContents);
        return "demo/new";
    }

    /**
     * 랭킹 페이지
     */
    @GetMapping("/ranking")
    public String rankingPage(Model model) {
        List<ContentDTO> rankingContents = demoPageService.getRankingContents(20);
        model.addAttribute("contents", rankingContents);
        return "demo/ranking";
    }

    /**
     * 탐색 페이지
     */
    @GetMapping("/explore")
    public String explorePage(@RequestParam(name = "domain", defaultValue = "GAME") String domainStr,
                              @RequestParam(defaultValue = "0") int page,
                              Model model) {
        Domain domain;
        try {
            domain = Domain.valueOf(domainStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            domain = Domain.GAME; // 잘못된 파라미터일 경우 기본값으로 설정
        }

        Pageable pageable = PageRequest.of(page, 12);
        Page<ContentDTO> contentPage = demoPageService.getExploreContents(domain, pageable);

        model.addAttribute("contentPage", contentPage);
        model.addAttribute("currentDomain", domain.name());
        model.addAttribute("domains", Domain.values()); // 모든 도메인 목록을 모델에 추가
        return "demo/explore";
    }

    /**
     * 관리자용 데모 데이터 준비 페이지
     */
    @GetMapping("/admin")
    public String adminDemoPage() {
        return "demo/admin";
    }

    /**
     * 콘텐츠 상세 페이지
     */
    @GetMapping("/content/{id}")
    public String contentDetailPage(@PathVariable("id") Long id, Model model) {
        ContentDetailDTO contentDetail = demoPageService.getContentDetails(id);
        model.addAttribute("content", contentDetail);
        return "demo/detail";
    }
}

