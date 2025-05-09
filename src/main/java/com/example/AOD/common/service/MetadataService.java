package com.example.AOD.common.service;

import com.example.AOD.Novel.NaverSeriesNovel.repository.NaverSeriesNovelRepository;
import com.example.AOD.common.dto.FieldInfoDTO;
import com.example.AOD.common.dto.PlatformInfoDTO;
import com.example.AOD.game.StreamAPI.repository.GameRepository;
import com.example.AOD.movie.CGV.repository.MovieRepository;
import com.example.AOD.OTT.Netflix.repository.NetflixContentRepository;
import com.example.AOD.Webtoon.NaverWebtoon.repository.WebtoonRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class MetadataService {

    private final NaverSeriesNovelRepository naverSeriesNovelRepository;
    private final MovieRepository movieRepository;
    private final NetflixContentRepository netflixContentRepository;
    private final WebtoonRepository webtoonRepository;
    private final GameRepository steamGameRepository;

    @Autowired
    public MetadataService(
            NaverSeriesNovelRepository naverSeriesNovelRepository,
            MovieRepository movieRepository,
            NetflixContentRepository netflixContentRepository,
            WebtoonRepository webtoonRepository,
            GameRepository steamGameRepository) {
        this.naverSeriesNovelRepository = naverSeriesNovelRepository;
        this.movieRepository = movieRepository;
        this.netflixContentRepository = netflixContentRepository;
        this.webtoonRepository = webtoonRepository;
        this.steamGameRepository = steamGameRepository;
    }

    // 콘텐츠 유형별 플랫폼 정보 제공
    public List<PlatformInfoDTO> getPlatformsForContentType(String contentType) {
        List<PlatformInfoDTO> platforms = new ArrayList<>();

        switch(contentType) {
            case "novel":
                platforms.add(new PlatformInfoDTO("naver", "네이버 시리즈", "com.example.AOD.Novel.NaverSeriesNovel.domain.NaverSeriesNovel"));
                // 다른 소설 플랫폼 추가 가능
                break;
            case "movie":
                platforms.add(new PlatformInfoDTO("cgv", "CGV", "com.example.AOD.movie.CGV.domain.Movie"));
                // 다른 영화 플랫폼 추가 가능
                break;
            case "ott":
                platforms.add(new PlatformInfoDTO("netflix", "Netflix", "com.example.AOD.OTT.Netflix.domain.NetflixContent"));
                // 다른 OTT 플랫폼 추가 가능
                break;
            case "webtoon":
                platforms.add(new PlatformInfoDTO("naver", "네이버 웹툰", "com.example.AOD.Webtoon.NaverWebtoon.domain.Webtoon"));
                // 다른 웹툰 플랫폼 추가 가능
                break;
            case "game":
                platforms.add(new PlatformInfoDTO("steam", "Steam", "com.example.AOD.game.StreamAPI.domain.SteamGame"));
                // 다른 게임 플랫폼 추가 가능
                break;
        }

        return platforms;
    }

    // Common 엔티티의 필드 정보 제공
    public List<FieldInfoDTO> getCommonFieldsForContentType(String contentType) {
        Class<?> clazz = null;

        try {
            switch(contentType) {
                case "novel":
                    clazz = Class.forName("com.example.AOD.common.commonDomain.NovelCommon");
                    break;
                case "movie":
                    clazz = Class.forName("com.example.AOD.common.commonDomain.MovieCommon");
                    break;
                case "ott":
                    clazz = Class.forName("com.example.AOD.common.commonDomain.OTTCommon");
                    break;
                case "webtoon":
                    clazz = Class.forName("com.example.AOD.common.commonDomain.WebtoonCommon");
                    break;
                case "game":
                    clazz = Class.forName("com.example.AOD.common.commonDomain.GameCommon");
                    break;
            }

            if (clazz != null) {
                return Arrays.stream(clazz.getDeclaredFields())
                        .map(field -> new FieldInfoDTO(field.getName(), field.getType().getSimpleName()))
                        .collect(Collectors.toList());
            }
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }

        return new ArrayList<>();
    }

    // 플랫폼 엔티티의 필드 정보 제공
    public List<FieldInfoDTO> getPlatformFields(String contentType, String platformId) {
        String className = "";

        // 콘텐츠 유형과 플랫폼에 따라 클래스 이름 결정
        switch(contentType) {
            case "novel":
                if ("naver".equals(platformId)) {
                    className = "com.example.AOD.Novel.NaverSeriesNovel.domain.NaverSeriesNovel";
                }
                break;
            case "movie":
                if ("cgv".equals(platformId)) {
                    className = "com.example.AOD.movie.CGV.domain.Movie";
                }
                break;
            case "ott":
                if ("netflix".equals(platformId)) {
                    className = "com.example.AOD.OTT.Netflix.domain.NetflixContent";
                }
                break;
            case "webtoon":
                if ("naver".equals(platformId)) {
                    className = "com.example.AOD.Webtoon.NaverWebtoon.domain.Webtoon";
                }
                break;
            case "game":
                if ("steam".equals(platformId)) {
                    className = "com.example.AOD.game.StreamAPI.domain.SteamGame";
                }
                break;
        }

        if (!className.isEmpty()) {
            try {
                Class<?> clazz = Class.forName(className);
                List<FieldInfoDTO> fields = new ArrayList<>();

                // 클래스의 모든 필드 반복
                for (Field field : getAllFields(clazz)) {
                    fields.add(new FieldInfoDTO(field.getName(), field.getType().getSimpleName()));
                }

                return fields;
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        }

        return new ArrayList<>();
    }

    // 플랫폼별 콘텐츠 목록 로드
    public List<?> getContentsForPlatform(String contentType, String platformId) {
        switch(contentType) {
            case "novel":
                if ("naver".equals(platformId)) {
                    return naverSeriesNovelRepository.findAll();
                }
                break;
            case "movie":
                if ("cgv".equals(platformId)) {
                    return movieRepository.findAll();
                }
                break;
            case "ott":
                if ("netflix".equals(platformId)) {
                    return netflixContentRepository.findAll();
                }
                break;
            case "webtoon":
                if ("naver".equals(platformId)) {
                    return webtoonRepository.findAll();
                }
                break;
            case "game":
                if ("steam".equals(platformId)) {
                    return steamGameRepository.findAll();
                }
                break;
        }

        return new ArrayList<>();
    }

    // 클래스의 모든 필드(상속 포함) 가져오기
    private List<Field> getAllFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>(Arrays.asList(clazz.getDeclaredFields()));

        if (clazz.getSuperclass() != null) {
            fields.addAll(getAllFields(clazz.getSuperclass()));
        }

        return fields;
    }
}