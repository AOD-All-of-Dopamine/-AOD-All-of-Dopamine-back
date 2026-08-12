package com.example.crawler.demo.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 데모 페이지 렌더링을 위한 컨트롤러
 * (2026-08 표준화: 템플릿이 존재하는 /demo/admin만 유지 — 나머지 뷰는 소스에서 삭제된 상태였음)
 */
@Controller
@RequestMapping("/demo")
public class DemoPageController {

    /**
     * 관리자용 데모 데이터 준비 페이지
     */
    @GetMapping("/admin")
    public String adminDemoPage() {
        return "demo/admin";
    }
}
