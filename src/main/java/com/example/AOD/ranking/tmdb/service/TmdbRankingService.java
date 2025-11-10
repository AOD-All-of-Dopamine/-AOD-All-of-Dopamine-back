package com.example.AOD.ranking.tmdb.service;

import com.example.AOD.ranking.entity.ExternalRanking;
import com.example.AOD.ranking.repo.ExternalRankingRepository;
import com.example.AOD.ranking.tmdb.fetcher.TmdbRankingFetcher;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TmdbRankingService {

    private final TmdbRankingFetcher tmdbRankingFetcher;
    private final ExternalRankingRepository rankingRepository;

    @Transactional
    public void updatePopularMoviesRanking() {
        log.info("TMDB 인기 영화 랭킹 업데이트를 시작합니다.");
        JsonNode popularMovies = tmdbRankingFetcher.fetchPopularMovies();

        if (popularMovies == null || !popularMovies.has("results")) {
            log.warn("TMDB 인기 영화 랭킹 정보를 가져오지 못했습니다.");
            return;
        }

        List<ExternalRanking> rankings = new ArrayList<>();
        int rank = 1;
        for (JsonNode movieNode : popularMovies.get("results")) {
            ExternalRanking ranking = new ExternalRanking();
            ranking.setContentId(movieNode.get("id").asLong());
            ranking.setTitle(movieNode.get("title").asText());
            ranking.setRanking(rank++);
            ranking.setPlatform("TMDB_MOVIE");
            rankings.add(ranking);
        }

        // 기존 TMDB 영화 랭킹 삭제 후 새로 저장 (간단한 구현)
        // rankingRepository.deleteByPlatform("TMDB_MOVIE");
        rankingRepository.saveAll(rankings);
        log.info("TMDB 인기 영화 랭킹 업데이트 완료. 총 {}개", rankings.size());
    }

    @Transactional
    public void updatePopularTvShowsRanking() {
        log.info("TMDB 인기 TV 쇼 랭킹 업데이트를 시작합니다.");
        JsonNode popularTvShows = tmdbRankingFetcher.fetchPopularTvShows();

        if (popularTvShows == null || !popularTvShows.has("results")) {
            log.warn("TMDB 인기 TV 쇼 랭킹 정보를 가져오지 못했습니다.");
            return;
        }

        List<ExternalRanking> rankings = new ArrayList<>();
        int rank = 1;
        for (JsonNode tvNode : popularTvShows.get("results")) {
            ExternalRanking ranking = new ExternalRanking();
            ranking.setContentId(tvNode.get("id").asLong());
            ranking.setTitle(tvNode.get("name").asText()); // TV 쇼는 'name' 필드를 사용
            ranking.setRanking(rank++);
            ranking.setPlatform("TMDB_TV");
            rankings.add(ranking);
        }

        // 기존 TMDB TV 랭킹 삭제 후 새로 저장
        rankingRepository.deleteAllInBatch(rankingRepository.findByPlatform("TMDB_TV"));
        rankingRepository.saveAll(rankings);
        log.info("TMDB 인기 TV 쇼 랭킹 업데이트 완료. 총 {}개", rankings.size());
    }
}
