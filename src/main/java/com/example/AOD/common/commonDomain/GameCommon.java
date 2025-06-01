package com.example.AOD.common.commonDomain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "game_common")
@Getter
@Setter
@NoArgsConstructor
public class GameCommon {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @Column(nullable = false)
    private String title;

    private String imageUrl;

    @ElementCollection
    @CollectionTable(name = "game_common_genre", joinColumns = @JoinColumn(name = "game_id"))
    @Column(name = "genre")
    private List<String> genre;

    private Long requiredAge;

    @Column(length = 10000)
    private String summary;

    private int initialPrice;
    private int finalPrice;
    private String platform;

    @ElementCollection
    @CollectionTable(name = "game_common_publisher", joinColumns = @JoinColumn(name = "game_id"))
    @Column(name = "publisher")
    private List<String> publisher;

    @ElementCollection
    @CollectionTable(name = "game_common_developer", joinColumns = @JoinColumn(name = "game_id"))
    @Column(name = "developer")
    private List<String> developers;

    // 1:1 관계 설정 - PlatformMapping 추가
    @OneToOne(mappedBy = "gameCommon", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private GamePlatformMapping platformMapping;

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
    public boolean isOnSteam() {
        return platformMapping != null && platformMapping.getSteamId() != null && platformMapping.getSteamId() > 0;
    }

    public boolean isOnEpic() {
        return platformMapping != null && platformMapping.getEpicId() != null && platformMapping.getEpicId() > 0;
    }

    public boolean isOnGog() {
        return platformMapping != null && platformMapping.getGogId() != null && platformMapping.getGogId() > 0;
    }

    // 플랫폼 매핑 설정 헬퍼 메서드
    public void setPlatformMapping(GamePlatformMapping mapping) {
        this.platformMapping = mapping;
        if (mapping != null) {
            mapping.setGameCommon(this);
        }
    }
}