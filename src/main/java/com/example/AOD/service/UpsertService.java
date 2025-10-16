package com.example.AOD.service;


import com.example.AOD.domain.Content;
import com.example.AOD.domain.entity.Domain;
import com.example.AOD.domain.entity.PlatformData;
import com.example.AOD.repo.ContentRepository;
import com.example.AOD.repo.PlatformDataRepository;
import com.example.AOD.rules.MappingRule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@RequiredArgsConstructor // 생성자 주입을 위해 변경
@Service
public class UpsertService {
    private final ContentRepository contentRepo;
    private final PlatformDataRepository platformRepo;
    private final DomainCoreUpsertService domainCoreUpsert;

    @Transactional
    public Long upsert(Domain domain,
                       Map<String,Object> master,
                       Map<String,Object> platform,
                       Map<String,Object> domainDoc,
                       String platformSpecificId,
                       String url,
                       MappingRule rule) { // [ ✨ 수정 ] MappingRule 파라미터 추가

        String platformName = (String) platform.get("platformName");

        // [개선] TMDB 이미지 URL 처리 로직
        // TMDB API는 이미지 파일 경로만 제공하므로, 전체 URL로 조합해주는 과정이 필요합니다.
        if (domain == Domain.AV && "TMDB".equals(platformName)) {
            String posterPath = (String) master.get("poster_image_url");
            if (posterPath != null && !posterPath.isBlank() && !posterPath.startsWith("http")) {
                master.put("poster_image_url", "https://image.tmdb.org/t/p/w500" + posterPath);
            }
        }

        String masterTitle = (String) master.get("master_title");
        if (masterTitle == null || masterTitle.isBlank()) {
            log.warn("Master title이 비어있어 해당 항목을 건너뜁니다. PlatformSpecificId: {}", platformSpecificId);
            return null; // 제목이 없으면 처리하지 않음
        }

        Integer initialYear = (master.get("release_year") instanceof Number) ? ((Number) master.get("release_year")).intValue() : null;
        if ((domain == Domain.AV || domain == Domain.GAME) && domainDoc.get("release_date") instanceof String dateStr) {
            initialYear = parseYearFromDateString(dateStr);
        }
        final Integer finalReleaseYear = initialYear;

        // 동일 콘텐츠 판단: 도메인, 정규화된 제목, 출시 연도를 기준으로 찾습니다.
        Content content = contentRepo
                .findFirstByDomainAndMasterTitleAndReleaseYear(domain, masterTitle, finalReleaseYear)
                .orElseGet(() -> {
                    Content c = new Content();
                    c.setDomain(domain);
                    c.setMasterTitle(masterTitle);
                    c.setOriginalTitle((String) master.get("original_title"));
                    c.setReleaseYear(finalReleaseYear);
                    c.setPosterImageUrl((String) master.get("poster_image_url"));
                    c.setSynopsis((String) master.get("synopsis"));
                    return contentRepo.save(c);
                });

        // 기존 콘텐츠 정보 업데이트 (null인 필드만)
        if (content.getReleaseYear() == null && finalReleaseYear != null) {
            content.setReleaseYear(finalReleaseYear);
        }
        if (content.getOriginalTitle() == null)
            content.setOriginalTitle((String) master.getOrDefault("original_title", content.getOriginalTitle()));
        if (content.getPosterImageUrl() == null)
            content.setPosterImageUrl((String) master.getOrDefault("poster_image_url", content.getPosterImageUrl()));
        if (content.getSynopsis() == null)
            content.setSynopsis((String) master.getOrDefault("synopsis", content.getSynopsis()));


        // [수정된 부분]
        Map<String, Object> attributes = (Map<String, Object>) platform.get("attributes");
        if ("TMDB".equals(platformName) && attributes != null && attributes.containsKey("watch_providers")) {
            Map<String, Object> watchProviders = (Map<String, Object>) attributes.get("watch_providers");
            if (watchProviders != null && watchProviders.containsKey("KR")) {
                // 기존 attributes 맵에서 "watch_providers" 키의 값만 수정합니다.
                attributes.put("watch_providers", Map.of("KR", watchProviders.get("KR")));
            } else {
                // KR 정보가 없으면 watch_providers 정보를 비웁니다.
                attributes.put("watch_providers", Map.of());
            }
        }

        savePlatformData(content, platformName, platformSpecificId, url, attributes);

        // 도메인별 상세 정보 저장
        // [ ✨ 수정 ] rule 객체를 넘겨줌
        domainCoreUpsert.upsert(domain, content, domainDoc, rule);

        return content.getContentId();
    }

    private void savePlatformData(Content content, String platformName, String platformSpecificId, String url, Map<String, Object> attributes) {
        Optional<PlatformData> existing = platformRepo.findByPlatformNameAndPlatformSpecificId(platformName, platformSpecificId);

        PlatformData pd = existing.orElseGet(PlatformData::new);

        pd.setContent(content);
        pd.setPlatformName(platformName);
        pd.setPlatformSpecificId(platformSpecificId);
        pd.setUrl(url);
        pd.setAttributes(attributes != null ? attributes : Map.of());
        pd.setLastSeenAt(Instant.now());

        platformRepo.save(pd);
    }

    private Integer parseYearFromDateString(String dateString) {
        if (dateString == null || dateString.isBlank()) {
            return null;
        }
        // "YYYY-MM-DD" 형식에서 연도만 추출
        Matcher matcher = Pattern.compile("^(\\d{4})").matcher(dateString.trim());
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}