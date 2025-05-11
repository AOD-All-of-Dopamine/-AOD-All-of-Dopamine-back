package com.example.AOD.game.StreamAPI.controller;

import com.example.AOD.game.StreamAPI.domain.SteamGame;
import com.example.AOD.game.StreamAPI.dto.GameDetailDto.GameDetailDto;
import com.example.AOD.game.StreamAPI.service.GameService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SteamGameController {

    private final GameService gameService;
    public SteamGameController(GameService gameService) {
        this.gameService = gameService;
    }

    /*
    권장 범위는 end-start = 100
     */
    @GetMapping("/startGameScrappingFromTo")
    public ResponseEntity<Map<String, Object>> getGameDetail(
            @RequestParam(value = "start", defaultValue = "0") Integer start,
            @RequestParam(value = "end", defaultValue = "300000") Integer end) {

        List<GameDetailDto> gameDetails = gameService.getGamesFromTo(start, end);
        for(GameDetailDto gameDetail : gameDetails) {
            SteamGame game = gameService.findBySteamGameId(gameDetail.getSteam_appid());
            if(game == null) gameService.saveGame(gameDetail);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("games", gameDetails);

        return ResponseEntity.ok(response);
    }

    /*
    @@@@@ 사용하지 말 것 @@@@@@
    API 실행시간 100시간(진짜임)
     */
    @GetMapping("/startAllSteamGameScrapping")
    public ResponseEntity<Map<String, Object>> getAllSteamGame() {
        gameService.fetchAll();

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "전체 게임 스크래핑 작업이 백그라운드에서 실행 중입니다.");
        return ResponseEntity.ok(response);
    }

}
