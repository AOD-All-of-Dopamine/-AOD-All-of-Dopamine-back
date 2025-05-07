package com.example.AOD.OTT.Netflix.service;


import java.util.List;
import java.util.stream.Collectors;

import com.example.AOD.OTT.Netflix.crawler.NetflixContentCrawler;
import com.example.AOD.OTT.Netflix.domain.NetflixContentActor;
import com.example.AOD.OTT.Netflix.domain.NetflixContentFeature;
import com.example.AOD.OTT.Netflix.domain.NetflixContentGenre;
import com.example.AOD.OTT.Netflix.domain.NetflixContent;
import com.example.AOD.OTT.Netflix.dto.NetflixContentDTO;
import com.example.AOD.OTT.Netflix.repository.NetflixContentActorRepository;
import com.example.AOD.OTT.Netflix.repository.NetflixContentFeatureRepository;
import com.example.AOD.OTT.Netflix.repository.NetflixContentGenreRepository;
import com.example.AOD.OTT.Netflix.repository.NetflixContentRepository;
import com.example.AOD.util.ChromeDriverProvider;
import lombok.RequiredArgsConstructor;
import org.openqa.selenium.WebDriver;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NetflixCrawlerService {

    private final NetflixContentRepository contentRepository;


    private final NetflixContentActorRepository netflixContentActorRepository;
    private final NetflixContentGenreRepository netflixContentGenreRepository;
    private final NetflixContentFeatureRepository netflixContentFeatureRepository;


    /**
     * 사용자 인증 정보(email, password)를 받아
     * 1) 크롤러(WebDriver) 초기화
     * 2) 로그인
     * 3) 크롤
     * 4) 반환된 DTO를 저장
     */
    // ChromeDriverProvider를 주입받는다고 가정
    private final ChromeDriverProvider chromeDriverProvider;

    @Async
    public void runCrawler(String email, String password) {
        // 1) 크롬드라이버 생성
        WebDriver driver = chromeDriverProvider.getDriver();

        // 2) 크롤러에 주입
        NetflixContentCrawler crawler = new NetflixContentCrawler(email, password, driver);

        try {
            // 3) 로그인
            if (!crawler.login()) {
                return; // 로그인 실패 시 종료
            }

            // 4) 크롤
            List<NetflixContentDTO> dtoList = crawler.crawl();

            // 5) DTO -> 엔티티 변환 & 저장
            for (NetflixContentDTO dto : dtoList) {
                saveNetflixContent(dto);
            }
        } finally {
            // 6) 드라이버 종료
            crawler.cleanup();
        }
    }

    public NetflixContent saveNetflixContent(NetflixContentDTO dto) {
        // 1) 기본 필드는 간단히 매핑
        NetflixContent content = new NetflixContent();
        content.setContentId(dto.getContentId());
        content.setTitle(dto.getTitle());
        content.setType(dto.getType());
        content.setUrl(dto.getUrl());
        content.setDetailUrl(dto.getDetailUrl());
        content.setThumbnail(dto.getThumbnail());
        content.setDescription(dto.getDescription());
        content.setCreator(dto.getCreator());
        content.setMaturityRating(dto.getMaturityRating());
        content.setReleaseYear(dto.getReleaseYear());
        content.setCrawledAt(dto.getCrawledAt());

        // 2) 배우(Actor) 리스트 처리
        // DTO에는 배우 이름 리스트가 들어있고, DB에는 Actor 엔티티가 존재
        // 존재하면 재사용, 없으면 새로 생성
        List<NetflixContentActor> netflixContentActorEntities = dto.getActors().stream()
                .map(actorName -> {
                    // findByName 등으로 존재하는 배우인지 확인
                    return netflixContentActorRepository.findByName(actorName)
                            .orElseGet(() -> {
                                // 없으면 새로 생성
                                NetflixContentActor newNetflixContentActor = new NetflixContentActor();
                                newNetflixContentActor.setName(actorName);
                                return netflixContentActorRepository.save(newNetflixContentActor);
                            });
                })
                .collect(Collectors.toList());

        // 3) 장르(Genre) 리스트 처리
        List<NetflixContentGenre> netflixContentGenreEntities = dto.getGenres().stream()
                .map(genreName -> {
                    return netflixContentGenreRepository.findByName(genreName)
                            .orElseGet(() -> {
                                NetflixContentGenre newNetflixContentGenre = new NetflixContentGenre();
                                newNetflixContentGenre.setName(genreName);
                                return netflixContentGenreRepository.save(newNetflixContentGenre);
                            });
                })
                .collect(Collectors.toList());

        // 4) 특징(Feature) 리스트 처리
        List<NetflixContentFeature> netflixContentFeatureEntities = dto.getFeatures().stream()
                .map(featureName -> {
                    return netflixContentFeatureRepository.findByName(featureName)
                            .orElseGet(() -> {
                                NetflixContentFeature newNetflixContentFeature = new NetflixContentFeature();
                                newNetflixContentFeature.setName(featureName);
                                return netflixContentFeatureRepository.save(newNetflixContentFeature);
                            });
                })
                .collect(Collectors.toList());

        // 5) 연관관계 설정
        content.setNetflixContentActors(netflixContentActorEntities);
        content.setNetflixContentGenres(netflixContentGenreEntities);
        content.setNetflixContentFeatures(netflixContentFeatureEntities);

        // 6) 최종 저장
        return contentRepository.save(content);
    }
}
