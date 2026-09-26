package com.example.crawler.ranking.naverseries;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 제목 정제 회귀 테스트 — [독점]·[2부] 같은 대괄호 태그는 시즌 구분 정보이므로 제거하지 않는다 (2026-09).
 */
class NaverSeriesDetailParserTest {

    private final NaverSeriesDetailParser parser = new NaverSeriesDetailParser();

    @Test
    void keepsBracketTagsAndCollapsesWhitespaceOnly() {
        var doc = Jsoup.parse("<html><head><meta property=\"og:title\" content=\"  [독점]   화산귀환 2부 \"></head></html>");
        assertEquals("[독점] 화산귀환 2부", parser.extractTitle(doc));
    }

    @Test
    void fallsBackToH2WhenOgTitleMissing() {
        var doc = Jsoup.parse("<html><body><h2>전지적 독자 시점 [외전]</h2></body></html>");
        assertEquals("전지적 독자 시점 [외전]", parser.extractTitle(doc));
    }

    @Test
    void nullDocumentYieldsNull() {
        assertNull(parser.extractTitle(null));
    }
}
