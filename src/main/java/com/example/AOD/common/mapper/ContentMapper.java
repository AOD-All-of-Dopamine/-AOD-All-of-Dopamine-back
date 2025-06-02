package com.example.AOD.common.mapper;

import com.example.AOD.common.commonDomain.*;
import com.example.AOD.common.dto.*;
import com.example.AOD.Webtoon.NaverWebtoon.domain.Days;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class ContentMapper {

    // ===== Novel 변환 메서드들 =====
    public ContentDetailDTO toDetailDTO(NovelCommon entity) {
        ContentDetailDTO dto = new ContentDetailDTO();

        // 기본 필드 설정
        dto.setId(entity.getId());
        dto.setContentType("novel");
        dto.setTitle(entity.getTitle());
        dto.setThumbnailUrl(entity.getImageUrl());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());

        // 소설 특화 데이터 설정
        NovelTypeData novelData = new NovelTypeData();
        novelData.setAuthors(entity.getAuthors());
        novelData.setGenres(entity.getGenre());
        novelData.setStatus(entity.getStatus());
        novelData.setPublisher(entity.getPublisher());
        novelData.setAgeRating(entity.getAgeRating());

        dto.setSpecificData(novelData);

        // 플랫폼 매핑 정보 설정
        if (entity.getPlatformMapping() != null) {
            NovelPlatformMapping mapping = entity.getPlatformMapping();
            Map<String, ContentDetailDTO.PlatformInfo> platforms = new HashMap<>();

            if (mapping.hasNaverSeries()) {
                platforms.put("naverSeries", new ContentDetailDTO.PlatformInfo(
                        mapping.getNaverSeriesId(),
                        "https://series.naver.com/novel/detail.series?productNo=" + mapping.getNaverSeriesId()
                ));
            }

            if (mapping.hasKakaoPage()) {
                platforms.put("kakaoPage", new ContentDetailDTO.PlatformInfo(
                        mapping.getKakaoPageId(),
                        "https://page.kakao.com/home?seriesId=" + mapping.getKakaoPageId()
                ));
            }

            if (mapping.hasRidibooks()) {
                platforms.put("ridibooks", new ContentDetailDTO.PlatformInfo(
                        mapping.getRidibooksId(),
                        "https://ridibooks.com/books/" + mapping.getRidibooksId()
                ));
            }

            dto.setPlatforms(platforms);

            // 간단한 플랫폼 존재 여부 맵 설정
            Map<String, Boolean> availablePlatforms = new HashMap<>();
            availablePlatforms.put("naverSeries", mapping.hasNaverSeries());
            availablePlatforms.put("kakaoPage", mapping.hasKakaoPage());
            availablePlatforms.put("ridibooks", mapping.hasRidibooks());

            dto.setAvailablePlatforms(availablePlatforms);
        }

        return dto;
    }

    public NovelCommon toNovelEntity(ContentManageDTO dto) {
        if (!"novel".equals(dto.getContentType())) {
            throw new IllegalArgumentException("Content type must be 'novel'");
        }

        NovelTypeData novelData = (NovelTypeData) dto.getSpecificData();

        NovelCommon entity = new NovelCommon();
        entity.setTitle(dto.getTitle());
        entity.setImageUrl(dto.getThumbnailUrl());
        entity.setAuthors(novelData.getAuthors());
        entity.setGenre(novelData.getGenres());
        entity.setStatus(novelData.getStatus());
        entity.setPublisher(novelData.getPublisher());
        entity.setAgeRating(novelData.getAgeRating());

        // ID가 있으면 설정 (업데이트의 경우)
        if (dto.getId() != null) {
            entity.setId(dto.getId());
        }

        // 플랫폼 매핑 생성
        if (dto.getPlatformIds() != null && !dto.getPlatformIds().isEmpty()) {
            NovelPlatformMapping mapping = new NovelPlatformMapping();
            mapping.setNovelCommon(entity);

            if (dto.getPlatformIds().containsKey("naverSeries")) {
                mapping.setNaverSeriesNovel(dto.getPlatformIds().get("naverSeries"));
            }
            if (dto.getPlatformIds().containsKey("kakaoPage")) {
                mapping.setKakaoPageNovel(dto.getPlatformIds().get("kakaoPage"));
            }
            if (dto.getPlatformIds().containsKey("ridibooks")) {
                mapping.setRidibooksNovel(dto.getPlatformIds().get("ridibooks"));
            }

            entity.setPlatformMapping(mapping);
        }

        return entity;
    }

    // ===== Movie 변환 메서드들 =====
    public ContentDetailDTO toDetailDTO(MovieCommon entity) {
        ContentDetailDTO dto = new ContentDetailDTO();

        // 기본 필드 설정
        dto.setId(entity.getId());
        dto.setContentType("movie");
        dto.setTitle(entity.getTitle());
        dto.setThumbnailUrl(entity.getImageUrl());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());

        // 영화 특화 데이터 설정
        MovieTypeData movieData = new MovieTypeData();
        movieData.setDirector(entity.getDirector());
        movieData.setActors(entity.getActors());
        movieData.setGenres(entity.getGenre());
        movieData.setRating(entity.getRating());
        movieData.setReservationRate(entity.getReservationRate());
        movieData.setRunningTime(entity.getRunningTime());
        movieData.setCountry(entity.getCountry());
        movieData.setReleaseDate(entity.getReleaseDate());
        movieData.setIsRerelease(entity.getIsRerelease());
        movieData.setAgeRating(entity.getAgeRating());
        movieData.setTotalAudience(entity.getTotalAudience());
        movieData.setSummary(entity.getSummary());

        dto.setSpecificData(movieData);

        // 플랫폼 매핑 정보 설정
        if (entity.getPlatformMapping() != null) {
            MoviePlatformMapping mapping = entity.getPlatformMapping();
            Map<String, ContentDetailDTO.PlatformInfo> platforms = new HashMap<>();

            if (mapping.hasCgv()) {
                platforms.put("cgv", new ContentDetailDTO.PlatformInfo(
                        mapping.getCgvId(),
                        "https://www.cgv.co.kr/movies/detail-view/?midx=" + mapping.getCgvId()
                ));
            }

            if (mapping.hasMegabox()) {
                platforms.put("megabox", new ContentDetailDTO.PlatformInfo(
                        mapping.getMegaboxId(),
                        "https://www.megabox.co.kr/movie-detail?rpstMovieNo=" + mapping.getMegaboxId()
                ));
            }

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

    public MovieCommon toMovieEntity(ContentManageDTO dto) {
        if (!"movie".equals(dto.getContentType())) {
            throw new IllegalArgumentException("Content type must be 'movie'");
        }

        MovieTypeData movieData = (MovieTypeData) dto.getSpecificData();

        MovieCommon entity = new MovieCommon();
        entity.setTitle(dto.getTitle());
        entity.setImageUrl(dto.getThumbnailUrl());
        entity.setDirector(movieData.getDirector());
        entity.setActors(movieData.getActors());
        entity.setGenre(movieData.getGenres());
        entity.setRating(movieData.getRating());
        entity.setReservationRate(movieData.getReservationRate());
        entity.setRunningTime(movieData.getRunningTime());
        entity.setCountry(movieData.getCountry());
        entity.setReleaseDate(movieData.getReleaseDate());
        entity.setIsRerelease(movieData.getIsRerelease());
        entity.setAgeRating(movieData.getAgeRating());
        entity.setTotalAudience(movieData.getTotalAudience());
        entity.setSummary(movieData.getSummary());

        // ID가 있으면 설정 (업데이트의 경우)
        if (dto.getId() != null) {
            entity.setId(dto.getId());
        }

        // 플랫폼 매핑 생성
        if (dto.getPlatformIds() != null && !dto.getPlatformIds().isEmpty()) {
            MoviePlatformMapping mapping = new MoviePlatformMapping();
            mapping.setMovieCommon(entity);

            if (dto.getPlatformIds().containsKey("cgv")) {
                mapping.setCgvMovie(dto.getPlatformIds().get("cgv"));
            }
            if (dto.getPlatformIds().containsKey("megabox")) {
                mapping.setMegaboxMovie(dto.getPlatformIds().get("megabox"));
            }
            if (dto.getPlatformIds().containsKey("lotteCinema")) {
                mapping.setLotteCinemaMovie(dto.getPlatformIds().get("lotteCinema"));
            }

            entity.setPlatformMapping(mapping);
        }

        return entity;
    }

    // ===== OTT 변환 메서드들 =====
    public ContentDetailDTO toDetailDTO(OTTCommon entity) {
        ContentDetailDTO dto = new ContentDetailDTO();

        // 기본 필드 설정
        dto.setId(entity.getId());
        dto.setContentType("ott");
        dto.setTitle(entity.getTitle());
        dto.setThumbnailUrl(entity.getThumbnail());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());

        // OTT 특화 데이터 설정
        OTTTypeData ottData = new OTTTypeData();
        ottData.setType(entity.getType());
        ottData.setCreator(entity.getCreator());
        ottData.setActors(entity.getActors());
        ottData.setGenres(entity.getGenre());
        ottData.setFeatures(entity.getFeatures());
        ottData.setDescription(entity.getDescription());
        ottData.setMaturityRating(entity.getMaturityRating());
        ottData.setReleaseYear(entity.getReleaseYear());

        dto.setSpecificData(ottData);

        // 플랫폼 매핑 정보 설정
        if (entity.getPlatformMapping() != null) {
            OTTPlatformMapping mapping = entity.getPlatformMapping();
            Map<String, ContentDetailDTO.PlatformInfo> platforms = new HashMap<>();

            if (mapping.hasNetflix()) {
                platforms.put("netflix", new ContentDetailDTO.PlatformInfo(mapping.getNetflixId()));
            }
            if (mapping.hasDisneyPlus()) {
                platforms.put("disneyPlus", new ContentDetailDTO.PlatformInfo(mapping.getDisneyPlusId()));
            }
            if (mapping.hasWatcha()) {
                platforms.put("watcha", new ContentDetailDTO.PlatformInfo(mapping.getWatchaId()));
            }
            if (mapping.hasWavve()) {
                platforms.put("wavve", new ContentDetailDTO.PlatformInfo(mapping.getWavveId()));
            }

            dto.setPlatforms(platforms);

            // 간단한 플랫폼 존재 여부 맵 설정
            Map<String, Boolean> availablePlatforms = new HashMap<>();
            availablePlatforms.put("netflix", mapping.hasNetflix());
            availablePlatforms.put("disneyPlus", mapping.hasDisneyPlus());
            availablePlatforms.put("watcha", mapping.hasWatcha());
            availablePlatforms.put("wavve", mapping.hasWavve());

            dto.setAvailablePlatforms(availablePlatforms);
        }

        return dto;
    }

    // ===== Webtoon 변환 메서드들 =====
    public ContentDetailDTO toDetailDTO(WebtoonCommon entity) {
        ContentDetailDTO dto = new ContentDetailDTO();

        // 기본 필드 설정
        dto.setId(entity.getId());
        dto.setContentType("webtoon");
        dto.setTitle(entity.getTitle());
        dto.setThumbnailUrl(entity.getImageUrl());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());

        // 웹툰 특화 데이터 설정
        WebtoonTypeData webtoonData = new WebtoonTypeData();

        // WebtoonAuthor 리스트를 String 리스트로 변환
        if (entity.getAuthor() != null) {
            webtoonData.setAuthors(entity.getAuthor().stream()
                    .map(author -> author.getName())
                    .collect(Collectors.toList()));
        }

        // WebtoonGenre 리스트를 String 리스트로 변환
        if (entity.getGenre() != null) {
            webtoonData.setGenres(entity.getGenre().stream()
                    .map(genre -> genre.getGenre())
                    .collect(Collectors.toList()));
        }

        // Days enum 리스트를 String 리스트로 변환
        if (entity.getUploadDay() != null) {
            webtoonData.setUploadDays(entity.getUploadDay().stream()
                    .map(Days::name)
                    .collect(Collectors.toList()));
        }

        webtoonData.setSummary(entity.getSummary());
        webtoonData.setPublishDate(entity.getPublishDate());

        dto.setSpecificData(webtoonData);

        // 플랫폼 매핑 정보 설정
        if (entity.getPlatformMapping() != null) {
            WebtoonPlatformMapping mapping = entity.getPlatformMapping();
            Map<String, ContentDetailDTO.PlatformInfo> platforms = new HashMap<>();

            if (mapping.hasNaver()) {
                platforms.put("naver", new ContentDetailDTO.PlatformInfo(
                        mapping.getNaverId(),
                        "https://comic.naver.com/webtoon/list?titleId=" + mapping.getNaverId()
                ));
            }

            if (mapping.hasKakao()) {
                platforms.put("kakao", new ContentDetailDTO.PlatformInfo(
                        mapping.getKakaoId(),
                        "https://webtoon.kakao.com/content/" + mapping.getKakaoId()
                ));
            }

            dto.setPlatforms(platforms);

            // 간단한 플랫폼 존재 여부 맵 설정
            Map<String, Boolean> availablePlatforms = new HashMap<>();
            availablePlatforms.put("naver", mapping.hasNaver());
            availablePlatforms.put("kakao", mapping.hasKakao());

            dto.setAvailablePlatforms(availablePlatforms);
        }

        return dto;
    }

    // ===== Game 변환 메서드들 =====
    public ContentDetailDTO toDetailDTO(GameCommon entity) {
        ContentDetailDTO dto = new ContentDetailDTO();

        // 기본 필드 설정
        dto.setId(entity.getId());
        dto.setContentType("game");
        dto.setTitle(entity.getTitle());
        dto.setThumbnailUrl(entity.getImageUrl());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());

        // 게임 특화 데이터 설정
        GameTypeData gameData = new GameTypeData();
        gameData.setDevelopers(entity.getDevelopers());
        gameData.setPublishers(entity.getPublisher());
        gameData.setGenres(entity.getGenre());
        gameData.setRequiredAge(entity.getRequiredAge());
        gameData.setSummary(entity.getSummary());
        gameData.setInitialPrice(entity.getInitialPrice());
        gameData.setFinalPrice(entity.getFinalPrice());

        dto.setSpecificData(gameData);

        // 플랫폼 매핑 정보 설정
        if (entity.getPlatformMapping() != null) {
            GamePlatformMapping mapping = entity.getPlatformMapping();
            Map<String, ContentDetailDTO.PlatformInfo> platforms = new HashMap<>();

            if (mapping.hasSteam()) {
                platforms.put("steam", new ContentDetailDTO.PlatformInfo(
                        mapping.getSteamId(),
                        "https://store.steampowered.com/app/" + mapping.getSteamId()
                ));
            }

            if (mapping.hasEpic()) {
                platforms.put("epic", new ContentDetailDTO.PlatformInfo(mapping.getEpicId()));
            }

            if (mapping.hasGog()) {
                platforms.put("gog", new ContentDetailDTO.PlatformInfo(mapping.getGogId()));
            }

            dto.setPlatforms(platforms);

            // 간단한 플랫폼 존재 여부 맵 설정
            Map<String, Boolean> availablePlatforms = new HashMap<>();
            availablePlatforms.put("steam", mapping.hasSteam());
            availablePlatforms.put("epic", mapping.hasEpic());
            availablePlatforms.put("gog", mapping.hasGog());

            dto.setAvailablePlatforms(availablePlatforms);
        }

        return dto;
    }

    // ===== 공통 변환 메서드 =====
    public ContentDTO toBasicDTO(Object entity) {
        if (entity instanceof NovelCommon) {
            return toBasicDTO((NovelCommon) entity);
        } else if (entity instanceof MovieCommon) {
            return toBasicDTO((MovieCommon) entity);
        } else if (entity instanceof OTTCommon) {
            return toBasicDTO((OTTCommon) entity);
        } else if (entity instanceof WebtoonCommon) {
            return toBasicDTO((WebtoonCommon) entity);
        } else if (entity instanceof GameCommon) {
            return toBasicDTO((GameCommon) entity);
        }
        throw new IllegalArgumentException("Unsupported entity type");
    }

    private ContentDTO toBasicDTO(NovelCommon entity) {
        ContentDTO dto = new ContentDTO();
        dto.setId(entity.getId());
        dto.setContentType("novel");
        dto.setTitle(entity.getTitle());
        dto.setThumbnailUrl(entity.getImageUrl());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());

        if (entity.getPlatformMapping() != null) {
            Map<String, Boolean> availablePlatforms = new HashMap<>();
            NovelPlatformMapping mapping = entity.getPlatformMapping();
            availablePlatforms.put("naverSeries", mapping.hasNaverSeries());
            availablePlatforms.put("kakaoPage", mapping.hasKakaoPage());
            availablePlatforms.put("ridibooks", mapping.hasRidibooks());
            dto.setAvailablePlatforms(availablePlatforms);
        }

        return dto;
    }

    private ContentDTO toBasicDTO(MovieCommon entity) {
        ContentDTO dto = new ContentDTO();
        dto.setId(entity.getId());
        dto.setContentType("movie");
        dto.setTitle(entity.getTitle());
        dto.setThumbnailUrl(entity.getImageUrl());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());

        if (entity.getPlatformMapping() != null) {
            Map<String, Boolean> availablePlatforms = new HashMap<>();
            MoviePlatformMapping mapping = entity.getPlatformMapping();
            availablePlatforms.put("cgv", mapping.hasCgv());
            availablePlatforms.put("megabox", mapping.hasMegabox());
            availablePlatforms.put("lotteCinema", mapping.hasLotteCinema());
            dto.setAvailablePlatforms(availablePlatforms);
        }

        return dto;
    }

    private ContentDTO toBasicDTO(OTTCommon entity) {
        ContentDTO dto = new ContentDTO();
        dto.setId(entity.getId());
        dto.setContentType("ott");
        dto.setTitle(entity.getTitle());
        dto.setThumbnailUrl(entity.getThumbnail());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());

        if (entity.getPlatformMapping() != null) {
            Map<String, Boolean> availablePlatforms = new HashMap<>();
            OTTPlatformMapping mapping = entity.getPlatformMapping();
            availablePlatforms.put("netflix", mapping.hasNetflix());
            availablePlatforms.put("disneyPlus", mapping.hasDisneyPlus());
            availablePlatforms.put("watcha", mapping.hasWatcha());
            availablePlatforms.put("wavve", mapping.hasWavve());
            dto.setAvailablePlatforms(availablePlatforms);
        }

        return dto;
    }

    private ContentDTO toBasicDTO(WebtoonCommon entity) {
        ContentDTO dto = new ContentDTO();
        dto.setId(entity.getId());
        dto.setContentType("webtoon");
        dto.setTitle(entity.getTitle());
        dto.setThumbnailUrl(entity.getImageUrl());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());

        if (entity.getPlatformMapping() != null) {
            Map<String, Boolean> availablePlatforms = new HashMap<>();
            WebtoonPlatformMapping mapping = entity.getPlatformMapping();
            availablePlatforms.put("naver", mapping.hasNaver());
            availablePlatforms.put("kakao", mapping.hasKakao());
            dto.setAvailablePlatforms(availablePlatforms);
        }

        return dto;
    }

    private ContentDTO toBasicDTO(GameCommon entity) {
        ContentDTO dto = new ContentDTO();
        dto.setId(entity.getId());
        dto.setContentType("game");
        dto.setTitle(entity.getTitle());
        dto.setThumbnailUrl(entity.getImageUrl());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());

        if (entity.getPlatformMapping() != null) {
            Map<String, Boolean> availablePlatforms = new HashMap<>();
            GamePlatformMapping mapping = entity.getPlatformMapping();
            availablePlatforms.put("steam", mapping.hasSteam());
            availablePlatforms.put("epic", mapping.hasEpic());
            availablePlatforms.put("gog", mapping.hasGog());
            dto.setAvailablePlatforms(availablePlatforms);
        }

        return dto;
    }
}