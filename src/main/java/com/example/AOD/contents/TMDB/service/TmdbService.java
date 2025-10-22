package com.example.AOD.contents.TMDB.service;

import com.example.AOD.contents.TMDB.processor.TmdbPayloadProcessor;
import com.example.AOD.contents.TMDB.dto.TmdbDiscoveryResult;
import com.example.AOD.contents.TMDB.dto.TmdbMovie;
import com.example.AOD.contents.TMDB.dto.TmdbTvDiscoveryResult;
import com.example.AOD.contents.TMDB.dto.TmdbTvShow;
import com.example.AOD.contents.TMDB.dto.WatchProviderResult;
import com.example.AOD.contents.TMDB.fetcher.TmdbApiFetcher;
import com.example.AOD.ingest.CollectorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class TmdbService {

    private final TmdbApiFetcher tmdbApiFetcher;
    private final CollectorService collectorService;
    private final TmdbPayloadProcessor payloadProcessor;

    // --- 연도별 전체 수집 로직 ---
    public void collectAllMoviesByYear(int startYear, int endYear, String language) {
        log.info("TMDB 전체 영화 데이터 수집 시작: {}년부터 {}년까지", startYear, endYear);
        for (int year = startYear; year >= endYear; year--) {
            log.info("===== {}년 영화 데이터 수집 시작 =====", year);
            String startDate = year + "-01-01";
            String endDate = year + "-12-31";
            collectMoviesForPeriod(startDate, endDate, language, 500); // 연도별 수집은 최대 500페이지까지
        }
        log.info("TMDB 전체 영화 데이터 수집 완료.");
    }

    public void collectAllTvShowsByYear(int startYear, int endYear, String language) {
        log.info("TMDB 전체 TV쇼 데이터 수집 시작: {}년부터 {}년까지", startYear, endYear);
        for (int year = startYear; year >= endYear; year--) {
            log.info("===== {}년 TV쇼 데이터 수집 시작 =====", year);
            String startDate = year + "-01-01";
            String endDate = year + "-12-31";
            collectTvShowsForPeriod(startDate, endDate, language, 500); // 연도별 수집은 최대 500페이지까지
        }
        log.info("TMDB 전체 TV쇼 데이터 수집 완료.");
    }

    // --- 샘플 데이터 수집 로직 ---
    public void collectPopularMovies(int maxPages, String language) {
        log.info("TMDB 인기 영화 샘플 데이터 수집 시작. 최대 {} 페이지까지 수집.", maxPages);
        collectMoviesForPeriod(null, null, language, maxPages);
        log.info("TMDB 인기 영화 샘플 데이터 수집 완료.");
    }

    public void collectPopularTvShows(int maxPages, String language) {
        log.info("TMDB 인기 TV쇼 샘플 데이터 수집 시작. 최대 {} 페이지까지 수집.", maxPages);
        collectTvShowsForPeriod(null, null, language, maxPages);
        log.info("TMDB 인기 TV쇼 샘플 데이터 수집 완료.");
    }

    public void collectMoviesByYearSample(int year, int maxPages, String language) {
        log.info("TMDB {}년 영화 샘플 데이터 수집 시작. 최대 {} 페이지까지 수집.", year, maxPages);
        String startDate = year + "-01-01";
        String endDate = year + "-12-31";
        collectMoviesForPeriod(startDate, endDate, language, maxPages);
        log.info("TMDB {}년 영화 샘플 데이터 수집 완료.", year);
    }

    public void collectTvShowsByYearSample(int year, int maxPages, String language) {
        log.info("TMDB {}년 TV쇼 샘플 데이터 수집 시작. 최대 {} 페이지까지 수집.", year, maxPages);
        String startDate = year + "-01-01";
        String endDate = year + "-12-31";
        collectTvShowsForPeriod(startDate, endDate, language, maxPages);
        log.info("TMDB {}년 TV쇼 샘플 데이터 수집 완료.", year);
    }


    // --- Private Helper Methods ---

    public void collectMoviesForPeriod(String startDate, String endDate, String language, int maxPages) {
        int currentPage = 1;
        int effectiveMaxPages = Math.min(maxPages, 500); // TMDB API는 최대 500페이지까지만 지원

        while (currentPage <= effectiveMaxPages) {
            try {
                // [개선] 통합된 API 호출 메서드 사용
                TmdbDiscoveryResult result = tmdbApiFetcher.discoverMovies(language, currentPage, startDate, endDate);

                if (result == null || result.getResults() == null || result.getResults().isEmpty()) {
                    log.info("해당 조건의 {} 페이지에 더 이상 영화 데이터가 없어 수집을 종료합니다.", currentPage);
                    break;
                }
                log.info("영화 {}/{} 페이지 수집 중... ({}개)", currentPage, result.getTotalPages(), result.getResults().size());
                processMovieList(result.getResults(), language);

                if (currentPage >= result.getTotalPages()) {
                    log.info("마지막 페이지({})에 도달하여 수집을 종료합니다.", currentPage);
                    break;
                }
                currentPage++;
                TimeUnit.MILLISECONDS.sleep(200); // 페이지 간 간격
            } catch (Exception e) {
                log.error("{} 페이지 수집 중 오류 발생: {}", currentPage, e.getMessage());
                break;
            }
        }
    }

    private void processMovieList(java.util.List<TmdbMovie> movies, String language) throws InterruptedException {
        for (TmdbMovie movie : movies) {
            try {
                Map<String, Object> detailedData = tmdbApiFetcher.getMovieDetails(movie.getId(), language);
                Map<String, Object> processedData = payloadProcessor.process(detailedData);
                processedData.put("av_type", "movie");
                collectorService.saveRaw("TMDB_MOVIE", "AV", processedData, String.valueOf(movie.getId()), "https://www.themoviedb.org/movie/" + movie.getId());
                TimeUnit.MILLISECONDS.sleep(100); // 개별 상세 조회에 대한 Rate Limiting
            } catch (Exception e) {
                log.error("영화 상세 정보 처리 중 오류 발생 (ID: {}): {}", movie.getId(), e.getMessage());
            }
        }
    }

    public void collectTvShowsForPeriod(String startDate, String endDate, String language, int maxPages) {
        int currentPage = 1;
        int effectiveMaxPages = Math.min(maxPages, 500);

        while (currentPage <= effectiveMaxPages) {
            try {
                // [개선] 통합된 API 호출 메서드 사용
                TmdbTvDiscoveryResult result = tmdbApiFetcher.discoverTvShows(language, currentPage, startDate, endDate);

                if (result == null || result.getResults() == null || result.getResults().isEmpty()) {
                    log.info("해당 조건의 {} 페이지에 더 이상 TV쇼 데이터가 없어 수집을 종료합니다.", currentPage);
                    break;
                }
                log.info("TV쇼 {}/{} 페이지 수집 중... ({}개)", currentPage, result.getTotalPages(), result.getResults().size());
                processTvShowList(result.getResults(), language);

                if (currentPage >= result.getTotalPages()) {
                    log.info("마지막 페이지({})에 도달하여 수집을 종료합니다.", currentPage);
                    break;
                }
                currentPage++;
                TimeUnit.MILLISECONDS.sleep(200); // 페이지 간 간격
            } catch (Exception e) {
                log.error("{} 페이지 수집 중 오류 발생: {}", currentPage, e.getMessage());
                break;
            }
        }
    }

    private void processTvShowList(java.util.List<TmdbTvShow> tvShows, String language) throws InterruptedException {
        for (TmdbTvShow tvShow : tvShows) {
            try {
                Map<String, Object> detailedData = tmdbApiFetcher.getTvShowDetails(tvShow.getId(), language);
                Map<String, Object> processedData = payloadProcessor.process(detailedData);
                processedData.put("av_type", "tv");
                collectorService.saveRaw("TMDB_TV", "AV", processedData, String.valueOf(tvShow.getId()), "https://www.themoviedb.org/tv/" + tvShow.getId());
                TimeUnit.MILLISECONDS.sleep(100);
            } catch (Exception e) {
                log.error("TV쇼 상세 정보 처리 중 오류 발생 (ID: {}): {}", tvShow.getId(), e.getMessage());
            }
        }
    }
}