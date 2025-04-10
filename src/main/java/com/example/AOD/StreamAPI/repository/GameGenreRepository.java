package com.example.AOD.StreamAPI.repository;

import com.example.AOD.StreamAPI.domain.GameDeveloper;
import com.example.AOD.StreamAPI.domain.GameGenre;
import com.example.AOD.StreamAPI.domain.SteamGameCategory;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameGenreRepository extends JpaRepository<GameGenre,Long> {
    Optional<GameGenre> findByName(String name);
}
