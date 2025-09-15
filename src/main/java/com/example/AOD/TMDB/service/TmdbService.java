package com.example.AOD.TMDB.service;

import com.example.AOD.TMDB.dto.TmdbDiscoveryResult;
import com.example.AOD.TMDB.dto.TmdbMovie;
import com.example.AOD.TMDB.dto.TmdbTvDiscoveryResult;
import com.example.AOD.TMDB.dto.TmdbTvShow;
import com.example.AOD.TMDB.dto.WatchProviderResult;
import com.example.AOD.TMDB.fetcher.TmdbApiFetcher;
import com.example.AOD.ingest.CollectorService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class TmdbService {

    private final TmdbApiFetcher tmdbApiFetcher;
    private final CollectorService collectorService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // --- 연도별 전체 수집 로직 ---
    public void collectAllMoviesByYear(int startYear, int endYear, String language) {
        log.info("TMDB 전체 영화 데이터 수집 시작: {}년부터 {}년까지", startYear, endYear);
        for (int year = startYear; year >= endYear; year--) {
            log.info("===== {}년 영화 데이터 수집 시작 =====", year);
            String startDate = year + "-01-01";
            String endDate = year + "-12-31";
            collectMoviesForPeriod(startDate, endDate, language);
        }
        log.info("TMDB 전체 영화 데이터 수집 완료.");
    }

    public void collectAllTvShowsByYear(int startYear, int endYear, String language) {
        log.info("TMDB 전체 TV쇼 데이터 수집 시작: {}년부터 {}년까지", startYear, endYear);
        for (int year = startYear; year >= endYear; year--) {
            log.info("===== {}년 TV쇼 데이터 수집 시작 =====", year);
            String startDate = year + "-01-01";
            String endDate = year + "-12-31";
            collectTvShowsForPeriod(startDate, endDate, language);
        }
        log.info("TMDB 전체 TV쇼 데이터 수집 완료.");
    }

    // --- [NEW] 인기 콘텐츠 샘플 수집 로직 ---
    /**
     * 인기 영화 데이터를 지정된 페이지 수만큼만 수집합니다. (시험용)
     * @param maxPages 수집할 최대 페이지 수
     * @param language 언어 코드
     */
    public void collectPopularMovies(int maxPages, String language) {
        log.info("TMDB 인기 영화 샘플 데이터 수집 시작. 최대 {} 페이지까지 수집.", maxPages);
        int currentPage = 1;
        int effectiveMaxPages = Math.min(maxPages, 500); // TMDB API 최대 페이지 제한

        while (currentPage <= effectiveMaxPages) {
            try {
                TmdbDiscoveryResult result = tmdbApiFetcher.discoverPopularMovies(currentPage, language);
                if (result == null || result.getResults() == null || result.getResults().isEmpty()) {
                    log.info("인기 영화 {} 페이지에 더 이상 데이터가 없어 수집을 종료합니다.", currentPage);
                    break;
                }
                log.info("인기 영화 {}/{} 페이지 수집 중... ({}개)", currentPage, result.getTotalPages(), result.getResults().size());

                processMovieList(result.getResults());

                if (currentPage >= result.getTotalPages()) {
                    log.info("인기 영화 마지막 페이지({})에 도달하여 수집을 종료합니다.", currentPage);
                    break;
                }
                currentPage++;
            } catch (Exception e) {
                log.error("인기 영화 {} 페이지 수집 중 오류 발생: {}", currentPage, e.getMessage());
                break;
            }
        }
        log.info("TMDB 인기 영화 샘플 데이터 수집 완료. 총 {} 페이지 수집.", currentPage - 1);
    }

    /**
     * 인기 TV쇼 데이터를 지정된 페이지 수만큼만 수집합니다. (시험용)
     * @param maxPages 수집할 최대 페이지 수
     * @param language 언어 코드
     */
    public void collectPopularTvShows(int maxPages, String language) {
        log.info("TMDB 인기 TV쇼 샘플 데이터 수집 시작. 최대 {} 페이지까지 수집.", maxPages);
        int currentPage = 1;
        int effectiveMaxPages = Math.min(maxPages, 500);

        while (currentPage <= effectiveMaxPages) {
            try {
                TmdbTvDiscoveryResult result = tmdbApiFetcher.discoverPopularTvShows(currentPage, language);
                if (result == null || result.getResults() == null || result.getResults().isEmpty()) {
                    log.info("인기 TV쇼 {} 페이지에 더 이상 데이터가 없어 수집을 종료합니다.", currentPage);
                    break;
                }
                log.info("인기 TV쇼 {}/{} 페이지 수집 중... ({}개)", currentPage, result.getTotalPages(), result.getResults().size());

                processTvShowList(result.getResults());

                if (currentPage >= result.getTotalPages()) {
                    log.info("인기 TV쇼 마지막 페이지({})에 도달하여 수집을 종료합니다.", currentPage);
                    break;
                }
                currentPage++;
            } catch (Exception e) {
                log.error("인기 TV쇼 {} 페이지 수집 중 오류 발생: {}", currentPage, e.getMessage());
                break;
            }
        }
        log.info("TMDB 인기 TV쇼 샘플 데이터 수집 완료. 총 {} 페이지 수집.", currentPage - 1);
    }

    // --- Private Helper Methods ---
    private void collectMoviesForPeriod(String startDate, String endDate, String language) {
        // ... (기존 연도별 수집 로직과 동일, 변경 없음)
    }

    private void collectTvShowsForPeriod(String startDate, String endDate, String language) {
        // ... (기존 연도별 수집 로직과 동일, 변경 없음)
    }

    private void processMovieList(java.util.List<TmdbMovie> movies) {
        for (TmdbMovie basicMovieInfo : movies) {
            try {
                // [수정] 상세 정보(runtime, credits 포함)를 다시 조회
                TmdbMovie detailedMovie = tmdbApiFetcher.getMovieDetails(basicMovieInfo.getId(), "ko-KR");
                if (detailedMovie == null) continue;

                WatchProviderResult watchProviders = tmdbApiFetcher.getWatchProviders(detailedMovie.getId());
                Map<String, Object> payload = createPayload(detailedMovie, watchProviders); // 상세 정보로 payload 생성

                collectorService.saveRaw("TMDB", "AV", payload, String.valueOf(detailedMovie.getId()), "https://www.themoviedb.org/movie/" + detailedMovie.getId());
                Thread.sleep(100); // API 과부하 방지
            } catch (Exception e) {
                log.error("Movie ID {} 처리 중 오류 발생: {}", basicMovieInfo.getId(), e.getMessage());
            }
        }
    }

    private void processTvShowList(java.util.List<TmdbTvShow> tvShows) {
        for (TmdbTvShow basicTvShowInfo : tvShows) {
            try {
                // [수정] 상세 정보(season_count, credits 포함)를 다시 조회합니다.
                TmdbTvShow detailedTvShow = tmdbApiFetcher.getTvShowDetails(basicTvShowInfo.getId(), "ko-KR");
                if (detailedTvShow == null) continue;

                WatchProviderResult watchProviders = tmdbApiFetcher.getTvShowWatchProviders(detailedTvShow.getId());
                Map<String, Object> payload = createTvPayload(detailedTvShow, watchProviders);

                collectorService.saveRaw("TMDB", "AV", payload, String.valueOf(detailedTvShow.getId()), "https://www.themoviedb.org/tv/" + detailedTvShow.getId());
                Thread.sleep(100);
            } catch (Exception e) {
                log.error("TV ID {} 처리 중 오류 발생: {}", basicTvShowInfo.getId(), e.getMessage());
            }
        }
    }

    private Map<String, Object> createPayload(TmdbMovie movie, WatchProviderResult providers) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("av_type", "MOVIE"); // <-- 이 줄을 추가하세요
        payload.put("movie_details", objectMapper.convertValue(movie, Map.class));
        if (providers != null && providers.getResults() != null) {
            payload.put("watch_providers", providers.getResults());
        }
        return payload;
    }

    private Map<String, Object> createTvPayload(TmdbTvShow tvShow, WatchProviderResult providers) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("av_type", "TV"); // <-- 이 줄을 추가하세요
        payload.put("tv_details", objectMapper.convertValue(tvShow, Map.class));
        if (providers != null && providers.getResults() != null) {
            payload.put("watch_providers", providers.getResults());
        }
        return payload;
    }
}

