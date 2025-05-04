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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
     * 모든 CGV 영화 크롤링 (비동기, 수동 트리거용)
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
     * 매일 자정에 실행되는 자동 크롤링 작업
     * cron = "0 0 0 * * ?" => 초 분 시 일 월 요일
     * 매일 자정(00:00:00)에 실행
     */
    @Scheduled(cron = "0 0 0 * * ?")
    @Transactional
    public void scheduledDailyCrawl() {
        LocalDateTime startTime = LocalDateTime.now();
        log.info("일일 자동 크롤링 작업 시작: {}", startTime);

        try {
            // 크롤러를 통해 영화 데이터 수집
            List<MovieDTO> movieDTOs = cgvCrawler.crawlAll();

            // 새 영화 목록 (로깅용)
            List<String> newMovieTitles = new ArrayList<>();

            // 새 영화 저장 (기존에 없는 ID만)
            int newMoviesCount = 0;
            for (MovieDTO movieDTO : movieDTOs) {
                if (!existsByExternalId(movieDTO.getExternalId())) {
                    Movie savedMovie = saveMovie(movieDTO);
                    newMoviesCount++;
                    newMovieTitles.add(savedMovie.getTitle());
                    log.info("새 영화 발견 및 저장: {}", movieDTO.getTitle());
                }
            }

            LocalDateTime endTime = LocalDateTime.now();
            log.info("일일 자동 크롤링 작업 완료. 소요 시간: {}초, {}개 신규 영화 저장됨",
                    endTime.getSecond() - startTime.getSecond(), newMoviesCount);

            // 신규 영화가 있으면 로그에 추가 기록
            if (!newMovieTitles.isEmpty()) {
                log.info("오늘 추가된 신규 영화 목록: {}", String.join(", ", newMovieTitles));
            } else {
                log.info("오늘은 추가된 신규 영화가 없습니다.");
            }

        } catch (Exception e) {
            log.error("일일 자동 크롤링 중 오류 발생: {}", e.getMessage(), e);
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
                .lastUpdated(LocalDate.now())
                .build();

        // 관계 설정
        movie.setActors(actors);
        movie.setGenres(genres);

        // 저장 및 반환
        log.info("영화 저장 완료: {}", movieDTO.getTitle());
        return movieRepository.save(movie);
    }

    /**
     * 기존 영화 데이터 업데이트 (예매율, 평점 등)
     *
     * @param movieDTO 업데이트할 영화 데이터
     * @return 업데이트된 영화 엔티티
     */
    @Transactional
    public Movie updateMovieData(MovieDTO movieDTO) {
        Movie movie = movieRepository.findByExternalId(movieDTO.getExternalId());
        if (movie != null) {
            // 변동 가능한 데이터만 업데이트
            movie.setReservationRate(movieDTO.getReservationRate());
            movie.setRating(movieDTO.getRating());
            movie.setLastUpdated(LocalDate.now());

            log.info("영화 데이터 업데이트: {} (평점: {}, 예매율: {})",
                    movie.getTitle(), movieDTO.getRating(), movieDTO.getReservationRate());

            return movieRepository.save(movie);
        }
        return null;
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