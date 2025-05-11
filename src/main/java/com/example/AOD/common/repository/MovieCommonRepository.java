package com.example.AOD.common.repository;

import com.example.AOD.common.commonDomain.MovieCommon;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MovieCommonRepository extends JpaRepository<MovieCommon, Long> {
    List<MovieCommon> findByTitleIgnoreCase(String title);
    List<MovieCommon> findByTitleContainingIgnoreCase(String keyword);
}
