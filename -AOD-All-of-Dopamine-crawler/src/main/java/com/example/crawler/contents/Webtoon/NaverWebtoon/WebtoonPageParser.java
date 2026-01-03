package com.example.crawler.contents.Webtoon.NaverWebtoon;


import org.jsoup.nodes.Document;

import java.util.Set;

/**
 * 웹툰 페이지 파싱 인터페이스
 * - 목록 페이지와 상세 페이지 파싱 로직을 추상화
 * - 나중에 다른 파싱 전략으로 교체 가능
 */
public interface WebtoonPageParser {

    /**
     * 목록 페이지에서 웹툰 상세 링크들을 수집
     *
     * @param listDocument 목록 페이지 Document
     * @return 웹툰 상세 페이지 URL 집합
     */
    Set<String> extractDetailUrls(Document listDocument);

    /**
     * 상세 페이지에서 웹툰 정보를 파싱하여 DTO로 변환
     *
     * @param detailDocument 상세 페이지 Document
     * @param detailUrl 상세 페이지 URL
     * @param crawlSource 크롤링 소스 (weekday_wed, finish 등)
     * @param weekday 연재 요일 (null 가능)
     * @return NaverWebtoonDTO 또는 null (파싱 실패시)
     */
    NaverWebtoonDTO parseWebtoonDetail(Document detailDocument, String detailUrl,
                                       String crawlSource, String weekday);

    /**
     * 모바일 URL을 PC URL로 변환
     *
     * @param mobileUrl 모바일 웹툰 URL
     * @return PC 웹툰 URL
     */
    String convertToPcUrl(String mobileUrl);

    /**
     * URL에서 titleId 추출
     *
     * @param url 웹툰 URL
     * @return titleId 또는 null
     */
    String extractTitleId(String url);

    /**
     * 파서의 이름 또는 버전 정보
     *
     * @return 파서 식별자
     */
    String getParserName();
}

