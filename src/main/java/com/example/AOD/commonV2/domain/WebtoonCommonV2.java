package com.example.AOD.commonV2.domain;

import com.example.AOD.Webtoon.NaverWebtoon.domain.Days;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Entity
@Table(name = "webtoon_common_v2")
@Getter
@Setter
@NoArgsConstructor
public class WebtoonCommonV2 {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 기본 정보
    @Column(nullable = false)
    private String title;

    @ElementCollection
    @CollectionTable(name = "webtoon_common_v2_authors", joinColumns = @JoinColumn(name = "webtoon_id"))
    @Column(name = "author")
    private List<String> authors;

    @ElementCollection
    @CollectionTable(name = "webtoon_common_v2_genres", joinColumns = @JoinColumn(name = "webtoon_id"))
    @Column(name = "genre")
    private List<String> genres;

    @ElementCollection
    @Enumerated(EnumType.STRING)
    private List<Days> uploadDays;

    @Column(nullable = false, length = 500)
    private String summary;

    @Column(nullable = false)
    private String publishDate;

    @Column(length = 1000)
    private String thumbnailUrl;

    // 1:1 관계 설정
    @OneToOne(mappedBy = "webtoonCommon", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private WebtoonPlatformMapping platformMapping;

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
    public boolean isOnNaver() {
        return platformMapping != null && platformMapping.getNaverId() != null && platformMapping.getNaverId() > 0;
    }

    public boolean isOnKakao() {
        return platformMapping != null && platformMapping.getKakaoId() != null && platformMapping.getKakaoId() > 0;
    }
}