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
//import com.example.AOD.OTT.Netflix.repository.NetflixContentRepository;
//import com.example.AOD.game.StreamAPI.repository.GameRepository;
//
//// 플랫폼별 Entity import (실제 클래스명에 맞게 수정 필요)
////import com.example.AOD.movie.CGV.domain.Movie;
////import com.example.AOD.Novel.NaverSeriesNovel.NaverSeriesNovel;
////import com.example.AOD.Webtoon.NaverWebtoon.domain.Webtoon;
////import com.example.AOD.OTT.Netflix.domain.NetflixContent;
////import com.example.AOD.game.StreamAPI.domain.SteamGame;
//
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.stereotype.Service;
//
//import java.util.*;
//import java.util.stream.Collectors;
//
//@Service
//public class ImprovedRecommendationService {
//
//    @Autowired
//    private UserPreferenceRepository userPreferenceRepository;
//
//    @Autowired
//    private ContentRatingRepository contentRatingRepository;
//
//    // 플랫폼별 리포지토리들
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
//    @Autowired
//    private SafeDTOConverter dtoConverter;
//
//    /**
//     * 메인 추천 메서드 - 하이브리드 접근법
//     */
//    public Map<String, List<?>> getRecommendationsForUser(String username) {
//        System.out.println("=== Improved Recommendation Service START ===");
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
//            if (userRatingCount >= 5) {
//                // 충분한 데이터 -> 협업 필터링 우선
//                System.out.println("Using collaborative filtering approach");
//                return getCollaborativeRecommendations(username, userRatings, preferenceOpt);
//            } else {
//                // 데이터 부족 -> 인기도 + 컨텐츠 기반
//                System.out.println("Using popularity-based approach");
//                return getPopularityBasedRecommendations(username, preferenceOpt);
//            }
//
//        } catch (Exception e) {
//            System.err.println("Error in getRecommendationsForUser: " + e.getMessage());
//            e.printStackTrace();
//            return getDefaultRecommendations();
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
//     * 특정 콘텐츠 타입에 대한 협업 필터링 추천
//     */
//    private List<?> getCollaborativeRecommendationsByType(
//            String username,
//            String contentType,
//            List<ContentRating> userRatings) {
//
//        try {
//            // 1. 해당 타입에서 사용자가 좋아한 콘텐츠들
//            Set<Long> likedContentIds = userRatings.stream()
//                    .filter(r -> contentType.equals(r.getContentType()) &&
//                            (r.getIsLiked() || r.getRating() >= 4.0))
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
//     * 유사한 취향의 사용자 찾기 (개선된 로직)
//     */
//    private List<String> findSimilarUsers(String username, String contentType, Set<Long> userLikedContent) {
//        try {
//            // 해당 콘텐츠 타입에서 평가가 있는 다른 사용자들
//            List<ContentRating> allRatings = contentRatingRepository.findByContentType(contentType);
//
//            Map<String, Set<Long>> userContentMap = allRatings.stream()
//                    .filter(r -> !username.equals(r.getUsername()) &&
//                            (r.getIsLiked() || r.getRating() >= 4.0))
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
//     * 유사 사용자들이 좋아한 콘텐츠 추천
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
//                            (rating.getIsLiked() || rating.getRating() >= 4.0)) {
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
//                    return movieRepository.findAll().stream()
//                            .limit(10)
//                            .map(this::convertToMovieDTO)
//                            .collect(Collectors.toList());
//                case "novel":
//                    return novelRepository.findAll().stream()
//                            .limit(10)
//                            .map(this::convertToNovelDTO)
//                            .collect(Collectors.toList());
//                case "webtoon":
//                    return webtoonRepository.findAll().stream()
//                            .limit(10)
//                            .map(this::convertToWebtoonDTO)
//                            .collect(Collectors.toList());
//                case "ott":
//                    return netflixRepository.findAll().stream()
//                            .limit(10)
//                            .map(this::convertToOTTDTO)
//                            .collect(Collectors.toList());
//                case "game":
//                    return gameRepository.findAll().stream()
//                            .limit(10)
//                            .map(this::convertToGameDTO)
//                            .collect(Collectors.toList());
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
//    // DTO 변환 메서드들 (SafeDTOConverter 사용)
//    private MovieRecommendationDTO convertToMovieDTO(Object movie) {
//        return dtoConverter.convertToMovieDTO(movie);
//    }
//
//    private NovelRecommendationDTO convertToNovelDTO(Object novel) {
//        return dtoConverter.convertToNovelDTO(novel);
//    }
//
//    private WebtoonRecommendationDTO convertToWebtoonDTO(Object webtoon) {
//        return dtoConverter.convertToWebtoonDTO(webtoon);
//    }
//
//    private OTTRecommendationDTO convertToOTTDTO(Object ott) {
//        return dtoConverter.convertToOTTDTO(ott);
//    }
//
//    private GameRecommendationDTO convertToGameDTO(Object game) {
//        return dtoConverter.convertToGameDTO(game);
//    }
//
//    // 유틸리티 메서드들
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
//    private Map<String, List<?>> getDefaultRecommendations() {
//        System.out.println("Getting default recommendations...");
//        Map<String, List<?>> recommendations = new HashMap<>();
//
//        try {
//            recommendations.put("movies", getPopularContentByType("movie"));
//            recommendations.put("novels", getPopularContentByType("novel"));
//            recommendations.put("webtoons", getPopularContentByType("webtoon"));
//            recommendations.put("ott", getPopularContentByType("ott"));
//            recommendations.put("games", getPopularContentByType("game"));
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