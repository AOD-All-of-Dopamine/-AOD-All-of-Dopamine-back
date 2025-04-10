package com.example.AOD.StreamAPI.service;

import static org.junit.jupiter.api.Assertions.*;

import com.example.AOD.StreamAPI.domain.Game;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class GameServiceTest {

    @Autowired
    private GameService gameService;

    @Test
    public void test(){
//        gameService.fetch();
        Game game = gameService.getGameDetailById(578080L);
        System.out.println("game = " + game);
    }
}