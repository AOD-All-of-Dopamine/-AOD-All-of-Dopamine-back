package com.example.AOD.recommendation.service;

import com.example.AOD.recommendation.domain.UserPreference;
import com.example.AOD.recommendation.domain.ContentRating;
import com.example.AOD.recommendation.repository.UserPreferenceRepository;
import com.example.AOD.recommendation.repository.ContentRatingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class UserPreferenceService {

    @Autowired
    private UserPreferenceRepository userPreferenceRepository;

    @Autowired
    private ContentRatingRepository contentRatingRepository;

    @Cacheable(value = "user-preferences", key = "#username")
    public UserPreference getUserPreferences(String username) {
        return userPreferenceRepository.findByUsername(username).orElse(null);
    }

    @CacheEvict(value = "user-preferences", key = "#username")
    public UserPreference saveUserPreferences(String username, UserPreference preferences) {
        preferences.setUsername(username);

        Optional<UserPreference> existingPref = userPreferenceRepository.findByUsername(username);
        if (existingPref.isPresent()) {
            preferences.setId(existingPref.get().getId());
        }

        return userPreferenceRepository.save(preferences);
    }

    // 사용자의 평가 기록을 바탕으로 선호도 자동 업데이트
    public void updatePreferencesBasedOnRatings(String username) {
        List<ContentRating> userRatings = contentRatingRepository.findByUsername(username);

        if (userRatings.isEmpty()) {
            return;
        }

        // 높은 평점을 받은 콘텐츠들의 장르 분석
        List<ContentRating> highRatedContent = userRatings.stream()
                .filter(rating -> rating.getRating() != null && rating.getRating() >= 4)
                .collect(Collectors.toList());

        // 장르 빈도 계산
        Map<String, Long> genreFrequency = new HashMap<>();
        Map<String, Long> contentTypeFrequency = new HashMap<>();

        for (ContentRating rating : highRatedContent) {
            contentTypeFrequency.merge(rating.getContentType(), 1L, Long::sum);

            // 실제 구현에서는 콘텐츠 ID로 장르 정보를 조회해야 함
            // 여기서는 예시로 간단히 처리
        }

        // 선호도 업데이트
        Optional<UserPreference> prefOpt = userPreferenceRepository.findByUsername(username);
        if (prefOpt.isPresent()) {
            UserPreference preference = prefOpt.get();

            // 자주 높은 평점을 준 콘텐츠 타입을 선호 목록에 추가
            List<String> preferredTypes = contentTypeFrequency.entrySet().stream()
                    .filter(entry -> entry.getValue() >= 2) // 2개 이상 높은 평점
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());

            preference.setPreferredContentTypes(preferredTypes);
            userPreferenceRepository.save(preference);
        }
    }

    // 사용자 선호도 분석
    public Map<String, Object> analyzeUserPreferences(String username) {
        Map<String, Object> analysis = new HashMap<>();

        UserPreference preferences = getUserPreferences(username);
        List<ContentRating> ratings = contentRatingRepository.findByUsername(username);

        analysis.put("hasPreferences", preferences != null);
        analysis.put("totalRatings", ratings.size());
        analysis.put("averageRating", ratings.stream()
                .filter(r -> r.getRating() != null)
                .mapToInt(ContentRating::getRating)
                .average()
                .orElse(0.0));

        // 콘텐츠 타입별 평가 개수
        Map<String, Long> ratingsByType = ratings.stream()
                .collect(Collectors.groupingBy(ContentRating::getContentType, Collectors.counting()));
        analysis.put("ratingsByContentType", ratingsByType);

        // 좋아요한 콘텐츠 개수
        long likedCount = ratings.stream()
                .filter(r -> Boolean.TRUE.equals(r.getIsLiked()))
                .count();
        analysis.put("likedContentCount", likedCount);

        // 위시리스트 개수
        long wishlistCount = ratings.stream()
                .filter(r -> Boolean.TRUE.equals(r.getIsWishlist()))
                .count();
        analysis.put("wishlistCount", wishlistCount);

        return analysis;
    }

    // 유사한 취향의 사용자 찾기
    public List<String> findSimilarUsers(String username, int limit) {
        UserPreference userPref = getUserPreferences(username);
        if (userPref == null) {
            return new ArrayList<>();
        }

        List<UserPreference> allPreferences = userPreferenceRepository.findAll();

        // 간단한 유사도 계산 (코사인 유사도 등을 사용할 수 있음)
        return allPreferences.stream()
                .filter(pref -> !pref.getUsername().equals(username))
                .filter(pref -> hasCommonPreferences(userPref, pref))
                .limit(limit)
                .map(UserPreference::getUsername)
                .collect(Collectors.toList());
    }

    private boolean hasCommonPreferences(UserPreference pref1, UserPreference pref2) {
        // 공통 장르가 있는지 확인
        if (pref1.getPreferredGenres() != null && pref2.getPreferredGenres() != null) {
            return pref1.getPreferredGenres().stream()
                    .anyMatch(genre -> pref2.getPreferredGenres().contains(genre));
        }

        // 공통 콘텐츠 타입이 있는지 확인
        if (pref1.getPreferredContentTypes() != null && pref2.getPreferredContentTypes() != null) {
            return pref1.getPreferredContentTypes().stream()
                    .anyMatch(type -> pref2.getPreferredContentTypes().contains(type));
        }

        return false;
    }
}