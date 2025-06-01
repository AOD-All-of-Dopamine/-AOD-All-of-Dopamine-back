package com.example.AOD.common.commonDomain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "webtoon_platform_mapping")
@Getter
@Setter
@NoArgsConstructor
public class WebtoonPlatformMapping {
    @Id
    private Long id;

    @OneToOne
    @MapsId
    @JoinColumn(name = "common_id")
    private WebtoonCommon webtoonCommon;

    // 플랫폼 ID들
    private Long naverId;
    private Long kakaoId;

    // 추후 다른 플랫폼 추가 시
    // private Long lezhinId;
    // private Long bomtoonId;

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
    public boolean hasNaver() {
        return naverId != null && naverId > 0;
    }

    public boolean hasKakao() {
        return kakaoId != null && kakaoId > 0;
    }

    public void setNaverWebtoon(Long naverId) {
        this.naverId = (naverId != null && naverId > 0) ? naverId : null;
    }

    public void setKakaoWebtoon(Long kakaoId) {
        this.kakaoId = (kakaoId != null && kakaoId > 0) ? kakaoId : null;
    }

    public void removeFromNaver() {
        this.naverId = null;
    }

    public void removeFromKakao() {
        this.kakaoId = null;
    }
}