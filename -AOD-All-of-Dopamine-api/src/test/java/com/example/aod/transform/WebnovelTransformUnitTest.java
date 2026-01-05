//package com.example.aod.transform;
//
//
//import com.example.AOD.rules.MappingRule;
//import com.example.AOD.service.RuleLoader;
//import com.example.AOD.service.TransformEngine;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import org.junit.jupiter.api.Test;
//import org.springframework.core.io.ClassPathResource;
//import org.springframework.test.context.ActiveProfiles;
//
//import java.io.InputStream;
//import java.util.Map;
//
//import static org.assertj.core.api.Assertions.assertThat;
//
//@ActiveProfiles("test")
//public class WebnovelTransformUnitTest {
//
//    private final RuleLoader ruleLoader = new RuleLoader();
//    private final TransformEngine engine = new TransformEngine();
//    private final ObjectMapper om = new ObjectMapper();
//
//    @Test
//    void kakaoPage_rule_maps_and_normalizes() throws Exception {
//        MappingRule rule = ruleLoader.load("rules/webnovel/kakaopage.yml");
//        Map<String,Object> raw;
//        try (InputStream in = new ClassPathResource("sample/raw-kakaopage.json").getInputStream()) {
//            raw = om.readValue(in, Map.class);
//        }
//
//        var tri = engine.transform(raw, rule);
//
//        // master
//        assertThat(tri.master().get("master_title")).isEqualTo("나 혼자만 레벨업"); // 괄호 제거 + 정규화
//        assertThat(tri.master().get("poster_image_url")).isEqualTo("https://res.kakao/cover.jpg");
//
//        // domain
//        assertThat(tri.domain().get("author")).isEqualTo("추공");
//        assertThat(tri.domain().get("started_at")).isEqualTo("2018-03-01");
//
//        // platform
//        assertThat(tri.platform().get("platformName")).isEqualTo("KakaoPage");
//        Map attrs = (Map) tri.platform().get("attributes");
//        assertThat(attrs.get("series_id")).isEqualTo("S12345");
//        assertThat(attrs.get("view_count")).isEqualTo(123456789);
//    }
//
//    @Test
//    void naverSeries_rule_maps_and_normalizes() throws Exception {
//        MappingRule rule = ruleLoader.load("rules/webnovel/naverseries.yml");
//        Map<String,Object> raw;
//        try (InputStream in = new ClassPathResource("sample/raw-naverseries.json").getInputStream()) {
//            raw = om.readValue(in, Map.class);
//        }
//
//        var tri = engine.transform(raw, rule);
//
//        // master
//        assertThat(tri.master().get("master_title")).isEqualTo("나 혼자만 레벨업"); // 동일 타이틀로 맞춰짐
//        assertThat(tri.master().get("poster_image_url")).isEqualTo("https://naver/img/cover.jpg");
//
//        // domain
//        assertThat(tri.domain().get("author")).isEqualTo("추공");
//        assertThat(tri.domain().get("started_at")).isEqualTo("2018-02-14");
//
//        // platform
//        assertThat(tri.platform().get("platformName")).isEqualTo("NaverSeries");
//        Map attrs = (Map) tri.platform().get("attributes");
//        assertThat(attrs.get("title_id")).isEqualTo("123456");
//        assertThat(attrs.get("weekday")).isEqualTo("수");
//    }
//}


