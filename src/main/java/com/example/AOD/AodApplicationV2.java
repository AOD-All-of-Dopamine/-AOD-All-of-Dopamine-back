package com.example.AOD;


import com.example.AOD.v2.domain.entity.Domain;
import com.example.AOD.v2.rules.MappingRule;
import com.example.AOD.v2.service.RuleLoader;
import com.example.AOD.v2.service.TransformEngine;
import com.example.AOD.v2.service.UpsertService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.util.Map;

@SpringBootApplication
public class AodApplicationV2 implements CommandLineRunner {

    private final RuleLoader ruleLoader;
    private final TransformEngine transformEngine;
    private final UpsertService upsertService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AodApplication(RuleLoader ruleLoader, TransformEngine transformEngine, UpsertService upsertService) {
        this.ruleLoader = ruleLoader;
        this.transformEngine = transformEngine;
        this.upsertService = upsertService;
    }

    public static void main(String[] args) {
        SpringApplication.run(AodApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        // 1) 규칙 로드
        MappingRule rule = ruleLoader.load("rules/av/netflix.yml");

        // 2) RAW JSON 로드(플랫폼 크롤/API 응답 가정)
        try (InputStream in = new ClassPathResource("sample/raw-netflix.json").getInputStream()) {
            @SuppressWarnings("unchecked")
            Map<String,Object> raw = objectMapper.readValue(in, Map.class);

            // 3) 변환
            var pair = transformEngine.transform(raw, rule);
            var master = pair.getKey();
            var platform = pair.getValue();

            // 4) 업서트(간단 매칭: domain+title+year)
            String platformSpecificId = (String) raw.get("platformSpecificId");
            String url = (String) raw.get("url");

            Long contentId = upsertService.upsert(
                    Domain.valueOf(rule.getDomain()),
                    master, platform,
                    platformSpecificId, url
            );

            System.out.println("UPSERT OK, content_id = " + contentId);
        }
    }
}
