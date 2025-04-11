package com.example.AOD.movie.crawler;

import com.example.AOD.movie.domain.CountryType;
import com.example.AOD.movie.domain.Movie;
import com.example.AOD.movie.repository.MovieRepository;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Component
@Slf4j
public class CgvCrawler {

    private final MovieRepository movieRepository;
    private final String BASE_URL = "http://www.cgv.co.kr";
    private final String MOVIE_LIST_URL = BASE_URL + "/movies/";
    private final String MOVIE_DETAIL_URL = BASE_URL + "/movies/detail-view/?midx=";

    private static final int CONNECTION_TIMEOUT = 30000;
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36";
    private static final int WAIT_TIMEOUT = 10;
    private static final int MAX_CLICK_ATTEMPTS = 10;

    @Autowired
    public CgvCrawler(MovieRepository movieRepository) {
        this.movieRepository = movieRepository;
    }

    public void crawlAll() {
        log.info("CGV 전체 영화 크롤링 시작");
        int newMoviesCount = 0;
        WebDriver driver = null;

        try {
            // NaverAutoLogin 방식으로 ChromeDriver 설정
            System.setProperty("webdriver.chrome.driver", "C:\\chromedriver-win64\\chromedriver.exe");

            ChromeOptions options = new ChromeOptions();
            options.addArguments("--headless");
            options.addArguments("--disable-gpu");
            options.addArguments("--no-sandbox");
            options.addArguments("--disable-dev-shm-usage");
            options.addArguments("--window-size=1920,1080");
            options.addArguments("user-agent=" + USER_AGENT);

            driver = new ChromeDriver(options);
            driver.get(MOVIE_LIST_URL);

            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(WAIT_TIMEOUT));
            clickMoreButtonUntilNoMore(driver, wait);

            log.info("영화 정보 추출 중...");
            List<String> externalIds = extractAllMovieIds(driver);
            log.info("총 {}개 영화 발견", externalIds.size());

            for (String externalId : externalIds) {
                try {
                    if (movieRepository.existsByExternalId(externalId)) {
                        log.debug("이미 존재하는 영화 ID: {}, 건너뜀", externalId);
                        continue;
                    }

                    Movie movie = crawlMovieDetail(externalId);
                    if (movie != null) {
                        movieRepository.save(movie);
                        newMoviesCount++;
                        log.info("영화 저장 완료: {}", movie.getTitle());
                    }

                    delay();
                } catch (Exception e) {
                    log.error("영화 ID {} 상세 정보 크롤링 오류: {}", externalId, e.getMessage(), e);
                }
            }

            log.info("CGV 전체 영화 크롤링 완료. {}개 신규 영화 크롤링됨", newMoviesCount);
        } catch (Exception e) {
            log.error("Selenium 크롤링 오류: {}", e.getMessage(), e);
        } finally {
            if (driver != null) {
                driver.quit();
                log.info("WebDriver 종료");
            }
        }
    }

    private void clickMoreButtonUntilNoMore(WebDriver driver, WebDriverWait wait) {
        int clickCount = 0;
        boolean hasMoreButton = true;

        while (hasMoreButton && clickCount < MAX_CLICK_ATTEMPTS) {
            try {
                Thread.sleep(1000);

                WebElement moreButton = null;
                try {
                    moreButton = wait.until(
                            ExpectedConditions.elementToBeClickable(
                                    By.cssSelector(".btn-more-fontbold")
                            )
                    );
                } catch (Exception e) {
                    log.info("'더보기' 버튼이 더 이상 없습니다. 모든 영화 로드됨.");
                    hasMoreButton = false;
                    break;
                }

                if (!moreButton.isDisplayed() || !moreButton.isEnabled()) {
                    log.info("'더보기' 버튼이 보이지 않거나 활성화되지 않았습니다. 모든 영화 로드됨.");
                    hasMoreButton = false;
                    break;
                }

                ((JavascriptExecutor) driver).executeScript(
                        "arguments[0].scrollIntoView({behavior: 'smooth', block: 'center'});",
                        moreButton
                );

                Thread.sleep(500);
                ((JavascriptExecutor) driver).executeScript("arguments[0].click();", moreButton);

                log.info("'더보기' 버튼 클릭. 시도: {}", clickCount + 1);
                clickCount++;
                Thread.sleep(1500);

            } catch (Exception e) {
                log.warn("'더보기' 버튼 클릭 오류: {}", e.getMessage());
                clickCount++;
            }
        }

        if (clickCount >= MAX_CLICK_ATTEMPTS) {
            log.info("최대 클릭 시도 횟수({})에 도달. 로드된 영화로 진행.", MAX_CLICK_ATTEMPTS);
        }
    }

    private List<String> extractAllMovieIds(WebDriver driver) {
        List<String> externalIds = new ArrayList<>();
        List<WebElement> movieElements = driver.findElements(By.cssSelector(".sect-movie-chart ol li"));

        for (WebElement movieElement : movieElements) {
            try {
                WebElement linkElement = movieElement.findElement(By.cssSelector(".box-image a"));
                String href = linkElement.getAttribute("href");
                String externalId = extractExternalId(href);

                if (!externalId.isEmpty()) {
                    externalIds.add(externalId);
                }
            } catch (Exception e) {
                log.warn("영화 ID 추출 오류: {}", e.getMessage());
            }
        }

        return externalIds;
    }

    public void crawlRecent() {
        log.info("CGV 최신 영화 크롤링 시작");
        int newMoviesCount = 0;
        WebDriver driver = null;

        try {
            // NaverAutoLogin 방식으로 ChromeDriver 설정
            System.setProperty("webdriver.chrome.driver", "C:\\chromedriver-win64\\chromedriver.exe");

            ChromeOptions options = new ChromeOptions();
            options.addArguments("--headless");
            options.addArguments("--disable-gpu");
            options.addArguments("--no-sandbox");
            options.addArguments("--disable-dev-shm-usage");
            options.addArguments("user-agent=" + USER_AGENT);

            driver = new ChromeDriver(options);
            driver.get(MOVIE_LIST_URL);

            List<String> externalIds = extractAllMovieIds(driver);
            log.info("첫 페이지에서 {}개 영화 발견", externalIds.size());

            LocalDate oneWeekAgo = LocalDate.now().minusWeeks(1);

            for (String externalId : externalIds) {
                try {
                    if (movieRepository.existsByExternalId(externalId)) {
                        log.debug("이미 존재하는 영화 ID: {}, 건너뜀", externalId);
                        continue;
                    }

                    Movie movie = crawlMovieDetail(externalId);
                    if (movie != null) {
                        movieRepository.save(movie);
                        newMoviesCount++;
                        log.info("최신 영화 저장 완료: {}", movie.getTitle());
                    }

                    delay();
                } catch (Exception e) {
                    log.error("영화 상세 정보 크롤링 오류: {}", e.getMessage(), e);
                }
            }

            log.info("CGV 최신 영화 크롤링 완료. {}개 신규 영화 크롤링됨", newMoviesCount);
        } catch (Exception e) {
            log.error("최신 영화 Selenium 크롤링 오류: {}", e.getMessage(), e);
        } finally {
            if (driver != null) {
                driver.quit();
                log.info("WebDriver 종료");
            }
        }
    }

    private Movie crawlMovieDetail(String externalId) {
        try {
            String url = MOVIE_DETAIL_URL + externalId;
            Document doc = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(CONNECTION_TIMEOUT)
                    .get();

            String title = doc.select(".box-contents .title strong").text();
            if (title.isEmpty()) {
                log.warn("영화 제목을 찾을 수 없습니다. externalId: {}", externalId);
                return null;
            }

            String director = doc.select(".spec dl dt:contains(감독) + dd a").text();

            Elements actorElements = doc.select(".spec dl dt:contains(배우) + dd.on a");
            List<String> actors = new ArrayList<>();
            for (Element actorElement : actorElements) {
                String actorName = actorElement.text().trim();
                if (!actorName.isEmpty()) {
                    actors.add(actorName);
                }
            }

            List<String> genres = parseGenres(doc);

            String ratingText = doc.select(".egg-gage.small .percent").text();
            Double rating = parseRating(ratingText);

            String reservationRateText = doc.select(".score .percent span").text();
            Double reservationRate = parseReservationRate(reservationRateText);

            String runningTimeText = doc.select(".spec dl dt:contains(기본 정보) + dd").text();
            Integer runningTime = parseRunningTime(runningTimeText);

            String countryText = doc.select(".spec dl dt:contains(기본 정보) + dd").text();
            CountryType country = parseCountry(countryText);

            String releaseDateText = doc.select(".spec dl dt:contains(개봉) + dd").text();
            LocalDate releaseDate = parseReleaseDate(releaseDateText);

            return Movie.builder()
                    .title(title)
                    .director(director)
                    .actors(actors)
                    .genres(genres)
                    .rating(rating)
                    .reservationRate(reservationRate)
                    .runningTime(runningTime)
                    .country(country)
                    .releaseDate(releaseDate)
                    .externalId(externalId)
                    .lastUpdated(LocalDate.now())
                    .build();

        } catch (IOException e) {
            log.error("ID {}의 영화 상세 정보 크롤링 오류: {}", externalId, e.getMessage(), e);
            return null;
        }
    }

    private List<String> parseGenres(Document doc) {
        List<String> genres = new ArrayList<>();

        // 장르 정보가 들어있는 dt 요소 찾기
        Element genreElement = doc.selectFirst(".spec dl dt:contains(장르 :)");

        if (genreElement != null) {
            String genreText = genreElement.text().trim();
            log.debug("장르 원본 텍스트: {}", genreText);

            // "장르 :" 부분 제거
            if (genreText.startsWith("장르 :")) {
                genreText = genreText.substring("장르 :".length()).trim();

                // 콤마로 구분된 장르들 분리
                genres = Arrays.stream(genreText.split(","))
                        .map(s -> s.trim())
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toList());

                log.info("파싱된 장르 목록: {}", genres);
            }
        } else {
            log.warn("장르 정보를 찾을 수 없습니다");
        }

        return genres;
    }

    private Double parseRating(String ratingText) {
        try {
            return Double.parseDouble(ratingText.replace("%", "").trim()) / 10.0;
        } catch (NumberFormatException e) {
            log.warn("평점 파싱 오류: {}", ratingText);
            return 0.0;
        }
    }

    private Double parseReservationRate(String reservationRateText) {
        try {
            return Double.parseDouble(reservationRateText.replace("%", "").trim());
        } catch (NumberFormatException e) {
            log.warn("예매율 파싱 오류: {}", reservationRateText);
            return 0.0;
        }
    }

    private Integer parseRunningTime(String runningTimeText) {
        try {
            if (runningTimeText.contains("분")) {
                String[] parts = runningTimeText.split(",");
                for (String part : parts) {
                    part = part.trim();
                    if (part.contains("분")) {
                        return Integer.parseInt(part.replaceAll("[^0-9]", ""));
                    }
                }
            }
            return 0;
        } catch (NumberFormatException e) {
            log.warn("상영시간 파싱 오류: {}", runningTimeText);
            return 0;
        }
    }

    private CountryType parseCountry(String countryText) {
        List<String> koreanTerms = Arrays.asList("한국", "대한민국");

        for (String term : koreanTerms) {
            if (countryText.contains(term)) {
                return CountryType.DOMESTIC;
            }
        }

        return CountryType.FOREIGN;
    }

    private LocalDate parseReleaseDate(String releaseDateText) {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy.MM.dd");
            return LocalDate.parse(releaseDateText, formatter);
        } catch (Exception e) {
            log.warn("개봉일 파싱 오류: {}", releaseDateText);
            return LocalDate.now();
        }
    }

    private String extractExternalId(String href) {
        if (href.contains("midx=")) {
            return href.substring(href.indexOf("midx=") + 5);
        }
        return "";
    }

    private void delay() {
        try {
            TimeUnit.SECONDS.sleep(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}