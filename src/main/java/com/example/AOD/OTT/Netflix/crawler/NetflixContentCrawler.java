package com.example.AOD.OTT.Netflix.crawler;

import com.example.AOD.OTT.Netflix.dto.NetflixContentDTO;
import io.github.bonigarcia.wdm.WebDriverManager;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.NoSuchElementException;
import java.util.logging.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private final int MAX_ITEMS = 5; // 테스트용

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

    public NetflixContentCrawler(String email, String password, WebDriver driver) {
        this.email = email;
        this.password = password;
        this.driver = driver;
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
            // 1) 시리즈 목록 페이지 진입
            driver.get(seriesUrl);
            Thread.sleep(3000);

            // 스크롤다운
            for (int i = 0; i < 5; i++) {
                ((JavascriptExecutor) driver).executeScript("window.scrollBy(0, 800);");
                Thread.sleep(1000);
            }

            // 2) 목록 아이템을 모두 찾기
            List<WebElement> items = driver.findElements(By.cssSelector("a.slider-refocus"));
            if (items.isEmpty()) {
                logger.warning("항목 없음");
                return results;
            }
            // 테스트용으로 최대 MAX_ITEMS만
            int limit = Math.min(MAX_ITEMS, items.size());

            // 3) '목록 아이템'에 대한 정보를 먼저 문자열로 추출해서 저장
            //    (StaleElement 방지를 위해, 여기서 WebElement에 의존하지 않고 필요한 데이터만 빼놓는다)
            List<String> urlList = new ArrayList<>();
            List<String> titleList = new ArrayList<>();
            List<String> contentIdList = new ArrayList<>();

            for (int i = 0; i < limit; i++) {
                WebElement item = items.get(i);

                // URL
                String url = item.getAttribute("href");
                if (url == null) {
                    continue;
                }

                // title
                String title = item.getAttribute("aria-label");
                if (title == null || title.isEmpty()) {
                    try {
                        title = item.findElement(By.tagName("img")).getAttribute("alt");
                    } catch (NoSuchElementException ex) {
                        title = "Unknown Title " + (i + 1);
                    }
                }

                // contentId
                String[] parts = url.split("\\?")[0].split("/");
                String contentId = parts[parts.length - 1];

                // 리스트에 저장
                urlList.add(url);
                titleList.add(title);
                contentIdList.add(contentId);
            }

            // 4) 목록에서 뽑아둔 정보로 상세 페이지를 순회하며 DTO 구성
            for (int i = 0; i < urlList.size(); i++) {
                String url = urlList.get(i);
                String title = titleList.get(i);
                String contentId = contentIdList.get(i);

                NetflixContentDTO dto = getDetailInfo(contentId, title, url);
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

    public List<NetflixContentDTO> crawlLatestContent() {
        List<NetflixContentDTO> results = new ArrayList<>();

        try {
            // 1) 최신 콘텐츠 페이지 접근
            String latestUrl = "https://www.netflix.com/latest";
            driver.get(latestUrl);
            Thread.sleep(3000);

            // 스크롤다운 - 모든 콘텐츠가 로드되도록
            for (int i = 0; i < 5; i++) {
                ((JavascriptExecutor) driver).executeScript("window.scrollBy(0, 800);");
                Thread.sleep(1000);
            }

            // 2) 최신 콘텐츠 목록 가져오기 - 'slider-refocus' 클래스를 가진 요소들
            List<WebElement> items = driver.findElements(By.cssSelector("a.slider-refocus"));
            if (items.isEmpty()) {
                logger.warning("최신 콘텐츠 항목 없음");
                return results;
            }

            logger.info("최신 콘텐츠 " + items.size() + "개 발견");

            // 3) 목록 아이템에 대한 정보를 먼저 문자열로 추출해서 저장
            List<String> urlList = new ArrayList<>();
            List<String> titleList = new ArrayList<>();
            List<String> contentIdList = new ArrayList<>();

            for (WebElement item : items) {
                // URL
                String url = item.getAttribute("href");
                if (url == null) {
                    continue;
                }

                // title
                String title = item.getAttribute("aria-label");
                if (title == null || title.isEmpty()) {
                    try {
                        title = item.findElement(By.tagName("img")).getAttribute("alt");
                    } catch (NoSuchElementException ex) {
                        title = "Unknown Title";
                    }
                }

                // contentId
                String[] parts = url.split("\\?")[0].split("/");
                String contentId = parts[parts.length - 1];

                // 중복 방지 (동일 contentId는 한 번만 추가)
                if (!contentIdList.contains(contentId)) {
                    urlList.add(url);
                    titleList.add(title);
                    contentIdList.add(contentId);
                    logger.info("발견한 최신 콘텐츠: " + title + " (ID: " + contentId + ")");
                }
            }

            // 4) 목록에서 뽑아둔 정보로 상세 페이지 순회하며 DTO 구성
            for (int i = 0; i < contentIdList.size(); i++) {
                String url = urlList.get(i);
                String title = titleList.get(i);
                String contentId = contentIdList.get(i);

                NetflixContentDTO dto = getDetailInfo(contentId, title, url);
                if (dto != null) {
                    results.add(dto);
                    logger.info("최신 콘텐츠 상세 정보 수집 완료: " + title);
                }

                // 요청 간 간격 두기
                Thread.sleep(randomSleep(1000, 2000));
            }

        } catch (Exception e) {
            logger.log(Level.SEVERE, "최신 콘텐츠 크롤링 중 오류", e);
        }

        return results;
    }


    /**
     * 이번주 공개된 넷플릭스 최신 콘텐츠만 크롤링 - 최적화 버전
     */
    public List<NetflixContentDTO> crawlThisWeekContent() {
        List<NetflixContentDTO> results = new ArrayList<>();

        try {
            // 1) 최신 콘텐츠 페이지 접근
            String latestUrl = "https://www.netflix.com/latest";
            driver.get(latestUrl);
            Thread.sleep(3000);

            // 스크롤다운 - 모든 콘텐츠가 로드되도록
            for (int i = 0; i < 5; i++) {
                ((JavascriptExecutor) driver).executeScript("window.scrollBy(0, 800);");
                Thread.sleep(1000);
            }

            // 2) "이번 주 공개 콘텐츠" 섹션 찾기 - 정확한 클래스와 텍스트 매칭
            WebElement thisWeekSection = null;
            List<WebElement> rowHeaderTitles = driver.findElements(By.cssSelector(".row-header-title"));

            // 모든 섹션 헤더 로깅
            logger.info("찾은 섹션 헤더:");
            for (WebElement header : rowHeaderTitles) {
                logger.info("- " + header.getText().trim());
            }

            // 정확한 텍스트로 섹션 찾기
            for (WebElement header : rowHeaderTitles) {
                String headerText = header.getText().trim();
                if (headerText.equals("이번 주 공개 콘텐츠")) {
                    // 헤더에서 lolomoRow를 찾아 올라가기
                    thisWeekSection = header.findElement(By.xpath("./ancestor::div[contains(@class, 'lolomoRow')]"));
                    logger.info("이번 주 공개 콘텐츠 섹션 발견!");
                    break;
                }
            }

            if (thisWeekSection == null) {
                logger.warning("정확한 '이번 주 공개 콘텐츠' 섹션을 찾지 못했습니다. 유사한 섹션을 찾습니다.");

                // 유사한 텍스트로 재시도
                for (WebElement header : rowHeaderTitles) {
                    String headerText = header.getText().trim();
                    if (headerText.contains("이번 주") || headerText.contains("이번주") ||
                            headerText.contains("공개 콘텐츠") || headerText.contains("New this week")) {
                        thisWeekSection = header.findElement(By.xpath("./ancestor::div[contains(@class, 'lolomoRow')]"));
                        logger.info("유사한 섹션 발견: " + headerText);
                        break;
                    }
                }
            }

            // 여전히 섹션을 찾지 못한 경우
            if (thisWeekSection == null) {
                logger.warning("이번 주 공개 콘텐츠 섹션을 찾지 못했습니다.");
                return results;
            }

            // 3) 이번 주 공개 콘텐츠 섹션 내의 슬라이더 콘텐츠 찾기
            WebElement sliderContent = thisWeekSection.findElement(By.cssSelector(".sliderContent"));
            logger.info("슬라이더 콘텐츠 요소 발견");

            // 4) 슬라이더 콘텐츠 내의 모든 slider-item 요소 찾기
            List<WebElement> sliderItems = sliderContent.findElements(By.cssSelector(".slider-item"));
            logger.info("슬라이더 아이템 수: " + sliderItems.size());

            // 5) 각 slider-item에서 slider-refocus 링크 추출
            List<WebElement> items = new ArrayList<>();
            for (WebElement item : sliderItems) {
                try {
                    WebElement link = item.findElement(By.cssSelector("a.slider-refocus"));
                    items.add(link);
                } catch (NoSuchElementException e) {
                    // 링크가 없는 슬라이더 아이템은 무시
                }
            }

            logger.info("추출된 콘텐츠 링크 수: " + items.size());

            if (items.isEmpty()) {
                logger.warning("이번 주 공개 콘텐츠 섹션에서 콘텐츠 링크를 찾지 못했습니다.");
                return results;
            }

            // 6) 링크에서 필요한 정보 추출
            List<String> urlList = new ArrayList<>();
            List<String> titleList = new ArrayList<>();
            List<String> contentIdList = new ArrayList<>();

            for (WebElement item : items) {
                // URL
                String url = item.getAttribute("href");
                if (url == null || !url.contains("/watch/")) {
                    continue;
                }

                // title (aria-label 속성)
                String title = item.getAttribute("aria-label");
                if (title == null || title.isEmpty()) {
                    try {
                        // fallback: 이미지의 alt 속성
                        WebElement img = item.findElement(By.cssSelector(".boxart-image"));
                        title = img.getAttribute("alt");
                    } catch (NoSuchElementException ex) {
                        // 이미지도 없는 경우
                        try {
                            // fallback: fallback-text 요소
                            WebElement fallbackText = item.findElement(By.cssSelector(".fallback-text"));
                            title = fallbackText.getText().trim();
                        } catch (NoSuchElementException ex2) {
                            // 모든 방법이 실패한 경우 ID로 제목 생성
                            String tempId = url.split("/watch/")[1].split("\\?")[0];
                            title = "Title #" + tempId;
                        }
                    }
                }

                // contentId
                String contentId;
                try {
                    contentId = url.split("/watch/")[1].split("\\?")[0];
                } catch (Exception e) {
                    // URL 파싱 실패 시 건너뛰기
                    continue;
                }

                // 중복 제거
                if (!contentIdList.contains(contentId)) {
                    urlList.add(url);
                    titleList.add(title);
                    contentIdList.add(contentId);
                    logger.info("발견한 이번 주 공개 콘텐츠: " + title + " (ID: " + contentId + ")");
                }
            }

            logger.info("중복 제거 후 이번 주 공개 콘텐츠 수: " + contentIdList.size());

            // 7) 각 콘텐츠의 상세 정보 가져오기
            for (int i = 0; i < contentIdList.size(); i++) {
                String url = urlList.get(i);
                String title = titleList.get(i);
                String contentId = contentIdList.get(i);

                NetflixContentDTO dto = getDetailInfo(contentId, title, url);
                if (dto != null) {
                    results.add(dto);
                    logger.info("이번 주 공개 콘텐츠 상세 정보 수집 완료: " + title);
                }

                // 요청 간 간격 두기
                Thread.sleep(randomSleep(1000, 2000));
            }

        } catch (Exception e) {
            logger.log(Level.SEVERE, "이번 주 공개 콘텐츠 크롤링 중 오류", e);
            e.printStackTrace();
        }

        return results;
    }

    /**
     * 상세 정보 페이지 접근, DTO에 세부 정보 셋팅
     */
    private NetflixContentDTO getDetailInfo(String contentId, String title, String url) {
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
                        .until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(".preview-modal-synopsis")));
                description = descElem.getText().trim();
            } catch (Exception ex) {
                logger.fine("설명 없음");
            }

            String releaseYear = "";
            // 1) 먼저 숫자 형태로 표시된 등급(span.maturity-number) 시도
            String maturityRating = "";
            List<WebElement> numberSpans = driver.findElements(By.cssSelector("span.maturity-number"));
            if (!numberSpans.isEmpty()) {
                // "15+" 등 플러스를 제거하고 숫자만 남김
                String txt = numberSpans.get(0).getText().trim();
                maturityRating = txt.replaceAll("\\D+", "");
            } else {
                // 2) 아이콘(svg) 형태 처리: span.maturity-graphic 안 첫 번째 svg
                try {
                    WebElement iconSvg = new WebDriverWait(driver, Duration.ofSeconds(5))
                            .until(ExpectedConditions.presenceOfElementLocated(
                                    By.cssSelector("span.maturity-graphic svg")
                            ));
                    String code = null;

                    // 2-1) id 속성에서 추출 (예: id="maturity-rating-976")
                    String svgId = iconSvg.getAttribute("id");
                    if (svgId != null && svgId.startsWith("maturity-rating-")) {
                        code = svgId.substring("maturity-rating-".length());
                    } else {
                        // 2-2) class 속성에서 추출 (예: class="svg-icon-maturity-rating-24306")
                        String classes = iconSvg.getAttribute("class");
                        Matcher m = Pattern.compile("maturity-rating-(\\d+)").matcher(classes);
                        if (m.find()) {
                            code = m.group(1);
                        }
                    }

                    // 3) code → 실제 나이로 매핑
                    if (code != null) {
                        switch (code) {
                            case "976":    // Netflix 내부 코드 976 → 12세
                                maturityRating = "12";
                                break;
                            case "24306":  // 내부 코드 24306 → 19세
                                maturityRating = "19";
                                break;
                            // 필요하면 다른 코드도 여기에 추가
                            default:
                                maturityRating = code;  // fallback: 숫자 그대로
                        }
                    }
                } catch (TimeoutException | NoSuchElementException ex) {
                    logger.fine("maturity-icon 요소를 찾지 못함");
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

            // 고화질 썸네일/스토리아트 이미지 URL 추출
            String imageUrl = "";
            try {
                // storyArt 클래스를 가진 div 안의 img 태그 찾기
                WebElement storyArtImg = new WebDriverWait(driver, Duration.ofSeconds(5))
                        .until(ExpectedConditions.presenceOfElementLocated(
                                By.cssSelector(".storyArt img")
                        ));
                if (storyArtImg != null) {
                    imageUrl = storyArtImg.getAttribute("src");
                    logger.info("이미지 추출 성공: " + imageUrl);
                }
            } catch (Exception ex) {
                logger.warning("이미지 추출 실패: " + ex.getMessage());
            }

            // DTO 필드 채우기
            dto.setContentId(contentId);
            dto.setTitle(title);
            dto.setType(contentType);
            dto.setUrl(url);
            dto.setDetailUrl(detailUrl);
            dto.setThumbnail(imageUrl); // 고화질 이미지를 썸네일로 사용
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