package com.example.crawler.demo.dto;

import com.example.shared.entity.Content;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 데모 페이지의 View에 콘텐츠 정보를 전달하기 위한 DTO
 */
@Getter
@Setter
@NoArgsConstructor
public class ContentDTO {

    private Long id;
    private String title;
    private String posterImageUrl;

    /**
     * Content 엔티티를 받아서 DTO 객체로 변환하는 생성자
     * @param content 원본 Content 엔티티
     */
    public ContentDTO(Content content) {
        if (content != null) {
            this.id = content.getContentId();
            this.title = content.getMasterTitle();
            this.posterImageUrl = content.getPosterImageUrl();
        }
    }
}

