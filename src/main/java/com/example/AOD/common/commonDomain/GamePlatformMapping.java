package com.example.AOD.common.commonDomain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "game_platform_mapping")
@Getter
@Setter
@NoArgsConstructor
public class GamePlatformMapping {
    @Id
    private Long id;

    @OneToOne
    @MapsId
    @JoinColumn(name = "common_id")
    private GameCommon gameCommon;

    // 플랫폼 ID들
    private Long steamId;
    private Long epicId;
    private Long gogId;

    // 추후 다른 플랫폼 추가 시
    // private Long originId;
    // private Long xboxId;

    // 메타 정보
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
    public boolean hasSteam() {
        return steamId != null && steamId > 0;
    }

    public boolean hasEpic() {
        return epicId != null && epicId > 0;
    }

    public boolean hasGog() {
        return gogId != null && gogId > 0;
    }

    public void setSteamGame(Long steamId) {
        this.steamId = (steamId != null && steamId > 0) ? steamId : null;
    }

    public void setEpicGame(Long epicId) {
        this.epicId = (epicId != null && epicId > 0) ? epicId : null;
    }

    public void setGogGame(Long gogId) {
        this.gogId = (gogId != null && gogId > 0) ? gogId : null;
    }

    public void removeFromSteam() {
        this.steamId = null;
    }

    public void removeFromEpic() {
        this.epicId = null;
    }

    public void removeFromGog() {
        this.gogId = null;
    }

    @Transient
    public int getPlatformCount() {
        int cnt = 0;
        if (hasSteam()) cnt++;
        if (hasEpic())  cnt++;
        if (hasGog())   cnt++;
        return cnt;
    }
}