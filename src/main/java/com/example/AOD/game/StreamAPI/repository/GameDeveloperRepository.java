package com.example.AOD.game.StreamAPI.repository;

import com.example.AOD.game.StreamAPI.domain.GameDeveloper;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameDeveloperRepository extends JpaRepository<GameDeveloper,Long> {
    Optional<GameDeveloper> findByName(String name);
}
