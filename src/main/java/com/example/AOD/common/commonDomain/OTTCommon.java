package com.example.AOD.common.commonDomain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "ott_common")
@Getter
@Setter
@NoArgsConstructor
public class OTTCommon {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @Column(nullable = false)
    private String title;

    private String imageUrl;

    @ElementCollection
    @CollectionTable(name = "ott_common_genre", joinColumns = @JoinColumn(name = "ott_id"))
    @Column(name = "genre")
    private List<String> genre;

    private String type;
    private String thumbnail;

    @Column(length = 1000)
    private String description;

    private String creator;
    private String maturityRating;
    private int releaseYear;

    @ElementCollection
    @CollectionTable(name = "ott_common_actors", joinColumns = @JoinColumn(name = "ott_id"))
    @Column(name = "actor")
    private List<String> actors;

    @ElementCollection
    @CollectionTable(name = "ott_common_features", joinColumns = @JoinColumn(name = "ott_id"))
    @Column(name = "feature")
    private List<String> features;

    // 1:1 관계 설정 - PlatformMapping 추가
    @OneToOne(mappedBy = "ottCommon", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private OTTPlatformMapping platformMapping;

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
    public boolean isOnNetflix() {
        return platformMapping != null && platformMapping.getNetflixId() != null && platformMapping.getNetflixId() > 0;
    }

    public boolean isOnDisneyPlus() {
        return platformMapping != null && platformMapping.getDisneyPlusId() != null && platformMapping.getDisneyPlusId() > 0;
    }

    public boolean isOnWatcha() {
        return platformMapping != null && platformMapping.getWatchaId() != null && platformMapping.getWatchaId() > 0;
    }

    public boolean isOnWavve() {
        return platformMapping != null && platformMapping.getWavveId() != null && platformMapping.getWavveId() > 0;
    }

    // 플랫폼 매핑 설정 헬퍼 메서드
    public void setPlatformMapping(OTTPlatformMapping mapping) {
        this.platformMapping = mapping;
        if (mapping != null) {
            mapping.setOttCommon(this);
        }
    }
}