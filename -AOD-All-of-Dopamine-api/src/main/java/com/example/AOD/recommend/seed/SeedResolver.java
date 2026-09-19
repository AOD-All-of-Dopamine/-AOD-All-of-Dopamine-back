package com.example.AOD.recommend.seed;

import com.example.AOD.domain.ContentLike;
import com.example.AOD.repo.BookmarkRepository;
import com.example.AOD.repo.ContentLikeRepository;
import com.example.AOD.repo.ReviewRepository;
import com.example.AOD.repo.UserContentRow;
import com.example.AOD.repo.UserReviewRow;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 시드 규칙 (REC_TAB_DESIGN §6-4).
 * 좋아요 ∪ 북마크 ∪ 평점 4 이상 리뷰 − 싫어요 − 평점 2 이하 리뷰.
 * 출처 우선순위 좋아요 > 리뷰 > 북마크, 정렬은 최근순(동률이면 content_id).
 *
 * 플랫폼당 50개 상한은 여기서 걸지 않는다 — 키로 바꾼 뒤에야 플랫폼을 알 수 있어서
 * RecommendService 가 매핑 후에 자른다.
 */
@Service
@RequiredArgsConstructor
public class SeedResolver {

    /** 시드 후보 스캔 상한. 플랫폼당 50개면 4플랫폼 200개라 넉넉하고, IN 절 크기를 묶어 준다. */
    public static final int MAX_SEEDS = 1_000;
    /** 싫어요 상한 (최근순 앞에서 자른다). */
    public static final int MAX_DISLIKED = 1_000;
    public static final double SEED_MIN_RATING = 4.0;
    public static final double SEED_BLOCK_RATING = 2.0;

    /** 작을수록 센 출처. 문구 우선순위 좋아요 > 리뷰 > 북마크. */
    private static final Map<String, Integer> SOURCE_RANK = Map.of(Seed.LIKE, 0, Seed.REVIEW, 1, Seed.BOOKMARK, 2);

    private final ContentLikeRepository contentLikeRepository;
    private final BookmarkRepository bookmarkRepository;
    private final ReviewRepository reviewRepository;

    /**
     * 최근순 시드 목록. dislikedContentIds 는 호출자가 {@link #disliked(Long)} 로 한 번만 읽어 넘긴다
     * (같은 쿼리를 두 번 돌리지 않으려고).
     */
    public List<Seed> resolve(Long userId, Collection<Long> dislikedContentIds) {
        Pageable cap = PageRequest.of(0, MAX_SEEDS);
        List<UserContentRow> likeRows =
                contentLikeRepository.findReactionRowsByUserId(userId, ContentLike.LikeType.LIKE, cap);
        List<UserContentRow> bookmarkRows = bookmarkRepository.findBookmarkRowsByUserId(userId, cap);
        List<UserReviewRow> reviewRows = reviewRepository.findReviewRowsByUserId(userId, cap);

        Set<Long> blocked = new HashSet<>(dislikedContentIds == null ? List.of() : dislikedContentIds);
        for (UserReviewRow review : reviewRows) {
            if (review.rating() != null && review.rating() <= SEED_BLOCK_RATING) blocked.add(review.contentId());
        }

        Map<Long, Seed> best = new HashMap<>();
        for (UserContentRow row : likeRows) merge(best, blocked, new Seed(row.contentId(), Seed.LIKE, row.at()));
        for (UserReviewRow review : reviewRows) {
            if (review.rating() == null || review.rating() < SEED_MIN_RATING) continue;
            merge(best, blocked, new Seed(review.contentId(), Seed.REVIEW, review.at()));
        }
        for (UserContentRow row : bookmarkRows) merge(best, blocked, new Seed(row.contentId(), Seed.BOOKMARK, row.at()));

        List<Seed> seeds = new ArrayList<>(best.values());
        seeds.sort(SeedResolver::byRecencyThenId);
        return seeds.size() > MAX_SEEDS ? new ArrayList<>(seeds.subList(0, MAX_SEEDS)) : seeds;
    }

    /** 싫어요한 작품 (최근순, 중복 제거, 상한 적용). 시드 제외와 라우터 disliked 에 둘 다 쓴다. */
    public List<Long> disliked(Long userId) {
        return contentLikeRepository
                .findReactionRowsByUserId(userId, ContentLike.LikeType.DISLIKE, PageRequest.of(0, MAX_DISLIKED))
                .stream()
                .map(UserContentRow::contentId)
                .distinct()
                .toList();
    }

    private static void merge(Map<Long, Seed> best, Set<Long> blocked, Seed candidate) {
        if (candidate.contentId() == null || blocked.contains(candidate.contentId())) return;
        Seed previous = best.get(candidate.contentId());
        if (previous == null) {
            best.put(candidate.contentId(), candidate);
            return;
        }
        // 출처는 우선순위가 센 쪽, 순서 기준 시각은 더 최근 쪽을 쓴다.
        String source = SOURCE_RANK.get(candidate.source()) < SOURCE_RANK.get(previous.source())
                ? candidate.source() : previous.source();
        best.put(candidate.contentId(), new Seed(candidate.contentId(), source, latest(previous.at(), candidate.at())));
    }

    private static LocalDateTime latest(LocalDateTime a, LocalDateTime b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.isAfter(b) ? a : b;
    }

    /** 최근순, 동률이면 content_id 작은 것 먼저. 시각이 없는 행(이론상)은 맨 뒤. */
    static int byRecencyThenId(Seed a, Seed b) {
        if (a.at() == null && b.at() != null) return 1;
        if (a.at() != null && b.at() == null) return -1;
        int byTime = (a.at() == null) ? 0 : b.at().compareTo(a.at());
        return byTime != 0 ? byTime : Long.compare(a.contentId(), b.contentId());
    }
}
