package com.example.crawler.crawl;

import com.example.crawler.common.queue.JobType;

import java.util.function.Function;

/**
 * Single source of truth for per-source crawl metadata.
 * Replaces scattered platform/domain/url literals and the JobType coupling that used to live
 * inside each crawl service and executor.
 *
 * Batch sizes preserve the values previously hard-coded per executor:
 *   Steam=5, TMDB(movie/tv)=6, NaverWebtoon=1, NaverSeries=3.
 */
public enum SourceDescriptor {

    STEAM_GAME("Steam", "GAME", JobType.STEAM_GAME, false, 1000L, 5,
            id -> "https://store.steampowered.com/app/" + id),

    TMDB_MOVIE("TMDB_MOVIE", "MOVIE", JobType.TMDB_MOVIE, false, 800L, 6,
            id -> "https://www.themoviedb.org/movie/" + id),

    TMDB_TV("TMDB_TV", "TV", JobType.TMDB_TV, false, 800L, 6,
            id -> "https://www.themoviedb.org/tv/" + id),

    // NOTE: confirm platformName/domain/url against NaverWebtoonCrawler during webtoon migration.
    NAVER_WEBTOON("NaverWebtoon", "WEBTOON", JobType.NAVER_WEBTOON, true, 5000L, 1,
            id -> "https://comic.naver.com/webtoon/list?titleId=" + id),

    NAVER_SERIES("NaverSeries", "WEBNOVEL", JobType.NAVER_SERIES_NOVEL, false, 2000L, 3,
            id -> "https://series.naver.com/novel/detail.series?productNo=" + id);

    private final String platformName;
    private final String domain;
    private final JobType jobType;
    private final boolean usesSelenium;
    private final long avgExecMillis;
    private final int recommendedBatchSize;
    private final Function<String, String> urlFn;

    SourceDescriptor(String platformName, String domain, JobType jobType, boolean usesSelenium,
                     long avgExecMillis, int recommendedBatchSize, Function<String, String> urlFn) {
        this.platformName = platformName;
        this.domain = domain;
        this.jobType = jobType;
        this.usesSelenium = usesSelenium;
        this.avgExecMillis = avgExecMillis;
        this.recommendedBatchSize = recommendedBatchSize;
        this.urlFn = urlFn;
    }

    public String platformName() {
        return platformName;
    }

    public String domain() {
        return domain;
    }

    public JobType jobType() {
        return jobType;
    }

    public boolean usesSelenium() {
        return usesSelenium;
    }

    public long avgExecMillis() {
        return avgExecMillis;
    }

    public int recommendedBatchSize() {
        return recommendedBatchSize;
    }

    public String urlOf(String platformSpecificId) {
        return urlFn.apply(platformSpecificId);
    }

    public static SourceDescriptor forJobType(JobType jobType) {
        for (SourceDescriptor d : values()) {
            if (d.jobType == jobType) {
                return d;
            }
        }
        throw new IllegalArgumentException("No SourceDescriptor for jobType: " + jobType);
    }
}
