package com.example.AOD.recommendation.service;

import com.example.AOD.recommendation.domain.UserPreference;
import com.example.AOD.recommendation.domain.ContentRating;
import com.example.AOD.recommendation.repository.UserPreferenceRepository;
import com.example.AOD.recommendation.repository.ContentRatingRepository;
import com.example.AOD.recommendation.dto.*;

// 플랫폼별 리포지토리 import
import com.example.AOD.movie.CGV.repository.MovieRepository;
import com.example.AOD.Novel.NaverSeriesNovel.repository.NaverSeriesNovelRepository;
import com.example.AOD.Webtoon.NaverWebtoon.repository.WebtoonRepository;
import com.example.AOD.OTT.Netflix.repository.NetflixContentRepository;
import com.example.AOD.game.StreamAPI.repository.GameRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TraditionalRecommendationService {

    @Autowired
    private UserPreferenceRepository userPreferenceRepository;

    @Autowired
    private ContentRatingRepository contentRatingRepository;

    // 플랫폼별 리포지토리들
    @Autowired
    private MovieRepository movieRepository;

    @Autowired
    private NaverSeriesNovelRepository novelRepository;

    @Autowired
    private WebtoonRepository webtoonRepository;

    @Autowired
    private NetflixContentRepository netflixRepository;

    @Autowired
    private GameRepository gameRepository;

    /**
     * 메인 추천 메서드 - 하이브리드 접근법
     */
    public Map<String, List<?>> getRecommendationsForUser(String username) {
        System.out.println("=== Improved Traditional Recommendation Service START ===");
        System.out.println("Username: " + username);

        try {
            // 1. 사용자 평가 데이터 양 확인
            List<ContentRating> userRatings = contentRatingRepository.findByUsername(username);
            int userRatingCount = userRatings.size();

            System.out.println("User rating count: " + userRatingCount);

            // 2. 사용자 선호도 조회
            Optional<UserPreference> preferenceOpt = userPreferenceRepository.findByUsername(username);

            if (userRatingCount >= 5) {
                // 충분한 데이터 -> 협업 필터링 우선
                System.out.println("Using collaborative filtering approach");
                return getCollaborativeRecommendations(username, userRatings, preferenceOpt);
            } else {
                // 데이터 부족 -> 인기도 + 컨텐츠 기반
                System.out.println("Using popularity-based approach");
                return getPopularityBasedRecommendations(username, preferenceOpt);
            }

        } catch (Exception e) {
            System.err.println("Error in getRecommendationsForUser: " + e.getMessage());
            e.printStackTrace();
            return getDefaultRecommendations();
        }
    }

    /**
     * 협업 필터링 기반 추천
     */
    private Map<String, List<?>> getCollaborativeRecommendations(
            String username,
            List<ContentRating> userRatings,
            Optional<UserPreference> preference) {

        Map<String, List<?>> recommendations = new HashMap<>();

        try {
            // 각 콘텐츠 타입별로 협업 필터링 적용
            Set<String> contentTypes = getContentTypesToRecommend(preference);

            for (String contentType : contentTypes) {
                List<?> recs = getCollaborativeRecommendationsByType(username, contentType, userRatings);
                recommendations.put(getRecommendationKey(contentType), recs);
            }

        } catch (Exception e) {
            System.err.println("Error in collaborative filtering: " + e.getMessage());
            return getPopularityBasedRecommendations(username, preference);
        }

        return recommendations;
    }

    /**
     * 특정 콘텐츠 타입에 대한 협업 필터링 추천 - 수정된 버전
     */
    private List<?> getCollaborativeRecommendationsByType(
            String username,
            String contentType,
            List<ContentRating> userRatings) {

        try {
            // 1. 해당 타입에서 사용자가 좋아한 콘텐츠들 - NULL 안전 체크
            Set<Long> likedContentIds = userRatings.stream()
                    .filter(r -> contentType.equals(r.getContentType()) &&
                            (r.isLikedSafe() || (r.getRating() != null && r.getRating() >= 4.0))) // 수정된 부분
                    .map(ContentRating::getContentId)
                    .collect(Collectors.toSet());

            if (likedContentIds.isEmpty()) {
                return getPopularContentByType(contentType);
            }

            // 2. 유사한 취향의 사용자들 찾기
            List<String> similarUsers = findSimilarUsers(username, contentType, likedContentIds);

            // 3. 유사 사용자들이 좋아한 콘텐츠 추천
            Set<Long> recommendedIds = getRecommendedContentFromSimilarUsers(
                    similarUsers, contentType, likedContentIds);

            // 4. 추천 콘텐츠를 DTO로 변환
            return convertContentIdsToDTO(recommendedIds, contentType);

        } catch (Exception e) {
            System.err.println("Error in collaborative filtering for " + contentType + ": " + e.getMessage());
            return getPopularContentByType(contentType);
        }
    }

    /**
     * 유사한 취향의 사용자 찾기 (개선된 로직) - 수정된 버전
     */
    private List<String> findSimilarUsers(String username, String contentType, Set<Long> userLikedContent) {
        try {
            // 해당 콘텐츠 타입에서 평가가 있는 다른 사용자들
            List<ContentRating> allRatings = contentRatingRepository.findByContentType(contentType);

            Map<String, Set<Long>> userContentMap = allRatings.stream()
                    .filter(r -> !username.equals(r.getUsername()) &&
                            (r.isLikedSafe() || (r.getRating() != null && r.getRating() >= 4.0))) // 수정된 부분
                    .collect(Collectors.groupingBy(
                            ContentRating::getUsername,
                            Collectors.mapping(ContentRating::getContentId, Collectors.toSet())
                    ));

            // 자카드 유사도 계산하여 유사 사용자 찾기
            List<UserSimilarity> similarities = new ArrayList<>();

            for (Map.Entry<String, Set<Long>> entry : userContentMap.entrySet()) {
                String otherUser = entry.getKey();
                Set<Long> otherUserContent = entry.getValue();

                double similarity = calculateJaccardSimilarity(userLikedContent, otherUserContent);
                if (similarity > 0.1) { // 최소 10% 유사도
                    similarities.add(new UserSimilarity(otherUser, similarity));
                }
            }

            // 유사도 기준 정렬 후 상위 10명 반환
            return similarities.stream()
                    .sorted((a, b) -> Double.compare(b.similarity, a.similarity))
                    .limit(10)
                    .map(us -> us.username)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            System.err.println("Error finding similar users: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 자카드 유사도 계산
     */
    private double calculateJaccardSimilarity(Set<Long> set1, Set<Long> set2) {
        if (set1.isEmpty() && set2.isEmpty()) return 0.0;

        Set<Long> intersection = new HashSet<>(set1);
        intersection.retainAll(set2);

        Set<Long> union = new HashSet<>(set1);
        union.addAll(set2);

        return (double) intersection.size() / union.size();
    }

    /**
     * 유사 사용자들이 좋아한 콘텐츠 추천 - 수정된 버전
     */
    private Set<Long> getRecommendedContentFromSimilarUsers(
            List<String> similarUsers,
            String contentType,
            Set<Long> userAlreadyLiked) {

        try {
            Map<Long, Integer> contentScoreMap = new HashMap<>();

            for (String similarUser : similarUsers) {
                List<ContentRating> similarUserRatings = contentRatingRepository
                        .findByUsernameAndContentType(similarUser, contentType);

                for (ContentRating rating : similarUserRatings) {
                    if (!userAlreadyLiked.contains(rating.getContentId()) &&
                            (rating.isLikedSafe() || (rating.getRating() != null && rating.getRating() >= 4.0))) { // 수정된 부분

                        contentScoreMap.merge(rating.getContentId(), 1, Integer::sum);
                    }
                }
            }

            // 점수 기준 상위 10개 반환
            return contentScoreMap.entrySet().stream()
                    .sorted(Map.Entry.<Long, Integer>comparingByValue().reversed())
                    .limit(10)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toSet());

        } catch (Exception e) {
            System.err.println("Error getting recommendations from similar users: " + e.getMessage());
            return new HashSet<>();
        }
    }

    /**
     * 인기도 기반 추천 (신규 사용자용)
     */
    private Map<String, List<?>> getPopularityBasedRecommendations(
            String username,
            Optional<UserPreference> preference) {

        Map<String, List<?>> recommendations = new HashMap<>();
        Set<String> contentTypes = getContentTypesToRecommend(preference);

        for (String contentType : contentTypes) {
            List<?> popular = getPopularContentByType(contentType);
            recommendations.put(getRecommendationKey(contentType), popular);
        }

        return recommendations;
    }

    /**
     * 타입별 인기 콘텐츠 조회
     */
    private List<?> getPopularContentByType(String contentType) {
        try {
            switch (contentType.toLowerCase()) {
                case "movie":
                    return movieRepository.findAll().stream()
                            .limit(10)
                            .map(this::convertToMovieDTO)
                            .collect(Collectors.toList());
                case "novel":
                    return novelRepository.findAll().stream()
                            .limit(10)
                            .map(this::convertToNovelDTO)
                            .collect(Collectors.toList());
                case "webtoon":
                    return webtoonRepository.findAll().stream()
                            .limit(10)
                            .map(this::convertToWebtoonDTO)
                            .collect(Collectors.toList());
                case "ott":
                    return netflixRepository.findAll().stream()
                            .limit(10)
                            .map(this::convertToOTTDTO)
                            .collect(Collectors.toList());
                case "game":
                    return gameRepository.findAll().stream()
                            .limit(10)
                            .map(this::convertToGameDTO)
                            .collect(Collectors.toList());
                default:
                    return new ArrayList<>();
            }
        } catch (Exception e) {
            System.err.println("Error getting popular content for " + contentType + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 콘텐츠 ID를 DTO로 변환
     */
    private List<?> convertContentIdsToDTO(Set<Long> contentIds, String contentType) {
        try {
            switch (contentType.toLowerCase()) {
                case "movie":
                    return movieRepository.findAllById(contentIds).stream()
                            .map(this::convertToMovieDTO)
                            .collect(Collectors.toList());
                case "novel":
                    return novelRepository.findAllById(contentIds).stream()
                            .map(this::convertToNovelDTO)
                            .collect(Collectors.toList());
                case "webtoon":
                    return webtoonRepository.findAllById(contentIds).stream()
                            .map(this::convertToWebtoonDTO)
                            .collect(Collectors.toList());
                case "ott":
                    return netflixRepository.findAllById(contentIds).stream()
                            .map(this::convertToOTTDTO)
                            .collect(Collectors.toList());
                case "game":
                    return gameRepository.findAllById(contentIds).stream()
                            .map(this::convertToGameDTO)
                            .collect(Collectors.toList());
                default:
                    return new ArrayList<>();
            }
        } catch (Exception e) {
            System.err.println("Error converting content IDs to DTO: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    // ============== DTO 변환 메서드들 (안전한 변환) ==============

    /**
     * 안전한 Movie DTO 변환
     */
    private MovieRecommendationDTO convertToMovieDTO(Object movieObj) {
        MovieRecommendationDTO dto = new MovieRecommendationDTO();
        try {
            dto.setId(extractId(movieObj));
            dto.setTitle(extractStringField(movieObj, "title", "movieTitle", "name"));
            dto.setDirector(extractStringField(movieObj, "director", "directorName"));
            dto.setThumbnailUrl(extractStringField(movieObj, "thumbnailUrl", "thumbnail", "posterUrl", "imageUrl"));
            dto.setRunningTime(extractIntegerField(movieObj, "runningTime", "runtime", "duration"));
            dto.setRating(extractStringField(movieObj, "rating", "movieRating", "grade"));
            dto.setAgeRating(extractStringField(movieObj, "ageRating", "ageLimit", "certification"));

            Object releaseDate = extractField(movieObj, "releaseDate", "releaseDay", "openDate");
            if (releaseDate != null) {
                dto.setReleaseDate(releaseDate.toString());
            }

            dto.setGenres(Collections.emptyList());
            dto.setActors(Collections.emptyList());

        } catch (Exception e) {
            System.err.println("Error converting movie to DTO: " + e.getMessage());
            dto.setId(extractId(movieObj));
            dto.setTitle("영화 제목 불러오기 실패");
        }
        return dto;
    }

    /**
     * 안전한 Novel DTO 변환
     */
    private NovelRecommendationDTO convertToNovelDTO(Object novelObj) {
        NovelRecommendationDTO dto = new NovelRecommendationDTO();
        try {
            dto.setId(extractId(novelObj));
            dto.setTitle(extractStringField(novelObj, "title", "novelTitle", "name"));
            dto.setThumbnail(extractStringField(novelObj, "thumbnail", "thumbnailUrl", "imageUrl", "posterUrl"));
            dto.setSummary(extractStringField(novelObj, "summary", "description", "synopsis"));
            dto.setUrl(extractStringField(novelObj, "url", "link", "novelUrl"));

            dto.setGenres(Collections.emptyList());
            dto.setAuthors(Collections.emptyList());

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
    private WebtoonRecommendationDTO convertToWebtoonDTO(Object webtoonObj) {
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

            dto.setGenres(Collections.emptyList());
            dto.setAuthors(Collections.emptyList());

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
    private OTTRecommendationDTO convertToOTTDTO(Object ottObj) {
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

            dto.setGenres(Collections.emptyList());
            dto.setFeatures(Collections.emptyList());
            dto.setActors(Collections.emptyList());

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
    private GameRecommendationDTO convertToGameDTO(Object gameObj) {
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

            dto.setGenres(Collections.emptyList());
            dto.setCategories(Collections.emptyList());
            dto.setDevelopers(Collections.emptyList());
            dto.setPublishers(Collections.emptyList());

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

    private Set<String> getContentTypesToRecommend(Optional<UserPreference> preference) {
        if (preference.isPresent() &&
                preference.get().getPreferredContentTypes() != null &&
                !preference.get().getPreferredContentTypes().isEmpty()) {
            return new HashSet<>(preference.get().getPreferredContentTypes());
        }

        // 기본값: 모든 콘텐츠 타입
        return Set.of("movie", "novel", "webtoon", "ott", "game");
    }

    private String getRecommendationKey(String contentType) {
        switch (contentType.toLowerCase()) {
            case "movie": return "movies";
            case "novel": return "novels";
            case "webtoon": return "webtoons";
            case "ott": return "ott";
            case "game": return "games";
            default: return contentType + "s";
        }
    }

    private Map<String, List<?>> getDefaultRecommendations() {
        System.out.println("Getting default recommendations...");
        Map<String, List<?>> recommendations = new HashMap<>();

        try {
            recommendations.put("movies", getPopularContentByType("movie"));
            recommendations.put("novels", getPopularContentByType("novel"));
            recommendations.put("webtoons", getPopularContentByType("webtoon"));
            recommendations.put("ott", getPopularContentByType("ott"));
            recommendations.put("games", getPopularContentByType("game"));

        } catch (Exception e) {
            System.err.println("Error getting default recommendations: " + e.getMessage());
            e.printStackTrace();
        }

        return recommendations;
    }

    // 사용자 유사도를 위한 내부 클래스
    private static class UserSimilarity {
        String username;
        double similarity;

        UserSimilarity(String username, double similarity) {
            this.username = username;
            this.similarity = similarity;
        }
    }
}