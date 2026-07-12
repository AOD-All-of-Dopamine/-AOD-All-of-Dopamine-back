package com.example.crawler.util;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * 플랫폼별 다양한 날짜 표기의 단일 파서 (RF-6).
 * GenericDomainUpserter.parseDate / ContentUpsertService.parseReleaseDate 에 복붙돼 있던
 * 동일 패턴 배열을 한곳으로 통합 — 새 표기는 여기 한 곳에만 추가한다.
 */
public final class FlexibleDateParser {

    private static final String[] PATTERNS = {
            "uuuu년 M월 d일",      // Steam 한국어: 1998년 11월 19일
            "yyyy-MM-dd",          // ISO 형식
            "yyyy.MM.dd",          // 점 구분
            "yyyy/MM/dd",          // 슬래시 구분
            "MMM d, yyyy"          // 영어: Nov 19, 1998
    };

    private FlexibleDateParser() {}

    public static LocalDate parse(Object value) {
        if (value == null) return null;
        if (value instanceof LocalDate d) return d;
        if (value instanceof Number n) return LocalDate.of(n.intValue(), 1, 1); // 연도만

        String v = value.toString().trim();
        for (String p : PATTERNS) {
            // 원본 코드는 KOREAN 로케일만 써서 "Nov 19, 1998"이 실제로는 파싱 불가였다(잠재 버그).
            // 주석에 명시된 의도("영어: Nov 19, 1998")대로 영어 로케일도 시도한다.
            for (Locale locale : new Locale[]{Locale.KOREAN, Locale.ENGLISH}) {
                try {
                    return LocalDate.parse(v, DateTimeFormatter.ofPattern(p, locale));
                } catch (Exception ignored) {
                }
            }
        }
        try {
            return LocalDate.of(Integer.parseInt(v), 1, 1); // 연도 문자열
        } catch (Exception ignored) {
        }
        return null;
    }
}
