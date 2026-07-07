package com.example.crawler.service;

import com.example.shared.entity.Content;
import com.example.shared.entity.Domain;
import com.example.shared.repository.ContentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ContentUpsertService {

    private final ContentRepository contentRepo;

    @Transactional
    public Content findOrCreateContent(Domain domain, Map<String, Object> master) {
        String masterTitle = (String) master.get("master_title");
        LocalDate releaseDate = parseReleaseDate(master.get("release_date"));

        Content content = contentRepo
                .findFirstByDomainAndMasterTitleAndReleaseDate(domain, masterTitle, releaseDate)
                .orElseGet(() -> {
                    Content newContent = new Content();
                    newContent.setDomain(domain);
                    newContent.setMasterTitle(masterTitle);
                    return newContent;
                });

        // Update fields only if they are currently null
        if (content.getOriginalTitle() == null) {
            content.setOriginalTitle((String) master.get("original_title"));
        }
        if (content.getReleaseDate() == null) {
            content.setReleaseDate(releaseDate);
        }
        if (content.getPosterImageUrl() == null) {
            content.setPosterImageUrl((String) master.get("poster_image_url"));
        }
        if (content.getSynopsis() == null) {
            content.setSynopsis((String) master.get("synopsis"));
        }

        return contentRepo.save(content);
    }

    /**
     * Content 엔티티를 구성만 하고 저장하지 않음 (중복 체크용)
     */
    public Content buildContent(Domain domain, Map<String, Object> master) {
        String masterTitle = (String) master.get("master_title");
        LocalDate releaseDate = parseReleaseDate(master.get("release_date"));

        Content content = new Content();
        content.setDomain(domain);
        content.setMasterTitle(masterTitle);
        content.setOriginalTitle((String) master.get("original_title"));
        content.setReleaseDate(releaseDate);
        content.setPosterImageUrl((String) master.get("poster_image_url"));
        content.setSynopsis((String) master.get("synopsis"));

        return content;
    }

    /**
     * Content 엔티티 저장
     */
    @Transactional
    public Content saveContent(Content content) {
        return contentRepo.save(content);
    }

    private LocalDate parseReleaseDate(Object value) {
        return com.example.crawler.util.FlexibleDateParser.parse(value); // RF-6: 단일 파서로 통합
    }
}


