package com.example.crawler.admin.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin/test")
public class TestController {

    @GetMapping("/config-list")
    public String testConfigList() {
        return "admin/integration/config-list";
    }

    @GetMapping("/config-form")
    public String testConfigForm() {
        return "admin/integration/config-form";
    }



}

