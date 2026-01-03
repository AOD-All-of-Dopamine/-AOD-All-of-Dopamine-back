package com.example.AOD.repo;

import com.example.AOD.domain.Bookmark;
import com.example.shared.entity.Content;
import com.example.AOD.user.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BookmarkRepository extends JpaRepository<Bookmark, Long> {
    
    // 특정 사용자의 북마크 목록 조회
    Page<Bookmark> findByUser(User user, Pageable pageable);
    
    // 특정 사용자의 특정 작품 북마크 조회
    Optional<Bookmark> findByContentAndUser(Content content, User user);
    
    // 북마크 존재 여부
    boolean existsByContentAndUser(Content content, User user);
    
    // 특정 사용자의 북마크 개수
    long countByUser(User user);
}


