package com.example.AOD.commonV2.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Entity
@Table(name = "game_common_v2")
@Getter
@Setter
@NoArgsConstructor
public class GameCommonV2 {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 기본 정보
    @Column(nullable = false)
    private String title;

    @ElementCollection
    @CollectionTable(name = "game_common_v2_developers", joinColumns = @JoinColumn(name = "game_id"))
    @Column(name = "developer")
    private List<String> developers;

    @ElementCollection
    @CollectionTable(name = "game_common_v2_publishers", joinColumns = @JoinColumn(name = "game_id"))
    @Column(name = "publisher")
    private List<String> publishers;

    @ElementCollection
    @CollectionTable(name = "game_common_v2_genres", joinColumns = @JoinColumn(name = "game_id"))
    @Column(name = "genre")
    private List<String> genres;

    private Long requiredAge;

    @Column(length = 10000)
    private String summary;

    private Integer initialPrice;
    private Integer finalPrice;

    @Column(length = 1000)
    private String thumbnailUrl;

    // 1:1 관계 설정
    @OneToOne(mappedBy = "gameCommon", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private GamePlatformMapping platformMapping;

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
    public boolean isOnSteam() {
        return platformMapping != null && platformMapping.getSteamId() != null && platformMapping.getSteamId() > 0;
    }

    public boolean isOnEpic() {
        return platformMapping != null && platformMapping.getEpicId() != null && platformMapping.getEpicId() > 0;
    }

    public boolean isOnGog() {
        return platformMapping != null && platformMapping.getGogId() != null && platformMapping.getGogId() > 0;
    }
}