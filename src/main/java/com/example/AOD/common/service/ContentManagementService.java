package com.example.AOD.common.service;

import com.example.AOD.common.commonDomain.*;
import com.example.AOD.common.dto.*;
import com.example.AOD.common.mapper.ContentMapper;
import com.example.AOD.common.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ContentManagementService {

    // Common 리포지토리들
    private final NovelCommonRepository novelCommonRepository;
    private final MovieCommonRepository movieCommonRepository;
    private final OTTCommonRepository ottCommonRepository;
    private final WebtoonCommonRepository webtoonCommonRepository;
    private final GameCommonRepository gameCommonRepository;

    // PlatformMapping 리포지토리들
    private final NovelPlatformMappingRepository novelMappingRepository;
    private final MoviePlatformMappingRepository movieMappingRepository;
    private final OTTPlatformMappingRepository ottMappingRepository;
    private final WebtoonPlatformMappingRepository webtoonMappingRepository;
    private final GamePlatformMappingRepository gameMappingRepository;

    private final ContentMapper contentMapper;

    /**
     * 모든 콘텐츠 유형에서 검색
     */
    @Transactional(readOnly = true)
    public Map<String, List<ContentDTO>> searchAllContent(String keyword) {
        Map<String, List<ContentDTO>> results = new HashMap<>();

        // 각 콘텐츠 타입별로 검색
        List<NovelCommon> novels = novelCommonRepository.findByTitleContainingIgnoreCase(keyword);
        results.put("novels", novels.stream()
                .map(contentMapper::toBasicDTO)
                .collect(Collectors.toList()));

        List<MovieCommon> movies = movieCommonRepository.findByTitleContainingIgnoreCase(keyword);
        results.put("movies", movies.stream()
                .map(contentMapper::toBasicDTO)
                .collect(Collectors.toList()));

        List<OTTCommon> otts = ottCommonRepository.findByTitleContainingIgnoreCase(keyword);
        results.put("otts", otts.stream()
                .map(contentMapper::toBasicDTO)
                .collect(Collectors.toList()));

        List<WebtoonCommon> webtoons = webtoonCommonRepository.findByTitleContainingIgnoreCase(keyword);
        results.put("webtoons", webtoons.stream()
                .map(contentMapper::toBasicDTO)
                .collect(Collectors.toList()));

        List<GameCommon> games = gameCommonRepository.findByTitleContainingIgnoreCase(keyword);
        results.put("games", games.stream()
                .map(contentMapper::toBasicDTO)
                .collect(Collectors.toList()));

        return results;
    }

    /**
     * 특정 콘텐츠 타입에서 검색 (페이징 지원)
     */
    /*@Transactional(readOnly = true)
    public Page<ContentDTO> searchContentByType(String contentType, String keyword, Pageable pageable) {
        switch (contentType.toLowerCase()) {
            case "novel":
                return novelCommonRepository.findByTitleContainingIgnoreCase(keyword, pageable)
                        .map(contentMapper::toBasicDTO);
            case "movie":
                return movieCommonRepository.findByTitleContainingIgnoreCase(keyword, pageable)
                        .map(contentMapper::toBasicDTO);
            case "ott":
                return ottCommonRepository.findByTitleContainingIgnoreCase(keyword, pageable)
                        .map(contentMapper::toBasicDTO);
            case "webtoon":
                return webtoonCommonRepository.findByTitleContainingIgnoreCase(keyword, pageable)
                        .map(contentMapper::toBasicDTO);
            case "game":
                return gameCommonRepository.findByTitleContainingIgnoreCase(keyword, pageable)
                        .map(contentMapper::toBasicDTO);
            default:
                throw new IllegalArgumentException("Unsupported content type: " + contentType);
        }
    }*/

    /**
     * 콘텐츠 상세 정보 조회
     */
    @Transactional(readOnly = true)
    public ContentDetailDTO getContentDetail(String contentType, Long id) {
        switch (contentType.toLowerCase()) {
            case "novel":
                return novelCommonRepository.findById(id)
                        .map(contentMapper::toDetailDTO)
                        .orElseThrow(() -> new NoSuchElementException("Novel not found with id: " + id));
            case "movie":
                return movieCommonRepository.findById(id)
                        .map(contentMapper::toDetailDTO)
                        .orElseThrow(() -> new NoSuchElementException("Movie not found with id: " + id));
            case "ott":
                return ottCommonRepository.findById(id)
                        .map(contentMapper::toDetailDTO)
                        .orElseThrow(() -> new NoSuchElementException("OTT content not found with id: " + id));
            case "webtoon":
                return webtoonCommonRepository.findById(id)
                        .map(contentMapper::toDetailDTO)
                        .orElseThrow(() -> new NoSuchElementException("Webtoon not found with id: " + id));
            case "game":
                return gameCommonRepository.findById(id)
                        .map(contentMapper::toDetailDTO)
                        .orElseThrow(() -> new NoSuchElementException("Game not found with id: " + id));
            default:
                throw new IllegalArgumentException("Unsupported content type: " + contentType);
        }
    }

    /**
     * 플랫폼별 콘텐츠 조회
     */
    @Transactional(readOnly = true)
    public List<ContentDTO> getContentByPlatform(String contentType, String platform) {
        switch (contentType.toLowerCase()) {
            case "novel":
                return getNovelsByPlatform(platform);
            case "movie":
                return getMoviesByPlatform(platform);
            case "ott":
                return getOTTsByPlatform(platform);
            case "webtoon":
                return getWebtoonsByPlatform(platform);
            case "game":
                return getGamesByPlatform(platform);
            default:
                throw new IllegalArgumentException("Unsupported content type: " + contentType);
        }
    }

    private List<ContentDTO> getNovelsByPlatform(String platform) {
        List<NovelCommon> novels;

        switch (platform.toLowerCase()) {
            case "naver":
            case "naverseries":
                novels = novelMappingRepository.findByNaverSeriesAvailable().stream()
                        .map(mapping -> mapping.getNovelCommon())
                        .collect(Collectors.toList());
                break;
            case "kakao":
            case "kakaopage":
                novels = novelMappingRepository.findByKakaoPageAvailable().stream()
                        .map(mapping -> mapping.getNovelCommon())
                        .collect(Collectors.toList());
                break;
            default:
                novels = new ArrayList<>();
        }

        return novels.stream()
                .map(contentMapper::toBasicDTO)
                .collect(Collectors.toList());
    }

    private List<ContentDTO> getMoviesByPlatform(String platform) {
        List<MovieCommon> movies;

        switch (platform.toLowerCase()) {
            case "cgv":
                movies = movieMappingRepository.findByCgvAvailable().stream()
                        .map(mapping -> mapping.getMovieCommon())
                        .collect(Collectors.toList());
                break;
            default:
                movies = new ArrayList<>();
        }

        return movies.stream()
                .map(contentMapper::toBasicDTO)
                .collect(Collectors.toList());
    }

    private List<ContentDTO> getOTTsByPlatform(String platform) {
        List<OTTCommon> otts;

        switch (platform.toLowerCase()) {
            case "netflix":
                otts = ottMappingRepository.findByNetflixAvailable().stream()
                        .map(mapping -> mapping.getOttCommon())
                        .collect(Collectors.toList());
                break;
            default:
                otts = new ArrayList<>();
        }

        return otts.stream()
                .map(contentMapper::toBasicDTO)
                .collect(Collectors.toList());
    }

    private List<ContentDTO> getWebtoonsByPlatform(String platform) {
        List<WebtoonCommon> webtoons;

        switch (platform.toLowerCase()) {
            case "naver":
                webtoons = webtoonMappingRepository.findByNaverAvailable().stream()
                        .map(mapping -> mapping.getWebtoonCommon())
                        .collect(Collectors.toList());
                break;
            default:
                webtoons = new ArrayList<>();
        }

        return webtoons.stream()
                .map(contentMapper::toBasicDTO)
                .collect(Collectors.toList());
    }

    private List<ContentDTO> getGamesByPlatform(String platform) {
        List<GameCommon> games;

        switch (platform.toLowerCase()) {
            case "steam":
                games = gameMappingRepository.findBySteamAvailable().stream()
                        .map(mapping -> mapping.getGameCommon())
                        .collect(Collectors.toList());
                break;
            default:
                games = new ArrayList<>();
        }

        return games.stream()
                .map(contentMapper::toBasicDTO)
                .collect(Collectors.toList());
    }

    /**
     * 멀티플랫폼 콘텐츠 조회
     */
    @Transactional(readOnly = true)
    public Map<String, List<ContentDTO>> getMultiPlatformContent() {
        Map<String, List<ContentDTO>> results = new HashMap<>();

        // 멀티플랫폼 소설
        List<NovelCommon> multiPlatformNovels = novelMappingRepository.findMultiPlatformNovels().stream()
                .map(mapping -> mapping.getNovelCommon())
                .collect(Collectors.toList());
        results.put("novels", multiPlatformNovels.stream()
                .map(contentMapper::toBasicDTO)
                .collect(Collectors.toList()));

        // 멀티플랫폼 영화
        List<MovieCommon> multiPlatformMovies = movieMappingRepository.findAllPlatformMovies().stream()
                .map(mapping -> mapping.getMovieCommon())
                .collect(Collectors.toList());
        results.put("movies", multiPlatformMovies.stream()
                .map(contentMapper::toBasicDTO)
                .collect(Collectors.toList()));

        // 멀티플랫폼 웹툰
        List<WebtoonCommon> multiPlatformWebtoons = webtoonMappingRepository.findMultiPlatformWebtoons().stream()
                .map(mapping -> mapping.getWebtoonCommon())
                .collect(Collectors.toList());
        results.put("webtoons", multiPlatformWebtoons.stream()
                .map(contentMapper::toBasicDTO)
                .collect(Collectors.toList()));

        return results;
    }

    /**
     * 플랫폼 독점 콘텐츠 조회
     */
    @Transactional(readOnly = true)
    public Map<String, List<ContentDTO>> getExclusiveContent(String platform) {
        Map<String, List<ContentDTO>> results = new HashMap<>();

        switch (platform.toLowerCase()) {
            case "netflix":
                List<OTTCommon> netflixExclusives = ottMappingRepository.findNetflixExclusives().stream()
                        .map(mapping -> mapping.getOttCommon())
                        .collect(Collectors.toList());
                results.put("otts", netflixExclusives.stream()
                        .map(contentMapper::toBasicDTO)
                        .collect(Collectors.toList()));
                break;

            case "cgv":
                List<MovieCommon> cgvExclusives = movieMappingRepository.findCgvExclusives().stream()
                        .map(mapping -> mapping.getMovieCommon())
                        .collect(Collectors.toList());
                results.put("movies", cgvExclusives.stream()
                        .map(contentMapper::toBasicDTO)
                        .collect(Collectors.toList()));
                break;

            case "steam":
                List<GameCommon> steamExclusives = gameMappingRepository.findSteamExclusives().stream()
                        .map(mapping -> mapping.getGameCommon())
                        .collect(Collectors.toList());
                results.put("games", steamExclusives.stream()
                        .map(contentMapper::toBasicDTO)
                        .collect(Collectors.toList()));
                break;
        }

        return results;
    }

    /**
     * 통계 정보 조회
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();

        // 전체 콘텐츠 수
        Map<String, Long> contentCounts = new HashMap<>();
        contentCounts.put("novels", novelCommonRepository.count());
        contentCounts.put("movies", movieCommonRepository.count());
        contentCounts.put("otts", ottCommonRepository.count());
        contentCounts.put("webtoons", webtoonCommonRepository.count());
        contentCounts.put("games", gameCommonRepository.count());
        stats.put("contentCounts", contentCounts);

        // 플랫폼별 통계
        Map<String, Map<String, Long>> platformStats = new HashMap<>();

        // 소설 플랫폼 통계
        Map<String, Long> novelPlatforms = new HashMap<>();
        novelPlatforms.put("naverSeries", (long) novelMappingRepository.findByNaverSeriesAvailable().size());
        novelPlatforms.put("kakaoPage", (long) novelMappingRepository.findByKakaoPageAvailable().size());
        novelPlatforms.put("multiPlatform", (long) novelMappingRepository.findMultiPlatformNovels().size());
        platformStats.put("novels", novelPlatforms);

        // 영화 플랫폼 통계
        Map<String, Long> moviePlatforms = new HashMap<>();
        moviePlatforms.put("cgv", (long) movieMappingRepository.findByCgvAvailable().size());
        moviePlatforms.put("cgvExclusive", (long) movieMappingRepository.findCgvExclusives().size());
        moviePlatforms.put("allPlatforms", (long) movieMappingRepository.findAllPlatformMovies().size());
        platformStats.put("movies", moviePlatforms);

        stats.put("platformStats", platformStats);

        return stats;
    }

    /**
     * 콘텐츠 생성 또는 업데이트
     */
    @Transactional
    public ContentDetailDTO createOrUpdateContent(ContentManageDTO dto) {
        switch (dto.getContentType().toLowerCase()) {
            case "novel":
                return createOrUpdateNovel(dto);
            case "movie":
                return createOrUpdateMovie(dto);
            // 다른 타입들도 구현...
            default:
                throw new IllegalArgumentException("Unsupported content type: " + dto.getContentType());
        }
    }

    /* ====== 소설 ====== */
    private ContentDetailDTO createOrUpdateNovel(ContentManageDTO dto) {
        NovelCommon novel = contentMapper.toNovelEntity(dto);

        // 기존 엔티티 확인 (업데이트)
        final Long novelId = novel.getId();              // ★ 람다 캡처용 final 변수
        if (novelId != null) {
            NovelCommon existing = novelCommonRepository.findById(novelId)
                    .orElseThrow(() ->
                            new NoSuchElementException("Novel not found with id: " + novelId));

            updateNovelFields(existing, novel);
            novel = existing;
        }

        // 저장
        novel = novelCommonRepository.save(novel);

        // 플랫폼 매핑 업데이트
        if (dto.getPlatformIds() != null && !dto.getPlatformIds().isEmpty()) {
            updateNovelPlatformMapping(novel, dto.getPlatformIds());
        }

        return contentMapper.toDetailDTO(novel);
    }

    private void updateNovelPlatformMapping(NovelCommon novel, Map<String, Long> platformIds) {
        NovelPlatformMapping mapping = novel.getPlatformMapping();
        if (mapping == null) {
            mapping = new NovelPlatformMapping();
            mapping.setNovelCommon(novel);
        }

        // ★ 일반 for-each 루프로 변경 → 람다 캡처 문제 해결
        for (Map.Entry<String, Long> entry : platformIds.entrySet()) {
            String platform = entry.getKey().toLowerCase();
            Long id = entry.getValue();

            switch (platform) {
                case "naverseries" -> mapping.setNaverSeriesNovel(id);
                case "kakaopage"   -> mapping.setKakaoPageNovel(id);
                case "ridibooks"   -> mapping.setRidibooksNovel(id);
            }
        }

        novelMappingRepository.save(mapping);
    }

    private void updateNovelFields(NovelCommon existing, NovelCommon updated) {
        existing.setTitle(updated.getTitle());
        existing.setImageUrl(updated.getImageUrl());
        existing.setAuthors(updated.getAuthors());
        existing.setGenre(updated.getGenre());
        existing.setStatus(updated.getStatus());
        existing.setPublisher(updated.getPublisher());
        existing.setAgeRating(updated.getAgeRating());
    }



    /* ====== 영화 ====== */
    private ContentDetailDTO createOrUpdateMovie(ContentManageDTO dto) {
        MovieCommon movie = contentMapper.toMovieEntity(dto);

        final Long movieId = movie.getId();              // ★ 람다 캡처용 final 변수
        if (movieId != null) {
            MovieCommon existing = movieCommonRepository.findById(movieId)
                    .orElseThrow(() ->
                            new NoSuchElementException("Movie not found with id: " + movieId));

            updateMovieFields(existing, movie);
            movie = existing;
        }

        movie = movieCommonRepository.save(movie);

        if (dto.getPlatformIds() != null && !dto.getPlatformIds().isEmpty()) {
            updateMoviePlatformMapping(movie, dto.getPlatformIds());
        }

        return contentMapper.toDetailDTO(movie);
    }

    private void updateMoviePlatformMapping(MovieCommon movie, Map<String, Long> platformIds) {
        MoviePlatformMapping mapping = movie.getPlatformMapping();
        if (mapping == null) {
            mapping = new MoviePlatformMapping();
            mapping.setMovieCommon(movie);
        }

        // ★ 일반 for-each 루프
        for (Map.Entry<String, Long> entry : platformIds.entrySet()) {
            String platform = entry.getKey().toLowerCase();
            Long id = entry.getValue();

            switch (platform) {
                case "cgv"         -> mapping.setCgvMovie(id);
                case "megabox"     -> mapping.setMegaboxMovie(id);
                case "lottecinema" -> mapping.setLotteCinemaMovie(id);
            }
        }

        movieMappingRepository.save(mapping);
    }

    private void updateMovieFields(MovieCommon existing, MovieCommon updated) {
        existing.setTitle(updated.getTitle());
        existing.setImageUrl(updated.getImageUrl());
        existing.setDirector(updated.getDirector());
        existing.setActors(updated.getActors());
        existing.setGenre(updated.getGenre());
        existing.setRating(updated.getRating());
        existing.setReservationRate(updated.getReservationRate());
        existing.setRunningTime(updated.getRunningTime());
        existing.setCountry(updated.getCountry());
        existing.setReleaseDate(updated.getReleaseDate());
        existing.setIsRerelease(updated.getIsRerelease());
        existing.setAgeRating(updated.getAgeRating());
        existing.setTotalAudience(updated.getTotalAudience());
        existing.setSummary(updated.getSummary());
    }


    /**
     * 콘텐츠 삭제
     */
    @Transactional
    public void deleteContent(String contentType, Long id) {
        switch (contentType.toLowerCase()) {
            case "novel":
                novelCommonRepository.deleteById(id);
                break;
            case "movie":
                movieCommonRepository.deleteById(id);
                break;
            case "ott":
                ottCommonRepository.deleteById(id);
                break;
            case "webtoon":
                webtoonCommonRepository.deleteById(id);
                break;
            case "game":
                gameCommonRepository.deleteById(id);
                break;
            default:
                throw new IllegalArgumentException("Unsupported content type: " + contentType);
        }
    }
}