package com.example.AOD.Novel.NaverSeriesNovel;

import com.example.AOD.ingest.CollectorService;
import com.example.AOD.util.ChromeDriverProvider;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Naver Series(웹소설) 크롤러
 * - 목록 페이지에서 상세 링크(productNo)만 수집
 * - 상세 페이지에서 필요한 필드만 추출
 * - 추출 결과를 평평한 Map payload로 raw_items에 저장
 */
@Slf4j
@Component
public class NaverSeriesCrawler {


    /**
     * 목록 페이지 순회 → 상세 파싱 → raw_items 저장
     * @param baseListUrl e.g. https://series.naver.com/novel/top100List.series?rankingTypeCode=DAILY&categoryCode=ALL&page=
     * @param cookieString 필요 시 쿠키(로그인/성인)
     * @param maxPages 0 또는 음수면 결과 없을 때까지
     * @return 저장 건수
     */
    private final CollectorService collector;
    private final ChromeDriverProvider chromeDriverProvider;
    private final int SLEEP_TIME = 500; // 페이지 로딩 대기 시간

    public NaverSeriesCrawler(CollectorService collector, ChromeDriverProvider chromeDriverProvider) {
        this.collector = collector;
        this.chromeDriverProvider = chromeDriverProvider;
    }

    /**
     * 목록 페이지 순회 → 상세 파싱 → raw_items 저장
     */
    public int crawlToRaw(String baseListUrl, int maxPages) throws Exception {
        int saved = 0;
        int page = 1;

        while (true) {
            if (maxPages > 0 && page > maxPages) break;

            String url = baseListUrl + page;

            WebDriver driver = null;
            try {
                driver = chromeDriverProvider.getDriver();
                driver.get(url);
                Thread.sleep(SLEEP_TIME);

                // 상세 링크 수집
                Set<String> detailUrls = extractDetailUrls(driver);

                if (detailUrls.isEmpty()) {
                    log.info("{}페이지에서 더 이상 작품을 찾을 수 없습니다.", page);
                    break;
                }

                log.info("{}페이지에서 {}개 작품 링크 수집", page, detailUrls.size());

                // 각 상세 페이지 크롤링
                for (String detailUrl : detailUrls) {
                    try {
                        Map<String, Object> payload = crawlDetailPage(detailUrl);
                        if (payload != null) {
                            collector.saveRaw("NaverSeries", "WEBNOVEL", payload, (String) payload.get("titleId"), (String) payload.get("productUrl"));
                            saved++;
                            log.debug("저장 완료: {}", payload.get("title"));
                        }
                    } catch (Exception e) {
                        log.error("상세 페이지 크롤링 실패: {}, 오류: {}", detailUrl, e.getMessage());
                    }
                }

            } finally {
                if (driver != null) {
                    driver.quit();
                }
            }

            page++;
            Thread.sleep(1000); // 페이지 간 딜레이
        }

        log.info("크롤링 완료: 총 {}개 작품 저장", saved);
        return saved;
    }

    /**
     * 목록 페이지에서 상세 링크 추출
     */
    private Set<String> extractDetailUrls(WebDriver driver) {
        Set<String> urls = new LinkedHashSet<>();

        try {
            List<WebElement> links = driver.findElements(
                    By.cssSelector("a[href*='/novel/detail.series'][href*='productNo=']")
            );

            for (WebElement link : links) {
                String href = link.getAttribute("href");
                if (href != null && !href.isEmpty()) {
                    if (!href.startsWith("http")) {
                        href = "https://series.naver.com" + href;
                    }
                    urls.add(href);
                }
            }

            log.debug("추출된 링크 수: {}", urls.size());

        } catch (Exception e) {
            log.error("링크 추출 중 오류: {}", e.getMessage());
        }

        return urls;
    }

    /**
     * 상세 페이지 크롤링 (Selenium 사용)
     */
    private Map<String, Object> crawlDetailPage(String detailUrl) {
        WebDriver driver = null;
        try {
            driver = chromeDriverProvider.getDriver();
            driver.get(detailUrl);
            Thread.sleep(SLEEP_TIME);

            log.debug("상세 페이지 파싱 시작: {}", detailUrl);

            // 기본 정보
            String title = parseTitle(driver);
            if (title == null || title.isBlank()) {
                log.warn("제목을 찾을 수 없음: {}", detailUrl);
                return null;
            }

            String imageUrl = parseImageUrl(driver);

            // ⭐ 핵심: 더보기 버튼 클릭 후 시놉시스 로드
            String synopsis = parseSynopsisWithMoreButton(driver);

            // 상세 정보
            String author = parseAuthor(driver);
            String publisher = parsePublisher(driver);
            String status = parseStatus(driver);
            String ageRating = parseAgeRating(driver);
            List<String> genres = parseGenres(driver);

            // 통계 정보
            BigDecimal rating = parseRating(driver);
            Long downloadCount = parseDownloadCount(driver);
            Long commentCount = parseCommentCount(driver);

            // productNo 추출
            String titleId = extractProductNo(detailUrl);

            // Payload 구성
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("title", nz(title));
            payload.put("author", nz(author));
            payload.put("publisher", nz(publisher));
            payload.put("status", nz(status));
            payload.put("ageRating", nz(ageRating));
            payload.put("synopsis", nz(synopsis));
            payload.put("imageUrl", nz(imageUrl));
            payload.put("productUrl", nz(detailUrl));
            payload.put("titleId", nz(titleId));

            if (rating != null) payload.put("rating", rating.toString());
            if (downloadCount != null) payload.put("downloadCount", downloadCount);
            if (commentCount != null) payload.put("commentCount", commentCount);
            if (!genres.isEmpty()) payload.put("genres", String.join(",", genres));

            return payload;

        } catch (Exception e) {
            log.error("상세 페이지 파싱 중 오류: {}, {}", detailUrl, e.getMessage());
            return null;
        } finally {
            if (driver != null) {
                driver.quit();
            }
        }
    }

    /**
     * 🎯 더보기 버튼 클릭 후 시놉시스 파싱
     */
    private String parseSynopsisWithMoreButton(WebDriver driver) {
        try {
            // 더보기 버튼 찾기 및 클릭
            try {
                WebElement moreButton = driver.findElement(
                        By.cssSelector("a.lk_more._toggleMore")
                );

                // JavaScript로 클릭 (일반 클릭이 안 될 경우 대비)
                JavascriptExecutor js = (JavascriptExecutor) driver;
                js.executeScript("arguments[0].click();", moreButton);

                log.debug("더보기 버튼 클릭 완료");
                Thread.sleep(300); // 확장 대기

            } catch (NoSuchElementException e) {
                log.debug("더보기 버튼 없음 (전체 텍스트 표시 상태)");
            }

            // 시놉시스 텍스트 추출
            WebElement synopsisElement = driver.findElement(
                    By.cssSelector("div.end_dsc ._synopsis")
            );

            String synopsis = synopsisElement.getText().trim();
            log.debug("시놉시스 추출 완료: {}자", synopsis.length());

            return synopsis;

        } catch (Exception e) {
            log.warn("시놉시스 파싱 실패: {}", e.getMessage());
            return null;
        }
    }

    private String parseTitle(WebDriver driver) {
        try {
            WebElement element = driver.findElement(By.cssSelector("meta[property='og:title']"));
            String rawTitle = element.getAttribute("content");
            return cleanTitle(rawTitle);
        } catch (Exception e) {
            log.debug("제목 파싱 실패: {}", e.getMessage());
            return null;
        }
    }

    private String parseImageUrl(WebDriver driver) {
        try {
            WebElement element = driver.findElement(By.cssSelector("meta[property='og:image']"));
            return element.getAttribute("content");
        } catch (Exception e) {
            log.debug("이미지 URL 파싱 실패: {}", e.getMessage());
            return null;
        }
    }

    private String parseAuthor(WebDriver driver) {
        try {
            WebElement infoUl = driver.findElement(By.cssSelector("ul.end_info li.info_lst > ul"));
            return findInfoValue(infoUl, "글");
        } catch (Exception e) {
            log.debug("작가 파싱 실패: {}", e.getMessage());
            return null;
        }
    }

    private String parsePublisher(WebDriver driver) {
        try {
            WebElement infoUl = driver.findElement(By.cssSelector("ul.end_info li.info_lst > ul"));
            return findInfoValue(infoUl, "출판사");
        } catch (Exception e) {
            log.debug("출판사 파싱 실패: {}", e.getMessage());
            return null;
        }
    }

    private String parseStatus(WebDriver driver) {
        try {
            WebElement statusElement = driver.findElement(By.cssSelector("ul.end_info li.ing > span"));
            return statusElement.getText().trim();
        } catch (Exception e) {
            log.debug("상태 파싱 실패: {}", e.getMessage());
            return null;
        }
    }

    private String parseAgeRating(WebDriver driver) {
        try {
            WebElement infoUl = driver.findElement(By.cssSelector("ul.end_info li.info_lst > ul"));

            List<WebElement> items = infoUl.findElements(By.cssSelector("> li"));
            for (WebElement li : items) {
                WebElement labelSpan = li.findElement(By.cssSelector("> span"));
                if (labelSpan != null && "이용가".equals(labelSpan.getText().trim())) {
                    WebElement valueSpan = li.findElement(By.cssSelector("span:nth-of-type(2)"));
                    if (valueSpan != null) {
                        return valueSpan.getText().trim();
                    }
                }
            }
        } catch (Exception e) {
            log.debug("이용가 파싱 실패: {}", e.getMessage());
        }
        return null;
    }

    private List<String> parseGenres(WebDriver driver) {
        List<String> genres = new ArrayList<>();
        try {
            WebElement infoUl = driver.findElement(By.cssSelector("ul.end_info li.info_lst > ul"));
            List<WebElement> items = infoUl.findElements(By.cssSelector("> li"));

            for (WebElement li : items) {
                String label = "";
                try {
                    WebElement labelSpan = li.findElement(By.cssSelector("> span"));
                    label = labelSpan.getText().trim();
                } catch (Exception ignored) {}

                if ("글".equals(label) || "출판사".equals(label) || "이용가".equals(label)) {
                    continue;
                }

                List<WebElement> genreLinks = li.findElements(By.cssSelector("a"));
                for (WebElement a : genreLinks) {
                    String genre = a.getText().trim();
                    if (!genre.isEmpty() && !genres.contains(genre)) {
                        genres.add(genre);
                    }
                }
            }
        } catch (Exception e) {
            log.debug("장르 파싱 실패: {}", e.getMessage());
        }
        return genres;
    }

    private BigDecimal parseRating(WebDriver driver) {
        try {
            WebElement ratingElement = driver.findElement(By.cssSelector("div.score_area em.num"));
            String ratingText = ratingElement.getText().trim();
            return new BigDecimal(ratingText);
        } catch (Exception e) {
            log.debug("별점 파싱 실패: {}", e.getMessage());
            return null;
        }
    }

    private Long parseDownloadCount(WebDriver driver) {
        try {
            WebElement headDiv = driver.findElement(By.cssSelector("div.end_head"));
            String headText = headDiv.getText();

            Pattern pattern = Pattern.compile("관심\\s+([0-9억만,]+)");
            Matcher matcher = pattern.matcher(headText);

            if (matcher.find()) {
                String countStr = matcher.group(1);
                return parseKoreanNumber(countStr);
            }
        } catch (Exception e) {
            log.debug("다운로드 수 파싱 실패: {}", e.getMessage());
        }
        return null;
    }

    private Long parseCommentCount(WebDriver driver) {
        try {

            WebElement commentCountSpan = driver.findElement(By.id("commentCount"));
            String countText = commentCountSpan.getText().trim();

            log.debug("댓글 수 추출: {}", countText);
            return parseKoreanNumber(countText);

        } catch (NoSuchElementException e) {
            log.debug("댓글 수 요소를 찾을 수 없음 (commentCount ID 없음)");
            return null;
        } catch (Exception e) {
            log.debug("댓글 수 파싱 실패: {}", e.getMessage());
            return null;
        }
    }

    // ===== Helper Methods =====

    private String findInfoValue(WebElement infoUl, String label) {
        try {
            List<WebElement> items = infoUl.findElements(By.cssSelector("> li"));
            for (WebElement li : items) {
                WebElement labelSpan = li.findElement(By.cssSelector("> span"));
                if (labelSpan != null && label.equals(labelSpan.getText().trim())) {
                    WebElement valueSpan = li.findElement(By.cssSelector("span:nth-of-type(2)"));
                    if (valueSpan != null) {
                        return valueSpan.getText().trim();
                    }
                }
            }
        } catch (Exception e) {
            log.debug("{} 값 찾기 실패: {}", label, e.getMessage());
        }
        return null;
    }

    private String extractProductNo(String url) {
        Pattern pattern = Pattern.compile("productNo=(\\d+)");
        Matcher matcher = pattern.matcher(url);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String cleanTitle(String title) {
        if (title == null) return null;

        // 괄호 제거
        title = title.replaceAll("\\([^)]*\\)", "").trim();
        // 다중 공백 정리
        title = title.replaceAll("\\s+", " ").trim();

        return title;
    }

    private Long parseKoreanNumber(String text) {
        if (text == null || text.isEmpty()) return null;

        try {
            text = text.replaceAll("[^0-9억만,]", "");

            long result = 0;
            if (text.contains("억")) {
                String[] parts = text.split("억");
                result += Long.parseLong(parts[0].replaceAll(",", "")) * 100000000L;
                if (parts.length > 1 && !parts[1].isEmpty()) {
                    String remainder = parts[1].replaceAll("만", "").replaceAll(",", "");
                    if (!remainder.isEmpty()) {
                        result += Long.parseLong(remainder) * 10000L;
                    }
                }
            } else if (text.contains("만")) {
                String num = text.replaceAll("만", "").replaceAll(",", "");
                result = Long.parseLong(num) * 10000L;
            } else {
                result = Long.parseLong(text.replaceAll(",", ""));
            }

            return result;

        } catch (Exception e) {
            log.debug("숫자 파싱 실패: {}", text);
            return null;
        }
    }

    private String nz(String s) {
        return s == null ? "" : s;
    }
}
