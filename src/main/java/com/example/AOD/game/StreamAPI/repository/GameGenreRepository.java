package com.example.AOD.game.StreamAPI.repository;

import com.example.AOD.game.StreamAPI.domain.GameGenre;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameGenreRepository extends JpaRepository<GameGenre,Long> {
    Optional<GameGenre> findByName(String name);
}
