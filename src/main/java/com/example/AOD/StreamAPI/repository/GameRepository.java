package com.example.AOD.StreamAPI.repository;

import com.example.AOD.StreamAPI.domain.Game;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameRepository extends JpaRepository<Game,Long> {
}
