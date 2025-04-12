package com.example.AOD.OTT.Netflix.crawler;

import com.example.AOD.OTT.Netflix.dto.NetflixContentDTO;
import io.github.bonigarcia.wdm.WebDriverManager;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.NoSuchElementException;
import java.util.logging.*;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.*;

public class NetflixContentCrawler {
    private static final Logger logger = Logger.getLogger("NetflixCrawler");

    private final String baseUrl = "https://www.netflix.com";
    private final String seriesUrl = "https://www.netflix.com/browse/genre/83?so=su";

    private String email;
    private String password;
    private WebDriver driver;
    private Random random = new Random();
    private final int MAX_ITEMS = 2; // 테스트용

    static {
        try {
            FileHandler fileHandler = new FileHandler("netflix_crawler.log", true);
            fileHandler.setFormatter(new SimpleFormatter());
            logger.addHandler(fileHandler);
            logger.setLevel(Level.INFO);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public NetflixContentCrawler(String email, String password) {
        this.email = email;
        this.password = password;
        WebDriverManager.chromedriver().setup();
    }

    public void setupDriver() {
        ChromeOptions options = new ChromeOptions();
        // options.addArguments("--headless");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--window-size=1920,1080");
        options.addArguments("--user-agent=Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)");
        driver = new ChromeDriver(options);
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
    }

    public boolean login() {
        try {
            driver.get(baseUrl + "/login");
            Thread.sleep(3000);

            WebElement emailField = new WebDriverWait(driver, Duration.ofSeconds(10))
                    .until(ExpectedConditions.presenceOfElementLocated(By.name("userLoginId")));
            emailField.clear();
            emailField.sendKeys(email);

            WebElement passwordField = driver.findElement(By.name("password"));
            passwordField.clear();
            passwordField.sendKeys(password);

            // 로그인 (Enter키로 시도)
            passwordField.sendKeys(Keys.ENTER);
            Thread.sleep(5000);

            boolean success = driver.getCurrentUrl().contains("browse");
            logger.info("로그인 " + (success ? "성공" : "실패") + ": " + driver.getCurrentUrl());
            return success;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "로그인 중 오류", e);
            return false;
        }
    }

    /**
     * 크롤링 수행, NetflixContentDTO 리스트 반환
     */
    public List<NetflixContentDTO> crawl() {
        List<NetflixContentDTO> results = new ArrayList<>();
        try {
            driver.get(seriesUrl);
            Thread.sleep(3000);

            // 스크롤다운
            for (int i = 0; i < 5; i++) {
                ((JavascriptExecutor) driver).executeScript("window.scrollBy(0, 800);");
                Thread.sleep(1000);
            }

            List<WebElement> items = driver.findElements(By.cssSelector("a.slider-refocus"));
            if (items.isEmpty()) {
                logger.warning("항목 없음");
                return results;
            }
            int limit = Math.min(MAX_ITEMS, items.size());
            for (int i = 0; i < limit; i++) {
                WebElement item = items.get(i);
                String url = item.getAttribute("href");
                if (url == null) continue;

                String title = item.getAttribute("aria-label");
                if (title == null || title.isEmpty()) {
                    try {
                        title = item.findElement(By.tagName("img")).getAttribute("alt");
                    } catch (NoSuchElementException ex) {
                        title = "Unknown Title " + (i + 1);
                    }
                }

                String imageUrl = "";
                try {
                    imageUrl = item.findElement(By.tagName("img")).getAttribute("src");
                } catch (Exception ex) {
                    // ignore
                }

                // URL에서 contentId 추출
                String[] parts = url.split("\\?")[0].split("/");
                String contentId = parts[parts.length - 1];

                NetflixContentDTO dto = getDetailInfo(contentId, title, url, imageUrl);
                if (dto != null) {
                    results.add(dto);
                }
                Thread.sleep(randomSleep(1000, 2000));
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "크롤링 중 오류", e);
        }
        return results;
    }

    /**
     * 상세 정보 페이지 접근, DTO에 세부 정보 셋팅
     */
    private NetflixContentDTO getDetailInfo(String contentId, String title, String url, String imageUrl) {
        NetflixContentDTO dto = new NetflixContentDTO();
        try {
            String detailUrl = "https://www.netflix.com/title/" + contentId;
            driver.get(detailUrl);
            Thread.sleep(randomSleep(2000, 3000));

            // contentType 추정 (간단 로직)
            String contentType = url.toLowerCase().contains("series") ? "series" : "movie";

            // 설명, 연도, 등급, 배우, 크리에이터 등 가져오기
            String description = "";
            try {
                WebElement descElem = new WebDriverWait(driver, Duration.ofSeconds(5))
                        .until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(".title-info-synopsis")));
                description = descElem.getText().trim();
            } catch (Exception ex) {
                logger.fine("설명 없음");
            }

            String releaseYear = "";
            String maturityRating = "";
            List<WebElement> metaItems = driver.findElements(By.cssSelector(".title-info-metadata-item"));
            for (WebElement meta : metaItems) {
                String text = meta.getText().trim();
                if (text.matches("\\d{4}")) {
                    releaseYear = text;
                } else if (Arrays.asList("TV-14", "TV-MA", "PG-13", "R").contains(text)) {
                    maturityRating = text;
                }
            }

            // 태그 정보
            String creator = null;
            List<String> actors = new ArrayList<>();
            List<String> genres = new ArrayList<>();
            List<String> features = new ArrayList<>();

            List<WebElement> tagGroups = driver.findElements(By.cssSelector(".previewModal--tags"));
            for (WebElement group : tagGroups) {
                try {
                    String label = group.findElement(By.cssSelector(".previewModal--tags-label")).getText().trim();
                    List<WebElement> tags = group.findElements(By.cssSelector(".tag-item"));
                    List<String> values = new ArrayList<>();
                    for (WebElement t : tags) {
                        values.add(t.getText().trim());
                    }
                    if (label.contains("출연:") || label.contains("주연:") || label.contains("Cast:")) {
                        actors = values;
                    } else if (label.contains("크리에이터:") || label.contains("Creators:")) {
                        creator = String.join(", ", values);
                    } else if (label.contains("장르:") || label.contains("Genres:")) {
                        genres = values;
                    } else if (label.contains("특징:") || label.contains("Features:")) {
                        features = values;
                    }
                } catch (Exception ex) {
                    // ignore
                }
            }

            // DTO 필드 채우기
            dto.setContentId(contentId);
            dto.setTitle(title);
            dto.setType(contentType);
            dto.setUrl(url);
            dto.setDetailUrl(detailUrl);
            dto.setThumbnail(imageUrl);
            dto.setDescription(description);
            dto.setCreator(creator);
            dto.setMaturityRating(maturityRating);
            dto.setReleaseYear(releaseYear);
            dto.setActors(actors);
            dto.setGenres(genres);
            dto.setFeatures(features);
            dto.setCrawledAt(LocalDateTime.now());

            logger.info("세부 정보 수집 완료: " + title);
        } catch (Exception e) {
            logger.log(Level.WARNING, "세부 정보 수집 실패", e);
            return null;
        }
        return dto;
    }

    private int randomSleep(int min, int max) {
        return min + random.nextInt(max - min);
    }

    public void cleanup() {
        if (driver != null) {
            driver.quit();
        }
    }
}