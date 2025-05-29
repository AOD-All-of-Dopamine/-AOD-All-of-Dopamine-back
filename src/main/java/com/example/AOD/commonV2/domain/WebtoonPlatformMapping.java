package com.example.AOD.commonV2.domain;

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
    private Long id;  // Common의 ID와 동일하게 설정

    @OneToOne
    @MapsId  // ID를 공유하도록 설정
    @JoinColumn(name = "common_id")
    private WebtoonCommonV2 webtoonCommon;

    // 플랫폼 ID들 (0 또는 null이면 해당 플랫폼에 없음)
    private Long naverId;    // 0이면 네이버웹툰에 없음, 값이 있으면 네이버의 webtoon ID
    private Long kakaoId;    // 0이면 카카오웹툰에 없음, 값이 있으면 카카오의 webtoon ID

    // 추후 다른 플랫폼 추가 시 여기에 추가
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
