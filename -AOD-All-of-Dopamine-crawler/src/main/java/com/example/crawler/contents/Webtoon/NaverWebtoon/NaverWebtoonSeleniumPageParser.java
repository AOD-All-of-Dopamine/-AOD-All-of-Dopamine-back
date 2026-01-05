package com.example.crawler.contents.Webtoon.NaverWebtoon;


import com.example.crawler.util.ChromeDriverProvider;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.openqa.selenium.*;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 네이버 웹툰 Selenium 기반 페이지 파서
 * - WebtoonPageParser 인터페이스 구현
 * - PC 상세 페이지를 Selenium으로 파싱
 * - React SPA 동적 콘텐츠 완벽 지원
 * - WebDriver 재사용으로 자원 누수 방지
 */
@Component
@Slf4j
public class NaverWebtoonSeleniumPageParser implements WebtoonPageParser {

    private final ChromeDriverProvider chromeDriverProvider;
    
    // WebDriver 재사용을 위한 ThreadLocal (멀티스레드 환경 대응)
    private final ThreadLocal<WebDriver> driverThreadLocal = ThreadLocal.withInitial(() -> null);
    private final ThreadLocal<Integer> usageCount = ThreadLocal.withInitial(() -> 0);
    private static final int MAX_REUSE_COUNT = 50; // 50회 사용 후 재생성 (메모리 누수 방지)

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
    
    /**
     * 재사용 가능한 WebDriver 획득
     * - ThreadLocal을 사용하여 스레드별 드라이버 관리
     * - 일정 횟수 사용 후 자동 재생성 (메모리 누수 방지)
     */
    private WebDriver getOrCreateDriver() {
        WebDriver driver = driverThreadLocal.get();
        Integer count = usageCount.get();
        
        // 드라이버가 없거나 MAX_REUSE_COUNT 초과 시 재생성
        if (driver == null || count >= MAX_REUSE_COUNT) {
            if (driver != null) {
                try {
                    driver.quit();
                    log.debug("WebDriver 재생성 (사용 횟수: {}회)", count);
                } catch (Exception e) {
                    log.warn("기존 WebDriver 종료 실패: {}", e.getMessage());
                } finally {
                    // 실패 여부와 관계없이 ThreadLocal 정리
                    driverThreadLocal.remove();
                    usageCount.remove();
                }
            }
            
            // 새 WebDriver 생성
            try {
                driver = chromeDriverProvider.getDriver();
                driverThreadLocal.set(driver);
                usageCount.set(0);
            } catch (Exception e) {
                log.error("새 WebDriver 생성 실패: {}", e.getMessage());
                throw new RuntimeException("WebDriver 초기화 실패", e);
            }
        }
        
        usageCount.set(count + 1);
        return driver;
    }
    
    /**
     * ThreadLocal WebDriver 정리 (작업 완료 후 호출 권장)
     */
    public void cleanup() {
        WebDriver driver = driverThreadLocal.get();
        if (driver != null) {
            try {
                driver.quit();
                log.debug("WebDriver 정리 완료");
            } catch (Exception e) {
                log.warn("WebDriver 정리 실패: {}", e.getMessage());
            } finally {
                driverThreadLocal.remove();
                usageCount.remove();
            }
        }
    }
    
    /**
     * Spring Bean 종료 시 자동으로 ThreadLocal 자원 정리
     */
    @PreDestroy
    public void onDestroy() {
        cleanup();
        log.info("NaverWebtoonSeleniumPageParser Bean 종료 - ThreadLocal 자원 정리 완료");
    }

    @Override
    public NaverWebtoonDTO parseWebtoonDetail(Document detailDocument, String detailUrl,
                                              String crawlSource, String weekday) {
        // Document 파라미터는 무시하고 detailUrl로 Selenium을 통해 접근
        WebDriver driver = null;
        
        try {
            driver = getOrCreateDriver(); // 재사용 가능한 드라이버 획듍
            
            // 🎯 핵심: 처음부터 1화부터 정렬된 URL로 접근 (한 번에 첫 화 날짜까지 크롤링)
            String sortedUrl = buildSortedUrl(detailUrl, weekday);
            log.debug("정렬된 URL로 웹툰 상세 파싱 시작: {}", sortedUrl);
            driver.get(sortedUrl);

            // React 앱 로딩 대기 - WebDriverWait 사용으로 더 확실하게 대기
            try {
                WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
                // 제목 요소가 나타날 때까지 대기 (React 렌더링 완료 확인)
                wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.cssSelector("h2[class*='EpisodeListInfo'][class*='title']")
                ));
                log.debug("React 렌더링 완료 확인");
            } catch (TimeoutException e) {
                log.warn("React 렌더링 대기 시간 초과: {}", sortedUrl);
                return null;
            }

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
            
            // 🎯 첫 화 연재 날짜 파싱 (이미 정렬된 페이지의 첫 번째 에피소드)
            LocalDate releaseDate = parseReleaseDate(driver);

            log.debug("파싱 완료: {} (관심: {}, 에피소드: {}, 태그: {}, 첫화날짜: {})",
                    title, likeCount, episodeCount, tags.size(), releaseDate);

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
                    .releaseDate(releaseDate)
                    .originalPlatform("NAVER_WEBTOON")
                    .crawlSource(crawlSource)
                    .build();

        } catch (Exception e) {
            log.error("Selenium 웹툰 상세 파싱 중 오류 발생: {}, {}", detailUrl, e.getMessage());
            // 일반 예외는 드라이버를 재사용하므로 정리하지 않음
            return null;
        }
        // finally 블록 제거: 드라이버를 재사용하므로 매번 quit()하지 않음
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

    /**
     * 첫 화의 연재 날짜를 파싱 (이미 정렬된 페이지에 있음)
     * 현재 페이지의 첫 번째 에피소드 날짜를 파싱
     */
    private LocalDate parseReleaseDate(WebDriver driver) {
        try {
            log.debug("첫 화 날짜 파싱 시작");
            
            // 1. 명시적 대기 추가 (최대 10초 대기로 증가)
            // React가 에피소드 리스트를 렌더링할 때까지 기다립니다.
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

            // 2. 해시값에 의존하지 않는 범용 셀렉터 사용
            // "EpisodeListList__item"이 포함된 li 태그를 찾습니다.
            log.debug("에피소드 리스트 로딩 대기 중...");
            wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.cssSelector("li[class*='EpisodeListList__item']")
            ));
            log.debug("에피소드 리스트 로딩 완료");

            List<WebElement> episodeItems = driver.findElements(
                    By.cssSelector("li[class*='EpisodeListList__item']")
            );

            log.debug("발견된 에피소드 수: {}", episodeItems.size());

            if (episodeItems.isEmpty()) {
                log.warn("에피소드 목록을 찾을 수 없음 (빈 리스트)");
                return null;
            }

            // 3. 첫 번째 에피소드에서 날짜 추출
            WebElement firstEpisode = episodeItems.get(0);
            log.debug("첫 번째 에피소드 요소 획득");

            // 날짜 요소도 범용 셀렉터 사용 (span 중 class에 date가 포함된 것)
            WebElement dateElement = firstEpisode.findElement(By.cssSelector("span[class*='date']"));
            String dateText = dateElement.getText().trim();

            log.debug("첫 화 날짜 텍스트 추출 성공: {}", dateText);

            // 날짜 파싱: "20.11.01" -> 2020-11-01
            return parseDateFromText(dateText);

        } catch (TimeoutException e) {
            log.warn("에피소드 리스트 로딩 시간 초과 (10초): {}", e.getMessage());
            // 페이지 소스 일부 로깅 (디버깅용)
            try {
                String pageSource = driver.getPageSource();
                if (pageSource.length() > 500) {
                    log.debug("페이지 소스 일부: {}", pageSource.substring(0, 500));
                }
            } catch (Exception logEx) {
                log.debug("페이지 소스 로깅 실패");
            }
            return null;
        } catch (NoSuchElementException e) {
            log.warn("첫 화 날짜 요소를 찾을 수 없음 (구조 변경 가능성): {}", e.getMessage());
            return null;
        } catch (Exception e) {
            log.warn("첫 화 날짜 파싱 실패: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * 1화부터 정렬된 URL을 생성
     * https://comic.naver.com/webtoon/list?titleId=758037&page=1&sort=ASC&tab=mon
     */
    private String buildSortedUrl(String detailUrl, String weekday) {
        String titleId = extractTitleId(detailUrl);
        if (titleId == null) {
            return detailUrl; // titleId를 찾을 수 없으면 원래 URL 반환
        }
        
        String tab = weekday != null ? "&tab=" + weekday : "";
        return "https://comic.naver.com/webtoon/list?titleId=" + titleId + "&page=1&sort=ASC" + tab;
    }
    
    /**
     * 날짜 텍스트를 LocalDate로 변환
     * 형식: "20.11.01" (yy.MM.dd) 또는 "2020.11.01" (yyyy.MM.dd)
     */
    private LocalDate parseDateFromText(String dateText) {
        if (dateText == null || dateText.trim().isEmpty()) {
            return null;
        }
        
        try {
            // "20.11.01" 형식 처리
            if (dateText.matches("\\d{2}\\.\\d{2}\\.\\d{2}")) {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yy.MM.dd");
                return LocalDate.parse(dateText, formatter);
            }
            
            // "2020.11.01" 형식 처리
            if (dateText.matches("\\d{4}\\.\\d{2}\\.\\d{2}")) {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy.MM.dd");
                return LocalDate.parse(dateText, formatter);
            }
            
            // "20-11-01" 형식 처리
            if (dateText.matches("\\d{2}-\\d{2}-\\d{2}")) {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yy-MM-dd");
                return LocalDate.parse(dateText, formatter);
            }
            
            log.warn("지원하지 않는 날짜 형식: {}", dateText);
            return null;
            
        } catch (DateTimeParseException e) {
            log.warn("날짜 파싱 실패: {}, 오류: {}", dateText, e.getMessage());
            return null;
        }
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

