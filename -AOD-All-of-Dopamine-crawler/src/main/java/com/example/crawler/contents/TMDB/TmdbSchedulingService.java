package com.example.crawler.contents.tmdb;

import com.example.crawler.common.queue.CrawlJobProducer;
import com.example.crawler.common.queue.JobType;
import com.example.crawler.contents.tmdb.dto.TmdbDiscoveryResult;
import com.example.crawler.contents.tmdb.dto.TmdbTvDiscoveryResult;
import com.example.crawler.contents.tmdb.TmdbApiFetcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * TMDB 크롤링 스케줄링 서비스
 * 
 * Job Queue 기반으로 작업을 생성합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TmdbSchedulingService {

    private final CrawlJobProducer crawlJobProducer;
    private final TmdbApiFetcher tmdbApiFetcher;

    private static final int OLDEST_YEAR = 1970; // 전체 크롤링 시 가장 오래된 연도
    private static final int MAX_PAGES = 10; // 최대 페이지 수 (매일 실행)
    private static final int MAX_PAGES_FULL_CRAWL = 500; // 전체 크롤링 시 페이지 수

    /**
     * TMDB 신규 콘텐츠 목록을 Job Queue에 등록합니다.
     * 
     * 매일 새벽 1시 실행 (최근 7일간의 영화/TV 데이터)
     */
    public void collectNewContentDaily() {
        LocalDate today = LocalDate.now();
        LocalDate sevenDaysAgo = today.minusDays(7);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        String startDate = sevenDaysAgo.format(formatter);
        String endDate = today.format(formatter);
        String language = "ko-KR";

        log.info("🎬 [TMDB Producer] TMDB 신규 콘텐츠 목록 수집 시작 (기간: {} ~ {})", startDate, endDate);
        
        try {
            // 영화 ID 목록 가져오기
            List<String> movieIds = fetchMovieIds(language, startDate, endDate, MAX_PAGES);
            
            if (!movieIds.isEmpty()) {
                int created = crawlJobProducer.createJobs(JobType.TMDB_MOVIE, movieIds, 4);
                log.info("✅ [TMDB Producer] 영화 {} 개 작업 생성 완료", created);
            } else {
                log.info("🔵 [TMDB Producer] 신규 영화 없음");
            }
            
            // TV 쇼 ID 목록 가져오기
            List<String> tvIds = fetchTvShowIds(language, startDate, endDate, MAX_PAGES);
            
            if (!tvIds.isEmpty()) {
                int created = crawlJobProducer.createJobs(JobType.TMDB_TV, tvIds, 4);
                log.info("✅ [TMDB Producer] TV 쇼 {} 개 작업 생성 완료", created);
            } else {
                log.info("🔵 [TMDB Producer] 신규 TV 쇼 없음");
            }
            
        } catch (Exception e) {
            log.error("❌ [TMDB Producer] TMDB 목록 수집 중 오류 발생", e);
        }
    }

    /**
     * TMDB API로부터 영화 ID 목록 가져오기
     */
    private List<String> fetchMovieIds(String language, String startDate, String endDate, int maxPages) {
        List<String> movieIds = new ArrayList<>();
        
        for (int page = 1; page <= maxPages; page++) {
            try {
                TmdbDiscoveryResult result = tmdbApiFetcher.discoverMovies(language, page, startDate, endDate);
                
                if (result == null || result.getResults() == null || result.getResults().isEmpty()) {
                    log.debug("[TMDB] 영화 페이지 {} 데이터 없음, 종료", page);
                    break;
                }
                
                result.getResults().forEach(movie -> {
                    movieIds.add(String.valueOf(movie.getId()));
                });
                
                log.debug("[TMDB] 영화 페이지 {}: {} 개 발견", page, result.getResults().size());
                
                // 마지막 페이지 확인
                if (page >= result.getTotalPages()) {
                    break;
                }
                
                // API 요청 제한 방지
                Thread.sleep(250);
                
            } catch (Exception e) {
                log.error("[TMDB] 영화 페이지 {} 가져오기 실패", page, e);
                break;
            }
        }
        
        return movieIds;
    }

    /**
     * TMDB API로부터 TV 쇼 ID 목록 가져오기
     */
    private List<String> fetchTvShowIds(String language, String startDate, String endDate, int maxPages) {
        List<String> tvIds = new ArrayList<>();
        
        for (int page = 1; page <= maxPages; page++) {
            try {
                TmdbTvDiscoveryResult result = tmdbApiFetcher.discoverTvShows(language, page, startDate, endDate);
                
                if (result == null || result.getResults() == null || result.getResults().isEmpty()) {
                    log.debug("[TMDB] TV 페이지 {} 데이터 없음, 종료", page);
                    break;
                }
                
                result.getResults().forEach(tv -> {
                    tvIds.add(String.valueOf(tv.getId()));
                });
                
                log.debug("[TMDB] TV 페이지 {}: {} 개 발견", page, result.getResults().size());
                
                // 마지막 페이지 확인
                if (page >= result.getTotalPages()) {
                    break;
                }
                
                // API 요청 제한 방지
                Thread.sleep(250);
                
            } catch (Exception e) {
                log.error("[TMDB] TV 페이지 {} 가져오기 실패", page, e);
                break;
            }
        }
        
        return tvIds;
    }

    /**
     * TMDB 전체 영화 목록을 Job Queue에 등록합니다.
     * 
     * Admin 페이지에서 수동 실행용 (1970년 ~ 현재)
     */
    public void collectAllMovies() {
        int currentYear = Year.now().getValue();
        collectMoviesByYearRange(OLDEST_YEAR, currentYear);
    }

    /**
     * TMDB 영화 목록을 특정 연도 범위로 Job Queue에 등록합니다.
     * 
     * @param startYear 시작 연도 (예: 2010)
     * @param endYear 끝 연도 (예: 2020)
     */
    public void collectMoviesByYearRange(int startYear, int endYear) {
        String language = "ko-KR";
        
        log.info("🎬 [TMDB Producer] TMDB 영화 목록 수집 시작 ({}년 ~ {}년)", startYear, endYear);
        
        try {
            int totalCount = 0;
            
            for (int year = startYear; year <= endYear; year++) {
                String startDate = year + "-01-01";
                String endDate = year + "-12-31";
                
                List<String> movieIds = fetchMovieIds(language, startDate, endDate, MAX_PAGES_FULL_CRAWL);
                
                if (!movieIds.isEmpty()) {
                    int created = crawlJobProducer.createJobs(JobType.TMDB_MOVIE, movieIds, 4);
                    totalCount += created;
                    log.info("✅ [TMDB Producer] {}년 영화 {} 개 작업 생성", year, created);
                }
                
                // API 요청 제한 방지 (연도별 간격)
                Thread.sleep(500);
            }
            
            log.info("✅ [TMDB Producer] 영화 수집 완료 ({}년~{}년): 총 {} 개 작업 생성", startYear, endYear, totalCount);
            
        } catch (Exception e) {
            log.error("❌ [TMDB Producer] TMDB 영화 목록 수집 중 오류 발생 ({}년~{}년)", startYear, endYear, e);
        }
    }

    /**
     * TMDB 전체 TV 쇼 목록을 Job Queue에 등록합니다.
     * 
     * Admin 페이지에서 수동 실행용 (1970년 ~ 현재)
     */
    public void collectAllTvShows() {
        int currentYear = Year.now().getValue();
        collectTvShowsByYearRange(OLDEST_YEAR, currentYear);
    }

    /**
     * TMDB TV 쇼 목록을 특정 연도 범위로 Job Queue에 등록합니다.
     * 
     * @param startYear 시작 연도 (예: 2010)
     * @param endYear 끝 연도 (예: 2020)
     */
    public void collectTvShowsByYearRange(int startYear, int endYear) {
        String language = "ko-KR";
        
        log.info("📺 [TMDB Producer] TMDB TV 쇼 목록 수집 시작 ({}년 ~ {}년)", startYear, endYear);
        
        try {
            int totalCount = 0;
            
            for (int year = startYear; year <= endYear; year++) {
                String startDate = year + "-01-01";
                String endDate = year + "-12-31";
                
                List<String> tvIds = fetchTvShowIds(language, startDate, endDate, MAX_PAGES_FULL_CRAWL);
                
                if (!tvIds.isEmpty()) {
                    int created = crawlJobProducer.createJobs(JobType.TMDB_TV, tvIds, 4);
                    totalCount += created;
                    log.info("✅ [TMDB Producer] {}년 TV 쇼 {} 개 작업 생성", year, created);
                }
                
                // API 요청 제한 방지 (연도별 간격)
                Thread.sleep(500);
            }
            
            log.info("✅ [TMDB Producer] TV 쇼 수집 완료 ({}년~{}년): 총 {} 개 작업 생성", startYear, endYear, totalCount);
            
        } catch (Exception e) {
            log.error("❌ [TMDB Producer] TMDB TV 쇼 목록 수집 중 오류 발생 ({}년~{}년)", startYear, endYear, e);
        }
    }

    /**
     * TMDB 전체 콘텐츠 (영화 + TV 쇼) 목록을 Job Queue에 등록합니다.
     * 
     * Admin 페이지에서 수동 실행용
     */
    public void collectAllContent() {
        log.info("🎬📺 [TMDB Producer] TMDB 전체 콘텐츠 목록 수집 시작");
        
        try {
            // 영화 전체 수집
            collectAllMovies();
            
            // TV 쇼 전체 수집
            collectAllTvShows();
            
            log.info("✅ [TMDB Producer] TMDB 전체 콘텐츠 수집 완료");
            
        } catch (Exception e) {
            log.error("❌ [TMDB Producer] TMDB 전체 콘텐츠 목록 수집 중 오류 발생", e);
        }
    }
}

