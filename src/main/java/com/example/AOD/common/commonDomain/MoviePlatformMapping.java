package com.example.AOD.common.commonDomain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "movie_platform_mapping")
@Getter
@Setter
@NoArgsConstructor
public class MoviePlatformMapping {
    @Id
    private Long id;  // Common의 ID와 동일하게 설정

    @OneToOne
    @MapsId  // ID를 공유하도록 설정
    @JoinColumn(name = "common_id")
    private MovieCommon movieCommon;

    // 플랫폼 ID들 (null이면 해당 플랫폼에 없음)
    private Long cgvId;         // CGV의 movie ID
    private Long megaboxId;     // 메가박스의 movie ID
    private Long lotteCinemaId; // 롯데시네마의 movie ID

    // 추후 다른 플랫폼 추가 시 여기에 추가
    // private Long kmdbId;  // 한국영화데이터베이스
    // private Long kobisId; // 영화진흥위원회

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
    public boolean hasCgv() {
        return cgvId != null && cgvId > 0;
    }

    public boolean hasMegabox() {
        return megaboxId != null && megaboxId > 0;
    }

    public boolean hasLotteCinema() {
        return lotteCinemaId != null && lotteCinemaId > 0;
    }

    public void setCgvMovie(Long cgvId) {
        this.cgvId = (cgvId != null && cgvId > 0) ? cgvId : null;
    }

    public void setMegaboxMovie(Long megaboxId) {
        this.megaboxId = (megaboxId != null && megaboxId > 0) ? megaboxId : null;
    }

    public void setLotteCinemaMovie(Long lotteCinemaId) {
        this.lotteCinemaId = (lotteCinemaId != null && lotteCinemaId > 0) ? lotteCinemaId : null;
    }

    public void removeFromCgv() {
        this.cgvId = null;
    }

    public void removeFromMegabox() {
        this.megaboxId = null;
    }

    public void removeFromLotteCinema() {
        this.lotteCinemaId = null;
    }
}