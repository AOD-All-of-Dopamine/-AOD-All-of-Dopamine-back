package com.example.AOD.commonV2.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "ott_platform_mapping")
@Getter
@Setter
@NoArgsConstructor
public class OTTPlatformMapping {
    @Id
    private Long id;  // Common의 ID와 동일하게 설정

    @OneToOne
    @MapsId  // ID를 공유하도록 설정
    @JoinColumn(name = "common_id")
    private OTTCommonV2 ottCommon;

    // 플랫폼 ID들 (null이면 해당 플랫폼에 없음)
    private Long netflixId;      // null이면 넷플릭스에 없음, 값이 있으면 넷플릭스의 content ID
    private Long disneyPlusId;   // null이면 디즈니플러스에 없음, 값이 있으면 디즈니플러스의 content ID
    private Long watchaId;       // null이면 왓챠에 없음, 값이 있으면 왓챠의 content ID
    private Long wavveId;        // null이면 웨이브에 없음, 값이 있으면 웨이브의 content ID

    // 추후 다른 플랫폼 추가 시 여기에 추가
    // private Long tvingId;
    // private Long laftelId;

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
    public boolean hasNetflix() {
        return netflixId != null && netflixId > 0;
    }

    public boolean hasDisneyPlus() {
        return disneyPlusId != null && disneyPlusId > 0;
    }

    public boolean hasWatcha() {
        return watchaId != null && watchaId > 0;
    }

    public boolean hasWavve() {
        return wavveId != null && wavveId > 0;
    }

    public void setNetflixContent(Long netflixId) {
        this.netflixId = (netflixId != null && netflixId > 0) ? netflixId : null;
    }

    public void setDisneyPlusContent(Long disneyPlusId) {
        this.disneyPlusId = (disneyPlusId != null && disneyPlusId > 0) ? disneyPlusId : null;
    }

    public void setWatchaContent(Long watchaId) {
        this.watchaId = (watchaId != null && watchaId > 0) ? watchaId : null;
    }

    public void setWavveContent(Long wavveId) {
        this.wavveId = (wavveId != null && wavveId > 0) ? wavveId : null;
    }

    public void removeFromNetflix() {
        this.netflixId = null;
    }

    public void removeFromDisneyPlus() {
        this.disneyPlusId = null;
    }

    public void removeFromWatcha() {
        this.watchaId = null;
    }

    public void removeFromWavve() {
        this.wavveId = null;
    }
}