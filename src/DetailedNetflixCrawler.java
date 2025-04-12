import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * 세부 정보 수집을 포함한 넷플릭스 크롤러
 * 파이썬 코드의 get_content_details 메소드 로직 구현
 */
public class DetailedNetflixCrawler {
    private static final Logger logger = Logger.getLogger("NetflixCrawler");

    // 기본 URL
    private final String baseUrl = "https://www.netflix.com";
    private final String seriesUrl = "https://www.netflix.com/browse/genre/83?so=su";

    // 인증 정보
    private String email;
    private String password;

    // 웹드라이버
    private WebDriver driver;

    // 수집된 콘텐츠 데이터
    private Map<String, Map<String, Object>> contentData = new HashMap<>();

    // 랜덤 대기 시간
    private Random random = new Random();

    // 제한 항목 수 (테스트용)
    private final int MAX_ITEMS = 2;

    // 데이터베이스 사용 여부
    private final boolean useDatabase = false;

    /**
     * 로깅 설정
     */
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

    /**
     * 생성자
     */
    public DetailedNetflixCrawler(String email, String password) {
        this.email = email;
        this.password = password;

        // WebDriverManager를 사용하여 크롬드라이버 자동 설정
        WebDriverManager.chromedriver().setup();
    }

    /**
     * 크롤러 실행
     */
    public void run() {
        try {
            // 1. 드라이버 설정
            setupDriver();

            // 2. 로그인
            if (!login()) {
                logger.severe("로그인 실패로 크롤링 종료");
                cleanup();
                return;
            }

            logger.info("로그인 성공! 크롤링 시작");

            // 3. 데이터베이스 준비 (사용하는 경우)
            if (useDatabase) {
                Connection connection = getDbConnection();
                if (connection != null) {
                    createTablesIfNotExist(connection);
                    connection.close();
                }
            }

            // 4. 세부 정보를 포함한 크롤링
            crawlContentWithDetails();

            // 5. 로그 및 결과 저장
            logger.info("크롤링 완료: 총 " + contentData.size() + "개 콘텐츠 수집");
            saveResultsToJson();

        } catch (Exception e) {
            logger.log(Level.SEVERE, "크롤링 중 오류 발생: " + e.getMessage(), e);
            e.printStackTrace();
        } finally {
            cleanup();
        }
    }

    /**
     * WebDriver 설정
     */
    private void setupDriver() {
        try {
            ChromeOptions options = new ChromeOptions();
            // options.addArguments("--headless");  // 필요시 헤드리스 모드 활성화
            options.addArguments("--no-sandbox");
            options.addArguments("--disable-dev-shm-usage");
            options.addArguments("--window-size=1920,1080");
            options.addArguments("--user-agent=Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15");

            driver = new ChromeDriver(options);
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
            logger.info("WebDriver 설정 완료");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "WebDriver 설정 실패: " + e.getMessage(), e);
            throw new RuntimeException("WebDriver 설정 실패", e);
        }
    }

    /**
     * 넷플릭스 로그인
     */
    private boolean login() {
        try {
            driver.get(baseUrl + "/login");
            Thread.sleep(3000);

            // 이메일 입력
            WebElement emailField = new WebDriverWait(driver, Duration.ofSeconds(10))
                    .until(ExpectedConditions.presenceOfElementLocated(By.name("userLoginId")));
            emailField.clear();
            emailField.sendKeys(email);
            logger.info("이메일 입력 완료");

            // 비밀번호 입력
            WebElement passwordField = new WebDriverWait(driver, Duration.ofSeconds(10))
                    .until(ExpectedConditions.presenceOfElementLocated(By.name("password")));
            passwordField.clear();
            passwordField.sendKeys(password);
            logger.info("비밀번호 입력 완료");

            // 로그인 버튼 클릭 - 여러 방법 시도
            try {
                // 방법 1: 텍스트로 버튼 찾기
                List<WebElement> buttons = driver.findElements(By.tagName("button"));
                for (WebElement button : buttons) {
                    String buttonText = button.getText();
                    if (buttonText.equals("로그인") || buttonText.equals("로그인하기") ||
                            buttonText.equalsIgnoreCase("Login") || buttonText.equalsIgnoreCase("Sign In")) {
                        logger.info("로그인 버튼 찾음 (텍스트 매칭): " + buttonText);
                        button.click();
                        Thread.sleep(5000);
                        break;
                    }
                }
            } catch (Exception e) {
                logger.warning("텍스트로 로그인 버튼 찾기 실패: " + e.getMessage());
                try {
                    // 방법 2: Enter 키 사용
                    logger.info("Enter 키로 로그인 시도");
                    passwordField.sendKeys(Keys.ENTER);
                    Thread.sleep(5000);
                } catch (Exception e2) {
                    logger.warning("Enter 키로 로그인 실패: " + e2.getMessage());
                }
            }

            // 프로필 선택 처리
            if (driver.getCurrentUrl().contains("profile") ||
                    (driver.getCurrentUrl().contains("browse") && !driver.getCurrentUrl().contains("genre"))) {

                logger.info("프로필 선택 페이지 감지");

                try {
                    // 첫 번째 프로필 클릭 시도
                    List<WebElement> profiles = driver.findElements(By.cssSelector(".profile-icon"));
                    if (!profiles.isEmpty()) {
                        profiles.get(0).click();
                        Thread.sleep(3000);
                    }
                } catch (Exception e) {
                    logger.warning("프로필 선택 실패: " + e.getMessage());
                }
            }

            // 로그인 성공 확인
            boolean success = driver.getCurrentUrl().contains("browse");
            if (success) {
                logger.info("로그인 성공: " + driver.getCurrentUrl());
            } else {
                logger.severe("로그인 실패: " + driver.getCurrentUrl());
            }
            return success;

        } catch (Exception e) {
            logger.log(Level.SEVERE, "로그인 프로세스 중 예외 발생: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * 콘텐츠 항목 클래스
     */
    private static class ContentItem {
        private String name;
        private String url;
        private String imageUrl;
        private String contentId;

        public ContentItem(String name, String url, String imageUrl) {
            this.name = name;
            this.url = url;
            this.imageUrl = imageUrl;

            // URL에서 콘텐츠 ID 추출
            try {
                String[] parts = url.split("\\?")[0].split("/");
                this.contentId = parts[parts.length - 1];
            } catch (Exception e) {
                this.contentId = "unknown-" + System.currentTimeMillis();
            }
        }

        public String getName() { return name; }
        public String getUrl() { return url; }
        public String getImageUrl() { return imageUrl; }
        public String getContentId() { return contentId; }
        public String getDetailUrl() { return "https://www.netflix.com/title/" + contentId; }
    }

    /**
     * 세부 정보를 포함한 크롤링
     */
    private void crawlContentWithDetails() {
        try {
            // 1. 시리즈 페이지로 이동
            driver.get(seriesUrl);
            Thread.sleep(3000);
            logger.info("시리즈 페이지 로드됨: " + driver.getCurrentUrl());

            // 2. 간단한 스크롤 다운 (10번만)
            for (int i = 0; i < 5; i++) {
                ((JavascriptExecutor) driver).executeScript("window.scrollBy(0, 800);");
                Thread.sleep(1000);
                WebElement body = driver.findElement(By.tagName("body"));
                body.sendKeys(Keys.PAGE_DOWN);
                Thread.sleep(1000);
                logger.info("스크롤 다운 " + (i+1) + "/5");
            }

            // 3. 컨텐츠 항목 찾기
            List<WebElement> items;
            try {
                items = driver.findElements(By.cssSelector(".slider-item a.slider-refocus"));
                logger.info("선택자 '.slider-item a.slider-refocus'로 " + items.size() + "개 항목 발견");
            } catch (Exception e) {
                items = driver.findElements(By.cssSelector("a.slider-refocus"));
                logger.info("선택자 'a.slider-refocus'로 " + items.size() + "개 항목 발견");
            }

            if (items.isEmpty()) {
                logger.warning("콘텐츠 항목을 찾을 수 없습니다");
                return;
            }

            // 4. 제한된 수의 항목만 처리 (테스트용)
            int limit = Math.min(MAX_ITEMS, items.size());
            logger.info("총 " + items.size() + "개 중 " + limit + "개만 처리합니다");

            // 5. 각 항목을 ContentItem 객체로 변환
            List<ContentItem> contentItems = new ArrayList<>();
            for (int i = 0; i < limit; i++) {
                try {
                    WebElement item = items.get(i);

                    // URL 가져오기
                    String url = item.getAttribute("href");
                    if (url == null || url.isEmpty()) continue;

                    // 제목 추출
                    String name = null;
                    try {
                        name = item.getAttribute("aria-label");
                    } catch (Exception e) {
                        // 무시
                    }

                    if (name == null || name.isEmpty()) {
                        try {
                            WebElement imgElement = item.findElement(By.tagName("img"));
                            name = imgElement.getAttribute("alt");
                        } catch (Exception e) {
                            name = "Unknown Title " + (i+1);
                        }
                    }

                    // 이미지 URL 추출
                    String imageUrl = null;
                    try {
                        WebElement imgElement = item.findElement(By.tagName("img"));
                        imageUrl = imgElement.getAttribute("src");
                    } catch (Exception e) {
                        // 무시
                    }

                    contentItems.add(new ContentItem(name, url, imageUrl));
                } catch (Exception e) {
                    logger.warning("항목 #" + (i+1) + " 변환 중 오류: " + e.getMessage());
                }
            }

            // 6. 각 항목에 대한, 세부 정보 가져오기
            for (int i = 0; i < contentItems.size(); i++) {
                ContentItem item = contentItems.get(i);

                logger.info("항목 " + (i+1) + "/" + contentItems.size() + " 처리 중: " + item.getName());

                try {
                    // 세부 정보 가져오기 (파이썬 코드의 get_content_details 함수 로직 구현)
                    Map<String, Object> detailData = getContentDetails(item);

                    if (detailData != null) {
                        // 데이터베이스에 저장
                        if (useDatabase) {
                            saveContentToDb(detailData);
                        }

                        // 콘텐츠 데이터 캐싱
                        contentData.put(item.getContentId(), detailData);

                        logger.info("항목 " + (i+1) + "/" + contentItems.size() + " 세부 정보 처리 완료: " + item.getName());
                    }
                } catch (Exception e) {
                    logger.warning("항목 '" + item.getName() + "' 세부 정보 처리 중 오류: " + e.getMessage());
                    e.printStackTrace();
                }

                // 요청 간격 조절 (봇 감지 방지)
                Thread.sleep(randomSleep(1000, 2000));
            }

        } catch (Exception e) {
            logger.severe("크롤링 중 오류: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 콘텐츠 세부 정보 가져오기 (파이썬 코드 로직 기반)
     */
    private Map<String, Object> getContentDetails(ContentItem item) {
        try {
            String detailUrl = item.getDetailUrl();
            logger.info("콘텐츠 '" + item.getName() + "' 세부 정보 가져오기 시작: " + detailUrl);

            // 세부 정보 페이지로 이동
            driver.get(detailUrl);
            Thread.sleep(randomSleep(2000, 3000));  // 무작위 대기 시간

            // 콘텐츠 타입 결정
            String contentType = item.getUrl().toLowerCase().contains("series") ||
                    item.getUrl().toLowerCase().contains("show") ? "series" : "movie";

            // 상세 정보 수집
            String creator = null;
            List<String> actors = new ArrayList<>();
            List<String> genres = new ArrayList<>();
            List<String> features = new ArrayList<>();
            String maturityRating = null;
            String releaseYear = null;
            String description = null;

            // 설명 가져오기
            try {
                WebElement descriptionElement = new WebDriverWait(driver, Duration.ofSeconds(5))
                        .until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(".title-info-synopsis")));
                description = descriptionElement.getText().trim();
            } catch (Exception e) {
                try {
                    WebElement descriptionElement = driver.findElement(By.cssSelector(".preview-modal-synopsis"));
                    description = descriptionElement.getText().trim();
                } catch (Exception e2) {
                    logger.fine("설명 정보를 찾을 수 없습니다: " + e2.getMessage());
                }
            }

            // 메타데이터 정보 가져오기
            try {
                List<WebElement> metadataItems = driver.findElements(By.cssSelector(".title-info-metadata-item"));
                for (WebElement item1 : metadataItems) {
                    String text = item1.getText().trim();
                    if (text.endsWith("%")) {  // 일치율인 경우
                        continue;
                    } else if (text.matches("\\d{4}")) {  // 연도인 경우
                        releaseYear = text;
                    } else if (Arrays.asList("TV-14", "TV-MA", "PG-13", "R", "TV-Y7", "TV-Y", "TV-G", "TV-PG", "G", "PG")
                            .contains(text)) {  // 등급인 경우
                        maturityRating = text;
                    }
                }
            } catch (Exception e) {
                logger.fine("메타데이터 정보를 찾을 수 없습니다: " + e.getMessage());
            }

            // 태그 정보 가져오기
            try {
                List<WebElement> tagGroups = driver.findElements(By.cssSelector(".previewModal--tags"));

                for (WebElement tagGroup : tagGroups) {
                    try {
                        WebElement labelElement = tagGroup.findElement(By.cssSelector(".previewModal--tags-label"));
                        String label = labelElement.getText().trim();

                        List<WebElement> tagItems = tagGroup.findElements(By.cssSelector(".tag-item"));
                        List<String> tagTexts = new ArrayList<>();
                        for (WebElement tag : tagItems) {
                            tagTexts.add(tag.getText().trim());
                        }

                        if (label.contains("출연:") || label.contains("주연:") || label.contains("Cast:")) {
                            actors = tagTexts;
                        } else if (label.contains("크리에이터:") || label.contains("Creators:")) {
                            creator = String.join(", ", tagTexts);
                        } else if (label.contains("장르:") || label.contains("Genres:")) {
                            genres = tagTexts;
                        } else if (label.contains("특징:") || label.contains("Features:") || label.contains("이 시리즈는:")) {
                            features = tagTexts;
                        }
                    } catch (Exception tagError) {
                        logger.warning("태그 그룹 처리 중 오류: " + tagError.getMessage());
                    }
                }
            } catch (Exception e) {
                logger.warning("태그 정보 가져오기 실패: " + e.getMessage());
            }

            // 결과 데이터 구성
            Map<String, Object> detailData = new HashMap<>();
            detailData.put("id", item.getContentId());
            detailData.put("title", item.getName());
            detailData.put("type", contentType);
            detailData.put("url", item.getUrl());
            detailData.put("detail_url", detailUrl);
            detailData.put("thumbnail", item.getImageUrl());
            detailData.put("description", description);
            detailData.put("creator", creator);
            detailData.put("actors", actors);
            detailData.put("genres", genres);
            detailData.put("features", features);
            detailData.put("maturity_rating", maturityRating);
            detailData.put("release_year", releaseYear);
            detailData.put("crawled_at", LocalDateTime.now().toString());

            logger.info("콘텐츠 '" + item.getName() + "' 세부 정보 수집 완료");

            // 화면 캡처 (디버깅 용도)
            //takeScreenshot("detail_" + item.getContentId() + ".png");

            return detailData;

        } catch (Exception e) {
            logger.log(Level.WARNING, "콘텐츠 '" + item.getName() + "' 세부 정보 가져오기 실패: " + e.getMessage(), e);
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 스크린샷 저장 (디버깅 용도)
     */
    private void takeScreenshot(String filename) {
        try {
            // JavascriptExecutor를 사용하여 스크린샷 캡쳐
            String script = "return document.body.parentNode.outerHTML";
            String html = (String) ((JavascriptExecutor) driver).executeScript(script);

            // 파일에 저장
            Files.write(Paths.get(filename.replace(".png", ".html")), html.getBytes());
            logger.info("HTML 캡처 저장: " + filename);
        } catch (Exception e) {
            logger.warning("스크린샷 저장 실패: " + e.getMessage());
        }
    }

    /**
     * H2 데이터베이스 연결
     */
    private Connection getDbConnection() {
        try {
            // H2 인메모리 데이터베이스 연결
            String url = "jdbc:h2:mem:netflixdb;DB_CLOSE_DELAY=-1";
            String user = "sa";
            String password = "";

            Connection connection = DriverManager.getConnection(url, user, password);

            if (connection != null) {
                logger.fine("데이터베이스 연결 성공");
                return connection;
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "데이터베이스 연결 오류: " + e.getMessage(), e);
        }
        return null;
    }

    /**
     * 필요한 테이블 생성
     */
    private void createTablesIfNotExist(Connection connection) {
        try {
            Statement stmt = connection.createStatement();

            // 콘텐츠 테이블 생성
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS contents (" +
                            "content_id VARCHAR(255) PRIMARY KEY, " +
                            "title VARCHAR(255) NOT NULL, " +
                            "type VARCHAR(50) NOT NULL, " +
                            "synopsis TEXT, " +
                            "thumbnail_url TEXT, " +
                            "maturity_rating VARCHAR(20), " +
                            "release_year VARCHAR(4), " +
                            "available BOOLEAN DEFAULT TRUE, " +
                            "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                            "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                            ")"
            );

            // 장르 테이블 생성
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS genres (" +
                            "genre_id INT AUTO_INCREMENT PRIMARY KEY, " +
                            "genre_name VARCHAR(100) NOT NULL UNIQUE" +
                            ")"
            );

            // 콘텐츠-장르 관계 테이블
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS content_genres (" +
                            "id INT AUTO_INCREMENT PRIMARY KEY, " +
                            "content_id VARCHAR(255) NOT NULL, " +
                            "genre_id INT NOT NULL, " +
                            "UNIQUE(content_id, genre_id)" +
                            ")"
            );

            // 인물 테이블
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS persons (" +
                            "person_id INT AUTO_INCREMENT PRIMARY KEY, " +
                            "person_name VARCHAR(255) NOT NULL UNIQUE" +
                            ")"
            );

            // 역할 테이블
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS roles (" +
                            "role_id INT AUTO_INCREMENT PRIMARY KEY, " +
                            "role_name VARCHAR(50) NOT NULL UNIQUE" +
                            ")"
            );

            // 콘텐츠-인물-역할 관계 테이블
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS content_persons (" +
                            "id INT AUTO_INCREMENT PRIMARY KEY, " +
                            "content_id VARCHAR(255) NOT NULL, " +
                            "person_id INT NOT NULL, " +
                            "role_id INT NOT NULL, " +
                            "UNIQUE(content_id, person_id, role_id)" +
                            ")"
            );

            // 크롤링 로그 테이블
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS crawl_logs (" +
                            "id INT AUTO_INCREMENT PRIMARY KEY, " +
                            "start_time TIMESTAMP, " +
                            "end_time TIMESTAMP, " +
                            "status VARCHAR(50), " +
                            "new_contents INT" +
                            ")"
            );

            // 콘텐츠 변경 이력 테이블
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS content_changes (" +
                            "id INT AUTO_INCREMENT PRIMARY KEY, " +
                            "content_id VARCHAR(255) NOT NULL, " +
                            "change_type VARCHAR(50) NOT NULL, " +
                            "new_data TEXT, " +
                            "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                            ")"
            );

            // 기본 역할 추가
            try {
                stmt.execute("INSERT INTO roles (role_name) VALUES ('actor')");
                stmt.execute("INSERT INTO roles (role_name) VALUES ('creator')");
            } catch (SQLException e) {
                // 이미 존재하면 무시
            }

            logger.info("필요한 테이블 생성 완료");

        } catch (Exception e) {
            logger.log(Level.SEVERE, "테이블 생성 실패: " + e.getMessage(), e);
        }
    }

    /**
     * 콘텐츠 정보 데이터베이스 저장
     */
    @SuppressWarnings("unchecked")
    private boolean saveContentToDb(Map<String, Object> content) {
        if (content == null) {
            return false;
        }

        Connection connection = null;
        try {
            connection = getDbConnection();
            if (connection == null) {
                logger.severe("데이터베이스 연결 실패");
                return false;
            }

            // 자동 커밋 비활성화
            connection.setAutoCommit(false);

            // 콘텐츠 기본 정보 저장
            String contentId = (String) content.get("id");
            String title = (String) content.get("title");
            String contentType = (String) content.get("type");
            String synopsis = (String) content.get("description");
            String thumbnailUrl = (String) content.get("thumbnail");
            String maturityRating = (String) content.get("maturity_rating");
            String releaseYear = (String) content.get("release_year");

            // 콘텐츠 테이블에 저장
            PreparedStatement stmt = connection.prepareStatement(
                    "MERGE INTO contents KEY(content_id) VALUES(?, ?, ?, ?, ?, ?, ?, TRUE, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP())"
            );

            stmt.setString(1, contentId);
            stmt.setString(2, title != null ? title : "");
            stmt.setString(3, contentType != null ? contentType : "");
            stmt.setString(4, synopsis != null ? synopsis : "");
            stmt.setString(5, thumbnailUrl != null ? thumbnailUrl : "");
            stmt.setString(6, maturityRating != null ? maturityRating : "");
            stmt.setString(7, releaseYear != null ? releaseYear : "");

            stmt.executeUpdate();

            // 장르 정보 저장
            List<String> genres = (List<String>) content.get("genres");
            if (genres != null) {
                for (String genreName : genres) {
                    if (genreName == null || genreName.isEmpty()) {
                        continue;
                    }

                    // 장르 테이블에 저장
                    PreparedStatement genreStmt = connection.prepareStatement(
                            "MERGE INTO genres KEY(genre_name) VALUES(?)"
                    );
                    genreStmt.setString(1, genreName);
                    genreStmt.executeUpdate();

                    // 장르 ID 조회
                    PreparedStatement genreIdStmt = connection.prepareStatement(
                            "SELECT genre_id FROM genres WHERE genre_name = ?"
                    );
                    genreIdStmt.setString(1, genreName);
                    ResultSet genreRs = genreIdStmt.executeQuery();

                    if (genreRs.next()) {
                        int genreId = genreRs.getInt(1);

                        // 콘텐츠-장르 관계 저장
                        PreparedStatement contentGenreStmt = connection.prepareStatement(
                                "MERGE INTO content_genres KEY(content_id, genre_id) VALUES(?, ?)"
                        );
                        contentGenreStmt.setString(1, contentId);
                        contentGenreStmt.setInt(2, genreId);
                        contentGenreStmt.executeUpdate();
                    }
                }
            }

            // 제작자 정보 저장
            String creator = (String) content.get("creator");
            if (creator != null && !creator.isEmpty()) {
                // 인물 테이블에 저장
                PreparedStatement personStmt = connection.prepareStatement(
                        "MERGE INTO persons KEY(person_name) VALUES(?)"
                );
                personStmt.setString(1, creator);
                personStmt.executeUpdate();

                // 인물 ID 조회
                PreparedStatement personIdStmt = connection.prepareStatement(
                        "SELECT person_id FROM persons WHERE person_name = ?"
                );
                personIdStmt.setString(1, creator);
                ResultSet personRs = personIdStmt.executeQuery();

                if (personRs.next()) {
                    int personId = personRs.getInt(1);

                    // 역할 ID 조회 (creator)
                    PreparedStatement roleIdStmt = connection.prepareStatement(
                            "SELECT role_id FROM roles WHERE role_name = 'creator'"
                    );
                    ResultSet roleRs = roleIdStmt.executeQuery();

                    if (roleRs.next()) {
                        int roleId = roleRs.getInt(1);

                        // 콘텐츠-인물-역할 관계 저장
                        PreparedStatement contentPersonStmt = connection.prepareStatement(
                                "MERGE INTO content_persons KEY(content_id, person_id, role_id) VALUES(?, ?, ?)"
                        );
                        contentPersonStmt.setString(1, contentId);
                        contentPersonStmt.setInt(2, personId);
                        contentPersonStmt.setInt(3, roleId);
                        contentPersonStmt.executeUpdate();
                    }
                }
            }

            // 배우 정보 저장
            List<String> actors = (List<String>) content.get("actors");
            if (actors != null) {
                for (String actorName : actors) {
                    if (actorName == null || actorName.isEmpty()) {
                        continue;
                    }

                    // 인물 테이블에 저장
                    PreparedStatement personStmt = connection.prepareStatement(
                            "MERGE INTO persons KEY(person_name) VALUES(?)"
                    );
                    personStmt.setString(1, actorName);
                    personStmt.executeUpdate();

                    // 인물 ID 조회
                    PreparedStatement personIdStmt = connection.prepareStatement(
                            "SELECT person_id FROM persons WHERE person_name = ?"
                    );
                    personIdStmt.setString(1, actorName);
                    ResultSet personRs = personIdStmt.executeQuery();

                    if (personRs.next()) {
                        int personId = personRs.getInt(1);

                        // 역할 ID 조회 (actor)
                        PreparedStatement roleIdStmt = connection.prepareStatement(
                                "SELECT role_id FROM roles WHERE role_name = 'actor'"
                        );
                        ResultSet roleRs = roleIdStmt.executeQuery();

                        if (roleRs.next()) {
                            int roleId = roleRs.getInt(1);

                            // 콘텐츠-인물-역할 관계 저장
                            PreparedStatement contentPersonStmt = connection.prepareStatement(
                                    "MERGE INTO content_persons KEY(content_id, person_id, role_id) VALUES(?, ?, ?)"
                            );
                            contentPersonStmt.setString(1, contentId);
                            contentPersonStmt.setInt(2, personId);
                            contentPersonStmt.setInt(3, roleId);
                            contentPersonStmt.executeUpdate();
                        }
                    }
                }
            }

            // 변경 이력 저장
            PreparedStatement changeStmt = connection.prepareStatement(
                    "INSERT INTO content_changes (content_id, change_type, new_data) VALUES(?, ?, ?)"
            );
            changeStmt.setString(1, contentId);
            changeStmt.setString(2, "added");
            changeStmt.setString(3, content.toString());
            changeStmt.executeUpdate();

            // 커밋
            connection.commit();
            logger.info("콘텐츠 '" + title + "' 데이터베이스 저장 완료");
            return true;

        } catch (Exception e) {
            logger.warning("콘텐츠 데이터 저장 실패: " + e.getMessage());

            if (connection != null) {
                try {
                    connection.rollback();
                } catch (SQLException ex) {
                    logger.warning("롤백 실패: " + ex.getMessage());
                }
            }
            return false;
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException e) {
                    logger.warning("데이터베이스 연결 닫기 실패: " + e.getMessage());
                }
            }
        }
    }

    /**
     * 결과를 JSON 파일로 저장
     */
    private void saveResultsToJson() {
        try {
            StringBuilder json = new StringBuilder("{\n");

            int count = 0;
            for (Map.Entry<String, Map<String, Object>> entry : contentData.entrySet()) {
                String contentId = entry.getKey();
                Map<String, Object> content = entry.getValue();

                json.append("  \"").append(contentId).append("\": {\n");

                // 기본 정보
                json.append("    \"title\": \"").append(content.get("title")).append("\",\n");
                json.append("    \"type\": \"").append(content.get("type")).append("\",\n");
                json.append("    \"url\": \"").append(content.get("url")).append("\",\n");

                // 추가 정보
                if (content.get("description") != null) {
                    json.append("    \"description\": \"").append(cleanJsonString((String)content.get("description"))).append("\",\n");
                }

                if (content.get("creator") != null) {
                    json.append("    \"creator\": \"").append(content.get("creator")).append("\",\n");
                }

                if (content.get("maturity_rating") != null) {
                    json.append("    \"maturity_rating\": \"").append(content.get("maturity_rating")).append("\",\n");
                }

                if (content.get("release_year") != null) {
                    json.append("    \"release_year\": \"").append(content.get("release_year")).append("\",\n");
                }

                // 배열 정보
                @SuppressWarnings("unchecked")
                List<String> actors = (List<String>) content.get("actors");
                if (actors != null && !actors.isEmpty()) {
                    json.append("    \"actors\": [");
                    for (int i = 0; i < actors.size(); i++) {
                        json.append("\"").append(actors.get(i)).append("\"");
                        if (i < actors.size() - 1) {
                            json.append(", ");
                        }
                    }
                    json.append("],\n");
                }

                @SuppressWarnings("unchecked")
                List<String> genres = (List<String>) content.get("genres");
                if (genres != null && !genres.isEmpty()) {
                    json.append("    \"genres\": [");
                    for (int i = 0; i < genres.size(); i++) {
                        json.append("\"").append(genres.get(i)).append("\"");
                        if (i < genres.size() - 1) {
                            json.append(", ");
                        }
                    }
                    json.append("],\n");
                }

                // 썸네일 (항상 마지막에 위치)
                json.append("    \"thumbnail\": \"").append(content.get("thumbnail")).append("\"\n");

                json.append("  }");

                if (++count < contentData.size()) {
                    json.append(",");
                }
                json.append("\n");
            }

            json.append("}");

            // 파일로 저장
            try (FileWriter file = new FileWriter("netflix_content.json")) {
                file.write(json.toString());
            }

            logger.info("크롤링 결과를 JSON 파일로 저장 완료 (netflix_content.json)");

        } catch (Exception e) {
            logger.warning("결과 저장 중 오류: " + e.getMessage());
        }
    }

    /**
     * JSON 문자열에서 특수 문자 처리
     */
    private String cleanJsonString(String input) {
        if (input == null) return "";
        return input.replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * 무작위 대기 시간 생성
     */
    private int randomSleep(int min, int max) {
        return min + random.nextInt(max - min);
    }

    /**
     * 리소스 정리
     */
    private void cleanup() {
        if (driver != null) {
            driver.quit();
            logger.info("WebDriver 종료");
        }
    }

    /**
     * 메인 메소드
     */
    public static void main(String[] args) {
        logger.info("세부 정보 수집 Netflix 크롤러 시작");

        try {
            // 로그인 정보 설정 (실제 사용 시 변경 필요)
            String email = "email";
            String password = "password";

            // 크롤러 생성 및 실행
            DetailedNetflixCrawler crawler = new DetailedNetflixCrawler(email, password);
            crawler.run();

        } catch (Exception e) {
            logger.log(Level.SEVERE, "크롤러 실행 중 오류 발생: " + e.getMessage(), e);
        }
    }
}
import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * 세부 정보 수집을 포함한 넷플릭스 크롤러
 * 파이썬 코드의 get_content_details 메소드 로직 구현
 */
public class DetailedNetflixCrawler {
    private static final Logger logger = Logger.getLogger("NetflixCrawler");

    // 기본 URL
    private final String baseUrl = "https://www.netflix.com";
    private final String seriesUrl = "https://www.netflix.com/browse/genre/83?so=su";

    // 인증 정보
    private String email;
    private String password;

    // 웹드라이버
    private WebDriver driver;

    // 수집된 콘텐츠 데이터
    private Map<String, Map<String, Object>> contentData = new HashMap<>();

    // 랜덤 대기 시간
    private Random random = new Random();

    // 제한 항목 수 (테스트용)
    private final int MAX_ITEMS = 2;

    // 데이터베이스 사용 여부
    private final boolean useDatabase = false;

    /**
     * 로깅 설정
     */
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

    /**
     * 생성자
     */
    public DetailedNetflixCrawler(String email, String password) {
        this.email = email;
        this.password = password;

        // WebDriverManager를 사용하여 크롬드라이버 자동 설정
        WebDriverManager.chromedriver().setup();
    }

    /**
     * 크롤러 실행
     */
    public void run() {
        try {
            // 1. 드라이버 설정
            setupDriver();

            // 2. 로그인
            if (!login()) {
                logger.severe("로그인 실패로 크롤링 종료");
                cleanup();
                return;
            }

            logger.info("로그인 성공! 크롤링 시작");

            // 3. 데이터베이스 준비 (사용하는 경우)
            if (useDatabase) {
                Connection connection = getDbConnection();
                if (connection != null) {
                    createTablesIfNotExist(connection);
                    connection.close();
                }
            }

            // 4. 세부 정보를 포함한 크롤링
            crawlContentWithDetails();

            // 5. 로그 및 결과 저장
            logger.info("크롤링 완료: 총 " + contentData.size() + "개 콘텐츠 수집");
            saveResultsToJson();

        } catch (Exception e) {
            logger.log(Level.SEVERE, "크롤링 중 오류 발생: " + e.getMessage(), e);
            e.printStackTrace();
        } finally {
            cleanup();
        }
    }

    /**
     * WebDriver 설정
     */
    private void setupDriver() {
        try {
            ChromeOptions options = new ChromeOptions();
            // options.addArguments("--headless");  // 필요시 헤드리스 모드 활성화
            options.addArguments("--no-sandbox");
            options.addArguments("--disable-dev-shm-usage");
            options.addArguments("--window-size=1920,1080");
            options.addArguments("--user-agent=Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15");

            driver = new ChromeDriver(options);
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
            logger.info("WebDriver 설정 완료");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "WebDriver 설정 실패: " + e.getMessage(), e);
            throw new RuntimeException("WebDriver 설정 실패", e);
        }
    }

    /**
     * 넷플릭스 로그인
     */
    private boolean login() {
        try {
            driver.get(baseUrl + "/login");
            Thread.sleep(3000);

            // 이메일 입력
            WebElement emailField = new WebDriverWait(driver, Duration.ofSeconds(10))
                    .until(ExpectedConditions.presenceOfElementLocated(By.name("userLoginId")));
            emailField.clear();
            emailField.sendKeys(email);
            logger.info("이메일 입력 완료");

            // 비밀번호 입력
            WebElement passwordField = new WebDriverWait(driver, Duration.ofSeconds(10))
                    .until(ExpectedConditions.presenceOfElementLocated(By.name("password")));
            passwordField.clear();
            passwordField.sendKeys(password);
            logger.info("비밀번호 입력 완료");

            // 로그인 버튼 클릭 - 여러 방법 시도
            try {
                // 방법 1: 텍스트로 버튼 찾기
                List<WebElement> buttons = driver.findElements(By.tagName("button"));
                for (WebElement button : buttons) {
                    String buttonText = button.getText();
                    if (buttonText.equals("로그인") || buttonText.equals("로그인하기") ||
                            buttonText.equalsIgnoreCase("Login") || buttonText.equalsIgnoreCase("Sign In")) {
                        logger.info("로그인 버튼 찾음 (텍스트 매칭): " + buttonText);
                        button.click();
                        Thread.sleep(5000);
                        break;
                    }
                }
            } catch (Exception e) {
                logger.warning("텍스트로 로그인 버튼 찾기 실패: " + e.getMessage());
                try {
                    // 방법 2: Enter 키 사용
                    logger.info("Enter 키로 로그인 시도");
                    passwordField.sendKeys(Keys.ENTER);
                    Thread.sleep(5000);
                } catch (Exception e2) {
                    logger.warning("Enter 키로 로그인 실패: " + e2.getMessage());
                }
            }

            // 프로필 선택 처리
            if (driver.getCurrentUrl().contains("profile") ||
                    (driver.getCurrentUrl().contains("browse") && !driver.getCurrentUrl().contains("genre"))) {

                logger.info("프로필 선택 페이지 감지");

                try {
                    // 첫 번째 프로필 클릭 시도
                    List<WebElement> profiles = driver.findElements(By.cssSelector(".profile-icon"));
                    if (!profiles.isEmpty()) {
                        profiles.get(0).click();
                        Thread.sleep(3000);
                    }
                } catch (Exception e) {
                    logger.warning("프로필 선택 실패: " + e.getMessage());
                }
            }

            // 로그인 성공 확인
            boolean success = driver.getCurrentUrl().contains("browse");
            if (success) {
                logger.info("로그인 성공: " + driver.getCurrentUrl());
            } else {
                logger.severe("로그인 실패: " + driver.getCurrentUrl());
            }
            return success;

        } catch (Exception e) {
            logger.log(Level.SEVERE, "로그인 프로세스 중 예외 발생: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * 콘텐츠 항목 클래스
     */
    private static class ContentItem {
        private String name;
        private String url;
        private String imageUrl;
        private String contentId;

        public ContentItem(String name, String url, String imageUrl) {
            this.name = name;
            this.url = url;
            this.imageUrl = imageUrl;

            // URL에서 콘텐츠 ID 추출
            try {
                String[] parts = url.split("\\?")[0].split("/");
                this.contentId = parts[parts.length - 1];
            } catch (Exception e) {
                this.contentId = "unknown-" + System.currentTimeMillis();
            }
        }

        public String getName() { return name; }
        public String getUrl() { return url; }
        public String getImageUrl() { return imageUrl; }
        public String getContentId() { return contentId; }
        public String getDetailUrl() { return "https://www.netflix.com/title/" + contentId; }
    }

    /**
     * 세부 정보를 포함한 크롤링
     */
    private void crawlContentWithDetails() {
        try {
            // 1. 시리즈 페이지로 이동
            driver.get(seriesUrl);
            Thread.sleep(3000);
            logger.info("시리즈 페이지 로드됨: " + driver.getCurrentUrl());

            // 2. 간단한 스크롤 다운 (10번만)
            for (int i = 0; i < 5; i++) {
                ((JavascriptExecutor) driver).executeScript("window.scrollBy(0, 800);");
                Thread.sleep(1000);
                WebElement body = driver.findElement(By.tagName("body"));
                body.sendKeys(Keys.PAGE_DOWN);
                Thread.sleep(1000);
                logger.info("스크롤 다운 " + (i+1) + "/5");
            }

            // 3. 컨텐츠 항목 찾기
            List<WebElement> items;
            try {
                items = driver.findElements(By.cssSelector(".slider-item a.slider-refocus"));
                logger.info("선택자 '.slider-item a.slider-refocus'로 " + items.size() + "개 항목 발견");
            } catch (Exception e) {
                items = driver.findElements(By.cssSelector("a.slider-refocus"));
                logger.info("선택자 'a.slider-refocus'로 " + items.size() + "개 항목 발견");
            }

            if (items.isEmpty()) {
                logger.warning("콘텐츠 항목을 찾을 수 없습니다");
                return;
            }

            // 4. 제한된 수의 항목만 처리 (테스트용)
            int limit = Math.min(MAX_ITEMS, items.size());
            logger.info("총 " + items.size() + "개 중 " + limit + "개만 처리합니다");

            // 5. 각 항목을 ContentItem 객체로 변환
            List<ContentItem> contentItems = new ArrayList<>();
            for (int i = 0; i < limit; i++) {
                try {
                    WebElement item = items.get(i);

                    // URL 가져오기
                    String url = item.getAttribute("href");
                    if (url == null || url.isEmpty()) continue;

                    // 제목 추출
                    String name = null;
                    try {
                        name = item.getAttribute("aria-label");
                    } catch (Exception e) {
                        // 무시
                    }

                    if (name == null || name.isEmpty()) {
                        try {
                            WebElement imgElement = item.findElement(By.tagName("img"));
                            name = imgElement.getAttribute("alt");
                        } catch (Exception e) {
                            name = "Unknown Title " + (i+1);
                        }
                    }

                    // 이미지 URL 추출
                    String imageUrl = null;
                    try {
                        WebElement imgElement = item.findElement(By.tagName("img"));
                        imageUrl = imgElement.getAttribute("src");
                    } catch (Exception e) {
                        // 무시
                    }

                    contentItems.add(new ContentItem(name, url, imageUrl));
                } catch (Exception e) {
                    logger.warning("항목 #" + (i+1) + " 변환 중 오류: " + e.getMessage());
                }
            }

            // 6. 각 항목에 대한, 세부 정보 가져오기
            for (int i = 0; i < contentItems.size(); i++) {
                ContentItem item = contentItems.get(i);

                logger.info("항목 " + (i+1) + "/" + contentItems.size() + " 처리 중: " + item.getName());

                try {
                    // 세부 정보 가져오기 (파이썬 코드의 get_content_details 함수 로직 구현)
                    Map<String, Object> detailData = getContentDetails(item);

                    if (detailData != null) {
                        // 데이터베이스에 저장
                        if (useDatabase) {
                            saveContentToDb(detailData);
                        }

                        // 콘텐츠 데이터 캐싱
                        contentData.put(item.getContentId(), detailData);

                        logger.info("항목 " + (i+1) + "/" + contentItems.size() + " 세부 정보 처리 완료: " + item.getName());
                    }
                } catch (Exception e) {
                    logger.warning("항목 '" + item.getName() + "' 세부 정보 처리 중 오류: " + e.getMessage());
                    e.printStackTrace();
                }

                // 요청 간격 조절 (봇 감지 방지)
                Thread.sleep(randomSleep(1000, 2000));
            }

        } catch (Exception e) {
            logger.severe("크롤링 중 오류: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 콘텐츠 세부 정보 가져오기 (파이썬 코드 로직 기반)
     */
    private Map<String, Object> getContentDetails(ContentItem item) {
        try {
            String detailUrl = item.getDetailUrl();
            logger.info("콘텐츠 '" + item.getName() + "' 세부 정보 가져오기 시작: " + detailUrl);

            // 세부 정보 페이지로 이동
            driver.get(detailUrl);
            Thread.sleep(randomSleep(2000, 3000));  // 무작위 대기 시간

            // 콘텐츠 타입 결정
            String contentType = item.getUrl().toLowerCase().contains("series") ||
                    item.getUrl().toLowerCase().contains("show") ? "series" : "movie";

            // 상세 정보 수집
            String creator = null;
            List<String> actors = new ArrayList<>();
            List<String> genres = new ArrayList<>();
            List<String> features = new ArrayList<>();
            String maturityRating = null;
            String releaseYear = null;
            String description = null;

            // 설명 가져오기
            try {
                WebElement descriptionElement = new WebDriverWait(driver, Duration.ofSeconds(5))
                        .until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(".title-info-synopsis")));
                description = descriptionElement.getText().trim();
            } catch (Exception e) {
                try {
                    WebElement descriptionElement = driver.findElement(By.cssSelector(".preview-modal-synopsis"));
                    description = descriptionElement.getText().trim();
                } catch (Exception e2) {
                    logger.fine("설명 정보를 찾을 수 없습니다: " + e2.getMessage());
                }
            }

            // 메타데이터 정보 가져오기
            try {
                List<WebElement> metadataItems = driver.findElements(By.cssSelector(".title-info-metadata-item"));
                for (WebElement item1 : metadataItems) {
                    String text = item1.getText().trim();
                    if (text.endsWith("%")) {  // 일치율인 경우
                        continue;
                    } else if (text.matches("\\d{4}")) {  // 연도인 경우
                        releaseYear = text;
                    } else if (Arrays.asList("TV-14", "TV-MA", "PG-13", "R", "TV-Y7", "TV-Y", "TV-G", "TV-PG", "G", "PG")
                            .contains(text)) {  // 등급인 경우
                        maturityRating = text;
                    }
                }
            } catch (Exception e) {
                logger.fine("메타데이터 정보를 찾을 수 없습니다: " + e.getMessage());
            }

            // 태그 정보 가져오기
            try {
                List<WebElement> tagGroups = driver.findElements(By.cssSelector(".previewModal--tags"));

                for (WebElement tagGroup : tagGroups) {
                    try {
                        WebElement labelElement = tagGroup.findElement(By.cssSelector(".previewModal--tags-label"));
                        String label = labelElement.getText().trim();

                        List<WebElement> tagItems = tagGroup.findElements(By.cssSelector(".tag-item"));
                        List<String> tagTexts = new ArrayList<>();
                        for (WebElement tag : tagItems) {
                            tagTexts.add(tag.getText().trim());
                        }

                        if (label.contains("출연:") || label.contains("주연:") || label.contains("Cast:")) {
                            actors = tagTexts;
                        } else if (label.contains("크리에이터:") || label.contains("Creators:")) {
                            creator = String.join(", ", tagTexts);
                        } else if (label.contains("장르:") || label.contains("Genres:")) {
                            genres = tagTexts;
                        } else if (label.contains("특징:") || label.contains("Features:") || label.contains("이 시리즈는:")) {
                            features = tagTexts;
                        }
                    } catch (Exception tagError) {
                        logger.warning("태그 그룹 처리 중 오류: " + tagError.getMessage());
                    }
                }
            } catch (Exception e) {
                logger.warning("태그 정보 가져오기 실패: " + e.getMessage());
            }

            // 결과 데이터 구성
            Map<String, Object> detailData = new HashMap<>();
            detailData.put("id", item.getContentId());
            detailData.put("title", item.getName());
            detailData.put("type", contentType);
            detailData.put("url", item.getUrl());
            detailData.put("detail_url", detailUrl);
            detailData.put("thumbnail", item.getImageUrl());
            detailData.put("description", description);
            detailData.put("creator", creator);
            detailData.put("actors", actors);
            detailData.put("genres", genres);
            detailData.put("features", features);
            detailData.put("maturity_rating", maturityRating);
            detailData.put("release_year", releaseYear);
            detailData.put("crawled_at", LocalDateTime.now().toString());

            logger.info("콘텐츠 '" + item.getName() + "' 세부 정보 수집 완료");

            // 화면 캡처 (디버깅 용도)
            //takeScreenshot("detail_" + item.getContentId() + ".png");

            return detailData;

        } catch (Exception e) {
            logger.log(Level.WARNING, "콘텐츠 '" + item.getName() + "' 세부 정보 가져오기 실패: " + e.getMessage(), e);
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 스크린샷 저장 (디버깅 용도)
     */
    private void takeScreenshot(String filename) {
        try {
            // JavascriptExecutor를 사용하여 스크린샷 캡쳐
            String script = "return document.body.parentNode.outerHTML";
            String html = (String) ((JavascriptExecutor) driver).executeScript(script);

            // 파일에 저장
            Files.write(Paths.get(filename.replace(".png", ".html")), html.getBytes());
            logger.info("HTML 캡처 저장: " + filename);
        } catch (Exception e) {
            logger.warning("스크린샷 저장 실패: " + e.getMessage());
        }
    }

    /**
     * H2 데이터베이스 연결
     */
    private Connection getDbConnection() {
        try {
            // H2 인메모리 데이터베이스 연결
            String url = "jdbc:h2:mem:netflixdb;DB_CLOSE_DELAY=-1";
            String user = "sa";
            String password = "";

            Connection connection = DriverManager.getConnection(url, user, password);

            if (connection != null) {
                logger.fine("데이터베이스 연결 성공");
                return connection;
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "데이터베이스 연결 오류: " + e.getMessage(), e);
        }
        return null;
    }

    /**
     * 필요한 테이블 생성
     */
    private void createTablesIfNotExist(Connection connection) {
        try {
            Statement stmt = connection.createStatement();

            // 콘텐츠 테이블 생성
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS contents (" +
                            "content_id VARCHAR(255) PRIMARY KEY, " +
                            "title VARCHAR(255) NOT NULL, " +
                            "type VARCHAR(50) NOT NULL, " +
                            "synopsis TEXT, " +
                            "thumbnail_url TEXT, " +
                            "maturity_rating VARCHAR(20), " +
                            "release_year VARCHAR(4), " +
                            "available BOOLEAN DEFAULT TRUE, " +
                            "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                            "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                            ")"
            );

            // 장르 테이블 생성
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS genres (" +
                            "genre_id INT AUTO_INCREMENT PRIMARY KEY, " +
                            "genre_name VARCHAR(100) NOT NULL UNIQUE" +
                            ")"
            );

            // 콘텐츠-장르 관계 테이블
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS content_genres (" +
                            "id INT AUTO_INCREMENT PRIMARY KEY, " +
                            "content_id VARCHAR(255) NOT NULL, " +
                            "genre_id INT NOT NULL, " +
                            "UNIQUE(content_id, genre_id)" +
                            ")"
            );

            // 인물 테이블
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS persons (" +
                            "person_id INT AUTO_INCREMENT PRIMARY KEY, " +
                            "person_name VARCHAR(255) NOT NULL UNIQUE" +
                            ")"
            );

            // 역할 테이블
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS roles (" +
                            "role_id INT AUTO_INCREMENT PRIMARY KEY, " +
                            "role_name VARCHAR(50) NOT NULL UNIQUE" +
                            ")"
            );

            // 콘텐츠-인물-역할 관계 테이블
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS content_persons (" +
                            "id INT AUTO_INCREMENT PRIMARY KEY, " +
                            "content_id VARCHAR(255) NOT NULL, " +
                            "person_id INT NOT NULL, " +
                            "role_id INT NOT NULL, " +
                            "UNIQUE(content_id, person_id, role_id)" +
                            ")"
            );

            // 크롤링 로그 테이블
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS crawl_logs (" +
                            "id INT AUTO_INCREMENT PRIMARY KEY, " +
                            "start_time TIMESTAMP, " +
                            "end_time TIMESTAMP, " +
                            "status VARCHAR(50), " +
                            "new_contents INT" +
                            ")"
            );

            // 콘텐츠 변경 이력 테이블
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS content_changes (" +
                            "id INT AUTO_INCREMENT PRIMARY KEY, " +
                            "content_id VARCHAR(255) NOT NULL, " +
                            "change_type VARCHAR(50) NOT NULL, " +
                            "new_data TEXT, " +
                            "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                            ")"
            );

            // 기본 역할 추가
            try {
                stmt.execute("INSERT INTO roles (role_name) VALUES ('actor')");
                stmt.execute("INSERT INTO roles (role_name) VALUES ('creator')");
            } catch (SQLException e) {
                // 이미 존재하면 무시
            }

            logger.info("필요한 테이블 생성 완료");

        } catch (Exception e) {
            logger.log(Level.SEVERE, "테이블 생성 실패: " + e.getMessage(), e);
        }
    }

    /**
     * 콘텐츠 정보 데이터베이스 저장
     */
    @SuppressWarnings("unchecked")
    private boolean saveContentToDb(Map<String, Object> content) {
        if (content == null) {
            return false;
        }

        Connection connection = null;
        try {
            connection = getDbConnection();
            if (connection == null) {
                logger.severe("데이터베이스 연결 실패");
                return false;
            }

            // 자동 커밋 비활성화
            connection.setAutoCommit(false);

            // 콘텐츠 기본 정보 저장
            String contentId = (String) content.get("id");
            String title = (String) content.get("title");
            String contentType = (String) content.get("type");
            String synopsis = (String) content.get("description");
            String thumbnailUrl = (String) content.get("thumbnail");
            String maturityRating = (String) content.get("maturity_rating");
            String releaseYear = (String) content.get("release_year");

            // 콘텐츠 테이블에 저장
            PreparedStatement stmt = connection.prepareStatement(
                    "MERGE INTO contents KEY(content_id) VALUES(?, ?, ?, ?, ?, ?, ?, TRUE, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP())"
            );

            stmt.setString(1, contentId);
            stmt.setString(2, title != null ? title : "");
            stmt.setString(3, contentType != null ? contentType : "");
            stmt.setString(4, synopsis != null ? synopsis : "");
            stmt.setString(5, thumbnailUrl != null ? thumbnailUrl : "");
            stmt.setString(6, maturityRating != null ? maturityRating : "");
            stmt.setString(7, releaseYear != null ? releaseYear : "");

            stmt.executeUpdate();

            // 장르 정보 저장
            List<String> genres = (List<String>) content.get("genres");
            if (genres != null) {
                for (String genreName : genres) {
                    if (genreName == null || genreName.isEmpty()) {
                        continue;
                    }

                    // 장르 테이블에 저장
                    PreparedStatement genreStmt = connection.prepareStatement(
                            "MERGE INTO genres KEY(genre_name) VALUES(?)"
                    );
                    genreStmt.setString(1, genreName);
                    genreStmt.executeUpdate();

                    // 장르 ID 조회
                    PreparedStatement genreIdStmt = connection.prepareStatement(
                            "SELECT genre_id FROM genres WHERE genre_name = ?"
                    );
                    genreIdStmt.setString(1, genreName);
                    ResultSet genreRs = genreIdStmt.executeQuery();

                    if (genreRs.next()) {
                        int genreId = genreRs.getInt(1);

                        // 콘텐츠-장르 관계 저장
                        PreparedStatement contentGenreStmt = connection.prepareStatement(
                                "MERGE INTO content_genres KEY(content_id, genre_id) VALUES(?, ?)"
                        );
                        contentGenreStmt.setString(1, contentId);
                        contentGenreStmt.setInt(2, genreId);
                        contentGenreStmt.executeUpdate();
                    }
                }
            }

            // 제작자 정보 저장
            String creator = (String) content.get("creator");
            if (creator != null && !creator.isEmpty()) {
                // 인물 테이블에 저장
                PreparedStatement personStmt = connection.prepareStatement(
                        "MERGE INTO persons KEY(person_name) VALUES(?)"
                );
                personStmt.setString(1, creator);
                personStmt.executeUpdate();

                // 인물 ID 조회
                PreparedStatement personIdStmt = connection.prepareStatement(
                        "SELECT person_id FROM persons WHERE person_name = ?"
                );
                personIdStmt.setString(1, creator);
                ResultSet personRs = personIdStmt.executeQuery();

                if (personRs.next()) {
                    int personId = personRs.getInt(1);

                    // 역할 ID 조회 (creator)
                    PreparedStatement roleIdStmt = connection.prepareStatement(
                            "SELECT role_id FROM roles WHERE role_name = 'creator'"
                    );
                    ResultSet roleRs = roleIdStmt.executeQuery();

                    if (roleRs.next()) {
                        int roleId = roleRs.getInt(1);

                        // 콘텐츠-인물-역할 관계 저장
                        PreparedStatement contentPersonStmt = connection.prepareStatement(
                                "MERGE INTO content_persons KEY(content_id, person_id, role_id) VALUES(?, ?, ?)"
                        );
                        contentPersonStmt.setString(1, contentId);
                        contentPersonStmt.setInt(2, personId);
                        contentPersonStmt.setInt(3, roleId);
                        contentPersonStmt.executeUpdate();
                    }
                }
            }

            // 배우 정보 저장
            List<String> actors = (List<String>) content.get("actors");
            if (actors != null) {
                for (String actorName : actors) {
                    if (actorName == null || actorName.isEmpty()) {
                        continue;
                    }

                    // 인물 테이블에 저장
                    PreparedStatement personStmt = connection.prepareStatement(
                            "MERGE INTO persons KEY(person_name) VALUES(?)"
                    );
                    personStmt.setString(1, actorName);
                    personStmt.executeUpdate();

                    // 인물 ID 조회
                    PreparedStatement personIdStmt = connection.prepareStatement(
                            "SELECT person_id FROM persons WHERE person_name = ?"
                    );
                    personIdStmt.setString(1, actorName);
                    ResultSet personRs = personIdStmt.executeQuery();

                    if (personRs.next()) {
                        int personId = personRs.getInt(1);

                        // 역할 ID 조회 (actor)
                        PreparedStatement roleIdStmt = connection.prepareStatement(
                                "SELECT role_id FROM roles WHERE role_name = 'actor'"
                        );
                        ResultSet roleRs = roleIdStmt.executeQuery();

                        if (roleRs.next()) {
                            int roleId = roleRs.getInt(1);

                            // 콘텐츠-인물-역할 관계 저장
                            PreparedStatement contentPersonStmt = connection.prepareStatement(
                                    "MERGE INTO content_persons KEY(content_id, person_id, role_id) VALUES(?, ?, ?)"
                            );
                            contentPersonStmt.setString(1, contentId);
                            contentPersonStmt.setInt(2, personId);
                            contentPersonStmt.setInt(3, roleId);
                            contentPersonStmt.executeUpdate();
                        }
                    }
                }
            }

            // 변경 이력 저장
            PreparedStatement changeStmt = connection.prepareStatement(
                    "INSERT INTO content_changes (content_id, change_type, new_data) VALUES(?, ?, ?)"
            );
            changeStmt.setString(1, contentId);
            changeStmt.setString(2, "added");
            changeStmt.setString(3, content.toString());
            changeStmt.executeUpdate();

            // 커밋
            connection.commit();
            logger.info("콘텐츠 '" + title + "' 데이터베이스 저장 완료");
            return true;

        } catch (Exception e) {
            logger.warning("콘텐츠 데이터 저장 실패: " + e.getMessage());

            if (connection != null) {
                try {
                    connection.rollback();
                } catch (SQLException ex) {
                    logger.warning("롤백 실패: " + ex.getMessage());
                }
            }
            return false;
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException e) {
                    logger.warning("데이터베이스 연결 닫기 실패: " + e.getMessage());
                }
            }
        }
    }

    /**
     * 결과를 JSON 파일로 저장
     */
    private void saveResultsToJson() {
        try {
            StringBuilder json = new StringBuilder("{\n");

            int count = 0;
            for (Map.Entry<String, Map<String, Object>> entry : contentData.entrySet()) {
                String contentId = entry.getKey();
                Map<String, Object> content = entry.getValue();

                json.append("  \"").append(contentId).append("\": {\n");

                // 기본 정보
                json.append("    \"title\": \"").append(content.get("title")).append("\",\n");
                json.append("    \"type\": \"").append(content.get("type")).append("\",\n");
                json.append("    \"url\": \"").append(content.get("url")).append("\",\n");

                // 추가 정보
                if (content.get("description") != null) {
                    json.append("    \"description\": \"").append(cleanJsonString((String)content.get("description"))).append("\",\n");
                }

                if (content.get("creator") != null) {
                    json.append("    \"creator\": \"").append(content.get("creator")).append("\",\n");
                }

                if (content.get("maturity_rating") != null) {
                    json.append("    \"maturity_rating\": \"").append(content.get("maturity_rating")).append("\",\n");
                }

                if (content.get("release_year") != null) {
                    json.append("    \"release_year\": \"").append(content.get("release_year")).append("\",\n");
                }

                // 배열 정보
                @SuppressWarnings("unchecked")
                List<String> actors = (List<String>) content.get("actors");
                if (actors != null && !actors.isEmpty()) {
                    json.append("    \"actors\": [");
                    for (int i = 0; i < actors.size(); i++) {
                        json.append("\"").append(actors.get(i)).append("\"");
                        if (i < actors.size() - 1) {
                            json.append(", ");
                        }
                    }
                    json.append("],\n");
                }

                @SuppressWarnings("unchecked")
                List<String> genres = (List<String>) content.get("genres");
                if (genres != null && !genres.isEmpty()) {
                    json.append("    \"genres\": [");
                    for (int i = 0; i < genres.size(); i++) {
                        json.append("\"").append(genres.get(i)).append("\"");
                        if (i < genres.size() - 1) {
                            json.append(", ");
                        }
                    }
                    json.append("],\n");
                }

                // 썸네일 (항상 마지막에 위치)
                json.append("    \"thumbnail\": \"").append(content.get("thumbnail")).append("\"\n");

                json.append("  }");

                if (++count < contentData.size()) {
                    json.append(",");
                }
                json.append("\n");
            }

            json.append("}");

            // 파일로 저장
            try (FileWriter file = new FileWriter("netflix_content.json")) {
                file.write(json.toString());
            }

            logger.info("크롤링 결과를 JSON 파일로 저장 완료 (netflix_content.json)");

        } catch (Exception e) {
            logger.warning("결과 저장 중 오류: " + e.getMessage());
        }
    }

    /**
     * JSON 문자열에서 특수 문자 처리
     */
    private String cleanJsonString(String input) {
        if (input == null) return "";
        return input.replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * 무작위 대기 시간 생성
     */
    private int randomSleep(int min, int max) {
        return min + random.nextInt(max - min);
    }

    /**
     * 리소스 정리
     */
    private void cleanup() {
        if (driver != null) {
            driver.quit();
            logger.info("WebDriver 종료");
        }
    }

    /**
     * 메인 메소드
     */
    public static void main(String[] args) {
        logger.info("세부 정보 수집 Netflix 크롤러 시작");

        try {
            // 로그인 정보 설정 (실제 사용 시 변경 필요)
            String email = "email";
            String password = "password";

            // 크롤러 생성 및 실행
            DetailedNetflixCrawler crawler = new DetailedNetflixCrawler(email, password);
            crawler.run();

        } catch (Exception e) {
            logger.log(Level.SEVERE, "크롤러 실행 중 오류 발생: " + e.getMessage(), e);
        }
    }
}
