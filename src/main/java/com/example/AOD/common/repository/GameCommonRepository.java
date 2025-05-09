package com.example.AOD.common.repository;

import com.example.AOD.common.commonDomain.GameCommon;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameCommonRepository extends JpaRepository<GameCommon, Long> {
}
