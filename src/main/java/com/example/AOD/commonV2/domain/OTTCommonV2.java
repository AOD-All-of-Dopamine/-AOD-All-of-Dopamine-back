package com.example.AOD.commonV2.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Entity
@Table(name = "ott_common_v2")
@Getter
@Setter
@NoArgsConstructor
public class OTTCommonV2 {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 기본 정보
    @Column(nullable = false)
    private String title;

    private String type; // series, movie
    private String creator;

    @ElementCollection
    @CollectionTable(name = "ott_common_v2_actors", joinColumns = @JoinColumn(name = "ott_id"))
    @Column(name = "actor")
    private List<String> actors;

    @ElementCollection
    @CollectionTable(name = "ott_common_v2_genres", joinColumns = @JoinColumn(name = "ott_id"))
    @Column(name = "genre")
    private List<String> genres;

    @ElementCollection
    @CollectionTable(name = "ott_common_v2_features", joinColumns = @JoinColumn(name = "ott_id"))
    @Column(name = "feature")
    private List<String> features;

    @Column(length = 1000)
    private String description;

    private String maturityRating;
    private String releaseYear;

    @Column(length = 1000)
    private String thumbnailUrl;

    // 1:1 관계 설정
    @OneToOne(mappedBy = "ottCommon", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private OTTPlatformMapping platformMapping;

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
    public boolean isOnNetflix() {
        return platformMapping != null && platformMapping.getNetflixId() != null && platformMapping.getNetflixId() > 0;
    }

    public boolean isOnDisneyPlus() {
        return platformMapping != null && platformMapping.getDisneyPlusId() != null && platformMapping.getDisneyPlusId() > 0;
    }

    public boolean isOnWatcha() {
        return platformMapping != null && platformMapping.getWatchaId() != null && platformMapping.getWatchaId() > 0;
    }
}