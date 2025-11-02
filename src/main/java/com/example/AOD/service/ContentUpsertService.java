package com.example.AOD.service;

import com.example.AOD.domain.Content;
import com.example.AOD.domain.entity.Domain;
import com.example.AOD.repo.ContentRepository;
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
        if (value == null) return null;
        if (value instanceof LocalDate) return (LocalDate) value;
        if (value instanceof String) {
            try {
                return LocalDate.parse((String) value);
            } catch (Exception e) {
                return null;
            }
        }
        if (value instanceof Number) {
            // year만 있는 경우 1월 1일로 변환
            int year = ((Number) value).intValue();
            return LocalDate.of(year, 1, 1);
        }
        return null;
    }
}
