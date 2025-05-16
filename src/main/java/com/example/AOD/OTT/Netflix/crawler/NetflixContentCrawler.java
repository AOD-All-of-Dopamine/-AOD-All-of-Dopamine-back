package com.example.AOD.OTT.Netflix.crawler;

import com.example.AOD.OTT.Netflix.dto.NetflixContentDTO;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.bonigarcia.wdm.WebDriverManager;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
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
    private String profileName;
    private String cookieFilePath;

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

    // 생성자 수정
    public NetflixContentCrawler(String email, String password, WebDriver driver) {
        this.email = email;
        this.password = password;
        this.driver = driver;
        this.profileName = System.getenv("NETFLIX_PROFILE_NAME");
        // 쿠키 파일 경로 설정
        this.cookieFilePath = "netflix_cookies.json";

        logger.info("넷플릭스 크롤러 초기화 - 프로필: " +
                (profileName != null && !profileName.isEmpty() ? profileName : "기본값(첫 번째 프로필)"));
    }

    // 로그인 메서드 수정
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

            // 로그인 성공 확인
            boolean loginSuccess = driver.getCurrentUrl().contains("browse") || driver.getCurrentUrl().contains("profile");
            logger.info("로그인 " + (loginSuccess ? "성공" : "실패") + ": " + driver.getCurrentUrl());

            // 로그인 성공했고 프로필 선택 페이지가 나타나면
            if (loginSuccess && driver.getCurrentUrl().contains("profile")) {
                return selectProfile(); // 프로필 선택 수행
            }

            return loginSuccess;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "로그인 중 오류", e);
            return false;
        }
    }

    public boolean loginWithCookies() {
        try {
            // 쿠키 파일이 존재하는지 확인
            File cookieFile = new File(cookieFilePath);
            if (cookieFile.exists()) {
                // 쿠키 파일 로드 시도
                if (loadCookies()) {
                    // 쿠키 로드 후 인증 확인
                    driver.get(baseUrl + "/browse");
                    Thread.sleep(5000);

                    logger.info("쿠키 로드 후 URL: " + driver.getCurrentUrl());

                    // 로그인 성공 확인
                    if (!driver.getCurrentUrl().contains("/login")) {
                        if (driver.findElements(By.className("list-profiles")).isEmpty()) {
                            logger.info("저장된 쿠키로 로그인 성공(프로필 게이트 없음)");
                            return true;               // ← 이 return 은 그대로 두고,
                        }
                        // 게이트가 있으면 프로필 선택 + 새 쿠키 저장
                        logger.info("게이트 발견 → 프로필 재선택");
                        if (!selectProfile()) {
                            logger.warning("프로필 재선택 실패");
                            return false;
                        }
                        saveCookies();
                        return true;                   // 여기서 최종 성공 반환
                    }

                    logger.warning("쿠키 인증 실패, 일반 로그인 시도");
                }
            }

            if (driver.getCurrentUrl().contains("/browse")) {
                // URL은 /browse 지만, 프로필 오버레이가 떠 있으면 다시 선택
                if (driver.findElements(By.className("list-profiles")).size() > 0) {
                    logger.info("프로필 게이트가 /browse 화면 위에 존재 → 프로필 재선택 시도");
                    if (!selectProfile()) {
                        logger.warning("프로필 재선택 실패");
                        return false;
                    }
                    saveCookies();            // 새 쿠키에 프로필 GUID 포함
                }
            }

            // 일반 로그인 시도
            return performFullLogin();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "로그인 중 오류", e);
            return false;
        }


    }

    private boolean performFullLogin() {
        try {
            // 로그인
            driver.get(baseUrl + "/login");
            Thread.sleep(3000);

            WebElement emailField = new WebDriverWait(driver, Duration.ofSeconds(10))
                    .until(ExpectedConditions.presenceOfElementLocated(By.name("userLoginId")));
            emailField.clear();
            emailField.sendKeys(email);

            WebElement passwordField = driver.findElement(By.name("password"));
            passwordField.clear();
            passwordField.sendKeys(password);

            passwordField.sendKeys(Keys.ENTER);
            Thread.sleep(5000);

            logger.info("로그인 시도 후 URL: " + driver.getCurrentUrl());

            // 로그인 성공 확인
            boolean loginSuccess = !driver.getCurrentUrl().contains("/login");

            // 프로필 선택 필요한 경우
            if (loginSuccess && driver.getCurrentUrl().contains("/profile")) {
                loginSuccess = selectProfile();
            }

            // 로그인 및 프로필 선택 성공 시 쿠키 저장
            if (loginSuccess && !driver.getCurrentUrl().contains("/profile")) {
                saveCookies();
                logger.info("로그인 및 프로필 선택 성공. 쿠키 저장됨.");
            }

            return loginSuccess;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "전체 로그인 프로세스 중 오류", e);
            return false;
        }
    }

    private void saveCookies() {
        try {
            Set<Cookie> cookies = driver.manage().getCookies();
            Gson gson = new GsonBuilder().setPrettyPrinting().create();

            List<Map<String, Object>> cookieList = new ArrayList<>();
            for (Cookie cookie : cookies) {
                Map<String, Object> cookieMap = new HashMap<>();
                cookieMap.put("name", cookie.getName());
                cookieMap.put("value", cookie.getValue());
                cookieMap.put("domain", cookie.getDomain());
                cookieMap.put("path", cookie.getPath());

                // 만료 시간이 있는 경우
                if (cookie.getExpiry() != null) {
                    cookieMap.put("expiry", cookie.getExpiry().getTime());
                }

                cookieMap.put("secure", cookie.isSecure());
                cookieMap.put("httpOnly", cookie.isHttpOnly());

                cookieList.add(cookieMap);
            }

            String json = gson.toJson(cookieList);
            FileWriter writer = new FileWriter(cookieFilePath);
            writer.write(json);
            writer.close();

            logger.info("쿠키 저장 완료: " + cookies.size() + "개 쿠키");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "쿠키 저장 중 오류", e);
        }
    }

    /**
     * 저장된 쿠키 로드
     */
    private boolean loadCookies() {
        try {
            Gson gson = new Gson();
            File cookieFile = new File(cookieFilePath);
            if (!cookieFile.exists()) {
                logger.warning("쿠키 파일이 존재하지 않습니다: " + cookieFilePath);
                return false;
            }

            // 쿠키를 설정하기 전에 넷플릭스 도메인에 접근
            driver.get(baseUrl);
            Thread.sleep(1000);

            String json = new String(Files.readAllBytes(cookieFile.toPath()));
            List<Map<String, Object>> cookieList = gson.fromJson(json, new TypeToken<List<Map<String, Object>>>(){}.getType());

            if (cookieList == null || cookieList.isEmpty()) {
                logger.warning("로드된 쿠키가 없습니다.");
                return false;
            }

            for (Map<String, Object> cookieMap : cookieList) {
                String name = (String) cookieMap.get("name");
                String value = (String) cookieMap.get("value");
                String domain = (String) cookieMap.get("domain");
                String path = (String) cookieMap.get("path");

                // null 체크
                if (name == null || value == null) {
                    continue;
                }

                Cookie.Builder cookieBuilder = new Cookie.Builder(name, value);

                if (domain != null) {
                    cookieBuilder.domain(domain);
                }

                if (path != null) {
                    cookieBuilder.path(path);
                }

                // 만료 시간이 있는 경우
                if (cookieMap.containsKey("expiry")) {
                    Date expiry = new Date(((Number) cookieMap.get("expiry")).longValue());
                    cookieBuilder.expiresOn(expiry);
                }

                if (cookieMap.containsKey("secure") && (Boolean) cookieMap.get("secure")) {
                    cookieBuilder.isSecure(true);
                }

                if (cookieMap.containsKey("httpOnly") && (Boolean) cookieMap.get("httpOnly")) {
                    cookieBuilder.isHttpOnly(true);
                }

                try {
                    Cookie cookie = cookieBuilder.build();
                    driver.manage().addCookie(cookie);
                } catch (Exception e) {
                    logger.warning("쿠키 추가 실패: " + name + " - " + e.getMessage());
                }
            }

            logger.info("쿠키 로드 완료: " + cookieList.size() + "개 쿠키");
            return true;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "쿠키 로드 중 오류", e);
            return false;
        }
    }

    // 프로필 선택 메서드 추가
    private boolean selectProfile() {
        try {
            // 프로필 선택 페이지 대기
            logger.info("프로필 선택 페이지 대기 중...");
            WebElement profileContainer = new WebDriverWait(driver, Duration.ofSeconds(10))
                    .until(ExpectedConditions.presenceOfElementLocated(By.className("list-profiles")));

            // 프로필 선택 페이지 제목 확인
            try {
                WebElement profileTitle = driver.findElement(By.className("profile-gate-label"));
                if (profileTitle != null) {
                    logger.info("프로필 선택 페이지 제목: " + profileTitle.getText());
                }
            } catch (Exception e) {
                // 제목 요소를 찾지 못한 경우 무시
            }

            // 프로필 목록 확인
            WebElement profileList = driver.findElement(By.className("choose-profile"));
            List<WebElement> profiles = profileList.findElements(By.tagName("li"));

            logger.info("프로필 목록 찾음: " + profiles.size() + "개 항목");

            // 모든 프로필의 이름과 URL 추출
            List<Map<String, String>> profileData = new ArrayList<>();

            for (WebElement profile : profiles) {
                try {
                    // 프로필 추가 버튼은 건너뛰기
                    if (profile.findElements(By.cssSelector("[data-uia='profile-choices-create-button']")).size() > 0) {
                        logger.info("'프로필 추가' 버튼 항목은 건너뜁니다.");
                        continue;
                    }

                    // 프로필 링크와 이름 추출
                    WebElement profileLink = profile.findElement(By.className("profile-link"));
                    WebElement profileNameElement = profile.findElement(By.className("profile-name"));

                    String name = profileNameElement.getText().trim();
                    String url = profileLink.getAttribute("href");

                    Map<String, String> data = new HashMap<>();
                    data.put("name", name);
                    data.put("url", url);
                    profileData.add(data);

                    logger.info("프로필 발견: " + name + " (URL: " + url + ")");
                } catch (Exception e) {
                    logger.warning("프로필 정보 추출 중 오류: " + e.getMessage());
                }
            }

            // 프로필 선택
            if (profileData.isEmpty()) {
                logger.warning("프로필 정보를 추출할 수 없습니다.");
                return false;
            }

            // 환경 변수에 지정된 프로필 이름이 있으면 해당 프로필 선택
            Map<String, String> selectedProfile = null;

            if (profileName != null && !profileName.isEmpty()) {
                for (Map<String, String> profile : profileData) {
                    if (profileName.equals(profile.get("name"))) {
                        selectedProfile = profile;
                        logger.info("지정된 프로필 '" + profileName + "'을 찾았습니다.");
                        break;
                    }
                }
            }

            // 지정된 프로필을 찾지 못했거나 지정되지 않은 경우 첫 번째 프로필 선택
            if (selectedProfile == null && !profileData.isEmpty()) {
                selectedProfile = profileData.get(0);
                logger.info("첫 번째 프로필 '" + selectedProfile.get("name") + "'을 선택합니다.");
            }

            if (selectedProfile == null) {
                logger.warning("선택할 프로필이 없습니다.");
                return false;
            }

            logger.info("프로필 클릭 실행: " + selectedProfile.get("name"));
            WebElement toClick = null;
            for (WebElement li : profiles) {
                try {
                    WebElement nameEl = li.findElement(By.className("profile-name"));
                    if (nameEl.getText().trim().equals(selectedProfile.get("name"))) {
                        toClick = li.findElement(By.className("profile-link"));
                        break;
                    }
                } catch (NoSuchElementException ignore) {}
            }
            if (toClick == null) {
                logger.warning("목표 프로필 링크를 찾지 못했습니다.");
                return false;
            }

            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", toClick);

            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            wait.until(ExpectedConditions.or(
                    ExpectedConditions.urlContains("/browse"),
                    drv -> drv.manage().getCookieNamed("nflxProfileGuid") != null));

            if (driver.findElements(By.name("pinInput")).size() > 0) {     // PIN 잠금
                driver.findElement(By.name("pinInput"))
                        .sendKeys(System.getenv("NETFLIX_PIN") + Keys.ENTER);
                wait.until(ExpectedConditions.urlContains("/browse"));
            }

            logger.info("프로필 전환 후 URL: " + driver.getCurrentUrl());

            // 아직 프로필 페이지에 있는지 확인
            if (driver.getCurrentUrl().contains("/profile") ||
                    driver.findElements(By.className("list-profiles")).size() > 0) {

                logger.warning("프로필 선택 후에도 여전히 프로필 페이지에 있습니다!");

                // 마지막 시도: JavaScript로 클릭
                try {
                    logger.info("JavaScript로 프로필 클릭 시도");
                    WebElement profileList2 = driver.findElement(By.className("choose-profile"));
                    List<WebElement> profiles2 = profileList2.findElements(By.tagName("li"));

                    for (WebElement profile : profiles2) {
                        try {
                            WebElement nameElement = profile.findElement(By.className("profile-name"));
                            if (nameElement != null && selectedProfile.get("name").equals(nameElement.getText().trim())) {
                                WebElement profileLink = profile.findElement(By.className("profile-link"));
                                if (profileLink != null) {
                                    ((JavascriptExecutor) driver).executeScript("arguments[0].click();", profileLink);
                                    Thread.sleep(5000);
                                    break;
                                }
                            }
                        } catch (Exception e) {
                            // 개별 프로필 처리 중 오류는 무시
                        }
                    }

                    logger.info("JavaScript 클릭 후 URL: " + driver.getCurrentUrl());
                } catch (Exception e) {
                    logger.warning("JavaScript 클릭 시도 중 오류: " + e.getMessage());
                }
            }

            // 최종 확인
            boolean success = !driver.getCurrentUrl().contains("/profile") &&
                    driver.findElements(By.className("list-profiles")).isEmpty();

            if (success) {
                logger.info("프로필 선택 성공: " + selectedProfile.get("name"));
                saveCookies();                // ← 추가: 프로필 GUID 포함해 덮어쓰기
            } else {
                logger.warning("프로필 선택이 완료되지 않았습니다.");

                // 마지막 시도: 메인 페이지로 직접 이동
                driver.get(baseUrl + "/browse");
                Thread.sleep(3000);
                logger.info("메인 페이지로 직접 이동: " + driver.getCurrentUrl());

                return !driver.getCurrentUrl().contains("/profile");
            }



            return success;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "프로필 선택 중 오류 발생", e);
            return false;
        }
    }
    /**
     * 크롤링 수행, NetflixContentDTO 리스트 반환
     */
    public List<NetflixContentDTO> crawl() {
        List<NetflixContentDTO> results = new ArrayList<>();

        try {
            // 쿠키 기반 로그인
            if (!loginWithCookies()) {
                logger.warning("로그인 실패. 크롤링을 중단합니다.");
                return results;
            }

            // 시리즈 목록 페이지 진입
            driver.get(seriesUrl);
            Thread.sleep(3000);

            // 현재 페이지 확인
            if (driver.getCurrentUrl().contains("/profile")) {
                logger.warning("시리즈 페이지 접근 후 프로필 페이지 감지. 다시 로그인 시도.");
                if (!performFullLogin()) {
                    logger.warning("재로그인 실패. 크롤링을 중단합니다.");
                    return results;
                }
                driver.get(seriesUrl);
                Thread.sleep(3000);
            }

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