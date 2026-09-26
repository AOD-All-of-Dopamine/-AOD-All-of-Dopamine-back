package com.example.shared.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.vladmihalcea.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Type;

import java.util.List;

/**
 * 외부 플랫폼 랭킹 데이터 엔티티
 * 
 * 타입 정책:
 * - id: Long (PK, 자동 증가)
 * - platformSpecificId: String (플랫폼별 고유 ID, 숫자/문자 혼합 가능)
 * - ranking: Integer (순위, NOT NULL)
 * - thumbnailUrl: String (이미지 URL, nullable)
 * - content: Content (FK, nullable - 매칭 실패 시 null)
 */
@Getter
@Setter
@Entity
@Table(name = "external_ranking")
public class ExternalRanking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;                            // 랭킹 엔트리 고유 ID

    @Column(nullable = false)
    private String platformSpecificId;          // 플랫폼별 고유 ID (예: Steam appId, 네이버 titleId)

    @JsonIgnore  // JSON 직렬화 시 제외 (Hibernate Proxy 문제 방지)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "content_id")
    private Content content;  // 내부 작품 매핑 (저장 시점 매핑, nullable)

    @Column(nullable = false)
    private String title;                       // 작품 제목

    @Column(nullable = false)
    private Integer ranking;                    // 랭킹 순위 (1, 2, 3, ...)

    @Column(nullable = false)
    private String platform;                    // 플랫폼 이름 (NaverWebtoon, Steam, TMDB_MOVIE, etc.)

    private String thumbnailUrl;                // 썸네일 이미지 URL (크롤링 시점 저장)

    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    private List<String> watchProviders;        // OTT 플랫폼 정보 (예: ["Netflix", "Disney Plus", "Watcha"])

    // ===== 랭킹을 받을 때의 신선한 평가 (홈 "오늘의 작품", 2026-09-26) — 콘텐츠 쪽 값은 수집 시점에 굳어 있다 =====
    private Double ratingScore;                 // TMDB vote_average · Steam 긍정 비율(0~1, total_positive/total_reviews)
    private Integer ratingCount;                // TMDB vote_count · Steam total_reviews
    private String ratingLabel;                 // Steam review_score_desc (영문 판정) · TMDB 는 null
    private java.time.Instant fetchedAt;        // 이 한 벌을 받은 시각

    // 생성자, 빌더 등 필요에 따라 추가
}
