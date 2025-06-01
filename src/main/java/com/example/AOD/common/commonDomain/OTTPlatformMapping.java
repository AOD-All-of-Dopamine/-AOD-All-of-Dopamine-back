package com.example.AOD.common.commonDomain;

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
    private Long id;

    @OneToOne
    @MapsId
    @JoinColumn(name = "common_id")
    private OTTCommon ottCommon;

    // 플랫폼 ID들
    private Long netflixId;
    private Long disneyPlusId;
    private Long watchaId;
    private Long wavveId;

    // 추후 다른 플랫폼 추가 시
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

    @Transient
    public int getPlatformCount() {
        int cnt = 0;
        if (hasNetflix())    cnt++;
        if (hasDisneyPlus()) cnt++;
        if (hasWatcha())     cnt++;
        if (hasWavve())      cnt++;
        return cnt;
    }
}