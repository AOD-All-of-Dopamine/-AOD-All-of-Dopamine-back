package com.example.AOD.api.trend;

import com.example.AOD.api.dto.WorkSummaryDTO;
import com.example.AOD.api.service.WorkApiService;
import com.example.shared.entity.Content;
import com.example.shared.entity.Domain;
import com.example.shared.entity.ExternalRanking;
import com.example.shared.repository.ContentRepository;
import com.example.shared.repository.ExternalRankingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/** 트렌드 "새로 나온 주목작" — 순위 먼저, 모자라면 분야별 근거로 채운다. */
class NotableReleaseServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 4);
    private final ExternalRankingRepository rankings = mock(ExternalRankingRepository.class);
    private final ContentRepository contents = mock(ContentRepository.class);
    private final WorkApiService works = mock(WorkApiService.class);
    private final NotableReleaseService service = new NotableReleaseService(rankings, contents, works,
            Clock.fixed(Instant.parse("2026-10-04T03:00:00Z"), ZoneOffset.UTC));
    private long ids = 1;

    @BeforeEach
    void setUp() {
        given(rankings.findByPlatformWithContent(anyString())).willReturn(List.of());
        given(contents.findReleasesInDateRange(any(Domain.class), any(), any(), anyInt(), any())).willReturn(new PageImpl<>(List.of()));
        given(works.toEnrichedSummaries(anyList())).willAnswer(inv -> {
            List<Content> cs = inv.getArgument(0);
            List<WorkSummaryDTO> out = new ArrayList<>();
            for (Content c : cs) {
                WorkSummaryDTO w = new WorkSummaryDTO();
                w.setId(c.getContentId());
                w.setTitle(c.getMasterTitle());
                w.setDomain(c.getDomain().name());
                w.setReleaseDate(c.getReleaseDate() == null ? null : c.getReleaseDate().toString());
                w.setExternalVoteCount(c.getReviewCount());    // 테스트용으로 reviewCount 칸에 투표 · 리뷰 수를 실어 보낸다
                w.setSteamReviewCount(c.getReviewCount());
                out.add(w);
            }
            return out;
        });
    }

    private Content content(Domain d, String title, LocalDate release, int count) {
        Content c = new Content();
        c.setContentId(ids++);
        c.setDomain(d);
        c.setMasterTitle(title);
        c.setReleaseDate(release);
        c.setReviewCount(count);
        return c;
    }

    private ExternalRanking rank(int r, Content c) {
        ExternalRanking e = new ExternalRanking();
        e.setRanking(r);
        e.setContent(c);
        return e;
    }

    @Test
    void rankingFirstWithinWindow() {
        Content old = content(Domain.GAME, "오래된 1위", TODAY.minusDays(400), 0);
        Content recent6 = content(Domain.GAME, "에이스 컴뱃 8", TODAY.minusDays(3), 2);
        Content recent45 = content(Domain.GAME, "EA FC 27", TODAY.minusDays(10), 13);
        Content future = content(Domain.GAME, "내일 출시", TODAY.plusDays(1), 0);
        given(rankings.findByPlatformWithContent("Steam")).willReturn(List.of(rank(1, old), rank(2, future), rank(6, recent6), rank(45, recent45)));

        List<NotableReleaseService.Item> items = service.pick(Domain.GAME, TODAY);

        assertThat(items).extracting(i -> i.work().getTitle()).containsExactly("에이스 컴뱃 8", "EA FC 27");
        assertThat(items.get(0).reason()).isEqualTo(new NotableReleaseService.Reason("RANK", 6, "Steam", null));
    }

    @Test
    void moviesFillByVotesWhenRankingIsShort() {
        Content ranked = content(Domain.MOVIE, "순위 신작", TODAY.minusDays(20), 30);
        given(rankings.findByPlatformWithContent("TMDB_MOVIE")).willReturn(List.of(rank(3, ranked)));
        Content few = content(Domain.MOVIE, "투표 적음", TODAY.minusDays(5), 4);
        Content many = content(Domain.MOVIE, "투표 많음", TODAY.minusDays(9), 28);
        given(contents.findReleasesInDateRange(eq(Domain.MOVIE), eq(TODAY.minusDays(60)), eq(TODAY), anyInt(), any()))
                .willReturn(new PageImpl<>(List.of(few, ranked, many)));

        List<NotableReleaseService.Item> items = service.pick(Domain.MOVIE, TODAY);

        assertThat(items).extracting(i -> i.work().getTitle()).containsExactly("순위 신작", "투표 많음");
        assertThat(items.get(1).reason()).isEqualTo(new NotableReleaseService.Reason("VOTES", 28, null, null));
    }

    @Test
    void webnovelFillsLatestAndEmptyDomainIsEmpty() {
        Content a = content(Domain.WEBNOVEL, "어제 시작", TODAY.minusDays(1), 0);
        Content b = content(Domain.WEBNOVEL, "그제 시작", TODAY.minusDays(2), 0);
        given(contents.findReleasesInDateRange(eq(Domain.WEBNOVEL), any(), any(), anyInt(), any())).willReturn(new PageImpl<>(List.of(a, b)));

        List<NotableReleaseService.Item> items = service.pick(Domain.WEBNOVEL, TODAY);
        assertThat(items).extracting(i -> i.reason().type()).containsExactly("LATEST", "LATEST");
        assertThat(items.get(0).reason().date()).isEqualTo(TODAY.minusDays(1).toString());

        assertThat(service.pick(Domain.TV, TODAY)).isEmpty();
        assertThat(service.notable()).extracting(NotableReleaseService.Group::domain)
                .containsExactly("MOVIE", "TV", "GAME", "WEBTOON", "WEBNOVEL");
    }

    @Test
    void adultInRankingIsSkipped() {
        Content adult = content(Domain.WEBTOON, "성인", TODAY.minusDays(3), 0);
        adult.setIsAdult(true);
        Content ok = content(Domain.WEBTOON, "정상", TODAY.minusDays(3), 0);
        given(rankings.findByPlatformWithContent("NaverWebtoon")).willReturn(List.of(rank(1, adult), rank(2, ok)));
        assertThat(service.pick(Domain.WEBTOON, TODAY)).extracting(i -> i.work().getTitle()).containsExactly("정상");
    }
}
