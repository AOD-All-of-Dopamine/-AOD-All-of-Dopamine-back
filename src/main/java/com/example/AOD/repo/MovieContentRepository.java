package com.example.AOD.repo;

import com.example.AOD.domain.entity.MovieContent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MovieContentRepository extends JpaRepository<MovieContent, Long> {
}
