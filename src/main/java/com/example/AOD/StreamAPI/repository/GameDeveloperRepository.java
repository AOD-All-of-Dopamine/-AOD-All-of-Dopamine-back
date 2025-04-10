package com.example.AOD.StreamAPI.repository;

import com.example.AOD.StreamAPI.domain.GameDeveloper;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameDeveloperRepository extends JpaRepository<GameDeveloper,Long> {
    Optional<GameDeveloper> findByName(String name);
}
