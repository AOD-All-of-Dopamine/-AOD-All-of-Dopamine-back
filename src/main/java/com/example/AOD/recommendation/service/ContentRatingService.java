//package com.example.AOD.recommendation.service;
//
//import com.example.AOD.recommendation.domain.ContentRating;
//import com.example.AOD.recommendation.repository.ContentRatingRepository;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.stereotype.Service;
//import org.springframework.data.domain.Page;
//import org.springframework.data.domain.Pageable;
//
//import java.util.*;
//import java.util.stream.Collectors;
//
//@Service
//public class ContentRatingService {
//
//    @Autowired
//    private ContentRatingRepository contentRatingRepository;
//
//    @Autowired
//    private UserPreferenceService userPreferenceService;
//
//    public ContentRating rateContent(String username, String contentType, Long contentId,
//                                     String contentTitle, Integer rating, Boolean isLiked,
//                                     Boolean isWatched, Boolean isWishlist, String review) {
//
//        // 기존 평가가 있는지 확인
//        Optional<ContentRating> existingRating = contentRatingRepository
//                .findByUsernameAndContentTypeAndContentId(username, contentType, contentId);
//
//        ContentRating contentRating;
//        if (existingRating.isPresent()) {
//            contentRating = existingRating.get();
//        } else {
//            contentRating = new ContentRating();
//            contentRating.setUsername(username);
//            contentRating.setContentType(contentType);
//            contentRating.setContentId(contentId);
//            contentRating.setContentTitle(contentTitle);
//        }
//
//        if (rating != null) contentRating.setRating(rating);
//        if (isLiked != null) contentRating.setIsLiked(isLiked);
//        if (isWatched != null) contentRating.setIsWatched(isWatched);
//        if (isWishlist != null) contentRating.setIsWishlist(isWishlist);
//        if (review != null) contentRating.setReview(review);
//
//        ContentRating savedRating = contentRatingRepository.save(contentRating);
//
//        // 평가 후 사용자 선호도 자동 업데이트
//        userPreferenceService.updatePreferencesBasedOnRatings(username);
//
//        return savedRating;
//    }
//
//    public List<ContentRating> getUserRatings(String username) {
//        return contentRatingRepository.findByUsername(username);
//    }
//
//    public List<ContentRating> getUserRatingsByContentType(String username, String contentType) {
//        return contentRatingRepository.findByUsernameAndContentType(username, contentType);
//    }
//
//    public List<ContentRating> getUserLikedContent(String username) {
//        return contentRatingRepository.findByUsernameAndIsLikedTrue(username);
//    }
//
//    public List<ContentRating> getUserWishlist(String username) {
//        return contentRatingRepository.findByUsernameAndIsWishlistTrue(username);
//    }
//
//    public Double getContentAverageRating(String contentType, Long contentId) {
//        return contentRatingRepository.getAverageRatingByContentTypeAndId(contentType, contentId);
//    }
//
//    public Optional<ContentRating> getUserContentRating(String username, String contentType, Long contentId) {
//        return contentRatingRepository.findByUsernameAndContentTypeAndContentId(username, contentType, contentId);
//    }
//
//    public void deleteUserContentRating(String username, String contentType, Long contentId) {
//        Optional<ContentRating> rating = contentRatingRepository
//                .findByUsernameAndContentTypeAndContentId(username, contentType, contentId);
//        rating.ifPresent(contentRatingRepository::delete);
//    }
//
//    // 콘텐츠 추천을 위한 통계 분석
//    public Map<String, Object> getContentStatistics(String contentType, Long contentId) {
//        Map<String, Object> stats = new HashMap<>();
//
//        Double averageRating = getContentAverageRating(contentType, contentId);
//        stats.put("averageRating", averageRating != null ? averageRating : 0.0);
//
//        // 해당 콘텐츠에 대한 모든 평가 조회 (실제로는 쿼리 최적화 필요)
//        List<ContentRating> allRatings = contentRatingRepository.findAll().stream()
//                .filter(r -> contentType.equals(r.getContentType()) && contentId.equals(r.getContentId()))
//                .collect(Collectors.toList());
//
//        stats.put("totalRatings", allRatings.size());
//        stats.put("likeCount", allRatings.stream().filter(r -> Boolean.TRUE.equals(r.getIsLiked())).count());
//        stats.put("wishlistCount", allRatings.stream().filter(r -> Boolean.TRUE.equals(r.getIsWishlist())).count());
//
//        // 평점 분포
//        Map<Integer, Long> ratingDistribution = allRatings.stream()
//                .filter(r -> r.getRating() != null)
//                .collect(Collectors.groupingBy(ContentRating::getRating, Collectors.counting()));
//        stats.put("ratingDistribution", ratingDistribution);
//
//        return stats;
//    }
//
//    // 인기 콘텐츠 조회 (평점 기준)
//    public List<Map<String, Object>> getPopularContentByType(String contentType, int limit) {
//        // 실제 구현에서는 데이터베이스 쿼리로 최적화해야 함
//        Map<Long, List<ContentRating>> contentRatings = contentRatingRepository.findAll().stream()
//                .filter(r -> contentType.equals(r.getContentType()))
//                .collect(Collectors.groupingBy(ContentRating::getContentId));
//
//        return contentRatings.entrySet().stream()
//                .map(entry -> {
//                    Long contentId = entry.getKey();
//                    List<ContentRating> ratings = entry.getValue();
//
//                    Double avgRating = ratings.stream()
//                            .filter(r -> r.getRating() != null)
//                            .mapToInt(ContentRating::getRating)
//                            .average()
//                            .orElse(0.0);
//
//                    Map<String, Object> contentInfo = new HashMap<>();
//                    contentInfo.put("contentId", contentId);
//                    contentInfo.put("contentType", contentType);
//                    contentInfo.put("contentTitle", ratings.get(0).getContentTitle());
//                    contentInfo.put("averageRating", avgRating);
//                    contentInfo.put("ratingCount", ratings.size());
//
//                    return contentInfo;
//                })
//                .sorted((a, b) -> Double.compare((Double) b.get("averageRating"), (Double) a.get("averageRating")))
//                .limit(limit)
//                .collect(Collectors.toList());
//    }
//}
