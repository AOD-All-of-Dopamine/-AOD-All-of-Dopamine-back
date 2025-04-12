package com.example.AOD.game.StreamAPI.repository;

import com.example.AOD.game.StreamAPI.domain.GamePublisher;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GamePublisherRepository extends JpaRepository<GamePublisher,Long> {
    Optional<GamePublisher> findByName(String name);
}
