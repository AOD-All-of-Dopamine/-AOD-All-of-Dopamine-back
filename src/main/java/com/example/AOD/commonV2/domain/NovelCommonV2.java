package com.example.AOD.commonV2.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Entity
@Table(name = "novel_common_v2")
@Getter
@Setter
@NoArgsConstructor
public class NovelCommonV2 {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 기본 정보
    @Column(nullable = false)
    private String title;

    @ElementCollection
    @CollectionTable(name = "novel_common_v2_authors", joinColumns = @JoinColumn(name = "novel_id"))
    @Column(name = "author")
    private List<String> authors;

    @ElementCollection
    @CollectionTable(name = "novel_common_v2_genres", joinColumns = @JoinColumn(name = "novel_id"))
    @Column(name = "genre")
    private List<String> genres;

    private String status; // 연재상태
    private String publisher;
    private String ageRating;

    @Column(length = 1000)
    private String imageUrl;

    // 1:1 관계 설정
    @OneToOne(mappedBy = "novelCommon", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private NovelPlatformMapping platformMapping;

    // 메타 정보
    private LocalDate createdAt;
    private LocalDate updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDate.now();
        updatedAt = LocalDate.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDate.now();
    }

    // 편의 메서드들
    public boolean isOnNaverSeries() {
        return platformMapping != null && platformMapping.getNaverSeriesId() != null && platformMapping.getNaverSeriesId() > 0;
    }

    public boolean isOnKakaoPage() {
        return platformMapping != null && platformMapping.getKakaoPageId() != null && platformMapping.getKakaoPageId() > 0;
    }

    public boolean isOnRidibooks() {
        return platformMapping != null && platformMapping.getRidibooksId() != null && platformMapping.getRidibooksId() > 0;
    }
}