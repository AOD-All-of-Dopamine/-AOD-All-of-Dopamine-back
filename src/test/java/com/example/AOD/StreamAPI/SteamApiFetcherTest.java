package com.example.AOD.StreamAPI;

import com.example.AOD.StreamAPI.dto.AllGameFetchDto.SimpleGameDto;
import com.example.AOD.StreamAPI.dto.GameDetailDto.GameDetailDto;
import java.sql.SQLOutput;
import java.util.List;
import org.junit.jupiter.api.Test;

class SteamApiFetcherTest {

    private final SteamApiFetcher steamApiFetcher = new SteamApiFetcher();
    @Test
    public void test(){
        List<SimpleGameDto> allGameId = steamApiFetcher.getAllGame();
        for(int i=0;i<100;i++){
            SimpleGameDto game = allGameId.get(i);
            System.out.println(game.getAppid()+" "+game.getName());
        }
    }

    @Test
    public void test2() throws InterruptedException {
        GameDetailDto gameDetailDto = steamApiFetcher.getGameDetailById(2456740L, "korean");
        System.out.println(gameDetailDto.getName());
        System.out.println(gameDetailDto.getDevelopers());
        System.out.println(gameDetailDto.getRelease_date().getDate());
        System.out.println(gameDetailDto.getPrice_overview().getFinal_());
    }

}