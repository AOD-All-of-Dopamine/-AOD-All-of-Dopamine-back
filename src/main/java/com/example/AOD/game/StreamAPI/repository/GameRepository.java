package com.example.AOD.game.StreamAPI.repository;

import com.example.AOD.game.StreamAPI.domain.SteamGame;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameRepository extends JpaRepository<SteamGame,Long> {
}
