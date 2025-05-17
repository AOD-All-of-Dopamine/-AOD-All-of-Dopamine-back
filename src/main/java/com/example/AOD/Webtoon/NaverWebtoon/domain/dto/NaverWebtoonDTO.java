package com.example.AOD.Webtoon.NaverWebtoon.domain.dto;

import com.example.AOD.Webtoon.NaverWebtoon.domain.Days;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@ToString
public class NaverWebtoonDTO {
    private String title;
    private String url;
    private String publishDate;
    private String summary;
    private String thumbnail;
    private List<Days> uploadDays;
    private List<String> authors;
    private List<String> genres;

    // 기본 생성자 추가
    public NaverWebtoonDTO() {
        this.title = "";
        this.url = "";
        this.publishDate = "";
        this.summary = "";
        this.thumbnail = "https://ssl.pstatic.net/static/comic/images/og_tag_v2.png"; // 기본 이미지 설정
        this.uploadDays = new ArrayList<>();
        this.authors = new ArrayList<>();
        this.genres = new ArrayList<>();
    }

    // 태그를 장르로 사용
    public void setTags(List<String> tags) {
        this.genres = tags != null ? tags : new ArrayList<>();
    }

    // 필수 필드에 대한 유효성 검사 메서드 추가
    public boolean isValid() {
        return url != null && !url.isEmpty() &&
                title != null && !title.isEmpty() &&
                thumbnail != null && !thumbnail.isEmpty();
    }

    // null 체크 후 필드 설정 메서드 추가
    public void setSafeThumbnail(String thumbnail) {
        this.thumbnail = (thumbnail != null && !thumbnail.isEmpty())
                ? thumbnail
                : "https://ssl.pstatic.net/static/comic/images/og_tag_v2.png";
    }

    public void setSafeSummary(String summary) {
        this.summary = (summary != null && !summary.isEmpty())
                ? summary
                : "줄거리 정보가 없습니다.";
    }

    public void setSafeTitle(String title) {
        this.title = (title != null && !title.isEmpty())
                ? title
                : "제목 없음";
    }

    public void setSafePublishDate(String publishDate) {
        this.publishDate = (publishDate != null && !publishDate.isEmpty())
                ? publishDate
                : "정보 없음";
    }
}