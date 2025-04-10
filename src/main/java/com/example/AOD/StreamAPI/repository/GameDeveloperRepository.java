package com.example.AOD.StreamAPI.repository;

import com.example.AOD.StreamAPI.domain.GameDeveloper;
import com.example.AOD.StreamAPI.domain.SteamGameCategory;
import com.example.AOD.naverWebtoonCrawler.domain.WebtoonAuthor;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameDeveloperRepository extends JpaRepository<GameDeveloper,Long> {
    Optional<GameDeveloper> findByName(String name);
}
