package com.example.AOD.common.commonDomain;

import com.example.AOD.Webtoon.NaverWebtoon.domain.Days;
import com.example.AOD.Webtoon.NaverWebtoon.domain.Webtoon;
import com.example.AOD.Webtoon.NaverWebtoon.domain.WebtoonAuthor;
import com.example.AOD.Webtoon.NaverWebtoon.domain.WebtoonGenre;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "webtoon_common")
@Getter
@Setter
@NoArgsConstructor
public class WebtoonCommon {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String imageUrl;

    @ManyToMany
    @JoinTable(
            name = "webtoon_genre_mapping",
            joinColumns = @JoinColumn(name = "webtoon_id"),
            inverseJoinColumns = @JoinColumn(name = "genre_id")
    )
    private List<WebtoonGenre> genre;

    @Column(nullable = false)
    private String publishDate;

    @ElementCollection
    @Enumerated(EnumType.STRING)
    private List<Days> uploadDay;

    @ManyToMany
    @JoinTable(
            name = "webtoon_author_mapping",
            joinColumns = @JoinColumn(name = "webtoon_id"),
            inverseJoinColumns = @JoinColumn(name = "author_id")
    )
    private List<WebtoonAuthor> author;

    @Column(nullable = false, length = 500)
    private String summary;

    private String platform;

    // 1:1 관계 설정 - PlatformMapping 추가
    @OneToOne(mappedBy = "webtoonCommon", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private WebtoonPlatformMapping platformMapping;

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

    // 기존 생성자는 유지 (호환성)
    public WebtoonCommon(Webtoon webtoon) {
        this.id = webtoon.getId();
        this.title = webtoon.getTitle();
        this.imageUrl = webtoon.getThumbnail();
        this.genre = webtoon.getWebtoonGenres();
        this.publishDate = webtoon.getPublishDate();
        this.uploadDay = webtoon.getUploadDays();
        this.author = webtoon.getWebtoonAuthors();
        this.summary = webtoon.getSummary();
        this.platform = "Naver";
    }

    // 편의 메서드들
    public boolean isOnNaver() {
        return platformMapping != null && platformMapping.getNaverId() != null && platformMapping.getNaverId() > 0;
    }

    public boolean isOnKakao() {
        return platformMapping != null && platformMapping.getKakaoId() != null && platformMapping.getKakaoId() > 0;
    }

    // 플랫폼 매핑 설정 헬퍼 메서드
    public void setPlatformMapping(WebtoonPlatformMapping mapping) {
        this.platformMapping = mapping;
        if (mapping != null) {
            mapping.setWebtoonCommon(this);
        }
    }
}
