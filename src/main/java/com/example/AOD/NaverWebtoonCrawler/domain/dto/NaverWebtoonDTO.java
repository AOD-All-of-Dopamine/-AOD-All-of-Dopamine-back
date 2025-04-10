package com.example.AOD.NaverWebtoonCrawler.domain.dto;

import com.example.AOD.NaverWebtoonCrawler.domain.Days;
import java.util.List;
import lombok.Getter;

@Getter
public class NaverWebtoonDTO {
    private String title;
    private String url;
    private String publishDate;
    private String summary;
    private String thumbnail;

    private List<Days> uploadDays;
    private List<String> authors;
    private List<String> genres;

    public NaverWebtoonDTO(String title, String url, String publishDate, String summary, String thumbnail,
                           List<Days> uploadDays, List<String> authors, List<String> genres) {
        this.title = title;
        this.url = url;
        this.publishDate = publishDate;
        this.summary = summary;
        this.thumbnail = thumbnail;
        this.uploadDays = uploadDays;
        this.authors = authors;
        this.genres = genres;
    }
}
