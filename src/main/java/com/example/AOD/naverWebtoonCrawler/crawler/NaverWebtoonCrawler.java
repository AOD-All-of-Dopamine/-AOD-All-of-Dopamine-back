package com.example.AOD.naverWebtoonCrawler.crawler;

import java.util.ArrayList;
import java.util.List;
import com.example.AOD.naverWebtoonCrawler.domain.Days;
import com.example.AOD.naverWebtoonCrawler.domain.dto.NaverWebtoonDTO;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.springframework.stereotype.Component;

@Component
public class NaverWebtoonCrawler {

    private final String baseUrl = "https://comic.naver.com/webtoon";
    private final int SLEEP_TIME = 300;

    public ArrayList<NaverWebtoonDTO> crawlAllOngoingWebtoons(WebDriver driver){
        ArrayList<String> hrefList = new ArrayList<>();
        try {
            driver.get(baseUrl);
            Thread.sleep(SLEEP_TIME);

            WebElement element = driver.findElement(By.className("WeekdayMainView__daily_all_wrap--UvRFc"));
            List<WebElement> listItems = element.findElements(By.tagName("li"));

            for (WebElement listItem : listItems) {
                WebElement link = listItem.findElement(By.className("Poster__link--sopnC"));
                String href = link.getAttribute("href");

                hrefList.add(href);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        ArrayList<NaverWebtoonDTO> webtoons = new ArrayList<>();
        for(String href : hrefList){
            NaverWebtoonDTO naverWebtoonDTO = crawlWebtoonDetails(href, driver);
            if(naverWebtoonDTO!=null) webtoons.add(naverWebtoonDTO);
       }

        driver.quit();

        return webtoons;
    }

    public NaverWebtoonDTO crawlWebtoonDetails(String href, WebDriver driver){
        href += "&sort=ASC";
        try {
            driver.get(href);
            Thread.sleep(SLEEP_TIME);

            WebElement element = driver.findElement(By.className("EpisodeListInfo__comic_info--yRAu0"));
            String thumbnail = getThumbnail(element);
            String title = getTitle(element);
            List<String> authors = getAuthors(element);
            List<Days> days = getUploadDates(element);
            String summary = getSummary(element);
            List<String> tags = getTags(element);

            WebElement elem = driver.findElement(By.className("EpisodeListList__meta_info--Cgquz"));
            String publishDate = elem.findElement(By.className("date")).getText();

            return new NaverWebtoonDTO(title, href, publishDate, summary, thumbnail, days, authors, tags);


        } catch (Exception e) {
            System.out.println("error! : "+ href);
        }
        return null;
    }

    private String getThumbnail(WebElement elements) {
        return elements.findElement(By.className("Poster__image--d9XTI")).getAttribute("src");
    }

    private String getTitle(WebElement elements) {
        return elements.findElement(By.className("EpisodeListInfo__info_area--hkinm")).findElement(By.tagName("h2")).getText();
    }

    private List<String> getAuthors(WebElement elements) {
        WebElement elem = elements.findElement(By.className("ContentMetaInfo__meta_info--GbTg4"));
        return elem.findElements(By.tagName("span")).stream()
                .map(WebElement::getText)
                .map(text -> text.replaceAll("\n|글|그림|∙|원작|/", "").trim())
                .filter(text -> !text.trim().isEmpty())
                .toList();
    }

    private List<Days> getUploadDates(WebElement elements) {
        WebElement elem = elements.findElement(By.tagName("em"));
        return Days.parseDays(elem.getText().replace("\n",""));
    }

    private String getSummary(WebElement elements) {
        WebElement elem = elements.findElement(By.className("EpisodeListInfo__summary_wrap--ZWNW5"));
        return elem.findElement(By.tagName("p")).getText().replace("\n"," ").trim();
    }

    private List<String> getTags(WebElement elements) {
        WebElement elem = elements.findElement(By.className("TagGroup__tag_group--uUJza"));
        return elem.findElements(By.tagName("a")).stream()
                .map(WebElement::getText)
                .filter(text -> !text.trim().isEmpty())
                .toList();
    }

}
