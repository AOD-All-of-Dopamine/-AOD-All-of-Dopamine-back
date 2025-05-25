package com.example.AOD.recommendation.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "llm_recommendation_requests")
@Getter
@Setter
@NoArgsConstructor
public class LLMRecommendationRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String username;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String userPrompt;

    @Column(columnDefinition = "TEXT")
    private String llmResponse;

    @Column(columnDefinition = "TEXT")
    private String recommendedContentIds;

    private Integer tokensUsed;
    private Integer responseTimeMs;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}