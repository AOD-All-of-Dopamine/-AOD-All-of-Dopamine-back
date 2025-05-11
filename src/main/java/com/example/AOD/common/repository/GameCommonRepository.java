package com.example.AOD.common.repository;

import com.example.AOD.common.commonDomain.GameCommon;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GameCommonRepository extends JpaRepository<GameCommon, Long> {
    List<GameCommon> findByTitleIgnoreCase(String title);
    List<GameCommon> findByTitleContainingIgnoreCase(String keyword);
}
