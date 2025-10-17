package com.example.AOD.service;

import com.example.AOD.domain.Content;
import com.example.AOD.domain.entity.Domain;
import com.example.AOD.repo.ContentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class ContentUpsertService {

    private final ContentRepository contentRepo;

    @Transactional
    public Content findOrCreateContent(Domain domain, Map<String, Object> master) {
        String masterTitle = (String) master.get("master_title");
        Integer releaseYear = master.get("release_year") instanceof Number ? ((Number) master.get("release_year")).intValue() : null;

        Content content = contentRepo
                .findFirstByDomainAndMasterTitleAndReleaseYear(domain, masterTitle, releaseYear)
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
        if (content.getReleaseYear() == null) {
            content.setReleaseYear(releaseYear);
        }
        if (content.getPosterImageUrl() == null) {
            content.setPosterImageUrl((String) master.get("poster_image_url"));
        }
        if (content.getSynopsis() == null) {
            content.setSynopsis((String) master.get("synopsis"));
        }

        return contentRepo.save(content);
    }
}
