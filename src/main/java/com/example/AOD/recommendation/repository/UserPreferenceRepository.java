package com.example.AOD.recommendation.repository;

import com.example.AOD.recommendation.domain.UserPreference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserPreferenceRepository extends JpaRepository<UserPreference, Long> {
    Optional<UserPreference> findByUsername(String username);
    boolean existsByUsername(String username);
}