package com.example.AOD.OTT.Netflix.service;


import java.util.List;
import java.util.stream.Collectors;

import com.example.AOD.OTT.Netflix.crawler.NetflixContentCrawler;
import com.example.AOD.OTT.Netflix.domain.Actor;
import com.example.AOD.OTT.Netflix.domain.Feature;
import com.example.AOD.OTT.Netflix.domain.Genre;
import com.example.AOD.OTT.Netflix.domain.NetflixContent;
import com.example.AOD.OTT.Netflix.dto.NetflixContentDTO;
import com.example.AOD.OTT.Netflix.repository.ActorRepository;
import com.example.AOD.OTT.Netflix.repository.FeatureRepository;
import com.example.AOD.OTT.Netflix.repository.GenreRepository;
import com.example.AOD.OTT.Netflix.repository.NetflixContentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NetflixCrawlerService {

    private final NetflixContentRepository contentRepository;

    // 배우 / 장르 / 특징을 각각 관리하는 레포지토리
    private final ActorRepository actorRepository;
    private final GenreRepository genreRepository;
    private final FeatureRepository featureRepository;

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
        List<Actor> actorEntities = dto.getActors().stream()
                .map(actorName -> {
                    // findByName 등으로 존재하는 배우인지 확인
                    return actorRepository.findByName(actorName)
                            .orElseGet(() -> {
                                // 없으면 새로 생성
                                Actor newActor = new Actor();
                                newActor.setName(actorName);
                                return actorRepository.save(newActor);
                            });
                })
                .collect(Collectors.toList());

        // 3) 장르(Genre) 리스트 처리
        List<Genre> genreEntities = dto.getGenres().stream()
                .map(genreName -> {
                    return genreRepository.findByName(genreName)
                            .orElseGet(() -> {
                                Genre newGenre = new Genre();
                                newGenre.setName(genreName);
                                return genreRepository.save(newGenre);
                            });
                })
                .collect(Collectors.toList());

        // 4) 특징(Feature) 리스트 처리
        List<Feature> featureEntities = dto.getFeatures().stream()
                .map(featureName -> {
                    return featureRepository.findByName(featureName)
                            .orElseGet(() -> {
                                Feature newFeature = new Feature();
                                newFeature.setName(featureName);
                                return featureRepository.save(newFeature);
                            });
                })
                .collect(Collectors.toList());

        // 5) 연관관계 설정
        content.setActors(actorEntities);
        content.setGenres(genreEntities);
        content.setFeatures(featureEntities);

        // 6) 최종 저장
        return contentRepository.save(content);
    }
}
