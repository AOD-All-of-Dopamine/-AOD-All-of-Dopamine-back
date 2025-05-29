package com.example.AOD.commonV2.service;

import com.example.AOD.commonV2.domain.*;
import com.example.AOD.commonV2.repository.*;
import com.example.AOD.Novel.NaverSeriesNovel.repository.NaverSeriesNovelRepository;
import com.example.AOD.movie.CGV.repository.MovieRepository;
import com.example.AOD.OTT.Netflix.repository.NetflixContentRepository;
import com.example.AOD.Webtoon.NaverWebtoon.repository.WebtoonRepository;
import com.example.AOD.game.StreamAPI.repository.GameRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ContentIntegrationServiceV2 {

    // Common 리포지토리들
    private final NovelCommonV2Repository novelCommonV2Repository;
    private final MovieCommonV2Repository movieCommonV2Repository;
    private final OTTCommonV2Repository ottCommonV2Repository;
    private final WebtoonCommonV2Repository webtoonCommonV2Repository;
    private final GameCommonV2Repository gameCommonV2Repository;

    // Mapping 리포지토리들
    private final NovelPlatformMappingRepository novelMappingRepository;
    private final MoviePlatformMappingRepository movieMappingRepository;
    private final OTTPlatformMappingRepository ottMappingRepository;
    private final WebtoonPlatformMappingRepository webtoonMappingRepository;
    private final GamePlatformMappingRepository gameMappingRepository;

    // 플랫폼 리포지토리들
    private final NaverSeriesNovelRepository naverSeriesNovelRepository;
    private final MovieRepository movieRepository;
    private final NetflixContentRepository netflixContentRepository;
    private final WebtoonRepository webtoonRepository;
    private final GameRepository gameRepository;

    /**
     * 플랫폼 콘텐츠를 Common과 Mapping에 통합
     */
    @Transactional
    public <T> void integrateContent(String contentType, Long platformId, String platform, T platformContent) {
        switch (contentType.toLowerCase()) {
            case "novel":
                integrateNovel(platformId, platform, platformContent);
                break;
            case "movie":
                integrateMovie(platformId, platform, platformContent);
                break;
            case "ott":
                integrateOTT(platformId, platform, platformContent);
                break;
            case "webtoon":
                integrateWebtoon(platformId, platform, platformContent);
                break;
            case "game":
                integrateGame(platformId, platform, platformContent);
                break;
            default:
                throw new IllegalArgumentException("지원하지 않는 콘텐츠 타입: " + contentType);
        }
    }

    /**
     * 소설 통합
     */
    @Transactional
    public void integrateNovel(Long platformId, String platform, Object platformContent) {
        try {
            // 1. 제목으로 기존 Common 검색
            String title = extractField(platformContent, "title", String.class);
            Optional<NovelCommonV2> existingCommon = novelCommonV2Repository.findByTitleIgnoreCase(title);

            NovelCommonV2 common;
            NovelPlatformMapping mapping;

            if (existingCommon.isPresent()) {
                // 기존 Common이 있으면 업데이트
                common = existingCommon.get();
                mapping = common.getPlatformMapping();
                if (mapping == null) {
                    mapping = new NovelPlatformMapping();
                    mapping.setNovelCommon(common);
                }
                log.info("기존 소설 Common 발견: {}", title);
            } else {
                // 새로운 Common 생성
                common = createNovelCommon(platformContent);
                common = novelCommonV2Repository.save(common);

                mapping = new NovelPlatformMapping();
                mapping.setNovelCommon(common);
                log.info("새로운 소설 Common 생성: {}", title);
            }

            // 2. 플랫폼 정보를 Mapping에 추가
            switch (platform.toLowerCase()) {
                case "naver":
                case "naverseries":
                    mapping.setNaverSeriesNovel(platformId);
                    break;
                case "kakao":
                case "kakaopage":
                    mapping.setKakaoPageNovel(platformId);
                    break;
                case "ridibooks":
                    mapping.setRidibooksNovel(platformId);
                    break;
                default:
                    log.warn("지원하지 않는 소설 플랫폼: {}", platform);
                    return;
            }

            // 3. Mapping 저장
            novelMappingRepository.save(mapping);
            log.info("소설 플랫폼 매핑 완료: {} -> {}", platform, title);

        } catch (Exception e) {
            log.error("소설 통합 중 오류 발생: {}", e.getMessage(), e);
            throw new RuntimeException("소설 통합 실패", e);
        }
    }

    /**
     * 영화 통합
     */
    @Transactional
    public void integrateMovie(Long platformId, String platform, Object platformContent) {
        try {
            String title = extractField(platformContent, "title", String.class);
            Optional<MovieCommonV2> existingCommon = movieCommonV2Repository.findByTitleIgnoreCase(title);

            MovieCommonV2 common;
            MoviePlatformMapping mapping;

            if (existingCommon.isPresent()) {
                common = existingCommon.get();
                mapping = common.getPlatformMapping();
                if (mapping == null) {
                    mapping = new MoviePlatformMapping();
                    mapping.setMovieCommon(common);
                }
                log.info("기존 영화 Common 발견: {}", title);
            } else {
                common = createMovieCommon(platformContent);
                common = movieCommonV2Repository.save(common);

                mapping = new MoviePlatformMapping();
                mapping.setMovieCommon(common);
                log.info("새로운 영화 Common 생성: {}", title);
            }

            // 플랫폼 정보를 Mapping에 추가
            switch (platform.toLowerCase()) {
                case "cgv":
                    mapping.setCgvMovie(platformId);
                    break;
                case "megabox":
                    mapping.setMegaboxMovie(platformId);
                    break;
                case "lottecinema":
                    mapping.setLotteCinemaMovie(platformId);
                    break;
                default:
                    log.warn("지원하지 않는 영화 플랫폼: {}", platform);
                    return;
            }

            movieMappingRepository.save(mapping);
            log.info("영화 플랫폼 매핑 완료: {} -> {}", platform, title);

        } catch (Exception e) {
            log.error("영화 통합 중 오류 발생: {}", e.getMessage(), e);
            throw new RuntimeException("영화 통합 실패", e);
        }
    }

    /**
     * OTT 통합
     */
    @Transactional
    public void integrateOTT(Long platformId, String platform, Object platformContent) {
        try {
            String title = extractField(platformContent, "title", String.class);
            Optional<OTTCommonV2> existingCommon = ottCommonV2Repository.findByTitleIgnoreCase(title);

            OTTCommonV2 common;
            OTTPlatformMapping mapping;

            if (existingCommon.isPresent()) {
                common = existingCommon.get();
                mapping = common.getPlatformMapping();
                if (mapping == null) {
                    mapping = new OTTPlatformMapping();
                    mapping.setOttCommon(common);
                }
                log.info("기존 OTT Common 발견: {}", title);
            } else {
                common = createOTTCommon(platformContent);
                common = ottCommonV2Repository.save(common);

                mapping = new OTTPlatformMapping();
                mapping.setOttCommon(common);
                log.info("새로운 OTT Common 생성: {}", title);
            }

            // 플랫폼 정보를 Mapping에 추가
            switch (platform.toLowerCase()) {
                case "netflix":
                    mapping.setNetflixContent(platformId);
                    break;
                case "disneyplus":
                    mapping.setDisneyPlusContent(platformId);
                    break;
                case "watcha":
                    mapping.setWatchaContent(platformId);
                    break;
                case "wavve":
                    mapping.setWavveContent(platformId);
                    break;
                default:
                    log.warn("지원하지 않는 OTT 플랫폼: {}", platform);
                    return;
            }

            ottMappingRepository.save(mapping);
            log.info("OTT 플랫폼 매핑 완료: {} -> {}", platform, title);

        } catch (Exception e) {
            log.error("OTT 통합 중 오류 발생: {}", e.getMessage(), e);
            throw new RuntimeException("OTT 통합 실패", e);
        }
    }

    /**
     * 웹툰 통합
     */
    @Transactional
    public void integrateWebtoon(Long platformId, String platform, Object platformContent) {
        try {
            String title = extractField(platformContent, "title", String.class);
            Optional<WebtoonCommonV2> existingCommon = webtoonCommonV2Repository.findByTitleIgnoreCase(title);

            WebtoonCommonV2 common;
            WebtoonPlatformMapping mapping;

            if (existingCommon.isPresent()) {
                common = existingCommon.get();
                mapping = common.getPlatformMapping();
                if (mapping == null) {
                    mapping = new WebtoonPlatformMapping();
                    mapping.setWebtoonCommon(common);
                }
                log.info("기존 웹툰 Common 발견: {}", title);
            } else {
                common = createWebtoonCommon(platformContent);
                common = webtoonCommonV2Repository.save(common);

                mapping = new WebtoonPlatformMapping();
                mapping.setWebtoonCommon(common);
                log.info("새로운 웹툰 Common 생성: {}", title);
            }

            // 플랫폼 정보를 Mapping에 추가
            switch (platform.toLowerCase()) {
                case "naver":
                    mapping.setNaverWebtoon(platformId);
                    break;
                case "kakao":
                    mapping.setKakaoWebtoon(platformId);
                    break;
                default:
                    log.warn("지원하지 않는 웹툰 플랫폼: {}", platform);
                    return;
            }

            webtoonMappingRepository.save(mapping);
            log.info("웹툰 플랫폼 매핑 완료: {} -> {}", platform, title);

        } catch (Exception e) {
            log.error("웹툰 통합 중 오류 발생: {}", e.getMessage(), e);
            throw new RuntimeException("웹툰 통합 실패", e);
        }
    }

    /**
     * 게임 통합
     */
    @Transactional
    public void integrateGame(Long platformId, String platform, Object platformContent) {
        try {
            String title = extractField(platformContent, "title", String.class);
            Optional<GameCommonV2> existingCommon = gameCommonV2Repository.findByTitleIgnoreCase(title);

            GameCommonV2 common;
            GamePlatformMapping mapping;

            if (existingCommon.isPresent()) {
                common = existingCommon.get();
                mapping = common.getPlatformMapping();
                if (mapping == null) {
                    mapping = new GamePlatformMapping();
                    mapping.setGameCommon(common);
                }
                log.info("기존 게임 Common 발견: {}", title);
            } else {
                common = createGameCommon(platformContent);
                common = gameCommonV2Repository.save(common);

                mapping = new GamePlatformMapping();
                mapping.setGameCommon(common);
                log.info("새로운 게임 Common 생성: {}", title);
            }

            // 플랫폼 정보를 Mapping에 추가
            switch (platform.toLowerCase()) {
                case "steam":
                    mapping.setSteamGame(platformId);
                    break;
                case "epic":
                    mapping.setEpicGame(platformId);
                    break;
                case "gog":
                    mapping.setGogGame(platformId);
                    break;
                default:
                    log.warn("지원하지 않는 게임 플랫폼: {}", platform);
                    return;
            }

            gameMappingRepository.save(mapping);
            log.info("게임 플랫폼 매핑 완료: {} -> {}", platform, title);

        } catch (Exception e) {
            log.error("게임 통합 중 오류 발생: {}", e.getMessage(), e);
            throw new RuntimeException("게임 통합 실패", e);
        }
    }

    /**
     * 배치 통합 - 여러 플랫폼 콘텐츠를 한 번에 통합
     */
    @Transactional
    public void batchIntegrateContent(String contentType, String platform, List<Long> platformIds) {
        log.info("배치 통합 시작: {} - {} 플랫폼, {}개 콘텐츠", contentType, platform, platformIds.size());

        List<Object> platformContents = loadPlatformContents(contentType, platform, platformIds);

        for (int i = 0; i < platformIds.size() && i < platformContents.size(); i++) {
            try {
                Long platformId = platformIds.get(i);
                Object platformContent = platformContents.get(i);

                if (platformContent != null) {
                    integrateContent(contentType, platformId, platform, platformContent);
                }
            } catch (Exception e) {
                log.error("배치 통합 중 개별 콘텐츠 처리 실패: {}", e.getMessage());
                // 개별 실패는 전체 배치를 중단하지 않음
            }
        }

        log.info("배치 통합 완료: {} - {} 플랫폼", contentType, platform);
    }

    /**
     * 플랫폼에서 콘텐츠 로드
     */
    private List<Object> loadPlatformContents(String contentType, String platform, List<Long> platformIds) {
        switch (contentType.toLowerCase()) {
            case "novel":
                return naverSeriesNovelRepository.findAllById(platformIds)
                        .stream().map(Object.class::cast).collect(Collectors.toList());
            case "movie":
                return movieRepository.findAllById(platformIds)
                        .stream().map(Object.class::cast).collect(Collectors.toList());
            case "ott":
                return netflixContentRepository.findAllById(platformIds)
                        .stream().map(Object.class::cast).collect(Collectors.toList());
            case "webtoon":
                return webtoonRepository.findAllById(platformIds)
                        .stream().map(Object.class::cast).collect(Collectors.toList());
            case "game":
                return gameRepository.findAllById(platformIds)
                        .stream().map(Object.class::cast).collect(Collectors.toList());
            default:
                return Collections.emptyList();
        }
    }

    /**
     * Common 엔티티 생성 헬퍼 메서드들
     */
    private NovelCommonV2 createNovelCommon(Object platformContent) {
        NovelCommonV2 common = new NovelCommonV2();
        common.setTitle(extractField(platformContent, "title", String.class));
        common.setStatus(extractField(platformContent, "status", String.class));
        common.setPublisher(extractField(platformContent, "publisher", String.class));
        common.setAgeRating(extractField(platformContent, "ageRating", String.class));
        common.setImageUrl(extractField(platformContent, "imageUrl", String.class));

        // 작가 정보 변환
        List<String> authors = convertAuthorsToStringList(platformContent);
        common.setAuthors(authors);

        // 장르 정보 변환
        List<String> genres = convertGenresToStringList(platformContent);
        common.setGenres(genres);

        return common;
    }

    private MovieCommonV2 createMovieCommon(Object platformContent) {
        MovieCommonV2 common = new MovieCommonV2();
        common.setTitle(extractField(platformContent, "title", String.class));
        common.setDirector(extractField(platformContent, "director", String.class));
        common.setRating(extractField(platformContent, "rating", Double.class));
        common.setReservationRate(extractField(platformContent, "reservationRate", Double.class));
        common.setRunningTime(extractField(platformContent, "runningTime", Integer.class));
        common.setCountry(extractField(platformContent, "country", String.class));
        common.setReleaseDate(extractField(platformContent, "releaseDate", java.time.LocalDate.class));
        common.setIsRerelease(extractField(platformContent, "isRerelease", Boolean.class));
        common.setAgeRating(extractField(platformContent, "ageRating", String.class));
        common.setThumbnailUrl(extractField(platformContent, "thumbnailUrl", String.class));

        // 배우 정보 변환
        List<String> actors = convertActorsToStringList(platformContent);
        common.setActors(actors);

        // 장르 정보 변환
        List<String> genres = convertGenresToStringList(platformContent);
        common.setGenres(genres);

        return common;
    }

    private OTTCommonV2 createOTTCommon(Object platformContent) {
        OTTCommonV2 common = new OTTCommonV2();
        common.setTitle(extractField(platformContent, "title", String.class));
        common.setType(extractField(platformContent, "type", String.class));
        common.setCreator(extractField(platformContent, "creator", String.class));
        common.setDescription(extractField(platformContent, "description", String.class));
        common.setMaturityRating(extractField(platformContent, "maturityRating", String.class));
        common.setReleaseYear(extractField(platformContent, "releaseYear", String.class));
        common.setThumbnailUrl(extractField(platformContent, "thumbnail", String.class));

        // 배우 정보 변환
        List<String> actors = convertNetflixActorsToStringList(platformContent);
        common.setActors(actors);

        // 장르 정보 변환
        List<String> genres = convertNetflixGenresToStringList(platformContent);
        common.setGenres(genres);

        // 특징 정보 변환
        List<String> features = convertNetflixFeaturesToStringList(platformContent);
        common.setFeatures(features);

        return common;
    }

    private WebtoonCommonV2 createWebtoonCommon(Object platformContent) {
        WebtoonCommonV2 common = new WebtoonCommonV2();
        common.setTitle(extractField(platformContent, "title", String.class));
        common.setSummary(extractField(platformContent, "summary", String.class));
        common.setPublishDate(extractField(platformContent, "publishDate", String.class));
        common.setThumbnailUrl(extractField(platformContent, "thumbnail", String.class));
        common.setUploadDays(extractField(platformContent, "uploadDays", List.class));

        // 작가 정보 변환
        List<String> authors = convertWebtoonAuthorsToStringList(platformContent);
        common.setAuthors(authors);

        // 장르 정보 변환
        List<String> genres = convertWebtoonGenresToStringList(platformContent);
        common.setGenres(genres);

        return common;
    }

    private GameCommonV2 createGameCommon(Object platformContent) {
        GameCommonV2 common = new GameCommonV2();
        common.setTitle(extractField(platformContent, "title", String.class));
        common.setRequiredAge(extractField(platformContent, "required_age", Long.class));
        common.setSummary(extractField(platformContent, "short_description", String.class));
        common.setInitialPrice(extractField(platformContent, "initialPrice", Integer.class));
        common.setFinalPrice(extractField(platformContent, "finalPrice", Integer.class));
        common.setThumbnailUrl(extractField(platformContent, "header_image", String.class));

        // 개발사 정보 변환
        List<String> developers = convertGameDevelopersToStringList(platformContent);
        common.setDevelopers(developers);

        // 퍼블리셔 정보 변환
        List<String> publishers = convertGamePublishersToStringList(platformContent);
        common.setPublishers(publishers);

        // 장르 정보 변환
        List<String> genres = convertGameGenresToStringList(platformContent);
        common.setGenres(genres);

        return common;
    }

    /**
     * 리플렉션을 사용한 필드 추출
     */
    @SuppressWarnings("unchecked")
    private <T> T extractField(Object source, String fieldName, Class<T> targetType) {
        try {
            Field field = findField(source.getClass(), fieldName);
            if (field != null) {
                field.setAccessible(true);
                Object value = field.get(source);
                return targetType.cast(value);
            }
        } catch (Exception e) {
            log.warn("필드 추출 실패: {} - {}", fieldName, e.getMessage());
        }
        return null;
    }

    private Field findField(Class<?> clazz, String fieldName) {
        try {
            return clazz.getDeclaredField(fieldName);
        } catch (NoSuchFieldException e) {
            if (clazz.getSuperclass() != null) {
                return findField(clazz.getSuperclass(), fieldName);
            }
            return null;
        }
    }

    /**
     * 엔티티별 특수 변환 메서드들
     */
    private List<String> convertAuthorsToStringList(Object platformContent) {
        try {
            Object authors = extractField(platformContent, "authors", Object.class);
            if (authors instanceof List) {
                return ((List<?>) authors).stream()
                        .map(author -> {
                            try {
                                Field nameField = findField(author.getClass(), "name");
                                nameField.setAccessible(true);
                                return (String) nameField.get(author);
                            } catch (Exception e) {
                                return author.toString();
                            }
                        })
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
            }
        } catch (Exception e) {
            log.warn("작가 정보 변환 실패: {}", e.getMessage());
        }
        return Collections.emptyList();
    }

    private List<String> convertGenresToStringList(Object platformContent) {
        try {
            Object genres = extractField(platformContent, "genres", Object.class);
            if (genres instanceof List) {
                return ((List<?>) genres).stream()
                        .map(genre -> {
                            try {
                                Field nameField = findField(genre.getClass(), "name");
                                if (nameField == null) {
                                    nameField = findField(genre.getClass(), "genre");
                                }
                                if (nameField != null) {
                                    nameField.setAccessible(true);
                                    return (String) nameField.get(genre);
                                }
                                return genre.toString();
                            } catch (Exception e) {
                                return genre.toString();
                            }
                        })
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
            }
        } catch (Exception e) {
            log.warn("장르 정보 변환 실패: {}", e.getMessage());
        }
        return Collections.emptyList();
    }

    // 각 플랫폼별 특수 변환 메서드들
    private List<String> convertActorsToStringList(Object platformContent) {
        try {
            Object actors = extractField(platformContent, "actors", Object.class);
            if (actors instanceof List) {
                return ((List<?>) actors).stream()
                        .map(actor -> {
                            try {
                                Field nameField = findField(actor.getClass(), "name");
                                nameField.setAccessible(true);
                                return (String) nameField.get(actor);
                            } catch (Exception e) {
                                return actor.toString();
                            }
                        })
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
            }
        } catch (Exception e) {
            log.warn("배우 정보 변환 실패: {}", e.getMessage());
        }
        return Collections.emptyList();
    }

    private List<String> convertNetflixActorsToStringList(Object platformContent) {
        try {
            Object actors = extractField(platformContent, "netflixContentActors", Object.class);
            if (actors instanceof List) {
                return ((List<?>) actors).stream()
                        .map(actor -> {
                            try {
                                Field nameField = findField(actor.getClass(), "name");
                                nameField.setAccessible(true);
                                return (String) nameField.get(actor);
                            } catch (Exception e) {
                                return actor.toString();
                            }
                        })
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
            }
        } catch (Exception e) {
            log.warn("넷플릭스 배우 정보 변환 실패: {}", e.getMessage());
        }
        return Collections.emptyList();
    }

    private List<String> convertNetflixGenresToStringList(Object platformContent) {
        try {
            Object genres = extractField(platformContent, "netflixContentGenres", Object.class);
            if (genres instanceof List) {
                return ((List<?>) genres).stream()
                        .map(genre -> {
                            try {
                                Field nameField = findField(genre.getClass(), "name");
                                nameField.setAccessible(true);
                                return (String) nameField.get(genre);
                            } catch (Exception e) {
                                return genre.toString();
                            }
                        })
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
            }
        } catch (Exception e) {
            log.warn("넷플릭스 장르 정보 변환 실패: {}", e.getMessage());
        }
        return Collections.emptyList();
    }

    private List<String> convertNetflixFeaturesToStringList(Object platformContent) {
        try {
            Object features = extractField(platformContent, "netflixContentFeatures", Object.class);
            if (features instanceof List) {
                return ((List<?>) features).stream()
                        .map(feature -> {
                            try {
                                Field nameField = findField(feature.getClass(), "name");
                                nameField.setAccessible(true);
                                return (String) nameField.get(feature);
                            } catch (Exception e) {
                                return feature.toString();
                            }
                        })
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
            }
        } catch (Exception e) {
            log.warn("넷플릭스 특징 정보 변환 실패: {}", e.getMessage());
        }
        return Collections.emptyList();
    }

    private List<String> convertWebtoonAuthorsToStringList(Object platformContent) {
        try {
            Object authors = extractField(platformContent, "webtoonAuthors", Object.class);
            if (authors instanceof List) {
                return ((List<?>) authors).stream()
                        .map(author -> {
                            try {
                                Field nameField = findField(author.getClass(), "name");
                                nameField.setAccessible(true);
                                return (String) nameField.get(author);
                            } catch (Exception e) {
                                return author.toString();
                            }
                        })
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
            }
        } catch (Exception e) {
            log.warn("웹툰 작가 정보 변환 실패: {}", e.getMessage());
        }
        return Collections.emptyList();
    }

    private List<String> convertWebtoonGenresToStringList(Object platformContent) {
        try {
            Object genres = extractField(platformContent, "webtoonGenres", Object.class);
            if (genres instanceof List) {
                return ((List<?>) genres).stream()
                        .map(genre -> {
                            try {
                                Field nameField = findField(genre.getClass(), "genre");
                                if (nameField == null) {
                                    nameField = findField(genre.getClass(), "name");
                                }
                                if (nameField != null) {
                                    nameField.setAccessible(true);
                                    return (String) nameField.get(genre);
                                }
                                return genre.toString();
                            } catch (Exception e) {
                                return genre.toString();
                            }
                        })
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
            }
        } catch (Exception e) {
            log.warn("웹툰 장르 정보 변환 실패: {}", e.getMessage());
        }
        return Collections.emptyList();
    }

    private List<String> convertGameDevelopersToStringList(Object platformContent) {
        try {
            Object developers = extractField(platformContent, "developers", Object.class);
            if (developers instanceof List) {
                return ((List<?>) developers).stream()
                        .map(developer -> {
                            try {
                                Field nameField = findField(developer.getClass(), "name");
                                if (nameField != null) {
                                    nameField.setAccessible(true);
                                    return (String) nameField.get(developer);
                                }
                                return developer.toString();
                            } catch (Exception e) {
                                return developer.toString();
                            }
                        })
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
            }
        } catch (Exception e) {
            log.warn("게임 개발사 정보 변환 실패: {}", e.getMessage());
        }
        return Collections.emptyList();
    }

    private List<String> convertGamePublishersToStringList(Object platformContent) {
        try {
            Object publishers = extractField(platformContent, "publishers", Object.class);
            if (publishers instanceof List) {
                return ((List<?>) publishers).stream()
                        .map(publisher -> {
                            try {
                                Field nameField = findField(publisher.getClass(), "name");
                                if (nameField != null) {
                                    nameField.setAccessible(true);
                                    return (String) nameField.get(publisher);
                                }
                                return publisher.toString();
                            } catch (Exception e) {
                                return publisher.toString();
                            }
                        })
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
            }
        } catch (Exception e) {
            log.warn("게임 퍼블리셔 정보 변환 실패: {}", e.getMessage());
        }
        return Collections.emptyList();
    }

    private List<String> convertGameGenresToStringList(Object platformContent) {
        try {
            Object genres = extractField(platformContent, "genres", Object.class);
            if (genres instanceof List) {
                return ((List<?>) genres).stream()
                        .map(genre -> {
                            try {
                                Field nameField = findField(genre.getClass(), "name");
                                if (nameField != null) {
                                    nameField.setAccessible(true);
                                    return (String) nameField.get(genre);
                                }
                                return genre.toString();
                            } catch (Exception e) {
                                return genre.toString();
                            }
                        })
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
            }
        } catch (Exception e) {
            log.warn("게임 장르 정보 변환 실패: {}", e.getMessage());
        }
        return Collections.emptyList();
    }

    /**
     * 통합 상태 확인 메서드
     */
    public Map<String, Object> getIntegrationStatus(String contentType) {
        Map<String, Object> status = new HashMap<>();

        switch (contentType.toLowerCase()) {
            case "novel":
                long novelCommonCount = novelCommonV2Repository.count();
                long novelMappingCount = novelMappingRepository.count();
                status.put("commonCount", novelCommonCount);
                status.put("mappingCount", novelMappingCount);
                break;
            case "movie":
                long movieCommonCount = movieCommonV2Repository.count();
                long movieMappingCount = movieMappingRepository.count();
                status.put("commonCount", movieCommonCount);
                status.put("mappingCount", movieMappingCount);
                break;
            case "ott":
                long ottCommonCount = ottCommonV2Repository.count();
                long ottMappingCount = ottMappingRepository.count();
                status.put("commonCount", ottCommonCount);
                status.put("mappingCount", ottMappingCount);
                break;
            case "webtoon":
                long webtoonCommonCount = webtoonCommonV2Repository.count();
                long webtoonMappingCount = webtoonMappingRepository.count();
                status.put("commonCount", webtoonCommonCount);
                status.put("mappingCount", webtoonMappingCount);
                break;
            case "game":
                long gameCommonCount = gameCommonV2Repository.count();
                long gameMappingCount = gameMappingRepository.count();
                status.put("commonCount", gameCommonCount);
                status.put("mappingCount", gameMappingCount);
                break;
            default:
                throw new IllegalArgumentException("지원하지 않는 콘텐츠 타입: " + contentType);
        }

        status.put("contentType", contentType);
        status.put("timestamp", java.time.LocalDateTime.now());

        return status;
    }

    /**
     * 중복 콘텐츠 찾기 (제목 기반)
     */
    public List<Map<String, Object>> findDuplicateContent(String contentType) {
        List<Map<String, Object>> duplicates = new ArrayList<>();

        switch (contentType.toLowerCase()) {
            case "novel":
                // 모든 소설을 가져와서 제목별로 그룹화하여 중복 찾기
                List<NovelCommonV2> allNovels = novelCommonV2Repository.findAll();
                Map<String, List<NovelCommonV2>> novelsByTitle = allNovels.stream()
                        .collect(Collectors.groupingBy(novel -> novel.getTitle().toLowerCase()));

                novelsByTitle.entrySet().stream()
                        .filter(entry -> entry.getValue().size() > 1)
                        .forEach(entry -> {
                            Map<String, Object> duplicateInfo = new HashMap<>();
                            duplicateInfo.put("title", entry.getKey());
                            duplicateInfo.put("count", entry.getValue().size());
                            duplicateInfo.put("contentType", "novel");
                            duplicates.add(duplicateInfo);
                        });
                break;
            case "movie":
                List<MovieCommonV2> allMovies = movieCommonV2Repository.findAll();
                Map<String, List<MovieCommonV2>> moviesByTitle = allMovies.stream()
                        .collect(Collectors.groupingBy(movie -> movie.getTitle().toLowerCase()));

                moviesByTitle.entrySet().stream()
                        .filter(entry -> entry.getValue().size() > 1)
                        .forEach(entry -> {
                            Map<String, Object> duplicateInfo = new HashMap<>();
                            duplicateInfo.put("title", entry.getKey());
                            duplicateInfo.put("count", entry.getValue().size());
                            duplicateInfo.put("contentType", "movie");
                            duplicates.add(duplicateInfo);
                        });
                break;
            // 다른 콘텐츠 타입들도 유사하게 구현
        }

        return duplicates;
    }

    /**
     * 플랫폼별 통합 통계
     */
    public Map<String, Object> getPlatformIntegrationStats(String contentType, String platform) {
        Map<String, Object> stats = new HashMap<>();

        switch (contentType.toLowerCase()) {
            case "novel":
                switch (platform.toLowerCase()) {
                    case "naver":
                    case "naverseries":
                        List<NovelPlatformMapping> naverMappings = novelMappingRepository.findAll().stream()
                                .filter(mapping -> mapping.getNaverSeriesId() != null && mapping.getNaverSeriesId() > 0)
                                .collect(Collectors.toList());
                        stats.put("integratedCount", naverMappings.size());
                        break;
                    case "kakao":
                        List<NovelPlatformMapping> kakaoMappings = novelMappingRepository.findAll().stream()
                                .filter(mapping -> mapping.getKakaoPageId() != null && mapping.getKakaoPageId() > 0)
                                .collect(Collectors.toList());
                        stats.put("integratedCount", kakaoMappings.size());
                        break;
                    case "ridibooks":
                        List<NovelPlatformMapping> ridibooksMappings = novelMappingRepository.findAll().stream()
                                .filter(mapping -> mapping.getRidibooksId() != null && mapping.getRidibooksId() > 0)
                                .collect(Collectors.toList());
                        stats.put("integratedCount", ridibooksMappings.size());
                        break;
                }
                break;
            case "movie":
                switch (platform.toLowerCase()) {
                    case "cgv":
                        List<MoviePlatformMapping> cgvMappings = movieMappingRepository.findAll().stream()
                                .filter(mapping -> mapping.getCgvId() != null && mapping.getCgvId() > 0)
                                .collect(Collectors.toList());
                        stats.put("integratedCount", cgvMappings.size());
                        break;
                    case "megabox":
                        List<MoviePlatformMapping> megaboxMappings = movieMappingRepository.findAll().stream()
                                .filter(mapping -> mapping.getMegaboxId() != null && mapping.getMegaboxId() > 0)
                                .collect(Collectors.toList());
                        stats.put("integratedCount", megaboxMappings.size());
                        break;
                }
                break;
            case "ott":
                switch (platform.toLowerCase()) {
                    case "netflix":
                        List<OTTPlatformMapping> netflixMappings = ottMappingRepository.findAll().stream()
                                .filter(mapping -> mapping.getNetflixId() != null && mapping.getNetflixId() > 0)
                                .collect(Collectors.toList());
                        stats.put("integratedCount", netflixMappings.size());
                        break;
                }
                break;
            case "webtoon":
                switch (platform.toLowerCase()) {
                    case "naver":
                        List<WebtoonPlatformMapping> naverWebtoonMappings = webtoonMappingRepository.findAll().stream()
                                .filter(mapping -> mapping.getNaverId() != null && mapping.getNaverId() > 0)
                                .collect(Collectors.toList());
                        stats.put("integratedCount", naverWebtoonMappings.size());
                        break;
                    case "kakao":
                        List<WebtoonPlatformMapping> kakaoWebtoonMappings = webtoonMappingRepository.findAll().stream()
                                .filter(mapping -> mapping.getKakaoId() != null && mapping.getKakaoId() > 0)
                                .collect(Collectors.toList());
                        stats.put("integratedCount", kakaoWebtoonMappings.size());
                        break;
                }
                break;
            case "game":
                switch (platform.toLowerCase()) {
                    case "steam":
                        List<GamePlatformMapping> steamMappings = gameMappingRepository.findAll().stream()
                                .filter(mapping -> mapping.getSteamId() != null && mapping.getSteamId() > 0)
                                .collect(Collectors.toList());
                        stats.put("integratedCount", steamMappings.size());
                        break;
                    case "epic":
                        List<GamePlatformMapping> epicMappings = gameMappingRepository.findAll().stream()
                                .filter(mapping -> mapping.getEpicId() != null && mapping.getEpicId() > 0)
                                .collect(Collectors.toList());
                        stats.put("integratedCount", epicMappings.size());
                        break;
                }
                break;
        }

        stats.put("contentType", contentType);
        stats.put("platform", platform);
        stats.put("timestamp", java.time.LocalDateTime.now());

        return stats;
    }

    /**
     * 통합 데이터 정리 (매핑이 없는 고아 데이터 제거)
     */
    @Transactional
    public int cleanupIntegrationData(String contentType) {
        int cleanedCount = 0;

        switch (contentType.toLowerCase()) {
            case "novel":
                // 매핑이 없는 Common 엔티티 찾아서 정리
                List<NovelCommonV2> allNovels = novelCommonV2Repository.findAll();
                List<NovelCommonV2> orphanedNovels = allNovels.stream()
                        .filter(novel -> novel.getPlatformMapping() == null)
                        .collect(Collectors.toList());
                novelCommonV2Repository.deleteAll(orphanedNovels);
                cleanedCount = orphanedNovels.size();
                break;
            case "movie":
                List<MovieCommonV2> allMovies = movieCommonV2Repository.findAll();
                List<MovieCommonV2> orphanedMovies = allMovies.stream()
                        .filter(movie -> movie.getPlatformMapping() == null)
                        .collect(Collectors.toList());
                movieCommonV2Repository.deleteAll(orphanedMovies);
                cleanedCount = orphanedMovies.size();
                break;
            case "ott":
                List<OTTCommonV2> allOTTs = ottCommonV2Repository.findAll();
                List<OTTCommonV2> orphanedOTTs = allOTTs.stream()
                        .filter(ott -> ott.getPlatformMapping() == null)
                        .collect(Collectors.toList());
                ottCommonV2Repository.deleteAll(orphanedOTTs);
                cleanedCount = orphanedOTTs.size();
                break;
            case "webtoon":
                List<WebtoonCommonV2> allWebtoons = webtoonCommonV2Repository.findAll();
                List<WebtoonCommonV2> orphanedWebtoons = allWebtoons.stream()
                        .filter(webtoon -> webtoon.getPlatformMapping() == null)
                        .collect(Collectors.toList());
                webtoonCommonV2Repository.deleteAll(orphanedWebtoons);
                cleanedCount = orphanedWebtoons.size();
                break;
            case "game":
                List<GameCommonV2> allGames = gameCommonV2Repository.findAll();
                List<GameCommonV2> orphanedGames = allGames.stream()
                        .filter(game -> game.getPlatformMapping() == null)
                        .collect(Collectors.toList());
                gameCommonV2Repository.deleteAll(orphanedGames);
                cleanedCount = orphanedGames.size();
                break;
        }

        log.info("통합 데이터 정리 완료: {} - {}개 정리됨", contentType, cleanedCount);
        return cleanedCount;
    }
}