package com.example.crawler.contents.novel.naverseries;

/**
 * 네이버 시리즈 CSS 셀렉터 단일 출처.
 * 사이트 개편 시 이 파일만 수정하면 되도록 목록/상세 셀렉터를 모은다.
 */
public final class NaverSeriesSelectors {

    private NaverSeriesSelectors() {
    }

    // ===== 목록 페이지 =====

    /** 상세 링크 (productNo 파라미터 포함 — 우선) */
    public static final String LIST_DETAIL_LINK_STRICT = "a[href*='/novel/detail.series'][href*='productNo=']";

    /** 상세 링크 (productNo 없는 경우 폴백) */
    public static final String LIST_DETAIL_LINK_FALLBACK = "a[href*='/novel/detail.series']";

    // ===== 상세 페이지 =====

    /** 시놉시스 */
    public static final String DETAIL_SYNOPSIS = "div.end_dsc ._synopsis";

    /** 작품정보란(ul) 직계 항목 */
    public static final String DETAIL_INFO_ITEMS = "> li";
}
