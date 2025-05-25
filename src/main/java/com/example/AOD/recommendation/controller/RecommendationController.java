// 7. 추천 시스템 컨트롤러
package com.example.AOD.recommendation.controller;

import com.example.AOD.recommendation.domain.UserPreference;
import com.example.AOD.recommendation.domain.ContentRating;
import com.example.AOD.recommendation.domain.LLMRecommendationRequest;
import com.example.AOD.recommendation.service.TraditionalRecommendationService;
import com.example.AOD.recommendation.service.LLMRecommendationService;
import com.example.AOD.recommendation.repository.UserPreferenceRepository;
import com.example.AOD.recommendation.repository.ContentRatingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/recommendations")
@CrossOrigin(origins = "http://localhost:3000")
public class RecommendationController {

    @Autowired
    private TraditionalRecommendationService traditionalRecommendationService;

    @Autowired
    private LLMRecommendationService llmRecommendationService;

    @Autowired
    private UserPreferenceRepository userPreferenceRepository;

    @Autowired
    private ContentRatingRepository contentRatingRepository;

    // 전통적인 추천 시스템
    @GetMapping("/traditional/{username}")
    public ResponseEntity<Map<String, List<?>>> getTraditionalRecommendations(@PathVariable String username) {
        try {
            Map<String, List<?>> recommendations = traditionalRecommendationService.getRecommendationsForUser(username);
            return ResponseEntity.ok(recommendations);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().build();
        }
    }

    // LLM 추천 시스템
    @PostMapping("/llm/{username}")
    public ResponseEntity<Map<String, Object>> getLLMRecommendations(
            @PathVariable String username,
            @RequestBody Map<String, String> request) {
        try {
            String userPrompt = request.get("prompt");
            if (userPrompt == null || userPrompt.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "프롬프트가 필요합니다."));
            }

            Map<String, Object> recommendations = llmRecommendationService.getLLMRecommendations(username, userPrompt);
            return ResponseEntity.ok(recommendations);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(Map.of("error", "추천 생성 중 오류가 발생했습니다."));
        }
    }

    // LLM 추천 히스토리
    @GetMapping("/llm/{username}/history")
    public ResponseEntity<List<LLMRecommendationRequest>> getLLMRecommendationHistory(@PathVariable String username) {
        try {
            List<LLMRecommendationRequest> history = llmRecommendationService.getUserRecommendationHistory(username);
            return ResponseEntity.ok(history);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().build();
        }
    }

    // 사용자 선호도 설정
    @PostMapping("/preferences/{username}")
    public ResponseEntity<UserPreference> setUserPreferences(
            @PathVariable String username,
            @RequestBody UserPreference preferences) {
        try {
            preferences.setUsername(username);

            Optional<UserPreference> existingPref = userPreferenceRepository.findByUsername(username);
            if (existingPref.isPresent()) {
                preferences.setId(existingPref.get().getId());
            }

            UserPreference savedPreferences = userPreferenceRepository.save(preferences);
            return ResponseEntity.ok(savedPreferences);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().build();
        }
    }

    // 사용자 선호도 조회
    @GetMapping("/preferences/{username}")
    public ResponseEntity<UserPreference> getUserPreferences(@PathVariable String username) {
        try {
            Optional<UserPreference> preferences = userPreferenceRepository.findByUsername(username);
            return preferences.map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().build();
        }
    }

    // 콘텐츠 평가 추가/수정
    @PostMapping("/ratings/{username}")
    public ResponseEntity<ContentRating> rateContent(
            @PathVariable String username,
            @RequestBody ContentRating rating) {
        try {
            rating.setUsername(username);

            // 기존 평가가 있는지 확인
            Optional<ContentRating> existingRating = contentRatingRepository
                    .findByUsernameAndContentTypeAndContentId(
                            username, rating.getContentType(), rating.getContentId());

            if (existingRating.isPresent()) {
                rating.setId(existingRating.get().getId());
                rating.setCreatedAt(existingRating.get().getCreatedAt());
            }

            ContentRating savedRating = contentRatingRepository.save(rating);
            return ResponseEntity.ok(savedRating);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().build();
        }
    }

    // 사용자별 평가 목록 조회
    @GetMapping("/ratings/{username}")
    public ResponseEntity<List<ContentRating>> getUserRatings(@PathVariable String username) {
        try {
            List<ContentRating> ratings = contentRatingRepository.findByUsername(username);
            return ResponseEntity.ok(ratings);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().build();
        }
    }

    // 사용자별 좋아요 목록 조회
    @GetMapping("/ratings/{username}/liked")
    public ResponseEntity<List<ContentRating>> getUserLikedContent(@PathVariable String username) {
        try {
            List<ContentRating> likedContent = contentRatingRepository.findByUsernameAndIsLikedTrue(username);
            return ResponseEntity.ok(likedContent);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().build();
        }
    }

    // 사용자별 위시리스트 조회
    @GetMapping("/ratings/{username}/wishlist")
    public ResponseEntity<List<ContentRating>> getUserWishlist(@PathVariable String username) {
        try {
            List<ContentRating> wishlist = contentRatingRepository.findByUsernameAndIsWishlistTrue(username);
            return ResponseEntity.ok(wishlist);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().build();
        }
    }

    // 콘텐츠별 평균 평점 조회
    @GetMapping("/ratings/average/{contentType}/{contentId}")
    public ResponseEntity<Map<String, Object>> getContentAverageRating(
            @PathVariable String contentType,
            @PathVariable Long contentId) {
        try {
            Double averageRating = contentRatingRepository.getAverageRatingByContentTypeAndId(contentType, contentId);
            Map<String, Object> result = Map.of(
                    "contentType", contentType,
                    "contentId", contentId,
                    "averageRating", averageRating != null ? averageRating : 0.0
            );
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().build();
        }
    }

    // 특정 콘텐츠에 대한 사용자 평가 조회
    @GetMapping("/ratings/{username}/{contentType}/{contentId}")
    public ResponseEntity<ContentRating> getUserContentRating(
            @PathVariable String username,
            @PathVariable String contentType,
            @PathVariable Long contentId) {
        try {
            Optional<ContentRating> rating = contentRatingRepository
                    .findByUsernameAndContentTypeAndContentId(username, contentType, contentId);
            return rating.map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().build();
        }
    }

    // 평가 삭제
    @DeleteMapping("/ratings/{username}/{contentType}/{contentId}")
    public ResponseEntity<Void> deleteContentRating(
            @PathVariable String username,
            @PathVariable String contentType,
            @PathVariable Long contentId) {
        try {
            Optional<ContentRating> rating = contentRatingRepository
                    .findByUsernameAndContentTypeAndContentId(username, contentType, contentId);

            if (rating.isPresent()) {
                contentRatingRepository.delete(rating.get());
                return ResponseEntity.ok().build();
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().build();
        }
    }
}