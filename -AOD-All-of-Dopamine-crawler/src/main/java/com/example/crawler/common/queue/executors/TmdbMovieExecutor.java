package com.example.crawler.common.queue.executors;

import com.example.crawler.common.queue.JobExecutor;
import com.example.crawler.common.queue.JobType;
import com.example.crawler.contents.tmdb.TmdbFetcher;
import com.example.crawler.contents.tmdb.TmdbPayloadProcessor;
import com.example.crawler.ingest.CollectorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * TMDB 영화 크롤링 Executor
 * 표준형: Fetcher 상세 호출 → PayloadProcessor.process → CollectorService.saveRaw
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TmdbMovieExecutor implements JobExecutor {

    private final TmdbFetcher tmdbFetcher;
    private final TmdbPayloadProcessor payloadProcessor;
    private final CollectorService collectorService;

    @Override
    public JobType getJobType() {
        return JobType.TMDB_MOVIE;
    }

    @Override
    public boolean execute(String targetId) {
        return collectMovieById(targetId);
    }

    /**
     * 단일 영화 크롤링 (Job Queue용)
     */
    private boolean collectMovieById(String movieId) {
        try {
            String language = "ko-KR";
            log.debug("🎬 [TMDB] 영화 ID {} 크롤링 시작", movieId);

            int id = Integer.parseInt(movieId);
            Map<String, Object> detailedData = tmdbFetcher.getMovieDetails(id, language);

            if (detailedData == null || detailedData.isEmpty()) {
                log.warn("⚠️ [TMDB] 영화 ID {} 데이터 없음", movieId);
                return false;
            }

            Map<String, Object> processedData = payloadProcessor.process(detailedData);
            collectorService.saveRaw("TMDB_MOVIE", "MOVIE", processedData, movieId,
                "https://www.themoviedb.org/movie/" + movieId);

            log.debug("✅ [TMDB] 영화 ID {} 크롤링 완료", movieId);
            return true;
        } catch (Exception e) {
            log.error("❌ [TMDB] 영화 ID {} 크롤링 실패", movieId, e);
            return false;
        }
    }

    @Override
    public long getAverageExecutionTime() {
        return 800; // API 기반, 평균 800ms
    }
}
