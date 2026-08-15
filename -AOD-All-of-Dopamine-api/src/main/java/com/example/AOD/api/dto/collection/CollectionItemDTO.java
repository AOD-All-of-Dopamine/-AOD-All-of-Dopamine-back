package com.example.AOD.api.dto.collection;

import com.example.AOD.api.dto.WorkSummaryDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 컬렉션 상세의 아이템 한 건.
 * 작품 표시 필드는 WorkApiService의 WorkSummary 보강 로직(enrich) 결과를 그대로 옮겨 담는다
 * (도메인별 표시 필드 재사용 — 중복 구현 금지).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CollectionItemDTO {
    private Long itemId;
    private Long contentId;
    private String comment;   // 큐레이터 한 줄 코멘트 (없으면 null)
    private Integer position;

    // ===== 작품 표시 필드 (WorkSummaryDTO 보강 결과) =====
    private String title;
    private String posterUrl;
    private String releaseDate;
    private String domain;
    private Double score;
    private String creator;             // 도메인별 대표 제작자: 감독/개발사/작가
    private List<String> genres;
    private List<String> platforms;
    private String weekday;             // 웹툰
    private String status;              // 웹툰
    private String ageRating;           // 웹툰/웹소설
    private String steamReviewDesc;     // 게임
    private Integer steamPositivePct;   // 게임
    private Double externalRating;      // 영화/TV (TMDB)

    /** 보강된 WorkSummaryDTO + 아이템 메타 → DTO */
    public static CollectionItemDTO of(Long itemId, String comment, Integer position, WorkSummaryDTO work) {
        return CollectionItemDTO.builder()
                .itemId(itemId)
                .contentId(work.getId())
                .comment(comment)
                .position(position)
                .title(work.getTitle())
                .posterUrl(work.getThumbnail())
                .releaseDate(work.getReleaseDate())
                .domain(work.getDomain())
                .score(work.getScore())
                .creator(work.getCreator())
                .genres(work.getGenres())
                .platforms(work.getPlatforms())
                .weekday(work.getWeekday())
                .status(work.getStatus())
                .ageRating(work.getAgeRating())
                .steamReviewDesc(work.getSteamReviewDesc())
                .steamPositivePct(work.getSteamPositivePct())
                .externalRating(work.getExternalRating())
                .build();
    }
}
