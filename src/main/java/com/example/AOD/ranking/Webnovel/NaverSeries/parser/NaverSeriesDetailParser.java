package com.example.AOD.ranking.Webnovel.NaverSeries.parser;

import com.example.AOD.contents.Novel.NaverSeriesNovel.NaverSeriesCrawler;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

/**
 * 네이버 시리즈 랭킹용 상세 페이지 파서
 * - 랭킹 수집 시 제목만 추출하기 위한 전용 파서
 * - NaverSeriesCrawler의 유틸리티 메서드 재사용
 */
@Component
@Slf4j
public class NaverSeriesDetailParser {

    /**
     * 상세 페이지에서 제목 추출
     * - og:title 메타 태그 우선
     * - 폴백: h2 태그
     * - [독점], [시리즈 에디션] 등 자동 제거
     * 
     * @param detailDoc 상세 페이지 Document
     * @return 정리된 제목 (null 가능)
     */
    public String extractTitle(Document detailDoc) {
        if (detailDoc == null) {
            return null;
        }

        try {
            // 1순위: og:title 메타 태그
            Element ogTitle = detailDoc.selectFirst("meta[property=og:title]");
            String rawTitle = ogTitle != null ? ogTitle.attr("content") : null;

            // 2순위 (폴백): h2 태그
            if (rawTitle == null || rawTitle.isEmpty()) {
                Element h2 = detailDoc.selectFirst("h2");
                rawTitle = h2 != null ? h2.text() : null;
            }

            // NaverSeriesCrawler의 cleanTitle 유틸 재사용
            return NaverSeriesCrawler.cleanTitle(rawTitle);

        } catch (Exception e) {
            log.warn("제목 추출 중 오류 발생: {}", e.getMessage());
            return null;
        }
    }
}
