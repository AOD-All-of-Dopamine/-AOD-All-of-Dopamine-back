package com.example.AOD.game.StreamAPI;

import com.example.AOD.game.StreamAPI.dto.AllGameFetchDto.AppListDto;
import com.example.AOD.game.StreamAPI.dto.AllGameFetchDto.AppListWrapper;
import com.example.AOD.game.StreamAPI.dto.AllGameFetchDto.SimpleGameDto;
import com.example.AOD.game.StreamAPI.dto.GameDetailDto.GameDetailDto;
import com.example.AOD.game.StreamAPI.dto.GameDetailDto.GameDetailsResponseDto;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class SteamApiFetcher {

    private final RestTemplate restTemplate = new RestTemplate();

    public List<SimpleGameDto> getAllGame(){
        String url = "https://api.steampowered.com/ISteamApps/GetAppList/v2";
        AppListWrapper appListWrapper = restTemplate.getForObject(url, AppListWrapper.class);
        AppListDto applist = appListWrapper.getApplist();
        List<SimpleGameDto> apps = applist.getApps();
        return apps.stream()
                .filter(game-> game.getName()!=null && !game.getName().isEmpty())
                .toList();
    }

    public GameDetailDto getGameDetailById(Long id, String language){
        String url = "https://store.steampowered.com/api/appdetails?";
        url += "appids="+id;
        url += "&l="+language;
        GameDetailsResponseDto res = restTemplate.getForObject(url, GameDetailsResponseDto.class);
        return res.get(Long.toString(id)).getData();
    }
}
