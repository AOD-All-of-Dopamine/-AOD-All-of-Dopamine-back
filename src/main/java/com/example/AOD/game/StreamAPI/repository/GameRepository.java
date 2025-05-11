package com.example.AOD.game.StreamAPI.repository;

import com.example.AOD.game.StreamAPI.domain.SteamGame;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameRepository extends JpaRepository<SteamGame,Long> {
    Optional<SteamGame> findBySteam_appid(Long steam_appid);
}
