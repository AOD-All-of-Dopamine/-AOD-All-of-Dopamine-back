package com.example.AOD.common.commonDomain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "novel_common")
@Getter
@Setter
@NoArgsConstructor
public class NovelCommon {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @Column(nullable = false)
    private String title;

    @Column(length = 1000)
    private String imageUrl;

    @ElementCollection
    @CollectionTable(name = "novel_common_genre", joinColumns = @JoinColumn(name = "novel_id"))
    @Column(name = "genre")
    private List<String> genre;

    private String status;

    @ElementCollection
    @CollectionTable(name = "novel_common_author", joinColumns = @JoinColumn(name = "novel_id"))
    @Column(name = "author")
    private List<String> authors;

    private String ageRating;

    private String publisher;

    // 1:1 관계 설정 - PlatformMapping 추가
    @OneToOne(mappedBy = "novelCommon", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private NovelPlatformMapping platformMapping;

    // 메타 정보 추가
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
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

    // 플랫폼 매핑 설정 헬퍼 메서드
    public void setPlatformMapping(NovelPlatformMapping mapping) {
        this.platformMapping = mapping;
        if (mapping != null) {
            mapping.setNovelCommon(this);
        }
    }
}