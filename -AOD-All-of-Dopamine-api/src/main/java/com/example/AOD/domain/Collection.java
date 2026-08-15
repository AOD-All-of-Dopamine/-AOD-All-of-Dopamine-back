package com.example.AOD.domain;

import com.example.AOD.user.model.User;
import com.example.shared.entity.Domain;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 사용자 큐레이션 컬렉션 (2026-08 컬렉션 기능).
 * 도메인 단위(게임/웹툰/영화·시리즈/웹소설)로 생성, 공개/비공개 선택.
 * 커버는 저장하지 않고 조회 시 담긴 작품 포스터에서 파생한다.
 */
@Entity
@Table(name = "collections",
       indexes = {
           // 발견 페이지: 공개 컬렉션을 (전체 | 도메인별) 인기/최신 정렬로 조회
           @Index(name = "idx_collections_visibility_domain", columnList = "visibility, domain"),
           // 내 컬렉션 조회
           @Index(name = "idx_collections_user", columnList = "user_id")
       })
@Getter
@Setter
@NoArgsConstructor
public class Collection {

    /** 큐레이터가 고르는 커버 틴트 6종 */
    public enum Tint { PINE, OLIVE, SLATE, TERRACOTTA, MOCHA, PLUM }

    public enum Visibility { PUBLIC, PRIVATE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 60)
    private String title;

    @Column(length = 300)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Domain domain;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Tint tint = Tint.PINE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Visibility visibility = Visibility.PUBLIC;

    // 비정규화 카운트 — 갱신은 반드시 리포지토리의 원자적 UPDATE 쿼리로만 (경합 안전)
    @Column(name = "like_count", nullable = false, columnDefinition = "integer default 0")
    private Integer likeCount = 0;

    @Column(name = "view_count", nullable = false, columnDefinition = "bigint default 0")
    private Long viewCount = 0L;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
