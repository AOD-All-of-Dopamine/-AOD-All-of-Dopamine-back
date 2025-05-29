package com.example.AOD.commonV2.mapper;

import com.example.AOD.commonV2.domain.*;
import com.example.AOD.commonV2.dto.*;

import java.util.HashMap;
import java.util.Map;

public class ContentMapper {

    // MovieCommonV2 -> ContentDetailDTO 변환 예시
    public static ContentDetailDTO toDetailDTO(MovieCommonV2 entity) {
        ContentDetailDTO dto = new ContentDetailDTO();

        // 기본 필드 설정
        dto.setId(entity.getId());
        dto.setContentType("movie");
        dto.setTitle(entity.getTitle());
        dto.setThumbnailUrl(entity.getThumbnailUrl());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());

        // 영화 특화 데이터 설정
        MovieTypeData movieData = new MovieTypeData();
        movieData.setDirector(entity.getDirector());
        movieData.setActors(entity.getActors());
        movieData.setGenres(entity.getGenres());
        movieData.setRating(entity.getRating());
        movieData.setReservationRate(entity.getReservationRate());
        movieData.setRunningTime(entity.getRunningTime());
        movieData.setCountry(entity.getCountry());
        movieData.setReleaseDate(entity.getReleaseDate());
        movieData.setIsRerelease(entity.getIsRerelease());
        movieData.setAgeRating(entity.getAgeRating());

        dto.setSpecificData(movieData);

        // 플랫폼 매핑 정보 설정
        if (entity.getPlatformMapping() != null) {
            MoviePlatformMapping mapping = entity.getPlatformMapping();
            Map<String, ContentDetailDTO.PlatformInfo> platforms = new HashMap<>();

            // CGV 정보
            if (mapping.hasCgv()) {
                platforms.put("cgv", new ContentDetailDTO.PlatformInfo(
                        mapping.getCgvId(),
                        "https://www.cgv.co.kr/movies/detail-view/?midx=" + mapping.getCgvId()
                ));
            }

            // 메가박스 정보
            if (mapping.hasMegabox()) {
                platforms.put("megabox", new ContentDetailDTO.PlatformInfo(
                        mapping.getMegaboxId(),
                        "https://www.megabox.co.kr/movie-detail?rpstMovieNo=" + mapping.getMegaboxId()
                ));
            }

            // 롯데시네마 정보
            if (mapping.hasLotteCinema()) {
                platforms.put("lotteCinema", new ContentDetailDTO.PlatformInfo(
                        mapping.getLotteCinemaId(),
                        "https://www.lottecinema.co.kr/NLCHS/Movie/MovieDetailView?movie=" + mapping.getLotteCinemaId()
                ));
            }

            dto.setPlatforms(platforms);

            // 간단한 플랫폼 존재 여부 맵 설정
            Map<String, Boolean> availablePlatforms = new HashMap<>();
            availablePlatforms.put("cgv", mapping.hasCgv());
            availablePlatforms.put("megabox", mapping.hasMegabox());
            availablePlatforms.put("lotteCinema", mapping.hasLotteCinema());

            dto.setAvailablePlatforms(availablePlatforms);
        }

        return dto;
    }

    // ContentManageDTO -> MovieCommonV2 변환 예시 (생성용)
    public static MovieCommonV2 toMovieEntity(ContentManageDTO dto) {
        if (!"movie".equals(dto.getContentType())) {
            throw new IllegalArgumentException("Content type must be 'movie'");
        }

        MovieTypeData movieData = (MovieTypeData) dto.getSpecificData();

        MovieCommonV2 entity = new MovieCommonV2();
        entity.setTitle(dto.getTitle());
        entity.setThumbnailUrl(dto.getThumbnailUrl());
        entity.setDirector(movieData.getDirector());
        entity.setActors(movieData.getActors());
        entity.setGenres(movieData.getGenres());
        entity.setRating(movieData.getRating());
        entity.setReservationRate(movieData.getReservationRate());
        entity.setRunningTime(movieData.getRunningTime());
        entity.setCountry(movieData.getCountry());
        entity.setReleaseDate(movieData.getReleaseDate());
        entity.setIsRerelease(movieData.getIsRerelease());
        entity.setAgeRating(movieData.getAgeRating());

        // 플랫폼 매핑 생성
        MoviePlatformMapping mapping = new MoviePlatformMapping();
        mapping.setMovieCommon(entity);

        // 플랫폼 ID 설정
        if (dto.getPlatformIds() != null) {
            if (dto.getPlatformIds().containsKey("cgv")) {
                mapping.setCgvMovie(dto.getPlatformIds().get("cgv"));
            }
            if (dto.getPlatformIds().containsKey("megabox")) {
                mapping.setMegaboxMovie(dto.getPlatformIds().get("megabox"));
            }
            if (dto.getPlatformIds().containsKey("lotteCinema")) {
                mapping.setLotteCinemaMovie(dto.getPlatformIds().get("lotteCinema"));
            }
        }

        entity.setPlatformMapping(mapping);

        return entity;
    }

    // 다른 엔티티 타입에 대한 유사한 메서드들...
}