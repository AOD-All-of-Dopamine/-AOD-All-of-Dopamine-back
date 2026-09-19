package com.example.AOD.recommend.seed;

import com.example.AOD.domain.ContentLike;
import com.example.AOD.repo.BookmarkRepository;
import com.example.AOD.repo.ContentLikeRepository;
import com.example.AOD.repo.ReviewRepository;
import com.example.AOD.repo.UserContentRow;
import com.example.AOD.repo.UserReviewRow;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class SeedResolverTest {

    private final ContentLikeRepository likes = mock(ContentLikeRepository.class);
    private final BookmarkRepository bookmarks = mock(BookmarkRepository.class);
    private final ReviewRepository reviews = mock(ReviewRepository.class);
    private final SeedResolver resolver = new SeedResolver(likes, bookmarks, reviews);

    private static final LocalDateTime T1 = LocalDateTime.of(2026, 9, 10, 0, 0);
    private static final LocalDateTime T2 = LocalDateTime.of(2026, 9, 11, 0, 0);
    private static final LocalDateTime T3 = LocalDateTime.of(2026, 9, 12, 0, 0);

    private void givenRows(List<UserContentRow> likeRows, List<UserContentRow> bookmarkRows,
                           List<UserReviewRow> reviewRows) {
        given(likes.findReactionRowsByUserId(eq(7L), eq(ContentLike.LikeType.LIKE), any(Pageable.class)))
                .willReturn(likeRows);
        given(bookmarks.findBookmarkRowsByUserId(eq(7L), any(Pageable.class))).willReturn(bookmarkRows);
        given(reviews.findReviewRowsByUserId(eq(7L), any(Pageable.class))).willReturn(reviewRows);
    }

    @Test
    void unionsLikesBookmarksAndHighRatedReviews() {
        givenRows(List.of(new UserContentRow(1L, T1)),
                  List.of(new UserContentRow(2L, T2)),
                  List.of(new UserReviewRow(3L, 4.5, T3), new UserReviewRow(4L, 3.0, T3)));

        List<Seed> seeds = resolver.resolve(7L, List.of());

        assertEquals(List.of(3L, 2L, 1L), seeds.stream().map(Seed::contentId).toList(),
                "최근순 — 리뷰(9/12) · 북마크(9/11) · 좋아요(9/10)");
        assertEquals(List.of(Seed.REVIEW, Seed.BOOKMARK, Seed.LIKE), seeds.stream().map(Seed::source).toList());
        assertTrue(seeds.stream().noneMatch(s -> s.contentId() == 4L), "평점 4 미만 리뷰는 시드가 아니다");
    }

    @Test
    void dislikeBeatsEveryPositiveSignal() {
        givenRows(List.of(new UserContentRow(1L, T3)),
                  List.of(new UserContentRow(1L, T3)),
                  List.of(new UserReviewRow(1L, 5.0, T3)));

        assertEquals(List.of(), resolver.resolve(7L, List.of(1L)));
    }

    @Test
    void lowRatedReviewRemovesTheWorkEvenIfLiked() {
        givenRows(List.of(new UserContentRow(1L, T3), new UserContentRow(2L, T2)),
                  List.of(),
                  List.of(new UserReviewRow(1L, 1.5, T1)));

        assertEquals(List.of(2L), resolver.resolve(7L, List.of()).stream().map(Seed::contentId).toList());
    }

    @Test
    void sourcePriorityIsLikeThenReviewThenBookmark() {
        givenRows(List.of(new UserContentRow(1L, T1)),
                  List.of(new UserContentRow(1L, T3), new UserContentRow(2L, T2)),
                  List.of(new UserReviewRow(2L, 4.0, T1)));

        List<Seed> seeds = resolver.resolve(7L, List.of());

        Seed first = seeds.stream().filter(s -> s.contentId() == 1L).findFirst().orElseThrow();
        Seed second = seeds.stream().filter(s -> s.contentId() == 2L).findFirst().orElseThrow();
        assertEquals(Seed.LIKE, first.source(), "좋아요가 북마크를 이긴다");
        assertEquals(T3, first.at(), "순서 기준 시각은 더 최근 상호작용을 쓴다");
        assertEquals(Seed.REVIEW, second.source(), "리뷰가 북마크를 이긴다");
    }

    @Test
    void tieOnTimestampOrdersBySmallerContentId() {
        givenRows(List.of(new UserContentRow(9L, T2), new UserContentRow(3L, T2), new UserContentRow(5L, T2)),
                  List.of(), List.of());

        assertEquals(List.of(3L, 5L, 9L), resolver.resolve(7L, List.of()).stream().map(Seed::contentId).toList());
    }

    @Test
    void candidateScanIsCapped() {
        List<UserContentRow> many = new java.util.ArrayList<>();
        for (int i = 0; i < SeedResolver.MAX_SEEDS + 50; i++) {
            many.add(new UserContentRow((long) i, T2.minusMinutes(i)));
        }
        givenRows(many, List.of(), List.of());

        assertEquals(SeedResolver.MAX_SEEDS, resolver.resolve(7L, List.of()).size());
    }

    @Test
    void dislikedReadsMostRecentOnesOnly() {
        given(likes.findReactionRowsByUserId(eq(7L), eq(ContentLike.LikeType.DISLIKE), any(Pageable.class)))
                .willReturn(List.of(new UserContentRow(8L, T3), new UserContentRow(9L, T2),
                        new UserContentRow(8L, T1)));

        assertEquals(List.of(8L, 9L), resolver.disliked(7L), "중복은 제거한다");
    }
}
