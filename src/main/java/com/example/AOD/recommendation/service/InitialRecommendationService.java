//package com.example.AOD.recommendation.service;
//
//import com.example.AOD.recommendation.domain.UserPreference;
//import com.example.AOD.recommendation.domain.ContentRating;
//import com.example.AOD.recommendation.repository.UserPreferenceRepository;
//import com.example.AOD.recommendation.repository.ContentRatingRepository;
//import com.example.AOD.common.repository.*;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.stereotype.Service;
//
//import java.util.*;
//import java.util.stream.Collectors;
//
//@Service
//public class InitialRecommendationService {
//
//    @Autowired
//    private UserPreferenceRepository userPreferenceRepository;
//
//    @Autowired
//    private ContentRatingRepository contentRatingRepository;
//
//    @Autowired
//    private MovieCommonRepository movieRepository;
//
//    @Autowired
//    private NovelCommonRepository novelRepository;
//
//    @Autowired
//    private WebtoonCommonRepository webtoonRepository;
//
//    @Autowired
//    private OTTCommonRepository ottRepository;
//
//    @Autowired
//    private GameCommonRepository gameRepository;
//
//    public Map<String, List<?>> getInitialRecommendations(String username) {
//        Map<String, List<?>> recommendations = new HashMap<>();
//
//        Optional<UserPreference> preferenceOpt = userPreferenceRepository.findByUsername(username);
//        if (preferenceOpt.isEmpty()) {
//            return getWelcomeRecommendations();
//        }
//
//        UserPreference preference = preferenceOpt.get();
//
//        // 선호 콘텐츠 타입에 따른 초기 추천
//        if (preference.getPreferredContentTypes() != null) {
//            for (String contentType : preference.getPreferredContentTypes()) {
//                switch (contentType.toLowerCase()) {
//                    case "movie":
//                        recommendations.put("movies", getGenreBasedMovies(preference.getPreferredGenres()));
//                        break;
//                    case "novel":
//                        recommendations.put("novels", getGenreBasedNovels(preference.getPreferredGenres()));
//                        break;
//                    case "webtoon":
//                        recommendations.put("webtoons", getGenreBasedWebtoons(preference.getPreferredGenres()));
//                        break;
//                    case "ott":
//                        recommendations.put("ott", getGenreBasedOTT(preference.getPreferredGenres()));
//                        break;
//                    case "game":
//                        recommendations.put("games", getGenreBasedGames(preference.getPreferredGenres()));
//                        break;
//                }
//            }
//        }
//
//        // 추천이 비어있다면 기본 추천 제공
//        if (recommendations.isEmpty()) {
//            return getWelcomeRecommendations();
//        }
//
//        return recommendations;
//    }
//
//    private List<Object> getGenreBasedMovies(List<String> genres) {
//        if (genres == null || genres.isEmpty()) {
//            return movieRepository.findAll().stream().limit(8).collect(Collectors.toList());
//        }
//
//        // 장르 기반 영화 추천 로직
//        return movieRepository.findAll().stream()
//                .filter(movie -> isContentMatchingGenres(movie, genres))
//                .limit(8)
//                .collect(Collectors.toList());
//    }
//
//    private List<Object> getGenreBasedNovels(List<String> genres) {
//        if (genres == null || genres.isEmpty()) {
//            return novelRepository.findAll().stream().limit(8).collect(Collectors.toList());
//        }
//
//        return novelRepository.findAll().stream()
//                .filter(novel -> isContentMatchingGenres(novel, genres))
//                .limit(8)
//                .collect(Collectors.toList());
//    }
//
//    private List<Object> getGenreBasedWebtoons(List<String> genres) {
//        if (genres == null || genres.isEmpty()) {
//            return webtoonRepository.findAll().stream().limit(8).collect(Collectors.toList());
//        }
//
//        return webtoonRepository.findAll().stream()
//                .filter(webtoon -> isContentMatchingGenres(webtoon, genres))
//                .limit(8)
//                .collect(Collectors.toList());
//    }
//
//    private List<Object> getGenreBasedOTT(List<String> genres) {
//        if (genres == null || genres.isEmpty()) {
//            return ottRepository.findAll().stream().limit(8).collect(Collectors.toList());
//        }
//
//        return ottRepository.findAll().stream()
//                .filter(ott -> isContentMatchingGenres(ott, genres))
//                .limit(8)
//                .collect(Collectors.toList());
//    }
//
//    private List<Object> getGenreBasedGames(List<String> genres) {
//        if (genres == null || genres.isEmpty()) {
//            return gameRepository.findAll().stream().limit(8).collect(Collectors.toList());
//        }
//
//        return gameRepository.findAll().stream()
//                .filter(game -> isContentMatchingGenres(game, genres))
//                .limit(8)
//                .collect(Collectors.toList());
//    }
//
//    private boolean isContentMatchingGenres(Object content, List<String> preferredGenres) {
//        try {
//            java.lang.reflect.Field genreField = content.getClass().getDeclaredField("genres");
//            genreField.setAccessible(true);
//            List<String> contentGenres = (List<String>) genreField.get(content);
//
//            if (contentGenres != null && preferredGenres != null) {
//                return contentGenres.stream()
//                        .anyMatch(genre -> preferredGenres.stream()
//                                .anyMatch(preferred -> preferred.toLowerCase().contains(genre.toLowerCase()) ||
//                                        genre.toLowerCase().contains(preferred.toLowerCase())));
//            }
//        } catch (Exception e) {
//            // 장르 정보를 가져올 수 없는 경우 무시
//        }
//        return false;
//    }
//
//    private Map<String, List<?>> getWelcomeRecommendations() {
//        Map<String, List<?>> recommendations = new HashMap<>();
//
//        // 각 카테고리에서 인기/추천 콘텐츠 제공
//        recommendations.put("movies", movieRepository.findAll().stream().limit(5).collect(Collectors.toList()));
//        recommendations.put("novels", novelRepository.findAll().stream().limit(5).collect(Collectors.toList()));
//        recommendations.put("webtoons", webtoonRepository.findAll().stream().limit(5).collect(Collectors.toList()));
//        recommendations.put("ott", ottRepository.findAll().stream().limit(5).collect(Collectors.toList()));
//        recommendations.put("games", gameRepository.findAll().stream().limit(5).collect(Collectors.toList()));
//
//        return recommendations;
//    }
//}