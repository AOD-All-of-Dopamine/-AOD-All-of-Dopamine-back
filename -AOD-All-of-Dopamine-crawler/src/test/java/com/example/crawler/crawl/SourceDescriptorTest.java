package com.example.crawler.crawl;

import com.example.crawler.common.queue.JobType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SourceDescriptorTest {

    @Test
    void steamGameMetadataMatchesCurrentLiterals() {
        SourceDescriptor d = SourceDescriptor.STEAM_GAME;
        assertThat(d.platformName()).isEqualTo("Steam");
        assertThat(d.domain()).isEqualTo("GAME");
        assertThat(d.jobType()).isEqualTo(JobType.STEAM_GAME);
        assertThat(d.usesSelenium()).isFalse();
        assertThat(d.recommendedBatchSize()).isEqualTo(5);
        assertThat(d.urlOf("400")).isEqualTo("https://store.steampowered.com/app/400");
    }

    @Test
    void tmdbMovieAndTvMatchCurrentLiterals() {
        assertThat(SourceDescriptor.TMDB_MOVIE.platformName()).isEqualTo("TMDB_MOVIE");
        assertThat(SourceDescriptor.TMDB_MOVIE.domain()).isEqualTo("MOVIE");
        assertThat(SourceDescriptor.TMDB_MOVIE.urlOf("27205"))
                .isEqualTo("https://www.themoviedb.org/movie/27205");
        assertThat(SourceDescriptor.TMDB_TV.platformName()).isEqualTo("TMDB_TV");
        assertThat(SourceDescriptor.TMDB_TV.domain()).isEqualTo("TV");
        assertThat(SourceDescriptor.TMDB_TV.urlOf("1396"))
                .isEqualTo("https://www.themoviedb.org/tv/1396");
    }

    @Test
    void naverSeriesMatchesCurrentLiterals() {
        SourceDescriptor d = SourceDescriptor.NAVER_SERIES;
        assertThat(d.platformName()).isEqualTo("NaverSeries");
        assertThat(d.domain()).isEqualTo("WEBNOVEL");
        assertThat(d.jobType()).isEqualTo(JobType.NAVER_SERIES_NOVEL);
        assertThat(d.recommendedBatchSize()).isEqualTo(3);
        assertThat(d.urlOf("123"))
                .isEqualTo("https://series.naver.com/novel/detail.series?productNo=123");
    }

    @Test
    void naverWebtoonUsesSeleniumWithBatchSizeOne() {
        SourceDescriptor d = SourceDescriptor.NAVER_WEBTOON;
        assertThat(d.usesSelenium()).isTrue();
        assertThat(d.recommendedBatchSize()).isEqualTo(1);
        assertThat(d.jobType()).isEqualTo(JobType.NAVER_WEBTOON);
    }

    @Test
    void forJobTypeResolvesDescriptor() {
        assertThat(SourceDescriptor.forJobType(JobType.TMDB_TV)).isEqualTo(SourceDescriptor.TMDB_TV);
    }

    @Test
    void forJobTypeThrowsForUnmapped() {
        assertThatThrownBy(() -> SourceDescriptor.forJobType(JobType.KAKAO_PAGE_NOVEL))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
