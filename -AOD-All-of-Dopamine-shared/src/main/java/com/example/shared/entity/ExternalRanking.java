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

    // ===== 히어로 그림 · 리뷰 한 줄 (홈 "오늘의 작품" 시네마틱, 2026-10-03, V12) — 문턱 통과 · 30위 이내 행만 로고 · 인용을 받는다 =====
    @Column(name = "backdrop_url", length = 1000)
    private String backdropUrl;                 // 넓은 배경 — Steam library_hero 1x · TMDB backdrop w1280
    @Column(name = "logo_url", length = 1000)
    private String logoUrl;                     // 투명 로고 — Steam logo(_koreana).png · TMDB logos w500 (.png)
    @Column(name = "logo_lang", length = 8)
    private String logoLang;                    // ko | other
    @Column(name = "quote_text", length = 400)
    private String quoteText;                   // 정리된 리뷰 한 줄 (ReviewQuotes 통과)
    @Column(name = "quote_author", length = 100)
    private String quoteAuthor;                 // Steam 작성자 이름 (없으면 null — 화면은 "Steam 사용자")
    @Column(name = "quote_votes")
    private Integer quoteVotes;                 // 도움이 됨 수
    @Column(name = "quote_hours")
    private Integer quoteHours;                 // 리뷰 당시 플레이 시간(시간)
    @Column(name = "quote_url", length = 1000)
    private String quoteUrl;                    // 원문 주소
    @Column(name = "quote_review_id", length = 32)
    private String quoteReviewId;               // Steam recommendationid — 차단 목록용

    // 이번 수집에서 "확인했는지" — 확인했으면(없다고 확인 포함) 덮고, 호출이 실패했으면 옛 값을 둔다 (RankingUpsertHelper)
    @Transient
    private boolean backdropChecked;
    @Transient
    private boolean logoChecked;
    @Transient
    private boolean quoteChecked;

    // 생성자, 빌더 등 필요에 따라 추가
}
