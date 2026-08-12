package com.example.crawler.util;

import org.jsoup.nodes.Element;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 크롤러 공용 HTML/문자열 파싱 헬퍼.
 * 플랫폼별 크롤러(Fetcher)가 공유하는 null-safe 접근자와 한국어 수치 파싱을 모은다.
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

    /** "2억 5,006만", "139.3만", "2.5천", "1,393,475" 등 한국어 수치 표기 지원 */
    public static Long parseKoreanCount(String s) {
        if (s == null)
            return null;
        s = s.trim().replace(",", "");

        if (s.contains("억")) {
            String[] parts = s.split("억");
            long total = 0;
            try {
                total += Math.round(Double.parseDouble(parts[0].trim()) * 100_000_000);
                if (parts.length > 1 && !parts[1].isBlank()) {
                    String manPart = parts[1].replace("만", "").trim();
                    if (!manPart.isEmpty()) {
                        total += Math.round(Double.parseDouble(manPart) * 10_000);
                    }
                }
                return total;
            } catch (NumberFormatException e) {
                /* 파싱 실패 시 다음 규칙으로 넘어감 */ }
        }

        Matcher m = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*만").matcher(s);
        if (m.find()) {
            return Math.round(Double.parseDouble(m.group(1)) * 10_000);
        }

        m = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*천").matcher(s);
        if (m.find()) {
            return Math.round(Double.parseDouble(m.group(1)) * 1_000);
        }

        try {
            return Long.parseLong(s);
        } catch (Exception ignored) {
            return null;
        }
    }
}
