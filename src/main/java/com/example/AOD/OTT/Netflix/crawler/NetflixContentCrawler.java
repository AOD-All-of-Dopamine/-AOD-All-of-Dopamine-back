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

import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.*;
import org.springframework.stereotype.Component;


@Component
@Slf4j
public class NetflixContentCrawler {
    private static final Logger logger = Logger.getLogger("NetflixCrawler");

    private Random random = new Random();
    private final int MAX_ITEMS = 5; // 테스트용
    private final int SCROLL_DOWN_COUNT = 5;

    private static class temporaryContent{
        public String url;
        public String title;
        public String contentId;

        temporaryContent(WebElement elem){
            this.title = getTitle(elem);
            this.url = getURL(elem);
            String[] parts = url.split("\\?")[0].split("/");
            this.contentId = parts[parts.length - 1];;
        }
        private String getURL(WebElement elem){
            try{
                return elem.getAttribute("href");
            }catch (Exception e){
                return null;
            }
        }
        private String getTitle(WebElement elem){
            String ret = elem.getAttribute("aria-label");
            if (ret == null || ret.isEmpty()) {
                try {
                    ret = elem.findElement(By.tagName("img")).getAttribute("alt");
                } catch (NoSuchElementException ex) {
                    ret = null;
                }
            }
            return ret;
        }
    }

    /**
     * 크롤링 수행, NetflixContentDTO 리스트 반환
     */
    public List<NetflixContentDTO> crawl(WebDriver driver){
        List<temporaryContent> tmpContentList = exportTemporaryContentList(driver, "https://www.netflix.com/browse/genre/83?so=su", "a.slider-refocus");
        return exportNetflixContentDtos(driver, tmpContentList, MAX_ITEMS);
    }

    /**
     * 최신 콘텐츠 크롤링
     */
    public List<NetflixContentDTO> crawlLatestContent(WebDriver driver){
        List<temporaryContent> tmpContentList = exportTemporaryContentList(driver, "https://www.netflix.com/latest", "a.slider-refocus");
        return exportNetflixContentDtos(driver, tmpContentList, tmpContentList.size());
    }

    /**
     * 이번주 공개된 넷플릭스 최신 콘텐츠만 크롤링 - 최적화 버전
     */
    public List<NetflixContentDTO> crawlThisWeekContent(WebDriver driver) {
        ArrayList<temporaryContent> result = new ArrayList<>();
        try{
            driver.get("https://www.netflix.com/latest");
            Thread.sleep(3000);
            scrollDown(driver, SCROLL_DOWN_COUNT);

            WebElement thisWeekSection = getThisWeekSection(driver);
            if(thisWeekSection==null) throw new Exception();
            List<WebElement> items = getContentElemFromSection(thisWeekSection);
            logger.info("추출된 콘텐츠 링크 수: " + items.size());

            for (WebElement item : items) {
                result.add(new temporaryContent(item));
            }

        } catch (Exception e){
            log.debug("Error - Can't crawl ThisWeek Contents");
        }

        return exportNetflixContentDtos(driver, result, result.size());
    }

    private List<temporaryContent> exportTemporaryContentList(WebDriver driver, String url, String elemCssSelector){
        ArrayList<temporaryContent> results = new ArrayList<>();
        try{
            driver.get(url);
            Thread.sleep(3000);
            scrollDown(driver, SCROLL_DOWN_COUNT);

            List<WebElement> items = driver.findElements(By.cssSelector(elemCssSelector));
            for (WebElement item : items) {
                temporaryContent dto = new temporaryContent(item);
                if(results.contains(dto)) continue;
                results.add(dto);
            }
        }catch (Exception e){
            log.debug("Error - Can't crawl netflixContentLinkList");
        }
        return results;
    }

    private List<NetflixContentDTO> exportNetflixContentDtos(WebDriver driver, List<temporaryContent> netflixHrefList, int count){
        ArrayList<NetflixContentDTO> results = new ArrayList<>();
        for (temporaryContent con : netflixHrefList) {
            NetflixContentDTO netflixContentDTO = getDetailInfo(driver, con);
            if(netflixContentDTO!=null) results.add(netflixContentDTO);
            if(results.size()==count) break;
        }
        return results;
    }

    private List<WebElement> getContentElemFromSection(WebElement thisWeekSection){
        WebElement sliderContent = thisWeekSection.findElement(By.cssSelector(".sliderContent"));
        logger.info("슬라이더 콘텐츠 요소 발견");

        List<WebElement> sliderItems = sliderContent.findElements(By.cssSelector(".slider-item"));
        logger.info("슬라이더 아이템 수: " + sliderItems.size());

        List<WebElement> items = new ArrayList<>();
        for (WebElement item : sliderItems) {
            try {
                WebElement link = item.findElement(By.className("slider-refocus"));
                items.add(link);
            } catch (Exception ignored) { }
        }
        return items;
    }

    private WebElement getThisWeekSection(WebDriver driver) {
        List<WebElement> rowHeaderTitles = driver.findElements(By.cssSelector(".row-header-title"));

        String[] sectionNameList = {"이번 주 공개 콘텐츠", "이번 주", "이번주", "공개 콘텐츠", "New this week"};
        for (String sectionName : sectionNameList){
            for (WebElement header : rowHeaderTitles) {
                if(sectionName.equals(header.getText().trim())) {
                    return header.findElement(By.xpath("./ancestor::div[contains(@class, 'lolomoRow')]"));
                }
            }
        }
        return null;
    }

    /**
     * 상세 정보 페이지 접근, DTO에 세부 정보 셋팅
     */
    private NetflixContentDTO getDetailInfo(WebDriver driver, temporaryContent con) {
        NetflixContentDTO dto = new NetflixContentDTO();
        try {
            String detailUrl = "https://www.netflix.com/title/" + con.contentId;
            driver.get(detailUrl);
            Thread.sleep(randomSleep(2000, 3000));

            // contentType 추정 (간단 로직)
            String contentType = getContentType(con);
            Long id = getContentId(con.contentId);
            String description = getDescription(driver);
            String maturityRating = getMaturityRating(driver);
            String imageUrl = getImageUrl(driver);

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

            String releaseYear = "";


            dto.setId(id);
            dto.setTitle(con.title);
            dto.setType(contentType);
            dto.setUrl(con.url);
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

            logger.info("세부 정보 수집 완료: " + con.title);
        } catch (Exception e) {
            logger.log(Level.WARNING, "세부 정보 수집 실패", e);
            return null;
        }
        return dto;
    }

    private Long getContentId(String contentId) {
        Long result = 0L;
        try {
            // String ID를 Long으로 변환 시도
            try {
                result = Long.parseLong(contentId);
            } catch (NumberFormatException e) {
                // ID가 숫자가 아닌 경우 대체 값 생성
                result = (long) contentId.hashCode();
                if (result < 0) result = Math.abs(result);
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "세부 정보 수집 실패", e);
        }
        return result;
    }

    private String getMaturityRating(WebDriver driver) {
        String result = "";
        List<WebElement> numberSpans = driver.findElements(By.cssSelector("span.maturity-number"));

        // 등급이 숫자 형태일 때
        if (!numberSpans.isEmpty()) {
            // "15+" 등 플러스를 제거하고 숫자만 남김
            String txt = numberSpans.get(0).getText().trim();
            return txt.replaceAll("\\D+", "");
        }

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
                        result = "12";
                        break;
                    case "24306":  // 내부 코드 24306 → 19세
                        result = "19";
                        break;
                    // 필요하면 다른 코드도 여기에 추가
                    default:
                        result = code;  // fallback: 숫자 그대로
                }
            }
        } catch (TimeoutException | NoSuchElementException ex) {
            logger.fine("maturity-icon 요소를 찾지 못함");
        }
        return result;
    }

    private String getContentType(temporaryContent con) {
        return con.url.toLowerCase().contains("series") ? "series" : "movie";
    }

    private String getDescription(WebDriver driver) {
        String result="";
        try {
            WebElement descElem = new WebDriverWait(driver, Duration.ofSeconds(5))
                    .until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(".preview-modal-synopsis")));
            result = descElem.getText().trim();
        } catch (Exception ex) {
            logger.fine("설명 없음");
        }
        return result;
    }

    private String getImageUrl(WebDriver driver) {
        String result="";
        try {
            WebElement storyArtImg = new WebDriverWait(driver, Duration.ofSeconds(5))
                    .until(ExpectedConditions.presenceOfElementLocated(
                            By.cssSelector(".storyArt img")
                    ));
            if (storyArtImg != null) {
                result = storyArtImg.getAttribute("src");
                logger.info("이미지 추출 성공: " + result);
            }
        } catch (Exception ex) {
            logger.warning("이미지 추출 실패: " + ex.getMessage());
        }
        return result;
    }

    private int randomSleep(int min, int max) {
        return min + random.nextInt(max - min);
    }

    private void scrollDown(WebDriver driver, int count) throws InterruptedException {
        JavascriptExecutor executor = (JavascriptExecutor) driver;
        for (int i = 0; i < count; i++) {
            executor.executeScript("window.scrollBy(0, 800);");
            Thread.sleep(1000);
        }
    }

}