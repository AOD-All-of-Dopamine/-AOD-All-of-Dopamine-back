package com.example.AOD.demo.dto;

import com.example.AOD.domain.Content;
import com.example.AOD.domain.entity.PlatformData;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.LinkedHashMap;
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
    private Integer releaseYear;
    private String domain;

    private List<PlatformInfo> platforms;
    private Map<String, Object> domainAttributes;


    public ContentDetailDTO(Content content, Map<String, Object> domainAttributes, List<PlatformData> platformData) {
        this.id = content.getContentId();
        this.title = content.getMasterTitle();
        this.posterImageUrl = content.getPosterImageUrl();
        this.synopsis = content.getSynopsis();
        this.originalTitle = content.getOriginalTitle();
        this.releaseYear = content.getReleaseYear();
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
            this.attributes = processAttributes(pd.getAttributes());
        }

        /**
         * attributes 맵을 화면에 표시하기 좋은 형태로 가공하는 메서드
         */
        private Map<String, Object> processAttributes(Map<String, Object> originalAttributes) {
            if (originalAttributes == null) {
                return new LinkedHashMap<>();
            }

            Map<String, Object> processedAttributes = new LinkedHashMap<>();
            List<String> displayKeys = List.of("status", "rating", "view_count", "download_count", "like_count", "price_overview", "genres", "cast", "crew", "developer", "developers", "publisher", "publishers", "author", "runtime", "season_count", "age_rating", "is_free");

            for (String key : displayKeys) {
                if (originalAttributes.containsKey(key)) {
                    Object value = originalAttributes.get(key);

                    if (value == null || value.toString().isEmpty() || value.toString().equals("[]")) {
                        continue;
                    }

                    switch (key) {
                        case "cast":
                            if (value instanceof List) {
                                String names = ((List<Map<String, Object>>) value).stream()
                                        .limit(5)
                                        .map(p -> (String) p.get("name"))
                                        .collect(Collectors.joining(", "));
                                processedAttributes.put(key, names);
                            }
                            break;

                        case "crew":
                            if (value instanceof List) {
                                List<Map<String, Object>> crewList = (List<Map<String, Object>>) value;

                                // 감독 필터링
                                String directors = crewList.stream()
                                        .filter(p -> "Director".equals(p.get("job")) && "Directing".equals(p.get("department")))
                                        .map(p -> (String) p.get("name"))
                                        .distinct()
                                        .collect(Collectors.joining(", "));

                                if (!directors.isEmpty()) {
                                    processedAttributes.put("감독", directors);
                                }

                                // ✅ 핵심 수정: "Story" 직책을 작가 필터링 조건에 추가
                                String writers = crewList.stream()
                                        .filter(p -> "Writer".equals(p.get("job"))
                                                || "Screenplay".equals(p.get("job"))
                                                || "Story".equals(p.get("job"))) // "Story" 조건 추가
                                        .map(p -> (String) p.get("name"))
                                        .distinct()
                                        .collect(Collectors.joining(", "));

                                if (!writers.isEmpty()) {
                                    processedAttributes.put("작가", writers);
                                }
                            }
                            break;

                        case "genres":
                            if (value instanceof List) {
                                if (!((List<?>) value).isEmpty() && ((List<?>) value).get(0) instanceof Map) {
                                    String genreNames = ((List<Map<String, Object>>) value).stream()
                                            .map(g -> (String) g.get("name"))
                                            .collect(Collectors.joining(", "));
                                    processedAttributes.put(key, genreNames);
                                } else {
                                    processedAttributes.put(key, String.join(", ", (List<String>) value));
                                }
                            }
                            break;

                        case "price_overview":
                            if (value instanceof Map) {
                                String price = (String) ((Map<String, Object>) value).get("final_formatted");
                                processedAttributes.put("price", price);
                            }
                            break;

                        case "is_free":
                            processedAttributes.put(key, (Boolean) value ? "무료" : "유료");
                            break;

                        default:
                            processedAttributes.put(key, value);
                            break;
                    }
                }
            }
            return processedAttributes;
        }
    }
}