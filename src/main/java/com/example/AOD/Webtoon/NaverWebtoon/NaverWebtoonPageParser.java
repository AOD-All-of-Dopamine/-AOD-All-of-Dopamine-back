package com.example.AOD.Webtoon.NaverWebtoon;


import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 네이버 웹툰 페이지 파서 구현체
 * - NaverWebtoonSelectors의 CSS 셀렉터를 사용
 * - 실제 셀렉터가 결정되면 해당 메서드들만 수정하면 됨
 */
@Component
@Slf4j
public class NaverWebtoonPageParser implements WebtoonPageParser {

    @Override
    public String convertToPcUrl(String mobileUrl) {
        if (mobileUrl == null) return null;

        // m.comic.naver.com -> comic.naver.com 변환
        return mobileUrl.replace("m.comic.naver.com", "comic.naver.com");
    }

    @Override
    public Set<String> extractDetailUrls(Document listDocument) {
        Set<String> detailUrls = new LinkedHashSet<>();

        // 우선순위에 따라 셀렉터 시도
        for (String selector : NaverWebtoonSelectors.WEBTOON_LINK_SELECTORS) {
            Elements links = listDocument.select(selector);

            for (Element link : links) {
                String href = link.attr("href");
                if (!href.startsWith("http")) {
                    href = "https://m.comic.naver.com" + href;
                }
                detailUrls.add(href);
            }

            // 링크를 찾았으면 다음 셀렉터는 시도하지 않음
            if (!detailUrls.isEmpty()) {
                log.debug("셀렉터 '{}' 사용하여 {}개 링크 수집", selector, detailUrls.size());
                break;
            }
        }

        log.info("총 {}개 웹툰 링크 수집됨", detailUrls.size());
        return detailUrls;
    }

    @Override
    public NaverWebtoonDTO parseWebtoonDetail(Document detailDocument, String detailUrl,
                                              String crawlSource, String weekday) {
        try {
            // titleId 추출
            String titleId = extractTitleId(detailUrl);

            // 기본 정보 파싱
            String title = parseTitle(detailDocument);
            String author = parseAuthor(detailDocument);
            String synopsis = parseSynopsis(detailDocument);
            String imageUrl = parseImageUrl(detailDocument);
            String productUrl = parseProductUrl(detailDocument, detailUrl);

            // 제목이 없으면 파싱 실패로 간주
            if (isBlank(title)) {
                log.warn("웹툰 제목을 찾을 수 없음: {}", detailUrl);
                return null;
            }

            // 웹툰 메타 정보 파싱
            String status = parseStatus(detailDocument);
            String detailWeekday = parseWeekday(detailDocument, weekday);
            Integer episodeCount = parseEpisodeCount(detailDocument);

            // 서비스 정보 파싱
            String ageRating = parseAgeRating(detailDocument);
            List<String> tags = parseTags(detailDocument);

            // 통계 정보 파싱

            Long likeCount = parseLikeCount(detailDocument);


            // DTO 빌드
            return NaverWebtoonDTO.builder()
                    .title(cleanText(title))
                    .author(cleanText(author))
                    .synopsis(cleanText(synopsis))
                    .imageUrl(imageUrl)
                    .productUrl(productUrl)
                    .titleId(titleId)
                    .weekday(detailWeekday)
                    .status(status)
                    .episodeCount(episodeCount)
                    .ageRating(ageRating)
                    .tags(tags)
                    .likeCount(likeCount)
                    .originalPlatform("NAVER_WEBTOON")
                    .crawlSource(crawlSource)
                    .build();

        } catch (Exception e) {
            log.error("웹툰 상세 파싱 중 오류 발생: {}, {}", detailUrl, e.getMessage());
            return null;
        }
    }

    @Override
    public String extractTitleId(String url) {
        Pattern pattern = Pattern.compile(NaverWebtoonSelectors.TITLE_ID_PATTERN);
        Matcher matcher = pattern.matcher(url);
        return matcher.find() ? matcher.group(1) : null;
    }

    @Override
    public String getParserName() {
        return "NaverWebtoonMobileParser_v1.0";
    }

    // ===== 개별 파싱 메서드들 =====

    private String parseTitle(Document doc) {
        // PC 페이지에서 제목 추출
        String title = getTextBySelector(doc, NaverWebtoonSelectors.DETAIL_TITLE);
        if (isBlank(title)) {
            // 메타 태그 폴백
            title = getMetaContent(doc, NaverWebtoonSelectors.META_OG_TITLE);
        }
        return title;
    }

    private String parseAuthor(Document doc) {
        // PC 페이지에서 작가 정보 추출 (글, 그림, 원작 통합)
        List<String> authors = new ArrayList<>();

        // 모든 작가 링크 수집
        Elements authorElements = doc.select(NaverWebtoonSelectors.DETAIL_AUTHORS);
        for (Element authorElement : authorElements) {
            String authorName = authorElement.text().trim();
            if (!isBlank(authorName)) {
                authors.add(authorName);
            }
        }

        // 작가들을 " / "로 구분하여 반환
        return authors.isEmpty() ? null : String.join(" / ", authors);
    }

    private String parseSynopsis(Document doc) {
        // PC 페이지에서 시놉시스 추출
        String synopsis = getTextBySelector(doc, NaverWebtoonSelectors.DETAIL_SYNOPSIS);
        if (isBlank(synopsis)) {
            // 메타 태그 폴백
            synopsis = getMetaContent(doc, NaverWebtoonSelectors.META_OG_DESCRIPTION);
        }
        return synopsis;
    }

    private String parseImageUrl(Document doc) {
        // PC 페이지에서 이미지 추출
        String imageUrl = getAttributeBySelector(doc, NaverWebtoonSelectors.DETAIL_THUMBNAIL, "src");
        if (isBlank(imageUrl)) {
            // 메타 태그 폴백
            imageUrl = getMetaContent(doc, NaverWebtoonSelectors.META_OG_IMAGE);
        }
        return imageUrl;
    }

    private String parseProductUrl(Document doc, String detailUrl) {
        String productUrl = getMetaContent(doc, NaverWebtoonSelectors.META_OG_URL);
        return isBlank(productUrl) ? detailUrl : productUrl;
    }

    private String parseStatus(Document doc) {
        String metaInfo = getTextBySelector(doc, NaverWebtoonSelectors.DETAIL_META_INFO);
        if (!isBlank(metaInfo)) {
            // "56화 완결 ∙ 15세 이용가"에서 상태 추출
            if (metaInfo.contains("완결")) {
                return "완결";
            } else if (metaInfo.contains("휴재")) {
                return "휴재";
            } else if (metaInfo.contains("화")) {  // "56화"가 있으면 연재중
                return "연재중";
            }
        }
        return null;
    }



    private String parseWeekday(Document doc, String fallbackWeekday) {
        // 연재 정보에서 요일 정보 추출
        String metaInfo = getTextBySelector(doc, NaverWebtoonSelectors.DETAIL_META_INFO);
        if (!isBlank(metaInfo)) {
            // "금요웹툰" -> "fri"로 변환
            if (metaInfo.contains("월요")) return "mon";
            if (metaInfo.contains("화요")) return "tue";
            if (metaInfo.contains("수요")) return "wed";
            if (metaInfo.contains("목요")) return "thu";
            if (metaInfo.contains("금요")) return "fri";
            if (metaInfo.contains("토요")) return "sat";
            if (metaInfo.contains("일요")) return "sun";
        }
        return fallbackWeekday;
    }

    private String parseAgeRating(Document doc) {
        // 연재 정보에서 연령등급 추출
        String metaInfo = getTextBySelector(doc, NaverWebtoonSelectors.DETAIL_META_INFO);
        if (!isBlank(metaInfo)) {
            // "15세 이용가", "전체이용가" 등 추출
            if (metaInfo.contains("전체")) return "전체이용가";
            if (metaInfo.contains("12세")) return "12세이용가";
            if (metaInfo.contains("15세")) return "15세이용가";
            if (metaInfo.contains("19세")) return "19세이용가";
        }
        return null;
    }


    private List<String> parseTags(Document doc) {
        // 모든 태그 수집
        List<String> tags = new ArrayList<>();
        Elements tagElements = doc.select(NaverWebtoonSelectors.DETAIL_TAGS);

        for (Element tag : tagElements) {
            String tagText = tag.text().trim();
            // #으로 시작하는 태그에서 # 제거
            if (tagText.startsWith("#")) {
                tagText = tagText.substring(1);
            }
            tags.add(tagText);
        }

        return tags;
    }

    private String parsePublisher(Document doc) {
        // 현재 구조에서는 출판사 정보를 직접 찾기 어려움
        // "네이버웹툰"으로 기본값 설정
        return "네이버웹툰";
    }

    // 나머지 파싱 메서드들 (현재 HTML에서 찾을 수 없는 정보들)
    private Integer parseEpisodeCount(Document doc) {
        String metaInfo = getTextBySelector(doc, NaverWebtoonSelectors.DETAIL_META_INFO);
        if (!isBlank(metaInfo)) {
            // "56화 완결"에서 숫자 추출
            Pattern pattern = Pattern.compile("(\\d+)화");
            Matcher matcher = pattern.matcher(metaInfo);
            if (matcher.find()) {
                return Integer.parseInt(matcher.group(1));
            }
        }
        return null;
    }


    private Long parseLikeCount(Document doc) {
        String countText = getTextBySelector(doc, NaverWebtoonSelectors.DETAIL_LIKE_COUNT);
        return parseKoreanNumber(countText);  // "29,458" → 29458
    }



    // ===== 유틸리티 메서드들 =====

    /**
     * 태그가 장르 관련인지 판단
     */
    private boolean isGenreTag(String tag) {
        // 장르 관련 키워드들
        String[] genreKeywords = {
                "무협", "사극", "로맨스", "액션", "스릴러", "코미디", "일상", "판타지",
                "SF", "공포", "추리", "스포츠", "학원", "직장", "요리", "음악", "댄스"
        };

        for (String keyword : genreKeywords) {
            if (tag.contains(keyword)) {
                return true;
            }
        }

        return false;
    }

    // ===== 유틸리티 메서드들 =====

    private String getTextBySelector(Document doc, String selector) {
        if (selector.startsWith("TODO:")) {
            return null; // TODO 셀렉터는 아직 구현되지 않음
        }
        Element element = doc.selectFirst(selector);
        return element != null ? element.text().trim() : null;
    }

    private String getAttributeBySelector(Document doc, String selector, String attribute) {
        if (selector.startsWith("TODO:")) {
            return null; // TODO 셀렉터는 아직 구현되지 않음
        }
        Element element = doc.selectFirst(selector);
        return element != null ? element.attr(attribute) : null;
    }

    private List<String> getTextListBySelector(Document doc, String selector) {
        if (selector.startsWith("TODO:")) {
            return new ArrayList<>(); // TODO 셀렉터는 아직 구현되지 않음
        }
        Elements elements = doc.select(selector);
        return elements.stream()
                .map(Element::text)
                .filter(text -> !text.trim().isEmpty())
                .collect(Collectors.toList());
    }

    private String getMetaContent(Document doc, String selector) {
        Element meta = doc.selectFirst(selector);
        return meta != null ? meta.attr("content") : null;
    }

    private Integer parseInteger(String text) {
        if (isBlank(text)) return null;
        try {
            // 숫자가 아닌 문자 제거 (예: "123화" -> "123")
            String cleanText = text.replaceAll("[^0-9]", "");
            return cleanText.isEmpty() ? null : Integer.parseInt(cleanText);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long parseLong(String text) {
        if (isBlank(text)) return null;
        try {
            // 숫자가 아닌 문자 제거하되, 천 단위 구분자 처리
            String cleanText = text.replaceAll("[^0-9.]", "");
            if (cleanText.isEmpty()) return null;

            // 만, 억 등의 단위 처리
            if (text.contains("만")) {
                double value = Double.parseDouble(cleanText) * 10000;
                return (long) value;
            } else if (text.contains("억")) {
                double value = Double.parseDouble(cleanText) * 100000000;
                return (long) value;
            } else {
                return Long.parseLong(cleanText);
            }
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private BigDecimal parseBigDecimal(String text) {
        if (isBlank(text)) return null;
        try {
            String cleanText = text.replaceAll("[^0-9.]", "");
            return cleanText.isEmpty() ? null : new BigDecimal(cleanText);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private LocalDate parseDate(String dateText) {
        if (isBlank(dateText)) return null;

        try {
            // 다양한 날짜 형식 시도
            DateTimeFormatter[] formatters = {
                    DateTimeFormatter.ofPattern("yyyy.MM.dd"),
                    DateTimeFormatter.ofPattern("yyyy-MM-dd"),
                    DateTimeFormatter.ofPattern("yy.MM.dd"),
                    DateTimeFormatter.ofPattern("MM.dd")
            };

            for (DateTimeFormatter formatter : formatters) {
                try {
                    return LocalDate.parse(dateText.trim(), formatter);
                } catch (Exception ignored) {
                    // 다음 포맷터 시도
                }
            }
        } catch (Exception e) {
            log.debug("날짜 파싱 실패: {}", dateText);
        }

        return null;
    }

    private Long parseKoreanNumber(String text) {
        if (isBlank(text)) return null;

        try {
            // 콤마 제거하고 숫자만 추출
            String cleanText = text.replaceAll("[^0-9]", "");
            if (cleanText.isEmpty()) return null;

            return Long.parseLong(cleanText);
        } catch (NumberFormatException e) {
            log.debug("한국어 숫자 파싱 실패: {}", text);
            return null;
        }
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