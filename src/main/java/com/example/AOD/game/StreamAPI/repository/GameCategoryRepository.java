package com.example.AOD.game.StreamAPI.repository;

import com.example.AOD.game.StreamAPI.domain.SteamGameCategory;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameCategoryRepository extends JpaRepository<SteamGameCategory,Long> {
    Optional<SteamGameCategory> findByName(String name);
}
