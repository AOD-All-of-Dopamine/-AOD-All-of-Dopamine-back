package com.example.AOD.Webtoon.NaverWebtoon;


import com.example.AOD.util.ChromeDriverProvider;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 네이버 웹툰 Selenium 기반 페이지 파서
 * - WebtoonPageParser 인터페이스 구현
 * - PC 상세 페이지를 Selenium으로 파싱
 * - React SPA 동적 콘텐츠 완벽 지원
 */
@Component
@Slf4j
public class NaverWebtoonSeleniumPageParser implements WebtoonPageParser {

    private final ChromeDriverProvider chromeDriverProvider;
    private final int SLEEP_TIME = 100; // React 로딩 대기 시간

    public NaverWebtoonSeleniumPageParser(ChromeDriverProvider chromeDriverProvider) {
        this.chromeDriverProvider = chromeDriverProvider;
    }

    @Override
    public String convertToPcUrl(String mobileUrl) {
        if (mobileUrl == null) return null;
        // m.comic.naver.com -> comic.naver.com 변환
        return mobileUrl.replace("m.comic.naver.com", "comic.naver.com");
    }

    @Override
    public Set<String> extractDetailUrls(Document listDocument) {
        // MobileListParser가 담당하므로 사실상 사용되지 않음
        // 인터페이스 호환성을 위해 빈 구현
        return new LinkedHashSet<>();
    }

    @Override
    public NaverWebtoonDTO parseWebtoonDetail(Document detailDocument, String detailUrl,
                                              String crawlSource, String weekday) {
        // Document 파라미터는 무시하고 detailUrl로 Selenium을 통해 접근
        WebDriver driver = null;
        try {
            driver = chromeDriverProvider.getDriver();
            driver.get(detailUrl);

            // React 앱 로딩 대기
            Thread.sleep(SLEEP_TIME);

            log.debug("Selenium으로 웹툰 상세 파싱 시작: {}", detailUrl);

            // titleId 추출
            String titleId = extractTitleId(detailUrl);

            // 기본 정보 파싱
            String title = parseTitle(driver);
            String author = parseAuthor(driver);
            String synopsis = parseSynopsis(driver);
            String imageUrl = parseImageUrl(driver);
            String productUrl = parseProductUrl(driver, detailUrl);

            // 제목이 없으면 파싱 실패로 간주
            if (isBlank(title)) {
                log.warn("웹툰 제목을 찾을 수 없음: {}", detailUrl);
                return null;
            }

            // 웹툰 메타 정보 파싱
            String status = parseStatus(driver);
            String detailWeekday = parseWeekday(driver, weekday);
            Integer episodeCount = parseEpisodeCount(driver);

            // 서비스 정보 파싱
            String ageRating = parseAgeRating(driver);
            List<String> tags = parseTags(driver);

            // 🎯 핵심: 관심수 파싱 (Selenium으로만 가능)
            Long likeCount = parseLikeCount(driver);

            log.debug("파싱 완료: {} (관심: {}, 에피소드: {}, 태그: {})",
                    title, likeCount, episodeCount, tags.size());

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
            log.error("Selenium 웹툰 상세 파싱 중 오류 발생: {}, {}", detailUrl, e.getMessage());
            return null;
        } finally {
            if (driver != null) {
                driver.quit();
            }
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
        return "NaverWebtoonSeleniumPageParser_v1.0";
    }

    // ===== Selenium 기반 개별 파싱 메서드들 =====

    private String parseTitle(WebDriver driver) {
        try {
            // 여러 셀렉터 시도
            String[] selectors = {
                    "h2.EpisodeListInfo__title--mYLjC",
                    "h2[class*='EpisodeListInfo'][class*='title']",
                    "h2[class*='title']"
            };

            for (String selector : selectors) {
                try {
                    WebElement element = driver.findElement(By.cssSelector(selector));
                    String title = element.getText().trim();
                    if (!title.isEmpty()) {
                        return title;
                    }
                } catch (NoSuchElementException ignored) {}
            }

            log.warn("제목을 찾을 수 없음");

        } catch (Exception e) {
            log.warn("제목 추출 실패: {}", e.getMessage());
        }
        return null;
    }

    private String parseAuthor(WebDriver driver) {
        try {
            List<WebElement> authorElements = driver.findElements(
                    By.cssSelector("div.ContentMetaInfo__meta_info--GbTg4 a.ContentMetaInfo__link--xTtO6")
            );

            List<String> authors = new ArrayList<>();
            for (WebElement element : authorElements) {
                String author = element.getText().trim();
                if (!author.isEmpty() && !authors.contains(author)) {
                    authors.add(author);
                }
            }

            return authors.isEmpty() ? null : String.join(" / ", authors);

        } catch (Exception e) {
            log.warn("작가 추출 실패: {}", e.getMessage());
            return null;
        }
    }

    private String parseSynopsis(WebDriver driver) {
        try {
            WebElement element = driver.findElement(By.cssSelector("p.EpisodeListInfo__summary--Jd1WG"));
            return element.getText().trim();
        } catch (NoSuchElementException e) {
            log.debug("시놉시스를 찾을 수 없음");
            return null;
        }
    }

    private String parseImageUrl(WebDriver driver) {
        try {
            WebElement element = driver.findElement(By.cssSelector("img.Poster__image--d9XTI"));
            return element.getAttribute("src");
        } catch (NoSuchElementException e) {
            log.debug("썸네일 이미지를 찾을 수 없음");
            return null;
        }
    }

    private String parseProductUrl(WebDriver driver, String detailUrl) {
        return detailUrl; // 현재 URL 그대로 사용
    }

    private String parseStatus(WebDriver driver) {
        try {
            List<WebElement> metaElements = driver.findElements(
                    By.cssSelector("em.ContentMetaInfo__info_item--utGrf")
            );

            for (WebElement meta : metaElements) {
                String metaText = meta.getText();
                if (metaText.contains("완결")) {
                    return "완결";
                } else if (metaText.contains("휴재")) {
                    return "휴재";
                } else if (metaText.contains("화")) {
                    return "연재중";
                }
            }
        } catch (Exception e) {
            log.debug("상태 파싱 실패: {}", e.getMessage());
        }
        return null;
    }

    private String parseWeekday(WebDriver driver, String fallbackWeekday) {
        try {
            List<WebElement> metaElements = driver.findElements(
                    By.cssSelector("em.ContentMetaInfo__info_item--utGrf")
            );

            for (WebElement meta : metaElements) {
                String metaText = meta.getText();
                if (metaText.contains("월요")) return "mon";
                if (metaText.contains("화요")) return "tue";
                if (metaText.contains("수요")) return "wed";
                if (metaText.contains("목요")) return "thu";
                if (metaText.contains("금요")) return "fri";
                if (metaText.contains("토요")) return "sat";
                if (metaText.contains("일요")) return "sun";
            }
        } catch (Exception e) {
            log.debug("요일 파싱 실패: {}", e.getMessage());
        }
        return fallbackWeekday;
    }

    private Integer parseEpisodeCount(WebDriver driver) {
        try {
            // 방법 1: 에피소드 리스트 아이템 개수 세기
            List<WebElement> episodeItems = driver.findElements(
                    By.cssSelector("li[class*='EpisodeList__item']")
            );
            if (!episodeItems.isEmpty()) {
                log.debug("에피소드 개수 찾음 (리스트): {}", episodeItems.size());
                return episodeItems.size();
            }

            // 방법 2: 다른 패턴들 시도
            String[] selectors = {
                    "li[class*='episode']",
                    "li[class*='Episode']",
                    "a[href*='no=']"
            };

            for (String selector : selectors) {
                List<WebElement> elements = driver.findElements(By.cssSelector(selector));
                if (!elements.isEmpty()) {
                    log.debug("에피소드 개수 찾음 ({}): {}", selector, elements.size());
                    return elements.size();
                }
            }

            // 방법 3: JavaScript로 검색
            JavascriptExecutor js = (JavascriptExecutor) driver;
            String script = """
                var episodes = document.querySelectorAll('li[class*="episode"], li[class*="Episode"], a[href*="no="]');
                return episodes.length;
                """;

            Object result = js.executeScript(script);
            if (result instanceof Number && ((Number) result).intValue() > 0) {
                int count = ((Number) result).intValue();
                log.debug("에피소드 개수 찾음 (JavaScript): {}", count);
                return count;
            }

        } catch (Exception e) {
            log.warn("에피소드 개수 추출 실패: {}", e.getMessage());
        }

        return null;
    }

    private String parseAgeRating(WebDriver driver) {
        try {
            List<WebElement> metaElements = driver.findElements(
                    By.cssSelector("em.ContentMetaInfo__info_item--utGrf")
            );

            for (WebElement meta : metaElements) {
                String metaText = meta.getText();
                if (metaText.contains("전체")) return "전체이용가";
                if (metaText.contains("12세")) return "12세이용가";
                if (metaText.contains("15세")) return "15세이용가";
                if (metaText.contains("19세")) return "19세이용가";
            }
        } catch (Exception e) {
            log.debug("연령등급 파싱 실패: {}", e.getMessage());
        }
        return null;
    }

    private List<String> parseTags(WebDriver driver) {
        List<String> tags = new ArrayList<>();

        try {
            // 방법 1: 직접 셀렉터
            List<WebElement> tagElements = driver.findElements(
                    By.cssSelector("div.TagGroup__tag_group--uUJza a.TagGroup__tag--xu0OH")
            );

            if (tagElements.isEmpty()) {
                // 방법 2: JavaScript로 # 포함 링크들 찾기
                JavascriptExecutor js = (JavascriptExecutor) driver;
                String script = """
                    var tags = [];
                    var links = document.querySelectorAll('a');
                    for (var i = 0; i < links.length; i++) {
                        var text = links[i].textContent.trim();
                        if (text.startsWith('#') && text.length > 1) {
                            tags.push(text.substring(1));
                        }
                    }
                    return tags;
                    """;

                @SuppressWarnings("unchecked")
                List<String> jsResult = (List<String>) js.executeScript(script);
                if (jsResult != null) {
                    tags.addAll(jsResult);
                }
            } else {
                // 일반적인 방법으로 태그 추출
                for (WebElement tag : tagElements) {
                    String tagText = tag.getText().trim();
                    if (tagText.startsWith("#")) {
                        tagText = tagText.substring(1);
                    }
                    if (!tagText.isEmpty()) {
                        tags.add(tagText);
                    }
                }
            }

            log.debug("태그 {}개 추출됨: {}", tags.size(), tags);

        } catch (Exception e) {
            log.warn("태그 추출 실패: {}", e.getMessage());
        }

        return tags;
    }

    // 🎯 핵심 메서드: 관심수 추출 (Selenium으로만 가능)
    private Long parseLikeCount(WebDriver driver) {
        try {
            // 방법 1: 직접 셀렉터 사용
            WebElement likeElement = driver.findElement(By.className("EpisodeListUser__count--fNEWK"));
            String likeText = likeElement.getText().trim();
            log.debug("관심수 찾음 (직접): {}", likeText);
            return parseKoreanNumber(likeText);

        } catch (NoSuchElementException e) {
            log.debug("직접 셀렉터로 관심수 못 찾음, 대안 방법 시도");

            // 방법 2: "관심" 텍스트 기반 검색
            try {
                JavascriptExecutor js = (JavascriptExecutor) driver;
                String script = """
                    var result = null;
                    var spans = document.querySelectorAll('span');
                    for (var i = 0; i < spans.length; i++) {
                        if (spans[i].textContent.trim() === '관심') {
                            var next = spans[i].nextElementSibling;
                            if (next && /\\d/.test(next.textContent)) {
                                result = next.textContent.trim();
                                break;
                            }
                        }
                    }
                    return result;
                    """;

                String result = (String) js.executeScript(script);
                if (result != null) {
                    log.debug("관심수 찾음 (JavaScript): {}", result);
                    return parseKoreanNumber(result);
                }

            } catch (Exception jsException) {
                log.debug("JavaScript 방법도 실패: {}", jsException.getMessage());
            }

            // 방법 3: 클래스 패턴 매칭
            try {
                List<WebElement> countElements = driver.findElements(
                        By.cssSelector("span[class*='EpisodeListUser'][class*='count']")
                );
                for (WebElement element : countElements) {
                    String text = element.getText().trim();
                    if (text.matches(".*\\d.*")) {
                        log.debug("관심수 찾음 (패턴 매칭): {}", text);
                        return parseKoreanNumber(text);
                    }
                }
            } catch (Exception patternException) {
                log.debug("패턴 매칭도 실패: {}", patternException.getMessage());
            }
        }

        log.warn("관심수를 찾을 수 없음");
        return null;
    }

    // ===== 유틸리티 메서드들 =====

    private Long parseKoreanNumber(String numberText) {
        if (numberText == null || numberText.trim().isEmpty()) {
            return null;
        }

        try {
            String text = numberText.trim();

            // "1,584"와 같은 콤마가 포함된 숫자 처리
            if (text.matches("^[0-9,]+$")) {
                return Long.parseLong(text.replaceAll(",", ""));
            }

            // "1.2만", "3.5억" 등 한글 단위 처리
            if (text.contains("만")) {
                String num = text.replace("만", "").replaceAll("[^0-9.]", "");
                return Math.round(Double.parseDouble(num) * 10000);
            }

            if (text.contains("억")) {
                String num = text.replace("억", "").replaceAll("[^0-9.]", "");
                return Math.round(Double.parseDouble(num) * 100000000);
            }

            // 일반 숫자 (콤마 제거)
            String cleanNumber = text.replaceAll("[^0-9]", "");
            if (!cleanNumber.isEmpty()) {
                return Long.parseLong(cleanNumber);
            }

        } catch (NumberFormatException e) {
            log.warn("숫자 파싱 실패: {}", numberText);
        }

        return null;
    }

    private String cleanText(String text) {
        return text != null ? text.trim() : null;
    }

    private boolean isBlank(String str) {
        return str == null || str.trim().isEmpty();
    }
}