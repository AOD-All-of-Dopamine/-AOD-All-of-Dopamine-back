package com.example.AOD.game.StreamAPI.service;

import com.example.AOD.game.StreamAPI.SteamApiFetcher;
import com.example.AOD.game.StreamAPI.domain.SteamGame;
import com.example.AOD.game.StreamAPI.domain.GameDeveloper;
import com.example.AOD.game.StreamAPI.domain.GameGenre;
import com.example.AOD.game.StreamAPI.domain.GamePublisher;
import com.example.AOD.game.StreamAPI.domain.SteamGameCategory;
import com.example.AOD.game.StreamAPI.dto.AllGameFetchDto.SimpleGameDto;
import com.example.AOD.game.StreamAPI.dto.GameDetailDto.GameDetailDto;
import com.example.AOD.game.StreamAPI.repository.GameCategoryRepository;
import com.example.AOD.game.StreamAPI.repository.GameDeveloperRepository;
import com.example.AOD.game.StreamAPI.repository.GameGenreRepository;
import com.example.AOD.game.StreamAPI.repository.GamePublisherRepository;
import com.example.AOD.game.StreamAPI.repository.GameRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class GameService {
    private final GameCategoryRepository gameCategoryRepository;
    private final GameDeveloperRepository gameDeveloperRepository;
    private final GamePublisherRepository gamePublisherRepository;
    private final GameGenreRepository gameGenreRepository;
    private final GameRepository gameRepository;
    private final SteamApiFetcher steamApiFetcher;

    public SteamGame saveGame(GameDetailDto dto) {
        List<GameDeveloper> developers = dto.getDevelopers().stream()
                .map(name -> gameDeveloperRepository.findByName(name)
                        .orElseGet(() -> gameDeveloperRepository.save(new GameDeveloper(name))))
                .collect(Collectors.toList());

        List<GamePublisher> publishers = dto.getPublishers().stream()
                .map(name -> gamePublisherRepository.findByName(name)
                        .orElseGet(() -> gamePublisherRepository.save(new GamePublisher(name))))
                .collect(Collectors.toList());

        List<GameGenre> genres = dto.getGenres().stream()
                .map(genre -> gameGenreRepository.findByName(genre.getName())
                        .orElseGet(() -> gameGenreRepository.save(new GameGenre(genre.getName()))))
                .collect(Collectors.toList());

        List<SteamGameCategory> categories = dto.getCategories().stream()
                .map(category -> gameCategoryRepository.findByName(category.getDescription())
                        .orElseGet(() -> gameCategoryRepository.save(new SteamGameCategory(category.getDescription()))))
                .collect(Collectors.toList());

        SteamGame steamGame = new SteamGame(dto);
        steamGame.setDevelopers(developers);
        steamGame.setPublishers(publishers);
        steamGame.setGenres(genres);
        steamGame.setCategories(categories);
        return gameRepository.save(steamGame);
    }
    
    //절대 이걸 써서는 안돼
    public void fetchAll() {
        List<SimpleGameDto> allGameId = steamApiFetcher.getAllGame();
        System.out.println("allGameId.size() = " + allGameId.size());
        int cnt = 0;
        for (SimpleGameDto game : allGameId) {
            Long id = game.getAppid();
            GameDetailDto gameDetailDto = steamApiFetcher.getGameDetailById(id, "korean");
            System.out.println(cnt+" : "+game.getName());
            cnt++;
            if(gameDetailDto==null || !Objects.equals(gameDetailDto.getType(), "game")){
                continue;
            }
            System.out.println(game.getAppid()+" "+game.getName());
            saveGame(gameDetailDto);
        }
    }

    public SteamGame getGameDetailById(Long id) {
        Optional<SteamGame> game = gameRepository.findById(id);
        if (game.isEmpty()) {
            GameDetailDto gameDetailDto = steamApiFetcher.getGameDetailById(id, "korean");
            return saveGame(gameDetailDto);
        }
        return game.get();
    }

    public List<GameDetailDto> getGamesFromTo(int start, int end){
        List<SimpleGameDto> allGameId = steamApiFetcher.getAllGame();
        List<GameDetailDto> ret = new ArrayList<>();

        start = Math.max(start, 0);
        end = Math.min(end, allGameId.size());

        for(int i=start;i<end;i++){
            SimpleGameDto game = allGameId.get(i);
            try {
                GameDetailDto g = steamApiFetcher.getGameDetailById(game.getAppid(), "korean");
                if(g!=null) ret.add(g);
            } catch (Exception e){
                break;
            }
        }
        return ret;
    }
}
