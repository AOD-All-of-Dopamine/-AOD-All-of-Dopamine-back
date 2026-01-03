package com.example.crawler.demo.dto;

import com.example.shared.entity.Content;
import com.example.shared.entity.PlatformData;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 데모 페이지에서 사용할 콘텐츠 상세 정보 DTO
 */
@Getter
@Setter
@NoArgsConstructor
public class ContentDetailDTO {

    private Long id;
    private String title;
    private String posterImageUrl;
    private String synopsis;
    private String originalTitle;
    private String releaseDate; // ISO 8601 format: yyyy-MM-dd
    private String domain;

    // 플랫폼별 고유 데이터
    private List<PlatformInfo> platforms;

    // 도메인별 추가 정보 (예: 장르, 감독, 작가 등)
    private Map<String, Object> domainAttributes;


    public ContentDetailDTO(Content content, Map<String, Object> domainAttributes, List<PlatformData> platformData) {
        this.id = content.getContentId();
        this.title = content.getMasterTitle();
        this.posterImageUrl = content.getPosterImageUrl();
        this.synopsis = content.getSynopsis();
        this.originalTitle = content.getOriginalTitle();
        this.releaseDate = content.getReleaseDate() != null ? content.getReleaseDate().toString() : null;
        this.domain = content.getDomain().name();
        this.domainAttributes = domainAttributes;
        this.platforms = platformData.stream().map(PlatformInfo::new).collect(Collectors.toList());
    }

    /**
     * 플랫폼 정보를 담는 내부 DTO
     */
    @Getter
    @Setter
    public static class PlatformInfo {
        private String platformName;
        private String url;
        private Map<String, Object> attributes;

        public PlatformInfo(PlatformData pd) {
            this.platformName = pd.getPlatformName();
            this.url = pd.getUrl();
            this.attributes = pd.getAttributes();
        }
    }
}

