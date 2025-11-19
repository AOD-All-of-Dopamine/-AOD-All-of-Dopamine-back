package com.example.AOD.ranking.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "content_id")
    private com.example.AOD.domain.Content content;  // 내부 작품 매핑 (저장 시점 매핑, nullable)

    @Column(nullable = false)
    private String title;                       // 작품 제목

    @Column(nullable = false)
    private Integer ranking;                    // 랭킹 순위 (1, 2, 3, ...)

    @Column(nullable = false)
    private String platform;                    // 플랫폼 이름 (NaverWebtoon, Steam, TMDB_MOVIE, etc.)

    private String thumbnailUrl;                // 썸네일 이미지 URL (크롤링 시점 저장)

    // 생성자, 빌더 등 필요에 따라 추가
}
