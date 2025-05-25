package com.example.AOD.recommendation.service;

import com.example.AOD.recommendation.domain.UserPreference;
import com.example.AOD.recommendation.domain.ContentRating;
import com.example.AOD.recommendation.repository.UserPreferenceRepository;
import com.example.AOD.recommendation.repository.ContentRatingRepository;
import com.example.AOD.common.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class TraditionalRecommendationService {

    @Autowired
    private UserPreferenceRepository userPreferenceRepository;

    @Autowired
    private ContentRatingRepository contentRatingRepository;

    @Autowired
    private MovieCommonRepository movieRepository;

    @Autowired
    private NovelCommonRepository novelRepository;

    @Autowired
    private WebtoonCommonRepository webtoonRepository;

    @Autowired
    private OTTCommonRepository ottRepository;

    @Autowired
    private GameCommonRepository gameRepository;

    public Map<String, List<?>> getRecommendationsForUser(String username) {
        Map<String, List<?>> recommendations = new HashMap<>();

        Optional<UserPreference> preferenceOpt = userPreferenceRepository.findByUsername(username);
        if (preferenceOpt.isEmpty()) {
            return getDefaultRecommendations();
        }

        UserPreference preference = preferenceOpt.get();
        List<ContentRating> userRatings = contentRatingRepository.findByUsername(username);

        // 각 콘텐츠 타입별로 추천
        if (preference.getPreferredContentTypes().contains("movie")) {
            recommendations.put("movies", getMovieRecommendations(preference, userRatings));
        }

        if (preference.getPreferredContentTypes().contains("novel")) {
            recommendations.put("novels", getNovelRecommendations(preference, userRatings));
        }

        if (preference.getPreferredContentTypes().contains("webtoon")) {
            recommendations.put("webtoons", getWebtoonRecommendations(preference, userRatings));
        }

        if (preference.getPreferredContentTypes().contains("ott")) {
            recommendations.put("ott", getOTTRecommendations(preference, userRatings));
        }

        if (preference.getPreferredContentTypes().contains("game")) {
            recommendations.put("games", getGameRecommendations(preference, userRatings));
        }

        return recommendations;
    }

    private List<Object> getMovieRecommendations(UserPreference preference, List<ContentRating> userRatings) {
        // 이미 평가한 영화들 제외
        Set<Long> ratedMovieIds = userRatings.stream()
                .filter(r -> "movie".equals(r.getContentType()))
                .map(ContentRating::getContentId)
                .collect(Collectors.toSet());

        return movieRepository.findAll().stream()
                .filter(movie -> !ratedMovieIds.contains(movie.getId()))
                .filter(movie -> isMovieMatchingPreference(movie, preference))
                .limit(10)
                .collect(Collectors.toList());
    }

    private boolean isMovieMatchingPreference(Object movie, UserPreference preference) {
        // 리플렉션을 사용하여 영화의 장르가 사용자 선호 장르와 일치하는지 확인
        try {
            java.lang.reflect.Field genreField = movie.getClass().getDeclaredField("genre");
            genreField.setAccessible(true);
            List<String> movieGenres = (List<String>) genreField.get(movie);

            if (movieGenres != null && preference.getPreferredGenres() != null) {
                return movieGenres.stream()
                        .anyMatch(genre -> preference.getPreferredGenres().contains(genre));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return true; // 장르 정보가 없으면 기본적으로 포함
    }

    // 다른 콘텐츠 타입들에 대한 유사한 메서드들...
    private List<Object> getNovelRecommendations(UserPreference preference, List<ContentRating> userRatings) {
        Set<Long> ratedNovelIds = userRatings.stream()
                .filter(r -> "novel".equals(r.getContentType()))
                .map(ContentRating::getContentId)
                .collect(Collectors.toSet());

        return novelRepository.findAll().stream()
                .filter(novel -> !ratedNovelIds.contains(novel.getId()))
                .limit(10)
                .collect(Collectors.toList());
    }

    private List<Object> getWebtoonRecommendations(UserPreference preference, List<ContentRating> userRatings) {
        Set<Long> ratedWebtoonIds = userRatings.stream()
                .filter(r -> "webtoon".equals(r.getContentType()))
                .map(ContentRating::getContentId)
                .collect(Collectors.toSet());

        return webtoonRepository.findAll().stream()
                .filter(webtoon -> !ratedWebtoonIds.contains(webtoon.getId()))
                .limit(10)
                .collect(Collectors.toList());
    }

    private List<Object> getOTTRecommendations(UserPreference preference, List<ContentRating> userRatings) {
        Set<Long> ratedOTTIds = userRatings.stream()
                .filter(r -> "ott".equals(r.getContentType()))
                .map(ContentRating::getContentId)
                .collect(Collectors.toSet());

        return ottRepository.findAll().stream()
                .filter(ott -> !ratedOTTIds.contains(ott.getId()))
                .limit(10)
                .collect(Collectors.toList());
    }

    private List<Object> getGameRecommendations(UserPreference preference, List<ContentRating> userRatings) {
        Set<Long> ratedGameIds = userRatings.stream()
                .filter(r -> "game".equals(r.getContentType()))
                .map(ContentRating::getContentId)
                .collect(Collectors.toSet());

        return gameRepository.findAll().stream()
                .filter(game -> !ratedGameIds.contains(game.getId()))
                .limit(10)
                .collect(Collectors.toList());
    }

    private Map<String, List<?>> getDefaultRecommendations() {
        Map<String, List<?>> recommendations = new HashMap<>();

        // 기본 추천: 각 카테고리에서 인기 콘텐츠 몇 개씩
        recommendations.put("movies", movieRepository.findAll().stream().limit(5).collect(Collectors.toList()));
        recommendations.put("novels", novelRepository.findAll().stream().limit(5).collect(Collectors.toList()));
        recommendations.put("webtoons", webtoonRepository.findAll().stream().limit(5).collect(Collectors.toList()));
        recommendations.put("ott", ottRepository.findAll().stream().limit(5).collect(Collectors.toList()));
        recommendations.put("games", gameRepository.findAll().stream().limit(5).collect(Collectors.toList()));

        return recommendations;
    }
}