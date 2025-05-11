package com.example.AOD.StreamAPI;

import com.example.AOD.game.StreamAPI.SteamApiFetcher;
import com.example.AOD.game.StreamAPI.dto.AllGameFetchDto.SimpleGameDto;
import com.example.AOD.game.StreamAPI.dto.GameDetailDto.GameDetailDto;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

class SteamApiFetcherTest {

    private final SteamApiFetcher steamApiFetcher = new SteamApiFetcher();
    @Test
    public void test() throws InterruptedException {
        /*
        매 200번째 요청마다 429 Too Many Requests 발생 : 5분 동안 요청 불가
        게임 1000개 가져오는데 약 23분
        전체 게임 다운로드하는 데 약 95시간
         */

        List<SimpleGameDto> allGameId = steamApiFetcher.getAllGame(); //size = 246'181
        long st = System.currentTimeMillis();

        for(int i=0;i<1000;i++){
            SimpleGameDto game = allGameId.get(i);
            try {
                GameDetailDto g = steamApiFetcher.getGameDetailById(game.getAppid(), "korean");
                if(g!=null){
                    System.out.println(i+": "+g.getSteam_appid()+" "+g.getName());
                }
            } catch (Exception e){
                System.out.println(e.getMessage());
                Thread.sleep(60*1000);
                i--;
            }
        }

        long en = System.currentTimeMillis();
        System.out.println("실행 시간: " + (en - st) + "ms");

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