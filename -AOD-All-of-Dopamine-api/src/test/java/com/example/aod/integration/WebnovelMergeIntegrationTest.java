//package com.example.aod.integration;
//
//
//import com.example.AOD.domain.Content;
//import com.example.AOD.domain.entity.Domain;
//import com.example.AOD.domain.entity.PlatformData;
//import com.example.AOD.repo.ContentRepository;
//import com.example.AOD.repo.PlatformDataRepository;
//import com.example.AOD.rules.MappingRule;
//import com.example.AOD.service.RuleLoader;
//import com.example.AOD.service.TransformEngine;
//import com.example.AOD.service.UpsertService;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import org.junit.jupiter.api.*;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.context.SpringBootTest;
//import org.springframework.core.io.ClassPathResource;
//import org.springframework.test.context.ActiveProfiles;
//import org.springframework.test.context.DynamicPropertyRegistry;
//import org.springframework.test.context.DynamicPropertySource;
//import org.testcontainers.containers.PostgreSQLContainer;
//import org.testcontainers.junit.jupiter.Testcontainers;
//
//import java.io.InputStream;
//import java.util.List;
//import java.util.Map;
//import java.util.Optional;
//
//import static org.assertj.core.api.Assertions.assertThat;
//
//@Testcontainers
//@SpringBootTest
//@ActiveProfiles("test")
//@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
//public class WebnovelMergeIntegrationTest {
//
//    static final PostgreSQLContainer<?> postgres =
//            new PostgreSQLContainer<>("postgres:16-alpine")
//                    .withDatabaseName("aod")
//                    .withUsername("postgres")
//                    .withPassword("postgres");
//
//    @DynamicPropertySource
//    static void props(DynamicPropertyRegistry r) {
//        postgres.start();
//        r.add("spring.datasource.url", postgres::getJdbcUrl);
//        r.add("spring.datasource.username", postgres::getUsername);
//        r.add("spring.datasource.password", postgres::getPassword);
//        r.add("spring.jpa.hibernate.ddl-auto", () -> "update");
//        r.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
//        r.add("spring.jpa.show-sql", () -> "true");
//    }
//
//    @Autowired
//    RuleLoader ruleLoader;
//    @Autowired
//    TransformEngine transform;
//    @Autowired
//    UpsertService upsert;
//    @Autowired
//    ContentRepository contentRepo;
//    @Autowired
//    PlatformDataRepository platformRepo;
//
//    ObjectMapper om = new ObjectMapper();
//    static Long contentIdAfterKakao;
//
//    @Test @Order(1)
//    void upsert_kakaopage_creates_content_and_platform() throws Exception {
//        MappingRule rule = ruleLoader.load("rules/webnovel/kakaopage.yml");
//
//        Map<String,Object> raw;
//        try (InputStream in = new ClassPathResource("sample/raw-kakaopage.json").getInputStream()) {
//            raw = om.readValue(in, Map.class);
//        }
//        var tri = transform.transform(raw, rule);
//
//        String platformSpecificId = (String) raw.get("platformSpecificId");
//        String url = (String) raw.get("url");
//
//        Long contentId = upsert.upsert(
//                Domain.valueOf(rule.getDomain()),
//                tri.master(), tri.platform(), tri.domain(),
//                platformSpecificId, url
//        );
//        contentIdAfterKakao = contentId;
//
//        // 검증: contents 1건, platform_data 1건
//        List<Content> contents = contentRepo.findAll();
//        List<PlatformData> pds = platformRepo.findAll();
//
//        assertThat(contents).hasSize(1);
//        assertThat(contents.get(0).getContentId()).isEqualTo(contentId);
//        assertThat(contents.get(0).getMasterTitle()).isEqualTo("나 혼자만 레벨업");
//
//        assertThat(pds).hasSize(1);
//        assertThat(pds.get(0).getPlatformName()).isEqualTo("KakaoPage");
//        assertThat(pds.get(0).getPlatformSpecificId()).isEqualTo("S12345");
//        assertThat(pds.get(0).getAttributes().get("series_id")).isEqualTo("S12345");
//    }
//
//    @Test @Order(2)
//    void upsert_naverseries_links_to_same_content() throws Exception {
//        MappingRule rule = ruleLoader.load("rules/webnovel/naverseries.yml");
//
//        Map<String,Object> raw;
//        try (InputStream in = new ClassPathResource("sample/raw-naverseries.json").getInputStream()) {
//            raw = om.readValue(in, Map.class);
//        }
//        var tri = transform.transform(raw, rule);
//
//        String platformSpecificId = (String) raw.get("platformSpecificId");
//        String url = (String) raw.get("url");
//
//        Long contentId = upsert.upsert(
//                Domain.valueOf(rule.getDomain()),
//                tri.master(), tri.platform(), tri.domain(),
//                platformSpecificId, url
//        );
//
//        // 동일 작품으로 매칭되어야 함
//        assertThat(contentId).isEqualTo(contentIdAfterKakao);
//
//        // 검증: contents 여전히 1건, platform_data는 2건
//        List<Content> contents = contentRepo.findAll();
//        List<PlatformData> pds = platformRepo.findAll();
//        assertThat(contents).hasSize(1);
//        assertThat(pds).hasSize(2);
//
//        // 두 플랫폼 모두 연결되었는지 확인
//        Optional<PlatformData> kakao = platformRepo.findByPlatformNameAndPlatformSpecificId("KakaoPage","S12345");
//        Optional<PlatformData> naver = platformRepo.findByPlatformNameAndPlatformSpecificId("NaverSeries","123456");
//        assertThat(kakao).isPresent();
//        assertThat(naver).isPresent();
//
//        // 네이버 attributes 확인
//        assertThat(naver.get().getAttributes().get("title_id")).isEqualTo("123456");
//        assertThat(naver.get().getAttributes().get("weekday")).isEqualTo("수");
//    }
//}


