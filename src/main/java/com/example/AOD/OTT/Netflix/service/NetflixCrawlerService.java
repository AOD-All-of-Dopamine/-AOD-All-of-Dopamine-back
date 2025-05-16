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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
@RequiredArgsConstructor
public class NetflixCrawlerService {
    private static final Logger logger = LoggerFactory.getLogger(NetflixCrawlerService.class);

    private final NetflixContentRepository contentRepository;
    private final NetflixContentActorRepository netflixContentActorRepository;
    private final NetflixContentGenreRepository netflixContentGenreRepository;
    private final NetflixContentFeatureRepository netflixContentFeatureRepository;
    private final ChromeDriverProvider chromeDriverProvider;

    /**
     * 일반 콘텐츠 크롤링 수행
     * 1) 크롤러(WebDriver) 초기화
     * 2) 로그인
     * 3) 크롤
     * 4) 반환된 DTO를 저장
     */
    @Async
    public void runCrawler(String email, String password) {
        logger.info("일반 넷플릭스 콘텐츠 크롤링 시작");
        WebDriver driver = chromeDriverProvider.getDriver();
        NetflixContentCrawler crawler = new NetflixContentCrawler(email, password, driver);

        try {
            // 크롤링 수행 - 내부적으로 쿠키 기반 로그인을 사용
            List<NetflixContentDTO> dtoList = crawler.crawl();
            logger.info("크롤링 완료: {} 개의 콘텐츠 발견", dtoList.size());

            // 저장
            int savedCount = 0;
            for (NetflixContentDTO dto : dtoList) {
                try {
                    saveNetflixContent(dto);
                    savedCount++;
                } catch (Exception e) {
                    logger.error("콘텐츠 저장 중 오류 발생: {}", dto.getTitle(), e);
                }
            }
            logger.info("저장 완료: {} 개의 콘텐츠 저장됨", savedCount);
        } catch (Exception e) {
            logger.error("크롤링 중 오류 발생", e);
        } finally {
            crawler.cleanup();
            logger.info("일반 넷플릭스 콘텐츠 크롤링 종료");
        }
    }

    /**
     * 최신 콘텐츠 크롤링 수행
     */
    @Async
    public void runLatestContentCrawler(String email, String password) {
        logger.info("최신 넷플릭스 콘텐츠 크롤링 시작");
        WebDriver driver = chromeDriverProvider.getDriver();
        NetflixContentCrawler crawler = new NetflixContentCrawler(email, password, driver);

        try {
            // 로그인
            if (!crawler.login()) {
                logger.error("넷플릭스 로그인 실패");
                return;
            }

            // 최신 콘텐츠 크롤링 수행
            List<NetflixContentDTO> dtoList = crawler.crawlLatestContent();
            logger.info("최신 콘텐츠 크롤링 완료: {} 개의 콘텐츠 발견", dtoList.size());


            // 저장
            int savedCount = 0;
            for (NetflixContentDTO dto : dtoList) {
                try {
                    saveNetflixContent(dto);
                    savedCount++;
                } catch (Exception e) {
                    logger.error("최신 콘텐츠 저장 중 오류 발생: {}", dto.getTitle(), e);
                }
            }
            logger.info("저장 완료: {} 개의 최신 콘텐츠 저장됨", savedCount);
        } catch (Exception e) {
            logger.error("최신 콘텐츠 크롤링 중 오류 발생", e);
        } finally {
            crawler.cleanup();
            logger.info("최신 넷플릭스 콘텐츠 크롤링 종료");
        }
    }

    /**
     * 이번주 공개된 최신 콘텐츠만 크롤링 수행
     */
    @Async
    public void runThisWeekContentCrawler(String email, String password) {
        logger.info("이번주 공개 넷플릭스 콘텐츠 크롤링 시작");
        WebDriver driver = chromeDriverProvider.getDriver();
        NetflixContentCrawler crawler = new NetflixContentCrawler(email, password, driver);

        try {
            // 로그인
            if (!crawler.login()) {
                logger.error("넷플릭스 로그인 실패");
                return;
            }

            // 이번주 공개 콘텐츠 크롤링 수행
            List<NetflixContentDTO> dtoList = crawler.crawlThisWeekContent();
            logger.info("이번주 공개 콘텐츠 크롤링 완료: {} 개의 콘텐츠 발견", dtoList.size());



            // 저장
            int savedCount = 0;
            for (NetflixContentDTO dto : dtoList) {
                try {
                    saveNetflixContent(dto);
                    savedCount++;
                } catch (Exception e) {
                    logger.error("이번주 공개 콘텐츠 저장 중 오류 발생: {}", dto.getTitle(), e);
                }
            }
            logger.info("저장 완료: {} 개의 이번주 공개 콘텐츠 저장됨", savedCount);
        } catch (Exception e) {
            logger.error("이번주 공개 콘텐츠 크롤링 중 오류 발생", e);
        } finally {
            crawler.cleanup();
            logger.info("이번주 공개 넷플릭스 콘텐츠 크롤링 종료");
        }
    }

    /**
     * 모든 콘텐츠 크롤링 (일반 + 최신)
     */
    @Async
    public void runAllContentCrawler(String email, String password) {
        logger.info("모든 넷플릭스 콘텐츠 크롤링 시작 (일반 + 최신)");
        runCrawler(email, password);
        runLatestContentCrawler(email, password);
        logger.info("모든 넷플릭스 콘텐츠 크롤링 완료");
    }

    /**
     * DTO를 엔티티로 변환하여 저장
     */
    public NetflixContent saveNetflixContent(NetflixContentDTO dto) {
        // 기존 콘텐츠가 있는지 확인 (contentId로 검색)
        NetflixContent content = contentRepository.findByContentId((dto.getContentId()))
                .orElse(new NetflixContent());

        // 기본 필드 매핑
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

        // 배우(Actor) 리스트 처리
        List<NetflixContentActor> netflixContentActorEntities = dto.getActors().stream()
                .map(actorName -> {
                    return netflixContentActorRepository.findByName(actorName)
                            .orElseGet(() -> {
                                NetflixContentActor newNetflixContentActor = new NetflixContentActor();
                                newNetflixContentActor.setName(actorName);
                                return netflixContentActorRepository.save(newNetflixContentActor);
                            });
                })
                .collect(Collectors.toList());

        // 장르(Genre) 리스트 처리
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

        // 특징(Feature) 리스트 처리
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

        // 연관관계 설정
        content.setNetflixContentActors(netflixContentActorEntities);
        content.setNetflixContentGenres(netflixContentGenreEntities);
        content.setNetflixContentFeatures(netflixContentFeatureEntities);

        // 최종 저장
        return contentRepository.save(content);
    }
}