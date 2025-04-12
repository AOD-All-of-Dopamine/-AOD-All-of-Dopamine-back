package com.example.AOD.OTT.Netflix.service;


import java.util.List;

import com.example.AOD.OTT.Netflix.crawler.NetflixContentCrawler;
import com.example.AOD.OTT.Netflix.domain.NetflixContent;
import com.example.AOD.OTT.Netflix.dto.NetflixContentDTO;
import com.example.AOD.OTT.Netflix.repository.NetflixContentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NetflixCrawlerService {

    private final NetflixContentRepository contentRepository;

    @Async
    public void crawlAndSave(String email, String password) {
        // 크롤러 생성
        NetflixContentCrawler crawler = new NetflixContentCrawler(email, password);
        try {
            crawler.setupDriver();
            if (!crawler.login()) {
                // 로그인 실패하면 종료
                return;
            }

            // 크롤링 → DTO 리스트
            List<NetflixContentDTO> dtoList = crawler.crawl();
            // DTO → 도메인 변환 후 저장
            for (NetflixContentDTO dto : dtoList) {
                NetflixContent entity = convertToEntity(dto);
                contentRepository.save(entity);
            }
        } finally {
            crawler.cleanup();
        }
    }

    private NetflixContent convertToEntity(NetflixContentDTO dto) {
        NetflixContent entity = new NetflixContent();
        entity.setContentId(dto.getContentId());
        entity.setTitle(dto.getTitle());
        entity.setType(dto.getType());
        entity.setUrl(dto.getUrl());
        entity.setDetailUrl(dto.getDetailUrl());
        entity.setThumbnail(dto.getThumbnail());
        entity.setDescription(dto.getDescription());
        entity.setCreator(dto.getCreator());
        entity.setMaturityRating(dto.getMaturityRating());
        entity.setReleaseYear(dto.getReleaseYear());
        entity.setCrawledAt(dto.getCrawledAt());
        entity.setActors(dto.getActors());
        entity.setGenres(dto.getGenres());
        entity.setFeatures(dto.getFeatures());
        return entity;
    }
}
