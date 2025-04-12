package com.example.AOD.OTT.Netflix.repository;

import com.example.AOD.OTT.Netflix.domain.Actor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ActorRepository extends JpaRepository<Actor, Long> {
    Optional<Actor> findByName(String actorName);
}
