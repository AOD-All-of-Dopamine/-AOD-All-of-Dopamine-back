package com.example.AOD.game.StreamAPI.repository;

import com.example.AOD.game.StreamAPI.domain.SteamGame;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface GameRepository extends JpaRepository<SteamGame,Long> {
    @Query("select g From SteamGame g where g.steam_appid = :steam_appid")
    Optional<SteamGame> findBySteam_appid(Long steam_appid);
}
