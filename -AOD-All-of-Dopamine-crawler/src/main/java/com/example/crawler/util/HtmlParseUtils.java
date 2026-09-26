package com.example.crawler.util;

import org.jsoup.nodes.Element;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * 크롤러 공용 HTML/문자열 파싱 헬퍼.
 * 플랫폼별 크롤러(Fetcher)가 공유하는 null-safe 접근자를 모은다.
 * (NaverSeriesCrawler의 자체 헬퍼를 승격 — 2026-08 crawler 표준화)
 */
public final class HtmlParseUtils {

    private HtmlParseUtils() {
    }

    /** null-safe 텍스트 추출 (NBSP를 일반 공백으로 치환) */
    public static String text(Element e) {
        return e == null ? "" : e.text().replace(' ', ' ').trim();
    }

    /** null-safe 속성 추출 */
    public static String attr(Element e, String name) {
        return e == null ? null : e.attr(name);
    }

    /**
     * 연속 공백을 한 칸으로 접고 양끝을 자른다 (null → null). 제목 정리용.
     * [독점]·[2부]·[외전] 같은 대괄호 태그는 다른 시즌을 구분하는 정보라 제거하지 않는다 (2026-09).
     */
    public static String collapseWhitespace(String s) {
        return s == null ? null : s.replaceAll("\\s+", " ").trim();
    }

    /** 상대 href를 절대 URL로 변환 */
    public static String absolutize(String href, String baseUrl) {
        return href.startsWith("http") ? href : baseUrl + href;
    }

    /** URL 쿼리 파라미터 추출 */
    public static String extractQueryParam(String url, String key) {
        if (url == null)
            return null;
        int idx = url.indexOf('?');
        if (idx < 0)
            return null;
        String qs = url.substring(idx + 1);
        for (String p : qs.split("&")) {
            String[] kv = p.split("=", 2);
            if (kv.length == 2 && kv[0].equals(key)) {
                return URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
            }
        }
        return null;
    }
}
