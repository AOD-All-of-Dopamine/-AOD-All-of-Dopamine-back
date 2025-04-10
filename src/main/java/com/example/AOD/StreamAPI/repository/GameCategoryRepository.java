package com.example.AOD.StreamAPI.repository;

import com.example.AOD.StreamAPI.domain.GameDeveloper;
import com.example.AOD.StreamAPI.domain.SteamGameCategory;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameCategoryRepository extends JpaRepository<SteamGameCategory,Long> {
    Optional<SteamGameCategory> findByName(String name);
}
