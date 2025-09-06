package com.example.AOD.service;


import com.example.AOD.domain.Content;
import com.example.AOD.domain.entity.Domain;
import com.example.AOD.domain.entity.GameContent;
import com.example.AOD.domain.entity.WebnovelContent;
import com.example.AOD.domain.entity.WebtoonContent;
import com.example.AOD.repo.GameContentRepository;
import com.example.AOD.repo.WebnovelContentRepository;
import com.example.AOD.repo.WebtoonContentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Service
public class DomainCoreUpsertService {

    private final GameContentRepository gameRepo;
    private final WebtoonContentRepository webtoonRepo;
    private final WebnovelContentRepository webnovelRepo;

    public DomainCoreUpsertService(GameContentRepository gameRepo,
                                   WebtoonContentRepository webtoonRepo,
                                   WebnovelContentRepository webnovelRepo) {
        this.gameRepo = gameRepo;
        this.webtoonRepo = webtoonRepo;
        this.webnovelRepo = webnovelRepo;
    }

    private LocalDate parseDate(Object s) {
        if (s == null) return null;
        String v = s.toString().trim();
        // 가장 흔한 포맷만 몇 개 지원(MVP). 필요 시 확장
        String[] patterns = {"yyyy-MM-dd","yyyy.MM.dd","yyyy/MM/dd","MMM d, yyyy"};
        for (String p : patterns) {
            try { return LocalDate.parse(v, DateTimeFormatter.ofPattern(p)); }
            catch (Exception ignored) {}
        }
        // 년도만 들어올 때
        try { return LocalDate.of(Integer.parseInt(v), 1, 1); } catch (Exception ignored) {}
        return null;
    }

    @Transactional
    public void upsert(Domain domain, Content content, Map<String,Object> domainDoc) {
        if (domainDoc == null || domainDoc.isEmpty()) return;

        switch (domain) {
            case GAME -> {
                GameContent g = gameRepo.findById(content.getContentId()).orElseGet(() -> {
                    GameContent x = new GameContent();
                    x.setContent(content);
                    return x;
                });
                if (domainDoc.get("developer") != null) g.setDeveloper(domainDoc.get("developer").toString());
                if (domainDoc.get("publisher") != null) g.setPublisher(domainDoc.get("publisher").toString());
                if (domainDoc.get("release_date") != null) g.setReleaseDate(parseDate(domainDoc.get("release_date")));
                if (domainDoc.get("platforms") instanceof Map<?,?> m)
                    g.setPlatforms((Map<String,Object>) m);
                if (domainDoc.get("genres") instanceof Map<?,?> m)
                    g.setGenres((Map<String,Object>) m);
                gameRepo.save(g);
            }
            case WEBTOON -> {
                WebtoonContent w = webtoonRepo.findById(content.getContentId()).orElseGet(() -> {
                    WebtoonContent x = new WebtoonContent();
                    x.setContent(content);
                    return x;
                });
                if (domainDoc.get("author") != null) w.setAuthor(domainDoc.get("author").toString());
                if (domainDoc.get("illustrator") != null) w.setIllustrator(domainDoc.get("illustrator").toString());
                if (domainDoc.get("status") != null) w.setStatus(domainDoc.get("status").toString());
                if (domainDoc.get("started_at") != null) w.setStartedAt(parseDate(domainDoc.get("started_at")));
                if (domainDoc.get("genres") instanceof Map<?,?> m)
                    w.setGenres((Map<String,Object>) m);
                webtoonRepo.save(w);
            }
            case WEBNOVEL -> {
                WebnovelContent wn = webnovelRepo.findById(content.getContentId()).orElseGet(() -> {
                    WebnovelContent x = new WebnovelContent();
                    x.setContent(content);
                    return x;
                });
                if (domainDoc.get("author") != null) wn.setAuthor(domainDoc.get("author").toString());
                if (domainDoc.get("translator") != null) wn.setTranslator(domainDoc.get("translator").toString());
                if (domainDoc.get("started_at") != null) wn.setStartedAt(parseDate(domainDoc.get("started_at")));
                if (domainDoc.get("genres") instanceof Map<?,?> m)
                    wn.setGenres((Map<String,Object>) m);
                webnovelRepo.save(wn);
            }
            default -> { /* AV는 TMDB로 처리 */ }
        }
    }
}
