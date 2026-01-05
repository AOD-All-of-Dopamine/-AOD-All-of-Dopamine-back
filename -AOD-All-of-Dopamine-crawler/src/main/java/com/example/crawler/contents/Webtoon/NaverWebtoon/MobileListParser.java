package com.example.crawler.contents.Webtoon.NaverWebtoon;


import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 모바일 목록 페이지에서 기본 정보를 추출하는 파서
 * - 제목, 작가, 썸네일, 관심수 등 목록에서 바로 수집 가능한 정보들
 * - PC 상세 페이지 접근 전에 기본 정보를 먼저 수집
 */
@Component
@Slf4j
public class MobileListParser {

    /**
     * 모바일 목록의 개별 아이템에서 기본 정보 추출
     *
     * @param listItem li.item 엘리먼트
     * @param mobileUrl 모바일 웹툰 URL
     * @param crawlSource 크롤링 소스
     * @param weekday 요일 정보
     * @return 기본 정보가 담긴 DTO (PC에서 추가 정보 보완 필요)
     */
    public NaverWebtoonDTO parseListItem(Element listItem, String mobileUrl, String crawlSource, String weekday) {
        try {
            // titleId 추출
            String titleId = extractTitleIdFromUrl(mobileUrl);

            // 기본 정보 추출
            String title = parseTitle(listItem);
            String author = parseAuthor(listItem);
            String imageUrl = parseImageUrl(listItem);

            if (isBlank(title)) {
                log.warn("목록에서 제목을 찾을 수 없음: {}", mobileUrl);
                return null;
            }

            // 추가 목록 정보 추출
            Long likeCount = parseLikeCount(listItem);
            List<String> badges = parseBadges(listItem);
            String status = parseStatus(listItem);

            // 뱃지 정보 분석
            boolean isNew = badges.contains("new");

            return NaverWebtoonDTO.builder()
                    .title(cleanText(title))
                    .author(cleanText(author))
                    .imageUrl(imageUrl)
                    .productUrl(mobileUrl) // 나중에 PC URL로 교체됨
                    .titleId(titleId)
                    .weekday(weekday)
                    .status(translateStatus(status))
                    .likeCount(likeCount)
                    .originalPlatform("NAVER_WEBTOON")
                    .crawlSource(crawlSource)
                    // 추가 메타데이터
                    .serviceType(determineServiceType(badges, isNew))
                    .build();

        } catch (Exception e) {
            log.error("목록 아이템 파싱 중 오류 발생: {}, {}", mobileUrl, e.getMessage());
            return null;
        }
    }

    /**
     * 목록에서 모든 웹툰 아이템과 기본 정보를 함께 추출
     *
     * @param listDocument 목록 페이지 Document
     * @param crawlSource 크롤링 소스
     * @param weekday 요일 정보
     * @return URL과 기본 DTO가 매핑된 Map
     */
    public Map<String, NaverWebtoonDTO> extractWebtoonsWithBasicInfo(Document listDocument, String crawlSource, String weekday) {
        Map<String, NaverWebtoonDTO> webtoonMap = new LinkedHashMap<>();

        // 웹툰 아이템들 선택
        Elements items = listDocument.select("ul.list_toon li.item");

        for (Element item : items) {
            // 배너나 광고 아이템 제외
            if (item.hasClass("banner")) {
                continue;
            }

            // 링크 추출
            Element linkElement = item.selectFirst("a.link[href*='titleId=']");
            if (linkElement == null) {
                continue;
            }

            String href = linkElement.attr("href");
            if (!href.startsWith("http")) {
                href = "https://m.comic.naver.com" + href;
            }

            // 기본 정보 파싱
            NaverWebtoonDTO basicDTO = parseListItem(item, href, crawlSource, weekday);
            if (basicDTO != null) {
                webtoonMap.put(href, basicDTO);
            }
        }

        log.info("목록에서 {}개 웹툰과 기본 정보 추출 완료", webtoonMap.size());
        return webtoonMap;
    }

    // ===== 개별 파싱 메서드들 =====

    private String parseTitle(Element listItem) {
        Element titleElement = listItem.selectFirst(NaverWebtoonSelectors.LIST_WEBTOON_TITLE);
        return titleElement != null ? titleElement.text().trim() : null;
    }

    private String parseAuthor(Element listItem) {
        Element authorElement = listItem.selectFirst(NaverWebtoonSelectors.LIST_WEBTOON_AUTHOR);
        return authorElement != null ? authorElement.text().trim() : null;
    }

    private String parseImageUrl(Element listItem) {
        Element imgElement = listItem.selectFirst(NaverWebtoonSelectors.LIST_WEBTOON_THUMBNAIL);
        return imgElement != null ? imgElement.attr("src") : null;
    }

    private Long parseLikeCount(Element listItem) {
        Element countElement = listItem.selectFirst(NaverWebtoonSelectors.LIST_WEBTOON_LIKE_COUNT);
        if (countElement == null) return null;

        String countText = countElement.text();
        return parseKoreanNumber(countText);
    }

    private List<String> parseBadges(Element listItem) {
        List<String> badges = new ArrayList<>();
        Elements badgeElements = listItem.select(NaverWebtoonSelectors.LIST_WEBTOON_BADGES);

        for (Element badge : badgeElements) {
            String badgeClass = badge.className();
            // badge 클래스에서 실제 타입 추출 (예: "badge bm" -> "bm")
            for (String className : badgeClass.split("\\s+")) {
                if (!"badge".equals(className)) {
                    badges.add(className);
                }
            }
        }

        return badges;
    }

    private String parseStatus(Element listItem) {
        Element statusElement = listItem.selectFirst(NaverWebtoonSelectors.LIST_WEBTOON_STATUS);
        if (statusElement == null) return null;

        String statusClass = statusElement.className();
        // "bullet up" -> "up", "bullet break" -> "break"
        for (String className : statusClass.split("\\s+")) {
            if (!"bullet".equals(className)) {
                return className;
            }
        }

        return null;
    }

    // ===== 유틸리티 메서드들 =====

    private String extractTitleIdFromUrl(String url) {
        int start = url.indexOf("titleId=");
        if (start == -1) return null;

        start += "titleId=".length();
        int end = url.indexOf("&", start);
        if (end == -1) end = url.length();

        return url.substring(start, end);
    }

    private Long parseKoreanNumber(String text) {
        if (isBlank(text)) return null;

        try {
            // 숫자 추출
            String numberPart = text.replaceAll("[^0-9.]", "");
            if (numberPart.isEmpty()) return null;

            double value = Double.parseDouble(numberPart);

            // 단위 처리
            if (text.contains("만")) {
                value *= 10000;
            } else if (text.contains("억")) {
                value *= 100000000;
            } else if (text.contains("천")) {
                value *= 1000;
            }

            return (long) value;
        } catch (NumberFormatException e) {
            log.debug("숫자 파싱 실패: {}", text);
            return null;
        }
    }

    private String translateStatus(String statusClass) {
        if (statusClass == null) return null;

        switch (statusClass) {
            case "up": return "업데이트";
            case "break": return "휴재";
            case "new": return "신작";
            default: return statusClass;
        }
    }

    private String determineServiceType(List<String> badges, boolean isNew) {
        if (isNew) return "신작";
        if (badges.contains("bm")) return "유료";
        return "일반";
    }

    private String cleanText(String text) {
        if (isBlank(text)) return null;
        return text.trim()
                .replaceAll("\\s+", " ")
                .replaceAll("[\\r\\n]+", " ");
    }

    private boolean isBlank(String str) {
        return str == null || str.trim().isEmpty();
    }
}

