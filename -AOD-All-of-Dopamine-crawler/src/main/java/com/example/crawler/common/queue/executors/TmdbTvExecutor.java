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
 * TMDB TV 크롤링 Executor
 * 표준형: Fetcher 상세 호출 → PayloadProcessor.process → CollectorService.saveRaw
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TmdbTvExecutor implements JobExecutor {

    private final TmdbFetcher tmdbFetcher;
    private final TmdbPayloadProcessor payloadProcessor;
    private final CollectorService collectorService;

    @Override
    public JobType getJobType() {
        return JobType.TMDB_TV;
    }

    @Override
    public boolean execute(String targetId) {
        return collectTvShowById(targetId);
    }

    /**
     * 단일 TV 쇼 크롤링 (Job Queue용)
     */
    private boolean collectTvShowById(String tvId) {
        try {
            String language = "ko-KR";
            log.debug("📺 [TMDB] TV ID {} 크롤링 시작", tvId);

            int id = Integer.parseInt(tvId);
            Map<String, Object> detailedData = tmdbFetcher.getTvShowDetails(id, language);

            if (detailedData == null || detailedData.isEmpty()) {
                log.warn("⚠️ [TMDB] TV ID {} 데이터 없음", tvId);
                return false;
            }

            Map<String, Object> processedData = payloadProcessor.process(detailedData);
            collectorService.saveRaw("TMDB_TV", "TV", processedData, tvId,
                "https://www.themoviedb.org/tv/" + tvId);

            log.debug("✅ [TMDB] TV ID {} 크롤링 완료", tvId);
            return true;
        } catch (Exception e) {
            log.error("❌ [TMDB] TV ID {} 크롤링 실패", tvId, e);
            return false;
        }
    }

    @Override
    public long getAverageExecutionTime() {
        return 800; // API 기반, 평균 800ms
    }
}
