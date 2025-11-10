package com.example.AOD.ranking.steam.service;

import com.example.AOD.ranking.entity.ExternalRanking;
import com.example.AOD.ranking.repo.ExternalRankingRepository;
import com.example.AOD.ranking.steam.fetcher.SteamRankingFetcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class SteamRankingService {

    private final SteamRankingFetcher steamRankingFetcher;
    private final ExternalRankingRepository rankingRepository;

    @Transactional
    public void updateTopSellersRanking() {
        log.info("Steam 최고 판매 랭킹 업데이트를 시작합니다 (Selenium 방식).");
        Document doc = steamRankingFetcher.fetchTopSellersPage();

        if (doc == null) {
            log.warn("Steam 최고 판매 랭킹 페이지를 가져오지 못해 작업을 중단합니다.");
            return;
        }

        try {
            // 1. 렌더링된 HTML에서 랭킹 행(tr) 찾기
            // class 속성이 있는 tr 찾기 (헤더는 빈 class라서 자동 제외됨)
            Elements rows = doc.select("tr[class]");
            
            if (rows.isEmpty()) {
                log.error("랭킹 데이터 행을 찾을 수 없습니다. 페이지 구조가 변경되었을 수 있습니다.");
                return;
            }

            log.info("총 {}개의 랭킹 항목을 발견했습니다.", rows.size());
            
            List<ExternalRanking> rankings = new ArrayList<>();
            for (Element row : rows) {
                try {
                    // 순위: 두 번째 td 태그
                    Element rankElement = row.select("td").get(1);
                    if (rankElement == null) continue;
                    
                    // 제목: '_1n_4' 패턴의 div (제목 전용 클래스)
                    Element titleElement = row.selectFirst("div[class*='_1n_4']");
                    if (titleElement == null) {
                        // fallback: 숫자가 아닌 텍스트를 가진 첫 번째 div
                        for (Element div : row.select("div")) {
                            String text = div.ownText().trim();
                            if (!text.isEmpty() && !text.matches("\\d+")) {
                                titleElement = div;
                                break;
                            }
                        }
                    }
                    if (titleElement == null) continue;
                    
                    // AppID: URL에서 추출
                    Element linkElement = row.selectFirst("a[href*='/app/']");
                    if (linkElement == null) continue;
                    
                    String href = linkElement.attr("href");
                    Pattern appIdPattern = Pattern.compile("/app/(\\d+)/");
                    Matcher matcher = appIdPattern.matcher(href);
                    if (!matcher.find()) continue;
                    
                    String rank = rankElement.text();
                    String title = titleElement.text();
                    Long appId = Long.parseLong(matcher.group(1));
                    
                    ExternalRanking ranking = new ExternalRanking();
                    ranking.setRanking(Integer.parseInt(rank));
                    ranking.setTitle(title);
                    ranking.setContentId(appId);
                    ranking.setPlatform("STEAM_GAME");
                    rankings.add(ranking);
                    
                } catch (Exception e) {
                    log.warn("랭킹 항목 파싱 중 오류 발생 (건너뜀): {}", e.getMessage());
                }
            }

            if (!rankings.isEmpty()) {
                // 기존 스팀 랭킹 삭제 후 새로 저장
                List<ExternalRanking> existingRankings = rankingRepository.findByPlatform("STEAM_GAME");
                if (!existingRankings.isEmpty()) {
                    rankingRepository.deleteAll(existingRankings);
                    log.info("기존 Steam 랭킹 {}개를 삭제했습니다.", existingRankings.size());
                }
                
                rankingRepository.saveAll(rankings);
                log.info("Steam 최고 판매 랭킹 업데이트 완료. 총 {}개의 데이터를 저장했습니다.", rankings.size());
            } else {
                log.warn("저장할 유효한 랭킹 데이터가 없습니다.");
            }

        } catch (Exception e) {
            log.error("Steam 랭킹 JSON 파싱 중 심각한 오류 발생", e);
        }
    }
}
