package com.example.AOD.common.repository;

import com.example.AOD.common.commonDomain.NovelCommon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NovelCommonRepository extends JpaRepository<NovelCommon, Long> {
    List<NovelCommon> findByTitleIgnoreCase(String title);
    // 이전에 추가한 메서드는 그대로 유지
    List<NovelCommon> findByTitleContainingIgnoreCase(String keyword);

}