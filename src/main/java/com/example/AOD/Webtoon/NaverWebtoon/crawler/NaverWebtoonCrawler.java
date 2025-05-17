package com.example.AOD.Webtoon.NaverWebtoon.crawler;

import com.example.AOD.Webtoon.NaverWebtoon.domain.Days;
import com.example.AOD.Webtoon.NaverWebtoon.domain.dto.NaverWebtoonDTO;
import java.util.ArrayList;
import java.util.List;

import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Component;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class NaverWebtoonCrawler {
    private static final Logger logger = LoggerFactory.getLogger(NaverWebtoonCrawler.class);

    private final String baseUrl = "https://comic.naver.com/webtoon";
    private final String newWebtoonUrl = "https://comic.naver.com/webtoon?tab=new";
    private final int SLEEP_TIME = 500;
    private final int WAIT_TIMEOUT = 10;

    // 크롤링할 최대 웹툰 수
    private final int MAX_WEBTOONS = 25;

    // 기본 썸네일 이미지 URL
    private final String DEFAULT_THUMBNAIL = "https://ssl.pstatic.net/static/comic/images/og_tag_v2.png";

    public ArrayList<NaverWebtoonDTO> crawlAllOngoingWebtoons(WebDriver driver){
        ArrayList<String> hrefList = new ArrayList<>();
        try {
            driver.get(baseUrl);
            Thread.sleep(SLEEP_TIME);

            // 명시적 대기 추가
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(WAIT_TIMEOUT));
            WebElement element = wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.className("WeekdayMainView__daily_all_wrap--UvRFc")));

            List<WebElement> listItems = element.findElements(By.tagName("li"));

            // 링크를 최대 MAX_WEBTOONS개만 수집
            int count = 0;
            for (WebElement listItem : listItems) {
                try {
                    WebElement link = listItem.findElement(By.className("Poster__link--sopnC"));
                    String href = link.getAttribute("href");
                    if (href != null && !href.isEmpty()) {
                        hrefList.add(href);
                        count++;

                        // MAX_WEBTOONS개 수집 후 종료
                        if (count >= MAX_WEBTOONS) {
                            logger.info("최대 {} 개 웹툰 URL 수집 완료. 크롤링 제한에 도달했습니다.", MAX_WEBTOONS);
                            break;
                        }
                    }
                } catch (Exception e) {
                    logger.warn("웹툰 링크 추출 중 오류 발생: {}", e.getMessage());
                    // 개별 항목 오류 무시하고 계속 진행
                }
            }
            logger.info("총 {} 개의 웹툰 URL을 수집했습니다.", hrefList.size());
        } catch (Exception e) {
            logger.error("웹툰 목록 크롤링 중 오류 발생: {}", e.getMessage());
            e.printStackTrace();
        }

        ArrayList<NaverWebtoonDTO> webtoons = new ArrayList<>();
        int successCount = 0;
        int failCount = 0;

        for (String href : hrefList) {
            try {
                NaverWebtoonDTO naverWebtoonDTO = crawlWebtoonDetails(href, driver);
                if (naverWebtoonDTO != null) {
                    webtoons.add(naverWebtoonDTO);
                    successCount++;
                } else {
                    failCount++;
                }
            } catch (Exception e) {
                logger.error("웹툰 상세 정보 크롤링 실패: {}", href);
                failCount++;
                // 계속 진행
            }
        }

        logger.info("웹툰 상세 정보 크롤링 결과: 성공 {} 개, 실패 {} 개", successCount, failCount);
        return webtoons;
    }

    public NaverWebtoonDTO crawlWebtoonDetails(String href, WebDriver driver) {
        href += "&sort=ASC";
        try {
            driver.get(href);
            Thread.sleep(SLEEP_TIME);

            // 명시적 대기 추가
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(WAIT_TIMEOUT));

            try {
                WebElement element = wait.until(ExpectedConditions.presenceOfElementLocated(
                        By.className("EpisodeListInfo__comic_info--yRAu0")));

//                String thumbnail = getThumbnail(element);
                String thumbnail = getThumbnail(driver);

                System.out.println(thumbnail);
                String title = getTitle(element);
                List<String> authors = getAuthors(element);
                List<Days> days = getUploadDates(element);
                String summary = getSummary(element);
                List<String> tags = getTags(element);

                WebElement elem = wait.until(ExpectedConditions.presenceOfElementLocated(
                        By.className("EpisodeListList__meta_info--Cgquz")));
                String publishDate = elem.findElement(By.className("date")).getText();

                // DTO 생성 시 null 체크 및 기본값 설정
                NaverWebtoonDTO dto = new NaverWebtoonDTO();
                dto.setSafeTitle(title);
                dto.setUrl(href);
                dto.setSafePublishDate(publishDate);
                dto.setSafeSummary(summary);
                dto.setSafeThumbnail(thumbnail);
                dto.setUploadDays(days != null ? days : new ArrayList<>());
                dto.setAuthors(authors != null ? authors : new ArrayList<>());
                dto.setTags(tags);

                logger.info("웹툰 정보 크롤링 성공: {}", title);
                return dto;
            } catch (TimeoutException te) {
                logger.error("요소 찾기 시간 초과 (URL: {}): {}", href, te.getMessage());
                return createEmptyDTO(href);
            }
        } catch (Exception e) {
            logger.error("웹툰 상세 정보 크롤링 중 오류 발생 (URL: {}): {}", href, e.getMessage());
            e.printStackTrace();
        }
        return createEmptyDTO(href);
    }

    /**
     * 네이버 웹툰 신작 탭에서 웹툰을 크롤링합니다. 최대 MAX_WEBTOONS개 제한
     */
    public ArrayList<NaverWebtoonDTO> crawlNewWebtoons(WebDriver driver) {
        ArrayList<String> hrefList = new ArrayList<>();
        try {
            driver.get(newWebtoonUrl);
            Thread.sleep(SLEEP_TIME);

            // 명시적 대기 추가
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(WAIT_TIMEOUT));
            WebElement newWebtoonContainer = wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.className("ContentList__content_list--q5KXY")));

            List<WebElement> listItems = newWebtoonContainer.findElements(By.tagName("li"));

            // 링크를 최대 MAX_WEBTOONS개만 수집
            int count = 0;
            for (WebElement listItem : listItems) {
                try {
                    WebElement link = listItem.findElement(By.className("Poster__link--sopnC"));
                    String href = link.getAttribute("href");
                    if (href != null && !href.isEmpty()) {
                        hrefList.add(href);
                        count++;

                        // MAX_WEBTOONS개 수집 후 종료
                        if (count >= MAX_WEBTOONS) {
                            logger.info("최대 {} 개 신작 웹툰 URL 수집 완료. 크롤링 제한에 도달했습니다.", MAX_WEBTOONS);
                            break;
                        }
                    }
                } catch (Exception e) {
                    logger.warn("신작 웹툰 링크 추출 중 오류 발생: {}", e.getMessage());
                    // 개별 항목 오류 무시하고 계속 진행
                }
            }
            logger.info("총 {} 개의 신작 웹툰 URL을 수집했습니다.", hrefList.size());
        } catch (Exception e) {
            logger.error("신작 웹툰 목록 크롤링 중 오류 발생: {}", e.getMessage());
            e.printStackTrace();
        }

        ArrayList<NaverWebtoonDTO> webtoons = new ArrayList<>();
        int successCount = 0;
        int failCount = 0;

        for (String href : hrefList) {
            try {
                NaverWebtoonDTO naverWebtoonDTO = crawlWebtoonDetails(href, driver);
                if (naverWebtoonDTO != null) {
                    webtoons.add(naverWebtoonDTO);
                    successCount++;
                } else {
                    failCount++;
                }
            } catch (Exception e) {
                logger.error("신작 웹툰 상세 정보 크롤링 실패: {}", href);
                failCount++;
                // 계속 진행
            }
        }

        logger.info("신작 웹툰 상세 정보 크롤링 결과: 성공 {} 개, 실패 {} 개", successCount, failCount);
        return webtoons;
    }

//    private String getThumbnail(WebElement elements) {
//        try {
//            String thumbnail = elements.findElement(By.className("Poster__image--d9XTI")).getAttribute("src");
//            System.out.println(thumbnail);
//            if (thumbnail != null && !thumbnail.isEmpty()) {
//                logger.info("이미지 추출 성공: {}", thumbnail);
//                return thumbnail;
//            } else {
//                logger.warn("썸네일 URL이 비어있어 기본 이미지를 사용합니다.");
//                return DEFAULT_THUMBNAIL;
//            }
//        } catch (Exception e) {
//            logger.error("썸네일 추출 실패: {}", e.getMessage());
//            return DEFAULT_THUMBNAIL; // 기본 이미지 URL
//        }
//    }

    private String getThumbnail(WebDriver driver) {
        try {
            // 명시적 대기 추가 - 이미지 로딩까지 충분히 대기
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15)); // 시간 증가

            // 스크린샷에서 본 실제 클래스 이름으로 시도
            WebElement imgElement = wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.cssSelector(".Poster__image--d9XTI")));

            // 이미지가 로드될 때까지 추가 대기
            wait.until(ExpectedConditions.attributeToBeNotEmpty(imgElement, "src"));

            String thumbnail = imgElement.getAttribute("src");

            if (thumbnail != null && !thumbnail.isEmpty()) {
                logger.info("이미지 추출 성공: {}", thumbnail);
                return thumbnail;
            }

            // 이미지가 없는 경우 JavaScript로 시도
            String jsScript = "return document.querySelector('.Poster__image--d9XTI').src";
            String jsResult = (String) ((JavascriptExecutor) driver).executeScript(jsScript);

            if (jsResult != null && !jsResult.isEmpty()) {
                return jsResult;
            }

            logger.warn("썸네일 URL이 비어있어 기본 이미지를 사용합니다.");
            return DEFAULT_THUMBNAIL;
        } catch (Exception e) {
            logger.error("썸네일 추출 실패: {}", e.getMessage());
            return DEFAULT_THUMBNAIL;
        }
    }





    private String getTitle(WebElement elements) {
        try {
            String title = elements.findElement(By.className("EpisodeListInfo__info_area--hkinm"))
                    .findElement(By.tagName("h2")).getText();
            return title != null && !title.isEmpty() ? title : "제목 없음";
        } catch (Exception e) {
            logger.error("제목 추출 실패: {}", e.getMessage());
            return "제목 없음";
        }
    }

    private List<String> getAuthors(WebElement elements) {
        try {
            WebElement elem = elements.findElement(By.className("ContentMetaInfo__meta_info--GbTg4"));
            List<String> authors = elem.findElements(By.tagName("span")).stream()
                    .map(WebElement::getText)
                    .map(text -> text.replaceAll("\n|글|그림|∙|원작|/", "").trim())
                    .filter(text -> !text.trim().isEmpty())
                    .toList();
            return authors.isEmpty() ? List.of("작가 미상") : authors;
        } catch (Exception e) {
            logger.error("작가 정보 추출 실패: {}", e.getMessage());
            List<String> defaultAuthor = new ArrayList<>();
            defaultAuthor.add("작가 미상");
            return defaultAuthor;
        }
    }

    private List<Days> getUploadDates(WebElement elements) {
        try {
            WebElement elem = elements.findElement(By.tagName("em"));
            return Days.parseDays(elem.getText().replace("\n",""));
        } catch (Exception e) {
            logger.error("업로드 요일 추출 실패: {}", e.getMessage());
            List<Days> defaultDays = new ArrayList<>();
//            defaultDays.add(Days.UNKNOWN);
            return defaultDays;
        }
    }

    private String getSummary(WebElement elements) {
        try {
            WebElement elem = elements.findElement(By.className("EpisodeListInfo__summary_wrap--ZWNW5"));
            String summary = elem.findElement(By.tagName("p")).getText().replace("\n"," ").trim();
            return summary != null && !summary.isEmpty() ? summary : "줄거리 정보가 없습니다.";
        } catch (Exception e) {
            logger.error("줄거리 추출 실패: {}", e.getMessage());
            return "줄거리 정보가 없습니다.";
        }
    }

    private List<String> getTags(WebElement elements) {
        try {
            WebElement elem = elements.findElement(By.className("TagGroup__tag_group--uUJza"));
            List<String> tags = elem.findElements(By.tagName("a")).stream()
                    .map(WebElement::getText)
                    .filter(text -> !text.trim().isEmpty())
                    .toList();
            return tags;
        } catch (Exception e) {
            logger.error("태그 추출 실패: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 크롤링 실패 시 기본 DTO를 생성합니다.
     */
    private NaverWebtoonDTO createEmptyDTO(String url) {
        NaverWebtoonDTO dto = new NaverWebtoonDTO();
        dto.setTitle("정보 수집 실패");
        dto.setUrl(url);
        dto.setPublishDate("정보 없음");
        dto.setSummary("웹툰 정보를 가져오지 못했습니다.");
        dto.setThumbnail(DEFAULT_THUMBNAIL);
        dto.setUploadDays(new ArrayList<>());
        dto.setAuthors(List.of("작가 미상"));
        dto.setGenres(new ArrayList<>());
        return dto;
    }
}