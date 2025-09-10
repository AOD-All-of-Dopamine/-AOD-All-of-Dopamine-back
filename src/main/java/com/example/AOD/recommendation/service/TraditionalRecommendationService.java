//package com.example.AOD.recommendation.service;
//
//import com.example.AOD.recommendation.domain.UserPreference;
//import com.example.AOD.recommendation.domain.ContentRating;
//import com.example.AOD.recommendation.repository.UserPreferenceRepository;
//import com.example.AOD.recommendation.repository.ContentRatingRepository;
//import com.example.AOD.recommendation.dto.*;
//
//// 플랫폼별 리포지토리 import
//import com.example.AOD.movie.CGV.repository.MovieRepository;
//import com.example.AOD.Novel.NaverSeriesNovel.NaverSeriesNovelRepository;
//import com.example.AOD.Webtoon.NaverWebtoon.repository.WebtoonRepository;
//import com.example.AOD.AV.Netflix.repository.NetflixContentRepository;
//import com.example.AOD.game.StreamAPI.repository.GameRepository;
//
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.jdbc.core.JdbcTemplate;
//import org.springframework.stereotype.Service;
//
//import java.lang.reflect.Field;
//import java.lang.reflect.Method;
//import java.util.*;
//import java.util.stream.Collectors;
//
//@Service
//public class TraditionalRecommendationService {
//
//    @Autowired
//    private UserPreferenceRepository userPreferenceRepository;
//
//    @Autowired
//    private ContentRatingRepository contentRatingRepository;
//
//    // JdbcTemplate 추가 - MoviesController와 동일한 방식 사용
//    @Autowired
//    private JdbcTemplate jdbcTemplate;
//
//    // 기존 리포지토리들은 유지
//    @Autowired
//    private MovieRepository movieRepository;
//
//    @Autowired
//    private NaverSeriesNovelRepository novelRepository;
//
//    @Autowired
//    private WebtoonRepository webtoonRepository;
//
//    @Autowired
//    private NetflixContentRepository netflixRepository;
//
//    @Autowired
//    private GameRepository gameRepository;
//
//    /**
//     * 메인 추천 메서드 - 하이브리드 접근법 (수정된 버전)
//     */
//    public Map<String, List<?>> getRecommendationsForUser(String username) {
//        System.out.println("=== Improved Traditional Recommendation Service START ===");
//        System.out.println("Username: " + username);
//
//        try {
//            // 1. 사용자 평가 데이터 양 확인
//            List<ContentRating> userRatings = contentRatingRepository.findByUsername(username);
//            int userRatingCount = userRatings.size();
//
//            System.out.println("User rating count: " + userRatingCount);
//
//            // 2. 사용자 선호도 조회
//            Optional<UserPreference> preferenceOpt = userPreferenceRepository.findByUsername(username);
//
//            Map<String, List<?>> recommendations;
//
//            if (userRatingCount >= 5) {
//                // 충분한 데이터 -> 협업 필터링 우선
//                System.out.println("Using collaborative filtering approach");
//                recommendations = getCollaborativeRecommendations(username, userRatings, preferenceOpt);
//            } else {
//                // 데이터 부족 -> 인기도 + 컨텐츠 기반
//                System.out.println("Using popularity-based approach");
//                recommendations = getPopularityBasedRecommendations(username, preferenceOpt);
//            }
//
//            // 3. 이미 평가한 콘텐츠 제외 (핵심 추가 부분)
//            recommendations = filterOutRatedContent(username, recommendations);
//
//            return recommendations;
//
//        } catch (Exception e) {
//            System.err.println("Error in getRecommendationsForUser: " + e.getMessage());
//            e.printStackTrace();
//            return getDefaultRecommendations(username); // username 추가
//        }
//    }
//
//    /**
//     * 이미 평가한 콘텐츠를 추천에서 제외하는 메서드 (새로 추가)
//     */
//    private Map<String, List<?>> filterOutRatedContent(String username, Map<String, List<?>> recommendations) {
//        try {
//            System.out.println("=== Filtering out rated content ===");
//
//            // 사용자가 평가한 모든 콘텐츠 ID 조회
//            List<Long> ratedContentIds = contentRatingRepository.findRatedContentIdsByUsername(username);
//            System.out.println("User has rated " + ratedContentIds.size() + " contents: " + ratedContentIds);
//
//            if (ratedContentIds.isEmpty()) {
//                System.out.println("No rated content found, returning original recommendations");
//                return recommendations;
//            }
//
//            Map<String, List<?>> filteredRecommendations = new HashMap<>();
//
//            for (Map.Entry<String, List<?>> entry : recommendations.entrySet()) {
//                String contentType = entry.getKey();
//                List<?> originalList = entry.getValue();
//
//                System.out.println("Filtering " + contentType + " - Original count: " + originalList.size());
//
//                // 각 콘텐츠 타입별로 필터링
//                List<?> filteredList = filterContentList(originalList, ratedContentIds);
//
//                System.out.println("Filtering " + contentType + " - Filtered count: " + filteredList.size());
//
//                // 필터링 후 콘텐츠가 너무 적으면 추가로 가져오기
//                if (filteredList.size() < 3 && originalList.size() > 0) {
//                    System.out.println("Too few contents after filtering, getting more...");
//                    String singleContentType = mapRecommendationKeyToContentType(contentType);
//                    List<?> additionalContent = getAdditionalUnratedContent(singleContentType, ratedContentIds, 5);
//
//                    // 기존 필터링된 결과와 추가 콘텐츠 합치기
//                    List<Object> combinedList = new ArrayList<>();
//                    combinedList.addAll((List<Object>) filteredList);
//                    combinedList.addAll((List<Object>) additionalContent);
//
//                    // 중복 제거 (ID 기준)
//                    filteredList = removeDuplicatesById(combinedList);
//
//                    System.out.println("Added additional content - Final count: " + filteredList.size());
//                }
//
//                filteredRecommendations.put(contentType, filteredList);
//            }
//
//            return filteredRecommendations;
//
//        } catch (Exception e) {
//            System.err.println("Error filtering rated content: " + e.getMessage());
//            e.printStackTrace();
//            return recommendations; // 오류 시 원본 반환
//        }
//    }
//
//    /**
//     * 콘텐츠 리스트에서 이미 평가한 콘텐츠 제외
//     */
//    private List<?> filterContentList(List<?> contentList, List<Long> ratedContentIds) {
//        if (contentList.isEmpty() || ratedContentIds.isEmpty()) {
//            return contentList;
//        }
//
//        return contentList.stream()
//                .filter(content -> {
//                    try {
//                        Long contentId = extractId(content);
//                        boolean isRated = ratedContentIds.contains(contentId);
//                        if (isRated) {
//                            System.out.println("Excluding rated content - ID: " + contentId + ", Title: " + extractStringField(content, "title"));
//                        }
//                        return !isRated;
//                    } catch (Exception e) {
//                        System.err.println("Error extracting ID from content: " + e.getMessage());
//                        return true; // 오류 시 포함
//                    }
//                })
//                .collect(Collectors.toList());
//    }
//
//    /**
//     * 추가 미평가 콘텐츠 가져오기
//     */
//    private List<?> getAdditionalUnratedContent(String contentType, List<Long> ratedContentIds, int limit) {
//        try {
//            System.out.println("Getting additional unrated content for: " + contentType);
//
//            // 더 많은 인기 콘텐츠 가져오기 (기존보다 더 많이)
//            List<?> moreContent = getPopularContentByTypeWithLimit(contentType, limit + ratedContentIds.size());
//
//            // 평가하지 않은 콘텐츠만 필터링
//            return moreContent.stream()
//                    .filter(content -> {
//                        try {
//                            Long contentId = extractId(content);
//                            return !ratedContentIds.contains(contentId);
//                        } catch (Exception e) {
//                            return true;
//                        }
//                    })
//                    .limit(limit)
//                    .collect(Collectors.toList());
//
//        } catch (Exception e) {
//            System.err.println("Error getting additional unrated content: " + e.getMessage());
//            return Collections.emptyList();
//        }
//    }
//
//    /**
//     * ID 기준으로 중복 제거
//     */
//    private List<?> removeDuplicatesById(List<Object> contentList) {
//        Set<Long> seenIds = new HashSet<>();
//        return contentList.stream()
//                .filter(content -> {
//                    try {
//                        Long id = extractId(content);
//                        return seenIds.add(id);
//                    } catch (Exception e) {
//                        return true; // 오류 시 포함
//                    }
//                })
//                .collect(Collectors.toList());
//    }
//
//    /**
//     * 추천 키를 콘텐츠 타입으로 매핑
//     */
//    private String mapRecommendationKeyToContentType(String recommendationKey) {
//        switch (recommendationKey.toLowerCase()) {
//            case "movies": return "movie";
//            case "novels": return "novel";
//            case "webtoons": return "webtoon";
//            case "ott": return "ott";
//            case "games": return "game";
//            default: return recommendationKey.replaceAll("s$", ""); // 단순히 s 제거
//        }
//    }
//
//    /**
//     * 제한된 수의 인기 콘텐츠 조회 (새로 추가)
//     */
//    private List<?> getPopularContentByTypeWithLimit(String contentType, int limit) {
//        try {
//            switch (contentType.toLowerCase()) {
//                case "movie":
//                    return getPopularMoviesFromCommonTableWithLimit(limit);
//                case "novel":
//                    return getPopularNovelsFromCommonTableWithLimit(limit);
//                case "webtoon":
//                    return getPopularWebtoonsFromCommonTableWithLimit(limit);
//                case "ott":
//                    return getPopularOttFromCommonTableWithLimit(limit);
//                case "game":
//                    return getPopularGamesFromCommonTableWithLimit(limit);
//                default:
//                    return new ArrayList<>();
//            }
//        } catch (Exception e) {
//            System.err.println("Error getting popular content with limit for " + contentType + ": " + e.getMessage());
//            return new ArrayList<>();
//        }
//    }
//
//    /**
//     * 제한된 수의 영화 데이터 가져오기
//     */
//    private List<MovieRecommendationDTO> getPopularMoviesFromCommonTableWithLimit(int limit) {
//        try {
//            String sql = """
//            SELECT mc.*,
//                   STRING_AGG(DISTINCT mca.actor, ', ') as actors,
//                   STRING_AGG(DISTINCT mcg.genre, ', ') as genres
//            FROM movie_common mc
//            LEFT JOIN movie_common_actors mca ON mc.id = mca.movie_id
//            LEFT JOIN movie_common_genre mcg ON mc.id = mcg.movie_id
//            GROUP BY mc.id, mc.title, mc.summary, mc.director, mc.image_url, mc.release_date, mc.country, mc.age_rating, mc.rating, mc.running_time, mc.total_audience, mc.is_rerelease, mc.reservation_rate, mc.created_at, mc.updated_at, mc.version
//            ORDER BY mc.rating DESC NULLS LAST
//            LIMIT ?
//            """;
//
//            List<Map<String, Object>> movies = jdbcTemplate.queryForList(sql, limit);
//            return movies.stream()
//                    .map(this::convertMapToMovieDTO)
//                    .collect(Collectors.toList());
//
//        } catch (Exception e) {
//            System.err.println("Error getting movies with limit: " + e.getMessage());
//            return Collections.emptyList();
//        }
//    }
//
//    /**
//     * 제한된 수의 소설 데이터 가져오기
//     */
//    private List<NovelRecommendationDTO> getPopularNovelsFromCommonTableWithLimit(int limit) {
//        try {
//            String sql = """
//            SELECT nc.*,
//                   STRING_AGG(DISTINCT nca.author, ', ') as authors,
//                   STRING_AGG(DISTINCT ncg.genre, ', ') as genres
//            FROM novel_common nc
//            LEFT JOIN novel_common_author nca ON nc.id = nca.novel_id
//            LEFT JOIN novel_common_genre ncg ON nc.id = ncg.novel_id
//            GROUP BY nc.id, nc.title, nc.summary, nc.image_url, nc.age_rating, nc.publisher, nc.status, nc.created_at, nc.updated_at, nc.version
//            ORDER BY nc.created_at DESC
//            LIMIT ?
//            """;
//
//            List<Map<String, Object>> novels = jdbcTemplate.queryForList(sql, limit);
//            return novels.stream()
//                    .map(this::convertMapToNovelDTO)
//                    .collect(Collectors.toList());
//
//        } catch (Exception e) {
//            System.err.println("Error getting novels with limit: " + e.getMessage());
//            return Collections.emptyList();
//        }
//    }
//
//    /**
//     * 제한된 수의 웹툰 데이터 가져오기
//     */
//    private List<WebtoonRecommendationDTO> getPopularWebtoonsFromCommonTableWithLimit(int limit) {
//        try {
//            String sql = """
//            SELECT wc.*,
//                   STRING_AGG(DISTINCT wca.author, ', ') as authors,
//                   STRING_AGG(DISTINCT wcg.genre, ', ') as genres
//            FROM webtoon_common wc
//            LEFT JOIN webtoon_common_author wca ON wc.id = wca.webtoon_id
//            LEFT JOIN webtoon_common_genre wcg ON wc.id = wcg.webtoon_id
//            GROUP BY wc.id, wc.title, wc.summary, wc.image_url, wc.platform, wc.publish_date, wc.created_at, wc.updated_at, wc.version
//            ORDER BY wc.publish_date DESC NULLS LAST
//            LIMIT ?
//            """;
//
//            List<Map<String, Object>> webtoons = jdbcTemplate.queryForList(sql, limit);
//            return webtoons.stream()
//                    .map(this::convertMapToWebtoonDTO)
//                    .collect(Collectors.toList());
//
//        } catch (Exception e) {
//            System.err.println("Error getting webtoons with limit: " + e.getMessage());
//            return Collections.emptyList();
//        }
//    }
//
//    /**
//     * 제한된 수의 OTT 데이터 가져오기
//     */
//    private List<OTTRecommendationDTO> getPopularOttFromCommonTableWithLimit(int limit) {
//        try {
//            String sql = """
//            SELECT oc.*,
//                   STRING_AGG(DISTINCT oca.actor, ', ') as actors,
//                   STRING_AGG(DISTINCT ocg.genre, ', ') as genres
//            FROM ott_common oc
//            LEFT JOIN ott_common_actors oca ON oc.id = oca.ott_id
//            LEFT JOIN ott_common_genre ocg ON oc.id = ocg.ott_id
//            GROUP BY oc.id, oc.title, oc.description, oc.creator, oc.image_url, oc.maturity_rating, oc.thumbnail, oc.type, oc.release_year, oc.created_at, oc.updated_at, oc.version
//            ORDER BY oc.release_year DESC NULLS LAST
//            LIMIT ?
//            """;
//
//            List<Map<String, Object>> ottContent = jdbcTemplate.queryForList(sql, limit);
//            return ottContent.stream()
//                    .map(this::convertMapToOttDTO)
//                    .collect(Collectors.toList());
//
//        } catch (Exception e) {
//            System.err.println("Error getting OTT with limit: " + e.getMessage());
//            return Collections.emptyList();
//        }
//    }
//
//    /**
//     * 제한된 수의 게임 데이터 가져오기
//     */
//    private List<GameRecommendationDTO> getPopularGamesFromCommonTableWithLimit(int limit) {
//        try {
//            System.out.println("=== Fetching games with limit: " + limit + " ===");
//
//            String sql = """
//        SELECT gc.*,
//               STRING_AGG(DISTINCT gcp.publisher, ', ') as publishers,
//               STRING_AGG(DISTINCT gcd.developer, ', ') as developers
//        FROM game_common gc
//        LEFT JOIN game_common_publisher gcp ON gc.id = gcp.game_id
//        LEFT JOIN game_common_developer gcd ON gc.id = gcd.game_id
//        GROUP BY gc.id, gc.title, gc.summary, gc.image_url, gc.platform, gc.required_age, gc.final_price, gc.initial_price, gc.created_at, gc.updated_at, gc.version
//        ORDER BY gc.created_at DESC
//        LIMIT ?
//        """;
//
//            List<Map<String, Object>> games = jdbcTemplate.queryForList(sql, limit);
//            System.out.println("Games from common table count: " + games.size());
//
//            List<GameRecommendationDTO> gameDTOs = new ArrayList<>();
//
//            for (Map<String, Object> game : games) {
//                GameRecommendationDTO dto = new GameRecommendationDTO();
//
//                dto.setId(getLongValue(game, "id"));
//                dto.setTitle(getStringValue(game, "title"));
//                dto.setDescription(getStringValue(game, "summary"));
//
//                String imageUrl = getStringValue(game, "image_url");
//                if (imageUrl == null || imageUrl.trim().isEmpty()) {
//                    imageUrl = "https://via.placeholder.com/460x215/1b2838/66c0f4?text=" +
//                            (dto.getTitle() != null ? dto.getTitle().replaceAll("\\s+", "+") : "Game");
//                }
//
//                dto.setThumbnailUrl(imageUrl);
//                dto.setGenres(Collections.emptyList());
//                dto.setCategories(Collections.emptyList());
//                dto.setDevelopers(parseStringList(getStringValue(game, "developers")));
//                dto.setPublishers(parseStringList(getStringValue(game, "publishers")));
//
//                gameDTOs.add(dto);
//            }
//
//            return gameDTOs;
//
//        } catch (Exception e) {
//            System.err.println("Error getting games with limit: " + e.getMessage());
//            return Collections.emptyList();
//        }
//    }
//
//    /**
//     * 협업 필터링 기반 추천
//     */
//    private Map<String, List<?>> getCollaborativeRecommendations(
//            String username,
//            List<ContentRating> userRatings,
//            Optional<UserPreference> preference) {
//
//        Map<String, List<?>> recommendations = new HashMap<>();
//
//        try {
//            // 각 콘텐츠 타입별로 협업 필터링 적용
//            Set<String> contentTypes = getContentTypesToRecommend(preference);
//
//            for (String contentType : contentTypes) {
//                List<?> recs = getCollaborativeRecommendationsByType(username, contentType, userRatings);
//                recommendations.put(getRecommendationKey(contentType), recs);
//            }
//
//        } catch (Exception e) {
//            System.err.println("Error in collaborative filtering: " + e.getMessage());
//            return getPopularityBasedRecommendations(username, preference);
//        }
//
//        return recommendations;
//    }
//
//    /**
//     * 특정 콘텐츠 타입에 대한 협업 필터링 추천 - 수정된 버전
//     */
//    private List<?> getCollaborativeRecommendationsByType(
//            String username,
//            String contentType,
//            List<ContentRating> userRatings) {
//
//        try {
//            // 1. 해당 타입에서 사용자가 좋아한 콘텐츠들 - NULL 안전 체크
//            Set<Long> likedContentIds = userRatings.stream()
//                    .filter(r -> contentType.equals(r.getContentType()) &&
//                            (r.isLikedSafe() || (r.getRating() != null && r.getRating() >= 4.0))) // 수정된 부분
//                    .map(ContentRating::getContentId)
//                    .collect(Collectors.toSet());
//
//            if (likedContentIds.isEmpty()) {
//                return getPopularContentByType(contentType);
//            }
//
//            // 2. 유사한 취향의 사용자들 찾기
//            List<String> similarUsers = findSimilarUsers(username, contentType, likedContentIds);
//
//            // 3. 유사 사용자들이 좋아한 콘텐츠 추천
//            Set<Long> recommendedIds = getRecommendedContentFromSimilarUsers(
//                    similarUsers, contentType, likedContentIds);
//
//            // 4. 추천 콘텐츠를 DTO로 변환
//            return convertContentIdsToDTO(recommendedIds, contentType);
//
//        } catch (Exception e) {
//            System.err.println("Error in collaborative filtering for " + contentType + ": " + e.getMessage());
//            return getPopularContentByType(contentType);
//        }
//    }
//
//    /**
//     * 유사한 취향의 사용자 찾기 (개선된 로직) - 수정된 버전
//     */
//    private List<String> findSimilarUsers(String username, String contentType, Set<Long> userLikedContent) {
//        try {
//            // 해당 콘텐츠 타입에서 평가가 있는 다른 사용자들
//            List<ContentRating> allRatings = contentRatingRepository.findByContentType(contentType);
//
//            Map<String, Set<Long>> userContentMap = allRatings.stream()
//                    .filter(r -> !username.equals(r.getUsername()) &&
//                            (r.isLikedSafe() || (r.getRating() != null && r.getRating() >= 4.0))) // 수정된 부분
//                    .collect(Collectors.groupingBy(
//                            ContentRating::getUsername,
//                            Collectors.mapping(ContentRating::getContentId, Collectors.toSet())
//                    ));
//
//            // 자카드 유사도 계산하여 유사 사용자 찾기
//            List<UserSimilarity> similarities = new ArrayList<>();
//
//            for (Map.Entry<String, Set<Long>> entry : userContentMap.entrySet()) {
//                String otherUser = entry.getKey();
//                Set<Long> otherUserContent = entry.getValue();
//
//                double similarity = calculateJaccardSimilarity(userLikedContent, otherUserContent);
//                if (similarity > 0.1) { // 최소 10% 유사도
//                    similarities.add(new UserSimilarity(otherUser, similarity));
//                }
//            }
//
//            // 유사도 기준 정렬 후 상위 10명 반환
//            return similarities.stream()
//                    .sorted((a, b) -> Double.compare(b.similarity, a.similarity))
//                    .limit(10)
//                    .map(us -> us.username)
//                    .collect(Collectors.toList());
//
//        } catch (Exception e) {
//            System.err.println("Error finding similar users: " + e.getMessage());
//            return new ArrayList<>();
//        }
//    }
//
//    /**
//     * 자카드 유사도 계산
//     */
//    private double calculateJaccardSimilarity(Set<Long> set1, Set<Long> set2) {
//        if (set1.isEmpty() && set2.isEmpty()) return 0.0;
//
//        Set<Long> intersection = new HashSet<>(set1);
//        intersection.retainAll(set2);
//
//        Set<Long> union = new HashSet<>(set1);
//        union.addAll(set2);
//
//        return (double) intersection.size() / union.size();
//    }
//
//    /**
//     * 유사 사용자들이 좋아한 콘텐츠 추천 - 수정된 버전
//     */
//    private Set<Long> getRecommendedContentFromSimilarUsers(
//            List<String> similarUsers,
//            String contentType,
//            Set<Long> userAlreadyLiked) {
//
//        try {
//            Map<Long, Integer> contentScoreMap = new HashMap<>();
//
//            for (String similarUser : similarUsers) {
//                List<ContentRating> similarUserRatings = contentRatingRepository
//                        .findByUsernameAndContentType(similarUser, contentType);
//
//                for (ContentRating rating : similarUserRatings) {
//                    if (!userAlreadyLiked.contains(rating.getContentId()) &&
//                            (rating.isLikedSafe() || (rating.getRating() != null && rating.getRating() >= 4.0))) { // 수정된 부분
//
//                        contentScoreMap.merge(rating.getContentId(), 1, Integer::sum);
//                    }
//                }
//            }
//
//            // 점수 기준 상위 10개 반환
//            return contentScoreMap.entrySet().stream()
//                    .sorted(Map.Entry.<Long, Integer>comparingByValue().reversed())
//                    .limit(10)
//                    .map(Map.Entry::getKey)
//                    .collect(Collectors.toSet());
//
//        } catch (Exception e) {
//            System.err.println("Error getting recommendations from similar users: " + e.getMessage());
//            return new HashSet<>();
//        }
//    }
//
//    /**
//     * 인기도 기반 추천 (신규 사용자용)
//     */
//    private Map<String, List<?>> getPopularityBasedRecommendations(
//            String username,
//            Optional<UserPreference> preference) {
//
//        Map<String, List<?>> recommendations = new HashMap<>();
//        Set<String> contentTypes = getContentTypesToRecommend(preference);
//
//        for (String contentType : contentTypes) {
//            List<?> popular = getPopularContentByType(contentType);
//            recommendations.put(getRecommendationKey(contentType), popular);
//        }
//
//        return recommendations;
//    }
//
//    /**
//     * 타입별 인기 콘텐츠 조회
//     */
//    private List<?> getPopularContentByType(String contentType) {
//        try {
//            switch (contentType.toLowerCase()) {
//                case "movie":
//                    return getPopularMoviesFromCommonTable();
//                case "novel":
//                    return getPopularNovelsFromCommonTable();
//                case "webtoon":
//                    return getPopularWebtoonsFromCommonTable();
//                case "ott":
//                    return getPopularOttFromCommonTable();
//                case "game":
//                    return getPopularGamesFromCommonTable(); // 여기가 핵심!
//                default:
//                    return new ArrayList<>();
//            }
//        } catch (Exception e) {
//            System.err.println("Error getting popular content for " + contentType + ": " + e.getMessage());
//            return new ArrayList<>();
//        }
//    }
//
//    /**
//     * game_common 테이블에서 게임 데이터 가져오기 (MoviesController와 동일)
//     */
//    /**
//     * 게임 데이터 조회 - 디버깅 및 폴백 로직 포함
//     */
//    private List<GameRecommendationDTO> getPopularGamesFromCommonTable() {
//        try {
//            System.out.println("=== Fetching games from game_common table ===");
//
//            // 1. 먼저 game_common 테이블에 데이터가 있는지 확인
//            String countSql = "SELECT COUNT(*) FROM game_common";
//            Long gameCommonCount = jdbcTemplate.queryForObject(countSql, Long.class);
//            System.out.println("game_common table count: " + gameCommonCount);
//
//            if (gameCommonCount == 0) {
//                System.out.println("game_common table is empty, trying steam_game table...");
//                return getGamesFromSteamTable();
//            }
//
//            String sql = """
//            SELECT gc.*,
//                   STRING_AGG(DISTINCT gcp.publisher, ', ') as publishers,
//                   STRING_AGG(DISTINCT gcd.developer, ', ') as developers
//            FROM game_common gc
//            LEFT JOIN game_common_publisher gcp ON gc.id = gcp.game_id
//            LEFT JOIN game_common_developer gcd ON gc.id = gcd.game_id
//            GROUP BY gc.id, gc.title, gc.summary, gc.image_url, gc.platform, gc.required_age, gc.final_price, gc.initial_price, gc.created_at, gc.updated_at, gc.version
//            ORDER BY gc.created_at DESC
//            LIMIT 10
//            """;
//
//            List<Map<String, Object>> games = jdbcTemplate.queryForList(sql);
//            System.out.println("Games from common table count: " + games.size());
//
//            // 첫 번째 게임의 상세 정보 출력
//            if (!games.isEmpty()) {
//                Map<String, Object> firstGame = games.get(0);
//                System.out.println("First game details:");
//                firstGame.forEach((key, value) ->
//                        System.out.println("  " + key + " = " + value));
//            }
//
//            List<GameRecommendationDTO> gameDTOs = new ArrayList<>();
//
//            for (Map<String, Object> game : games) {
//                GameRecommendationDTO dto = new GameRecommendationDTO();
//
//                dto.setId(getLongValue(game, "id"));
//                dto.setTitle(getStringValue(game, "title"));
//                dto.setDescription(getStringValue(game, "summary"));
//
//                // image_url 필드 직접 사용
//                String imageUrl = getStringValue(game, "image_url");
//                System.out.println("Game ID " + dto.getId() + " image_url: '" + imageUrl + "'");
//
//                // 이미지 URL이 없으면 기본 이미지 제공
//                if (imageUrl == null || imageUrl.trim().isEmpty()) {
//                    imageUrl = "https://via.placeholder.com/460x215/1b2838/66c0f4?text=" +
//                            (dto.getTitle() != null ? dto.getTitle().replaceAll("\\s+", "+") : "Game");
//                    System.out.println("Using placeholder image: " + imageUrl);
//                }
//
//                dto.setThumbnailUrl(imageUrl);
//                dto.setGenres(Collections.emptyList());
//                dto.setCategories(Collections.emptyList());
//                dto.setDevelopers(parseStringList(getStringValue(game, "developers")));
//                dto.setPublishers(parseStringList(getStringValue(game, "publishers")));
//
//                gameDTOs.add(dto);
//
//                System.out.println("Game DTO created - ID: " + dto.getId() +
//                        ", Title: " + dto.getTitle() +
//                        ", ThumbnailUrl: " + dto.getThumbnailUrl());
//            }
//
//            return gameDTOs;
//
//        } catch (Exception e) {
//            System.err.println("Error getting games from common table: " + e.getMessage());
//            e.printStackTrace();
//
//            // 폴백: steam_game 테이블에서 시도
//            System.out.println("Falling back to steam_game table...");
//            return getGamesFromSteamTable();
//        }
//    }
//
//    /**
//     * 폴백: steam_game 테이블에서 게임 데이터 가져오기
//     */
//    private List<GameRecommendationDTO> getGamesFromSteamTable() {
//        try {
//            System.out.println("=== Fetching games from steam_game table ===");
//
//            String sql = """
//            SELECT sg.*,
//                   STRING_AGG(DISTINCT sgp.publisher, ', ') as publishers,
//                   STRING_AGG(DISTINCT sgd.developer, ', ') as developers
//            FROM steam_game sg
//            LEFT JOIN steam_game_publisher sgp ON sg.id = sgp.game_id
//            LEFT JOIN steam_game_developer sgd ON sg.id = sgd.game_id
//            GROUP BY sg.id, sg.title, sg.short_description, sg.header_image, sg.capsule_image, sg.final_price, sg.initial_price
//            LIMIT 10
//            """;
//
//            List<Map<String, Object>> games = jdbcTemplate.queryForList(sql);
//            System.out.println("Games from steam table count: " + games.size());
//
//            List<GameRecommendationDTO> gameDTOs = new ArrayList<>();
//
//            for (Map<String, Object> game : games) {
//                GameRecommendationDTO dto = new GameRecommendationDTO();
//
//                dto.setId(getLongValue(game, "id"));
//                dto.setTitle(getStringValue(game, "title"));
//                dto.setDescription(getStringValue(game, "short_description"));
//
//                // Steam 게임의 경우 header_image 사용
//                String imageUrl = getStringValue(game, "header_image");
//                if (imageUrl == null || imageUrl.trim().isEmpty()) {
//                    imageUrl = getStringValue(game, "capsule_image");
//                }
//
//                System.out.println("Steam Game ID " + dto.getId() + " image_url: '" + imageUrl + "'");
//
//                if (imageUrl == null || imageUrl.trim().isEmpty()) {
//                    imageUrl = "https://via.placeholder.com/460x215/1b2838/66c0f4?text=" +
//                            (dto.getTitle() != null ? dto.getTitle().replaceAll("\\s+", "+") : "Game");
//                }
//
//                dto.setThumbnailUrl(imageUrl);
//                dto.setGenres(Collections.emptyList());
//                dto.setCategories(Collections.emptyList());
//                dto.setDevelopers(parseStringList(getStringValue(game, "developers")));
//                dto.setPublishers(parseStringList(getStringValue(game, "publishers")));
//
//                gameDTOs.add(dto);
//
//                System.out.println("Steam Game DTO created - ID: " + dto.getId() +
//                        ", Title: " + dto.getTitle() +
//                        ", ThumbnailUrl: " + dto.getThumbnailUrl());
//            }
//
//            return gameDTOs;
//
//        } catch (Exception e) {
//            System.err.println("Error getting games from steam table: " + e.getMessage());
//            e.printStackTrace();
//            return Collections.emptyList();
//        }
//    }
//
//    /**
//     * movie_common 테이블에서 영화 데이터 가져오기
//     */
//    private List<MovieRecommendationDTO> getPopularMoviesFromCommonTable() {
//        try {
//            String sql = """
//                SELECT mc.*,
//                       STRING_AGG(DISTINCT mca.actor, ', ') as actors,
//                       STRING_AGG(DISTINCT mcg.genre, ', ') as genres
//                FROM movie_common mc
//                LEFT JOIN movie_common_actors mca ON mc.id = mca.movie_id
//                LEFT JOIN movie_common_genre mcg ON mc.id = mcg.movie_id
//                GROUP BY mc.id, mc.title, mc.summary, mc.director, mc.image_url, mc.release_date, mc.country, mc.age_rating, mc.rating, mc.running_time, mc.total_audience, mc.is_rerelease, mc.reservation_rate, mc.created_at, mc.updated_at, mc.version
//                ORDER BY mc.rating DESC NULLS LAST
//                LIMIT 10
//                """;
//
//            List<Map<String, Object>> movies = jdbcTemplate.queryForList(sql);
//            return movies.stream()
//                    .map(this::convertMapToMovieDTO)
//                    .collect(Collectors.toList());
//
//        } catch (Exception e) {
//            System.err.println("Error getting movies from common table: " + e.getMessage());
//            return Collections.emptyList();
//        }
//    }
//
//    /**
//     * novel_common 테이블에서 웹소설 데이터 가져오기
//     */
//    private List<NovelRecommendationDTO> getPopularNovelsFromCommonTable() {
//        try {
//            String sql = """
//                SELECT nc.*,
//                       STRING_AGG(DISTINCT nca.author, ', ') as authors,
//                       STRING_AGG(DISTINCT ncg.genre, ', ') as genres
//                FROM novel_common nc
//                LEFT JOIN novel_common_author nca ON nc.id = nca.novel_id
//                LEFT JOIN novel_common_genre ncg ON nc.id = ncg.novel_id
//                GROUP BY nc.id, nc.title, nc.summary, nc.image_url, nc.age_rating, nc.publisher, nc.status, nc.created_at, nc.updated_at, nc.version
//                ORDER BY nc.created_at DESC
//                LIMIT 10
//                """;
//
//            List<Map<String, Object>> novels = jdbcTemplate.queryForList(sql);
//            return novels.stream()
//                    .map(this::convertMapToNovelDTO)
//                    .collect(Collectors.toList());
//
//        } catch (Exception e) {
//            System.err.println("Error getting novels from common table: " + e.getMessage());
//            return Collections.emptyList();
//        }
//    }
//
//    /**
//     * webtoon_common 테이블에서 웹툰 데이터 가져오기
//     */
//    private List<WebtoonRecommendationDTO> getPopularWebtoonsFromCommonTable() {
//        try {
//            String sql = """
//                SELECT wc.*,
//                       STRING_AGG(DISTINCT wca.author, ', ') as authors,
//                       STRING_AGG(DISTINCT wcg.genre, ', ') as genres
//                FROM webtoon_common wc
//                LEFT JOIN webtoon_common_author wca ON wc.id = wca.webtoon_id
//                LEFT JOIN webtoon_common_genre wcg ON wc.id = wcg.webtoon_id
//                GROUP BY wc.id, wc.title, wc.summary, wc.image_url, wc.platform, wc.publish_date, wc.created_at, wc.updated_at, wc.version
//                ORDER BY wc.publish_date DESC NULLS LAST
//                LIMIT 10
//                """;
//
//            List<Map<String, Object>> webtoons = jdbcTemplate.queryForList(sql);
//            return webtoons.stream()
//                    .map(this::convertMapToWebtoonDTO)
//                    .collect(Collectors.toList());
//
//        } catch (Exception e) {
//            System.err.println("Error getting webtoons from common table: " + e.getMessage());
//            return Collections.emptyList();
//        }
//    }
//
//    /**
//     * ott_common 테이블에서 OTT 데이터 가져오기
//     */
//    private List<OTTRecommendationDTO> getPopularOttFromCommonTable() {
//        try {
//            String sql = """
//                SELECT oc.*,
//                       STRING_AGG(DISTINCT oca.actor, ', ') as actors,
//                       STRING_AGG(DISTINCT ocg.genre, ', ') as genres
//                FROM ott_common oc
//                LEFT JOIN ott_common_actors oca ON oc.id = oca.ott_id
//                LEFT JOIN ott_common_genre ocg ON oc.id = ocg.ott_id
//                GROUP BY oc.id, oc.title, oc.description, oc.creator, oc.image_url, oc.maturity_rating, oc.thumbnail, oc.type, oc.release_year, oc.created_at, oc.updated_at, oc.version
//                ORDER BY oc.release_year DESC NULLS LAST
//                LIMIT 10
//                """;
//
//            List<Map<String, Object>> ottContent = jdbcTemplate.queryForList(sql);
//            return ottContent.stream()
//                    .map(this::convertMapToOttDTO)
//                    .collect(Collectors.toList());
//
//        } catch (Exception e) {
//            System.err.println("Error getting OTT from common table: " + e.getMessage());
//            return Collections.emptyList();
//        }
//    }
//
//// ============== Map을 DTO로 변환하는 헬퍼 메서드들 ==============
//
//    private MovieRecommendationDTO convertMapToMovieDTO(Map<String, Object> map) {
//        MovieRecommendationDTO dto = new MovieRecommendationDTO();
//        dto.setId(getLongValue(map, "id"));
//        dto.setTitle(getStringValue(map, "title"));
//        dto.setDirector(getStringValue(map, "director"));
//        dto.setThumbnailUrl(getStringValue(map, "image_url"));
//        dto.setGenres(parseStringList(getStringValue(map, "genres")));
//        dto.setActors(parseStringList(getStringValue(map, "actors")));
//        return dto;
//    }
//
//    private NovelRecommendationDTO convertMapToNovelDTO(Map<String, Object> map) {
//        NovelRecommendationDTO dto = new NovelRecommendationDTO();
//        dto.setId(getLongValue(map, "id"));
//        dto.setTitle(getStringValue(map, "title"));
//        dto.setThumbnail(getStringValue(map, "image_url"));
//        dto.setSummary(getStringValue(map, "summary"));
//        dto.setGenres(parseStringList(getStringValue(map, "genres")));
//        dto.setAuthors(parseStringList(getStringValue(map, "authors")));
//        return dto;
//    }
//
//    private WebtoonRecommendationDTO convertMapToWebtoonDTO(Map<String, Object> map) {
//        WebtoonRecommendationDTO dto = new WebtoonRecommendationDTO();
//        dto.setId(getLongValue(map, "id"));
//        dto.setTitle(getStringValue(map, "title"));
//        dto.setThumbnail(getStringValue(map, "image_url"));
//        dto.setSummary(getStringValue(map, "summary"));
//        dto.setGenres(parseStringList(getStringValue(map, "genres")));
//        dto.setAuthors(parseStringList(getStringValue(map, "authors")));
//        return dto;
//    }
//
//    private OTTRecommendationDTO convertMapToOttDTO(Map<String, Object> map) {
//        OTTRecommendationDTO dto = new OTTRecommendationDTO();
//        dto.setId(getLongValue(map, "id"));
//        dto.setTitle(getStringValue(map, "title"));
//        dto.setDescription(getStringValue(map, "description"));
//        dto.setThumbnailUrl(getStringValue(map, "image_url"));
//        dto.setGenres(parseStringList(getStringValue(map, "genres")));
//        dto.setActors(parseStringList(getStringValue(map, "actors")));
//        return dto;
//    }
//
//    // ============== 헬퍼 메서드들 ==============
//
//    private Long getLongValue(Map<String, Object> map, String key) {
//        Object value = map.get(key);
//        if (value instanceof Number) {
//            return ((Number) value).longValue();
//        }
//        return 0L;
//    }
//
//    private String getStringValue(Map<String, Object> map, String key) {
//        Object value = map.get(key);
//        return value != null ? value.toString() : null;
//    }
//
//    private List<String> parseStringList(String str) {
//        if (str == null || str.trim().isEmpty()) {
//            return Collections.emptyList();
//        }
//        return Arrays.stream(str.split(","))
//                .map(String::trim)
//                .filter(s -> !s.isEmpty())
//                .collect(Collectors.toList());
//    }
//
//    /**
//     * 콘텐츠 ID를 DTO로 변환
//     */
//    private List<?> convertContentIdsToDTO(Set<Long> contentIds, String contentType) {
//        try {
//            switch (contentType.toLowerCase()) {
//                case "movie":
//                    return movieRepository.findAllById(contentIds).stream()
//                            .map(this::convertToMovieDTO)
//                            .collect(Collectors.toList());
//                case "novel":
//                    return novelRepository.findAllById(contentIds).stream()
//                            .map(this::convertToNovelDTO)
//                            .collect(Collectors.toList());
//                case "webtoon":
//                    return webtoonRepository.findAllById(contentIds).stream()
//                            .map(this::convertToWebtoonDTO)
//                            .collect(Collectors.toList());
//                case "ott":
//                    return netflixRepository.findAllById(contentIds).stream()
//                            .map(this::convertToOTTDTO)
//                            .collect(Collectors.toList());
//                case "game":
//                    return gameRepository.findAllById(contentIds).stream()
//                            .map(this::convertToGameDTO)
//                            .collect(Collectors.toList());
//                default:
//                    return new ArrayList<>();
//            }
//        } catch (Exception e) {
//            System.err.println("Error converting content IDs to DTO: " + e.getMessage());
//            return new ArrayList<>();
//        }
//    }
//
//    // ============== DTO 변환 메서드들 (안전한 변환) ==============
//
//    /**
//     * 안전한 Movie DTO 변환
//     */
//    private MovieRecommendationDTO convertToMovieDTO(Object movieObj) {
//        MovieRecommendationDTO dto = new MovieRecommendationDTO();
//        try {
//            dto.setId(extractId(movieObj));
//            dto.setTitle(extractStringField(movieObj, "title", "movieTitle", "name"));
//            dto.setDirector(extractStringField(movieObj, "director", "directorName"));
//            dto.setThumbnailUrl(extractStringField(movieObj, "thumbnailUrl", "thumbnail", "posterUrl", "imageUrl"));
//            dto.setRunningTime(extractIntegerField(movieObj, "runningTime", "runtime", "duration"));
//            dto.setRating(extractStringField(movieObj, "rating", "movieRating", "grade"));
//            dto.setAgeRating(extractStringField(movieObj, "ageRating", "ageLimit", "certification"));
//
//            Object releaseDate = extractField(movieObj, "releaseDate", "releaseDay", "openDate");
//            if (releaseDate != null) {
//                dto.setReleaseDate(releaseDate.toString());
//            }
//
//            dto.setGenres(Collections.emptyList());
//            dto.setActors(Collections.emptyList());
//
//        } catch (Exception e) {
//            System.err.println("Error converting movie to DTO: " + e.getMessage());
//            dto.setId(extractId(movieObj));
//            dto.setTitle("영화 제목 불러오기 실패");
//        }
//        return dto;
//    }
//
//    /**
//     * 안전한 Novel DTO 변환
//     */
//    private NovelRecommendationDTO convertToNovelDTO(Object novelObj) {
//        NovelRecommendationDTO dto = new NovelRecommendationDTO();
//        try {
//            dto.setId(extractId(novelObj));
//            dto.setTitle(extractStringField(novelObj, "title", "novelTitle", "name"));
//
//            // image_url을 가장 먼저 시도
//            dto.setThumbnail(extractStringField(novelObj, "image_url", "imageUrl", "thumbnail", "thumbnailUrl", "posterUrl"));
//
//            dto.setSummary(extractStringField(novelObj, "summary", "description", "synopsis"));
//            dto.setUrl(extractStringField(novelObj, "url", "link", "novelUrl"));
//
//            dto.setGenres(Collections.emptyList());
//            dto.setAuthors(Collections.emptyList());
//
//            // 디버깅을 위한 로그
//            System.out.println("Novel DTO - ID: " + dto.getId() +
//                    ", Title: " + dto.getTitle() +
//                    ", Thumbnail: " + dto.getThumbnail());
//
//        } catch (Exception e) {
//            System.err.println("Error converting novel to DTO: " + e.getMessage());
//            dto.setId(extractId(novelObj));
//            dto.setTitle("소설 제목 불러오기 실패");
//            dto.setThumbnail(""); // 빈 문자열로 설정
//        }
//        return dto;
//    }
//
//    /**
//     * 안전한 Webtoon DTO 변환
//     */
//    private WebtoonRecommendationDTO convertToWebtoonDTO(Object webtoonObj) {
//        WebtoonRecommendationDTO dto = new WebtoonRecommendationDTO();
//        try {
//            dto.setId(extractId(webtoonObj));
//            dto.setTitle(extractStringField(webtoonObj, "title", "webtoonTitle", "name"));
//            dto.setThumbnail(extractStringField(webtoonObj, "thumbnail", "thumbnailUrl", "imageUrl"));
//            dto.setSummary(extractStringField(webtoonObj, "summary", "description", "synopsis"));
//            dto.setUrl(extractStringField(webtoonObj, "url", "link", "webtoonUrl"));
//
//            Object publishDate = extractField(webtoonObj, "publishDate", "publishDay", "releaseDate");
//            if (publishDate != null) {
//                dto.setPublishDate(publishDate.toString());
//            }
//
//            dto.setGenres(Collections.emptyList());
//            dto.setAuthors(Collections.emptyList());
//
//        } catch (Exception e) {
//            System.err.println("Error converting webtoon to DTO: " + e.getMessage());
//            dto.setId(extractId(webtoonObj));
//            dto.setTitle("웹툰 제목 불러오기 실패");
//        }
//        return dto;
//    }
//
//    /**
//     * 안전한 OTT DTO 변환
//     */
//    private OTTRecommendationDTO convertToOTTDTO(Object ottObj) {
//        OTTRecommendationDTO dto = new OTTRecommendationDTO();
//        try {
//            dto.setId(extractId(ottObj));
//            dto.setTitle(extractStringField(ottObj, "title", "contentTitle", "name"));
//            dto.setDescription(extractStringField(ottObj, "description", "summary", "synopsis"));
//
//            // image_url을 가장 먼저 시도
//            dto.setThumbnailUrl(extractStringField(ottObj, "image_url", "imageUrl", "thumbnailUrl", "thumbnail", "posterUrl"));
//
//            Object releaseDate = extractField(ottObj, "releaseDate", "releaseDay", "publishDate");
//            if (releaseDate != null) {
//                dto.setReleaseDate(releaseDate.toString());
//            }
//
//            dto.setGenres(Collections.emptyList());
//            dto.setFeatures(Collections.emptyList());
//            dto.setActors(Collections.emptyList());
//
//            // 디버깅을 위한 로그
//            System.out.println("OTT DTO - ID: " + dto.getId() +
//                    ", Title: " + dto.getTitle() +
//                    ", ThumbnailUrl: " + dto.getThumbnailUrl());
//
//        } catch (Exception e) {
//            System.err.println("Error converting OTT to DTO: " + e.getMessage());
//            dto.setId(extractId(ottObj));
//            dto.setTitle("OTT 콘텐츠 제목 불러오기 실패");
//            dto.setThumbnailUrl(""); // 빈 문자열로 설정
//        }
//        return dto;
//    }
//
//    /**
//     * 안전한 Game DTO 변환
//     */
//    private GameRecommendationDTO convertToGameDTO(Object gameObj) {
//        GameRecommendationDTO dto = new GameRecommendationDTO();
//        try {
//            dto.setId(extractId(gameObj));
//            dto.setTitle(extractStringField(gameObj, "title", "gameTitle", "name"));
//            dto.setDescription(extractStringField(gameObj, "description", "summary", "gameDescription"));
//
//            // image_url을 가장 먼저 시도
//            dto.setThumbnailUrl(extractStringField(gameObj, "image_url", "imageUrl", "thumbnailUrl", "thumbnail"));
//
//            Object releaseDate = extractField(gameObj, "releaseDate", "releaseDay", "publishDate");
//            if (releaseDate != null) {
//                dto.setReleaseDate(releaseDate.toString());
//            }
//
//            dto.setGenres(Collections.emptyList());
//            dto.setCategories(Collections.emptyList());
//            dto.setDevelopers(Collections.emptyList());
//            dto.setPublishers(Collections.emptyList());
//
//            // 디버깅을 위한 로그
//            System.out.println("Game DTO - ID: " + dto.getId() +
//                    ", Title: " + dto.getTitle() +
//                    ", ThumbnailUrl: " + dto.getThumbnailUrl());
//
//        } catch (Exception e) {
//            System.err.println("Error converting game to DTO: " + e.getMessage());
//            dto.setId(extractId(gameObj));
//            dto.setTitle("게임 제목 불러오기 실패");
//            dto.setThumbnailUrl(""); // 빈 문자열로 설정
//        }
//        return dto;
//    }
//
//    // ============== 유틸리티 메서드들 ==============
//
//    /**
//     * ID 추출 (여러 가능한 필드명 시도)
//     */
//    private Long extractId(Object obj) {
//        if (obj == null) return 0L;
//
//        String[] idFields = {"id", "contentId", "movieId", "novelId", "webtoonId", "gameId", "ottId"};
//
//        for (String fieldName : idFields) {
//            try {
//                Object value = extractField(obj, fieldName);
//                if (value != null) {
//                    if (value instanceof Long) return (Long) value;
//                    if (value instanceof Integer) return ((Integer) value).longValue();
//                    if (value instanceof String) {
//                        try {
//                            return Long.parseLong((String) value);
//                        } catch (NumberFormatException ignored) {}
//                    }
//                }
//            } catch (Exception ignored) {}
//        }
//        return 0L;
//    }
//
//    /**
//     * String 필드 추출 (여러 가능한 필드명 시도)
//     */
//    private String extractStringField(Object obj, String... fieldNames) {
//        for (String fieldName : fieldNames) {
//            try {
//                Object value = extractField(obj, fieldName);
//                if (value instanceof String && !((String) value).trim().isEmpty()) {
//                    return (String) value;
//                }
//            } catch (Exception ignored) {}
//        }
//        return null;
//    }
//
//    /**
//     * Integer 필드 추출 (여러 가능한 필드명 시도)
//     */
//    private Integer extractIntegerField(Object obj, String... fieldNames) {
//        for (String fieldName : fieldNames) {
//            try {
//                Object value = extractField(obj, fieldName);
//                if (value instanceof Integer) return (Integer) value;
//                if (value instanceof Long) return ((Long) value).intValue();
//                if (value instanceof String) {
//                    try {
//                        return Integer.parseInt((String) value);
//                    } catch (NumberFormatException ignored) {}
//                }
//            } catch (Exception ignored) {}
//        }
//        return null;
//    }
//
//    /**
//     * 안전한 필드 추출 (Reflection + Getter 메서드 모두 시도)
//     */
//    private Object extractField(Object obj, String... fieldNames) {
//        if (obj == null) return null;
//
//        for (String fieldName : fieldNames) {
//            // 1. Getter 메서드 시도
//            try {
//                String getterName = "get" + capitalize(fieldName);
//                Method getter = obj.getClass().getMethod(getterName);
//                return getter.invoke(obj);
//            } catch (Exception ignored) {}
//
//            // 2. 직접 필드 접근 시도
//            try {
//                Field field = obj.getClass().getDeclaredField(fieldName);
//                field.setAccessible(true);
//                return field.get(obj);
//            } catch (Exception ignored) {}
//
//            // 3. 상위 클래스 필드 접근 시도
//            try {
//                Class<?> clazz = obj.getClass();
//                while (clazz != null) {
//                    try {
//                        Field field = clazz.getDeclaredField(fieldName);
//                        field.setAccessible(true);
//                        return field.get(obj);
//                    } catch (NoSuchFieldException e) {
//                        clazz = clazz.getSuperclass();
//                    }
//                }
//            } catch (Exception ignored) {}
//        }
//        return null;
//    }
//
//    /**
//     * 첫 글자 대문자로 변환
//     */
//    private String capitalize(String str) {
//        if (str == null || str.isEmpty()) return str;
//        return str.substring(0, 1).toUpperCase() + str.substring(1);
//    }
//
//    private Set<String> getContentTypesToRecommend(Optional<UserPreference> preference) {
//        if (preference.isPresent() &&
//                preference.get().getPreferredContentTypes() != null &&
//                !preference.get().getPreferredContentTypes().isEmpty()) {
//            return new HashSet<>(preference.get().getPreferredContentTypes());
//        }
//
//        // 기본값: 모든 콘텐츠 타입
//        return Set.of("movie", "novel", "webtoon", "ott", "game");
//    }
//
//    private String getRecommendationKey(String contentType) {
//        switch (contentType.toLowerCase()) {
//            case "movie": return "movies";
//            case "novel": return "novels";
//            case "webtoon": return "webtoons";
//            case "ott": return "ott";
//            case "game": return "games";
//            default: return contentType + "s";
//        }
//    }
//
//    /**
//     * 기본 추천 (수정된 버전 - username 포함)
//     */
//    private Map<String, List<?>> getDefaultRecommendations(String username) {
//        System.out.println("Getting default recommendations for user: " + username);
//        Map<String, List<?>> recommendations = new HashMap<>();
//
//        try {
//            recommendations.put("movies", getPopularContentByType("movie"));
//            recommendations.put("novels", getPopularContentByType("novel"));
//            recommendations.put("webtoons", getPopularContentByType("webtoon"));
//            recommendations.put("ott", getPopularContentByType("ott"));
//            recommendations.put("games", getPopularContentByType("game"));
//
//            // 기본 추천에서도 평가한 콘텐츠 제외
//            recommendations = filterOutRatedContent(username, recommendations);
//
//        } catch (Exception e) {
//            System.err.println("Error getting default recommendations: " + e.getMessage());
//            e.printStackTrace();
//        }
//
//        return recommendations;
//    }
//
//    // 사용자 유사도를 위한 내부 클래스
//    private static class UserSimilarity {
//        String username;
//        double similarity;
//
//        UserSimilarity(String username, double similarity) {
//            this.username = username;
//            this.similarity = similarity;
//        }
//    }
//}