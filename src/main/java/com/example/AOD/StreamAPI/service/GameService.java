package com.example.AOD.StreamAPI.service;

import com.example.AOD.StreamAPI.SteamApiFetcher;
import com.example.AOD.StreamAPI.domain.Game;
import com.example.AOD.StreamAPI.domain.GameDeveloper;
import com.example.AOD.StreamAPI.domain.GameGenre;
import com.example.AOD.StreamAPI.domain.GamePublisher;
import com.example.AOD.StreamAPI.domain.SteamGameCategory;
import com.example.AOD.StreamAPI.dto.AllGameFetchDto.SimpleGameDto;
import com.example.AOD.StreamAPI.dto.GameDetailDto.GameDetailDto;
import com.example.AOD.StreamAPI.repository.GameCategoryRepository;
import com.example.AOD.StreamAPI.repository.GameDeveloperRepository;
import com.example.AOD.StreamAPI.repository.GameGenreRepository;
import com.example.AOD.StreamAPI.repository.GamePublisherRepository;
import com.example.AOD.StreamAPI.repository.GameRepository;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GameService {
    private final GameCategoryRepository gameCategoryRepository;
    private final GameDeveloperRepository gameDeveloperRepository;
    private final GamePublisherRepository gamePublisherRepository;
    private final GameGenreRepository gameGenreRepository;
    private final GameRepository gameRepository;
    private final SteamApiFetcher steamApiFetcher;

    public Game saveGame(GameDetailDto dto) {
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

        Game game = new Game(dto);
        game.setDevelopers(developers);
        game.setPublishers(publishers);
        game.setGenres(genres);
        game.setCategories(categories);
        return gameRepository.save(game);
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

    public Game getGameDetailById(Long id) {
        Optional<Game> game = gameRepository.findById(id);
        if (game.isEmpty()) {
            GameDetailDto gameDetailDto = steamApiFetcher.getGameDetailById(id, "korean");
            return saveGame(gameDetailDto);
        }
        return game.get();
    }
}
