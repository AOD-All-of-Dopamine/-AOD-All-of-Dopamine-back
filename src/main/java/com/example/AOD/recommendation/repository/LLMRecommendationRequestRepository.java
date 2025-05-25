package com.example.AOD.recommendation.repository;

import com.example.AOD.recommendation.domain.LLMRecommendationRequest;
import com.example.AOD.recommendation.domain.UserPreference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LLMRecommendationRequestRepository extends JpaRepository<LLMRecommendationRequest, Long> {
    List<LLMRecommendationRequest> findByUsernameOrderByCreatedAtDesc(String username);
    List<LLMRecommendationRequest> findTop10ByUsernameOrderByCreatedAtDesc(String username);
}