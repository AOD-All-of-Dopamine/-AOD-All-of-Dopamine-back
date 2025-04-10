package com.example.AOD.StreamAPI.repository;

import com.example.AOD.StreamAPI.domain.GameDeveloper;
import com.example.AOD.StreamAPI.domain.GamePublisher;
import com.example.AOD.StreamAPI.domain.SteamGameCategory;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GamePublisherRepository extends JpaRepository<GamePublisher,Long> {
    Optional<GamePublisher> findByName(String name);
}
