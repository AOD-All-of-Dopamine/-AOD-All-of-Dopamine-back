package com.example.AOD.movie.CGV.service;

import com.example.AOD.movie.CGV.crawler.CgvCrawler;
import com.example.AOD.movie.CGV.domain.Movie;
import com.example.AOD.movie.CGV.domain.MovieActor;
import com.example.AOD.movie.CGV.domain.MovieGenre;
import com.example.AOD.movie.CGV.dto.MovieDTO;
import com.example.AOD.movie.CGV.repository.MovieActorRepository;
import com.example.AOD.movie.CGV.repository.MovieGenreRepository;
import com.example.AOD.movie.CGV.repository.MovieRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CGVService {

    private final CgvCrawler cgvCrawler;
    private final MovieRepository movieRepository;
    private final MovieActorRepository actorRepository;
    private final MovieGenreRepository genreRepository;

    /**
     * 모든 CGV 영화 크롤링 (비동기)
     */
    @Async
    public void crawlMovies() {
        log.info("CGV 영화 크롤링 작업 시작");
        try {
            // 크롤러를 통해 영화 데이터 수집
            List<MovieDTO> movieDTOs = cgvCrawler.crawlAll();

            // 수집된 각 영화 저장
            int newMoviesCount = 0;
            for (MovieDTO movieDTO : movieDTOs) {
                // 이미 존재하는 영화는 건너뜀
                if (existsByExternalId(movieDTO.getExternalId())) {
                    log.debug("이미 존재하는 영화 ID: {}, 건너뜀", movieDTO.getExternalId());
                    continue;
                }

                // 새 영화 저장
                saveMovie(movieDTO);
                newMoviesCount++;
            }

            log.info("CGV 영화 크롤링 작업 완료. {}개 신규 영화 저장됨", newMoviesCount);
        } catch (Exception e) {
            log.error("CGV 영화 크롤링 중 오류 발생: {}", e.getMessage(), e);
        }
    }

    /**
     * MovieDTO를 받아 저장 (DTO -> Entity 변환)
     */
    @Transactional
    public Movie saveMovie(MovieDTO movieDTO) {
        // 배우 처리 - 기존에 있으면 재사용, 없으면 새로 생성
        List<MovieActor> actors = movieDTO.getActors().stream()
                .map(name -> actorRepository.findByName(name)
                        .orElseGet(() -> actorRepository.save(new MovieActor(name))))
                .collect(Collectors.toList());

        // 장르 처리 - 기존에 있으면 재사용, 없으면 새로 생성
        List<MovieGenre> genres = movieDTO.getGenres().stream()
                .map(name -> genreRepository.findByName(name)
                        .orElseGet(() -> genreRepository.save(new MovieGenre(name))))
                .collect(Collectors.toList());

        // DTO를 Entity로 변환
        Movie movie = Movie.builder()
                .title(movieDTO.getTitle())
                .director(movieDTO.getDirector())
                .reservationRate(movieDTO.getReservationRate())
                .rating(movieDTO.getRating())
                .runningTime(movieDTO.getRunningTime())
                .country(movieDTO.getCountry())
                .releaseDate(movieDTO.getReleaseDate())
                .isRerelease(movieDTO.getIsRerelease())
                .ageRating(movieDTO.getAgeRating())
                .externalId(movieDTO.getExternalId())
                .lastUpdated(movieDTO.getLastUpdated())
                .build();

        // 관계 설정
        movie.setActors(actors);
        movie.setGenres(genres);

        // 저장 및 반환
        log.info("영화 저장 완료: {}", movieDTO.getTitle());
        return movieRepository.save(movie);
    }

    /**
     * 외부 ID로 영화 존재 여부 확인
     */
    public boolean existsByExternalId(String externalId) {
        return movieRepository.existsByExternalId(externalId);
    }

    /**
     * 외부 ID로 영화 조회
     */
    public Movie findByExternalId(String externalId) {
        return movieRepository.findByExternalId(externalId);
    }
}