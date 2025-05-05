package com.example.AOD.StreamAPI.service;

import com.example.AOD.game.StreamAPI.domain.SteamGame;
import com.example.AOD.game.StreamAPI.service.GameService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class SteamGameServiceTest {

    @Autowired
    private GameService gameService;

    @Test
    public void test(){
//        gameService.fetch();
        SteamGame steamGame = gameService.getGameDetailById(578080L);
        System.out.println("game = " + steamGame);
    }
}