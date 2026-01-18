package com.example.crawler.common.queue;

import com.example.crawler.contents.Novel.NaverSeriesNovel.NaverSeriesCrawler;
import com.example.crawler.contents.TMDB.service.TmdbService;
import com.example.crawler.contents.Webtoon.NaverWebtoon.NaverWebtoonService;
import com.example.crawler.game.steam.service.SteamCrawlService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 크롤링 작업 소비자 (Consumer)
 * 
 * 큐에서 작업을 가져와 실제 크롤링을 수행합니다.
 * 타입별로 균등하게 분배하여 처리합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CrawlJobConsumer {

    private final CrawlJobRepository crawlJobRepository;
    private final SteamCrawlService steamCrawlService;
    private final TmdbService tmdbService;
    private final NaverWebtoonService naverWebtoonService;
    private final NaverSeriesCrawler naverSeriesCrawler;

    /**
     * 주기적으로 큐에서 작업을 타입별로 균등하게 가져와 처리합니다.
     * 
     * fixedDelay: 이전 작업이 끝나고 5초 후 다시 실행
     */
    @Scheduled(fixedDelay = 5000, initialDelay = 3000)
    @Transactional
    public void processBatchBalanced() {
        log.debug("🔍 [Consumer] 배치 처리 시작 - 큐에서 작업 조회 중...");
        try {
            // 타입별로 균등하게 분배
            int steamProcessed = processByType(JobType.STEAM_GAME, 5);
            int tmdbMovieProcessed = processByType(JobType.TMDB_MOVIE, 3);
            int tmdbTvProcessed = processByType(JobType.TMDB_TV, 2);
            int webtoonProcessed = processByType(JobType.NAVER_WEBTOON, 2);
            int webtoonFinishedProcessed = processByType(JobType.NAVER_WEBTOON_FINISHED, 2);
            int novelProcessed = processByType(JobType.NAVER_SERIES_NOVEL, 2);

            int total = steamProcessed + tmdbMovieProcessed + tmdbTvProcessed + webtoonProcessed
                    + webtoonFinishedProcessed + novelProcessed;

            if (total > 0) {
                log.info("📦 [Consumer] 배치 처리 완료 - Steam:{}, TMDB-M:{}, TMDB-TV:{}, 웹툰:{}, 완결웹툰:{}, 소설:{}",
                        steamProcessed, tmdbMovieProcessed, tmdbTvProcessed, webtoonProcessed, webtoonFinishedProcessed,
                        novelProcessed);
            } else {
                log.debug("⏸️ [Consumer] 처리할 작업 없음 - 큐가 비어있습니다");
            }

        } catch (Exception e) {
            log.error("❌ [Consumer] 배치 처리 중 오류 발생", e);
        }
    }

    /**
     * 특정 타입의 작업을 지정된 개수만큼 처리
     */
    private int processByType(JobType jobType, int limit) {
        List<CrawlJob> jobs = crawlJobRepository.findPendingJobsByTypeWithLock(jobType, limit);

        if (jobs.isEmpty()) {
            return 0;
        }

        log.info("🎯 [Consumer] {} 작업 {}개 처리 시작", jobType, jobs.size());

        for (CrawlJob job : jobs) {
            processJob(job);
        }

        crawlJobRepository.saveAll(jobs);
        return jobs.size();
    }

    /**
     * 개별 작업 처리
     */
    private void processJob(CrawlJob job) {
        job.markAsProcessing();

        try {
            boolean success = false;

            switch (job.getJobType()) {
                case STEAM_GAME:
                    success = steamCrawlService.collectGameByAppId(Long.parseLong(job.getTargetId()));
                    break;

                case TMDB_MOVIE:
                    success = tmdbService.collectMovieById(job.getTargetId());
                    break;

                case TMDB_TV:
                    success = tmdbService.collectTvShowById(job.getTargetId());
                    break;

                case NAVER_WEBTOON:
                case NAVER_WEBTOON_FINISHED:
                    success = naverWebtoonService.collectWebtoonById(job.getTargetId());
                    break;

                case NAVER_SERIES_NOVEL:
                    success = naverSeriesCrawler.collectNovelById(job.getTargetId());
                    break;

                default:
                    log.warn("⚠️ 처리 로직이 없는 작업 타입: {}", job.getJobType());
                    job.markAsFailed("지원하지 않는 작업 타입");
                    return;
            }

            if (success) {
                job.markAsCompleted();
                log.debug("✅ [Consumer] 작업 성공: {} - {}", job.getJobType(), job.getTargetId());
            } else {
                job.markAsFailed("크롤링 실패 (상세 정보 없음)");
                log.warn("❌ [Consumer] 작업 실패: {} - {}", job.getJobType(), job.getTargetId());
            }

        } catch (Exception e) {
            job.markAsFailed(e.getMessage());
            log.error("❌ [Consumer] 작업 처리 중 예외 발생: {} - {}",
                    job.getJobType(), job.getTargetId(), e);
        }
    }

    /**
     * 재시도 가능한 작업들을 다시 PENDING 상태로 변경
     */
    @Scheduled(cron = "0 0 * * * *") // 1시간마다
    @Transactional
    public void retryFailedJobs() {
        // TODO: RETRY 상태인 작업들을 PENDING으로 변경
        log.debug("🔄 재시도 작업 처리 스케줄 실행");
    }
}
