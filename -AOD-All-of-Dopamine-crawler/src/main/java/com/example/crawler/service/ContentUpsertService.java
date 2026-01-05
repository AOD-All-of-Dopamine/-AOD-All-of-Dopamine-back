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
        if (value == null) return null;
        if (value instanceof LocalDate) return (LocalDate) value;
        if (value instanceof String dateStr) {
            // 다양한 날짜 형식 시도
            String[] patterns = {
                "uuuu년 M월 d일",      // Steam 한국어: 1998년 11월 19일
                "yyyy-MM-dd",          // ISO 형식
                "yyyy.MM.dd",          // 점 구분
                "yyyy/MM/dd",          // 슬래시 구분
                "MMM d, yyyy"          // 영어: Nov 19, 1998
            };
            
            for (String pattern : patterns) {
                try {
                    java.time.format.DateTimeFormatter formatter = 
                        java.time.format.DateTimeFormatter.ofPattern(pattern, java.util.Locale.KOREAN);
                    return LocalDate.parse(dateStr, formatter);
                } catch (Exception ignored) {
                }
            }
            
            // 연도만 있는 경우 (숫자 문자열)
            try {
                int year = Integer.parseInt(dateStr.trim());
                return LocalDate.of(year, 1, 1);
            } catch (Exception ignored) {
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


