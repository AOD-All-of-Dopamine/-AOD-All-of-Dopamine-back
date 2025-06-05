package com.example.AOD.common.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin/statistics")
public class StatisticController {

    @GetMapping("/overview")
    public String overview() {
        return "/admin/statistics/overview";
    }

    @GetMapping("/platform")
    public String platform() {
        return "/admin/statistics/platform";
    }

    @GetMapping("/multi-platform")
    public String multiPlatform() {
        return "/admin/statistics/multi-platform";
    }

    @GetMapping("/duplicates")
    public String duplicates() {
        return "/admin/statistics/duplicates";
    }
}
