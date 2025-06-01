package com.example.AOD.commonV2.domain;

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
    private Long id;  // Common의 ID와 동일하게 설정

    @OneToOne
    @MapsId  // ID를 공유하도록 설정
    @JoinColumn(name = "common_id")
    private GameCommonV2 gameCommon;

    // 플랫폼 ID들 (null이면 해당 플랫폼에 없음)
    private Long steamId;    // null이면 스팀에 없음, 값이 있으면 스팀의 game ID
    private Long epicId;     // null이면 에픽게임즈에 없음, 값이 있으면 에픽의 game ID
    private Long gogId;      // null이면 GOG에 없음, 값이 있으면 GOG의 game ID

    // 추후 다른 플랫폼 추가 시 여기에 추가
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
}