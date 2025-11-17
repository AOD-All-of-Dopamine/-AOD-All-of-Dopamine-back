package com.example.AOD.ranking.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "external_ranking")
public class ExternalRanking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long contentId; // 작품 ID

    @Column(nullable = false)
    private String title; // 작품 제목

    @Column(nullable = false)
    private Integer ranking; // 랭킹 순위

    @Column(nullable = false)
    private String platform; // 랭킹 출처 플랫폼 (e.g., "Naver Webtoon", "KakaoPage")

    // 생성자, 빌더 등 필요에 따라 추가
}
