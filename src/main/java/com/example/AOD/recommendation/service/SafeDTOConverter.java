package com.example.AOD.recommendation.service;

import com.example.AOD.recommendation.dto.*;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

@Component
public class SafeDTOConverter {

    /**
     * 안전한 Movie DTO 변환 (실제 필드명에 맞게)
     */
    public MovieRecommendationDTO convertToMovieDTO(Object movieObj) {
        MovieRecommendationDTO dto = new MovieRecommendationDTO();
        try {
            // ID 필드 찾기 및 설정
            Long id = extractId(movieObj);
            dto.setId(id);

            // 각 필드를 안전하게 추출
            dto.setTitle(extractStringField(movieObj, "title", "movieTitle", "name"));
            dto.setDirector(extractStringField(movieObj, "director", "directorName"));
            dto.setThumbnailUrl(extractStringField(movieObj, "thumbnailUrl", "thumbnail", "posterUrl", "imageUrl"));
            dto.setRunningTime(extractIntegerField(movieObj, "runningTime", "runtime", "duration"));
            dto.setRating(extractStringField(movieObj, "rating", "movieRating", "grade"));
            dto.setAgeRating(extractStringField(movieObj, "ageRating", "ageLimit", "certification"));

            // 날짜 필드 안전하게 처리
            Object releaseDate = extractField(movieObj, "releaseDate", "releaseDay", "openDate");
            if (releaseDate != null) {
                dto.setReleaseDate(releaseDate.toString());
            }

            // 연관 관계는 빈 리스트로 초기화 (성능상 이유)
            dto.setGenres(java.util.Collections.emptyList());
            dto.setActors(java.util.Collections.emptyList());

        } catch (Exception e) {
            System.err.println("Error converting movie to DTO: " + e.getMessage());
            // 최소한의 정보라도 설정
            dto.setId(extractId(movieObj));
            dto.setTitle("영화 제목 불러오기 실패");
        }
        return dto;
    }

    /**
     * 안전한 Novel DTO 변환
     */
    public NovelRecommendationDTO convertToNovelDTO(Object novelObj) {
        NovelRecommendationDTO dto = new NovelRecommendationDTO();
        try {
            dto.setId(extractId(novelObj));
            dto.setTitle(extractStringField(novelObj, "title", "novelTitle", "name"));
            dto.setThumbnail(extractStringField(novelObj, "thumbnail", "thumbnailUrl", "imageUrl", "posterUrl"));
            dto.setSummary(extractStringField(novelObj, "summary", "description", "synopsis"));
            dto.setUrl(extractStringField(novelObj, "url", "link", "novelUrl"));

            dto.setGenres(java.util.Collections.emptyList());
            dto.setAuthors(java.util.Collections.emptyList());

        } catch (Exception e) {
            System.err.println("Error converting novel to DTO: " + e.getMessage());
            dto.setId(extractId(novelObj));
            dto.setTitle("소설 제목 불러오기 실패");
        }
        return dto;
    }

    /**
     * 안전한 Webtoon DTO 변환
     */
    public WebtoonRecommendationDTO convertToWebtoonDTO(Object webtoonObj) {
        WebtoonRecommendationDTO dto = new WebtoonRecommendationDTO();
        try {
            dto.setId(extractId(webtoonObj));
            dto.setTitle(extractStringField(webtoonObj, "title", "webtoonTitle", "name"));
            dto.setThumbnail(extractStringField(webtoonObj, "thumbnail", "thumbnailUrl", "imageUrl"));
            dto.setSummary(extractStringField(webtoonObj, "summary", "description", "synopsis"));
            dto.setUrl(extractStringField(webtoonObj, "url", "link", "webtoonUrl"));

            Object publishDate = extractField(webtoonObj, "publishDate", "publishDay", "releaseDate");
            if (publishDate != null) {
                dto.setPublishDate(publishDate.toString());
            }

            dto.setGenres(java.util.Collections.emptyList());
            dto.setAuthors(java.util.Collections.emptyList());

        } catch (Exception e) {
            System.err.println("Error converting webtoon to DTO: " + e.getMessage());
            dto.setId(extractId(webtoonObj));
            dto.setTitle("웹툰 제목 불러오기 실패");
        }
        return dto;
    }

    /**
     * 안전한 OTT DTO 변환
     */
    public OTTRecommendationDTO convertToOTTDTO(Object ottObj) {
        OTTRecommendationDTO dto = new OTTRecommendationDTO();
        try {
            dto.setId(extractId(ottObj));
            dto.setTitle(extractStringField(ottObj, "title", "contentTitle", "name"));
            dto.setDescription(extractStringField(ottObj, "description", "summary", "synopsis"));
            dto.setThumbnailUrl(extractStringField(ottObj, "thumbnailUrl", "thumbnail", "imageUrl", "posterUrl"));

            Object releaseDate = extractField(ottObj, "releaseDate", "releaseDay", "publishDate");
            if (releaseDate != null) {
                dto.setReleaseDate(releaseDate.toString());
            }

            dto.setGenres(java.util.Collections.emptyList());
            dto.setFeatures(java.util.Collections.emptyList());
            dto.setActors(java.util.Collections.emptyList());

        } catch (Exception e) {
            System.err.println("Error converting OTT to DTO: " + e.getMessage());
            dto.setId(extractId(ottObj));
            dto.setTitle("OTT 콘텐츠 제목 불러오기 실패");
        }
        return dto;
    }

    /**
     * 안전한 Game DTO 변환
     */
    public GameRecommendationDTO convertToGameDTO(Object gameObj) {
        GameRecommendationDTO dto = new GameRecommendationDTO();
        try {
            dto.setId(extractId(gameObj));
            dto.setTitle(extractStringField(gameObj, "title", "gameTitle", "name"));
            dto.setDescription(extractStringField(gameObj, "description", "summary", "gameDescription"));
            dto.setThumbnailUrl(extractStringField(gameObj, "thumbnailUrl", "thumbnail", "imageUrl"));

            Object releaseDate = extractField(gameObj, "releaseDate", "releaseDay", "publishDate");
            if (releaseDate != null) {
                dto.setReleaseDate(releaseDate.toString());
            }

            dto.setGenres(java.util.Collections.emptyList());
            dto.setCategories(java.util.Collections.emptyList());
            dto.setDevelopers(java.util.Collections.emptyList());
            dto.setPublishers(java.util.Collections.emptyList());

        } catch (Exception e) {
            System.err.println("Error converting game to DTO: " + e.getMessage());
            dto.setId(extractId(gameObj));
            dto.setTitle("게임 제목 불러오기 실패");
        }
        return dto;
    }

    // ============== 유틸리티 메서드들 ==============

    /**
     * ID 추출 (여러 가능한 필드명 시도)
     */
    private Long extractId(Object obj) {
        if (obj == null) return 0L;

        String[] idFields = {"id", "contentId", "movieId", "novelId", "webtoonId", "gameId", "ottId"};

        for (String fieldName : idFields) {
            try {
                Object value = extractField(obj, fieldName);
                if (value != null) {
                    if (value instanceof Long) return (Long) value;
                    if (value instanceof Integer) return ((Integer) value).longValue();
                    if (value instanceof String) {
                        try {
                            return Long.parseLong((String) value);
                        } catch (NumberFormatException ignored) {}
                    }
                }
            } catch (Exception ignored) {}
        }
        return 0L;
    }

    /**
     * String 필드 추출 (여러 가능한 필드명 시도)
     */
    private String extractStringField(Object obj, String... fieldNames) {
        for (String fieldName : fieldNames) {
            try {
                Object value = extractField(obj, fieldName);
                if (value instanceof String && !((String) value).trim().isEmpty()) {
                    return (String) value;
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    /**
     * Integer 필드 추출 (여러 가능한 필드명 시도)
     */
    private Integer extractIntegerField(Object obj, String... fieldNames) {
        for (String fieldName : fieldNames) {
            try {
                Object value = extractField(obj, fieldName);
                if (value instanceof Integer) return (Integer) value;
                if (value instanceof Long) return ((Long) value).intValue();
                if (value instanceof String) {
                    try {
                        return Integer.parseInt((String) value);
                    } catch (NumberFormatException ignored) {}
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    /**
     * 안전한 필드 추출 (Reflection + Getter 메서드 모두 시도)
     */
    private Object extractField(Object obj, String... fieldNames) {
        if (obj == null) return null;

        for (String fieldName : fieldNames) {
            // 1. Getter 메서드 시도
            try {
                String getterName = "get" + capitalize(fieldName);
                Method getter = obj.getClass().getMethod(getterName);
                return getter.invoke(obj);
            } catch (Exception ignored) {}

            // 2. 직접 필드 접근 시도
            try {
                Field field = obj.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(obj);
            } catch (Exception ignored) {}

            // 3. 상위 클래스 필드 접근 시도
            try {
                Class<?> clazz = obj.getClass();
                while (clazz != null) {
                    try {
                        Field field = clazz.getDeclaredField(fieldName);
                        field.setAccessible(true);
                        return field.get(obj);
                    } catch (NoSuchFieldException e) {
                        clazz = clazz.getSuperclass();
                    }
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    /**
     * 첫 글자 대문자로 변환
     */
    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}