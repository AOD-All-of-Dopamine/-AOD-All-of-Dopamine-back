package com.example.AOD.repo;

import com.example.AOD.domain.Collection;
import com.example.AOD.user.model.User;
import com.example.shared.entity.Domain;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CollectionRepository extends JpaRepository<Collection, Long> {

    // 발견 페이지 — 공개 컬렉션 목록 (정렬은 Pageable Sort로: 인기=likeCount desc, 최신=createdAt desc)
    Page<Collection> findByVisibility(Collection.Visibility visibility, Pageable pageable);

    Page<Collection> findByVisibilityAndDomain(Collection.Visibility visibility, Domain domain, Pageable pageable);

    // 내 컬렉션 (비공개 포함)
    Page<Collection> findByUser(User user, Pageable pageable);

    // 담기 팝오버용 — 해당 도메인의 내 컬렉션 (사용자당 개수가 작아 리스트 반환)
    List<Collection> findByUserAndDomainOrderByCreatedAtDesc(User user, Domain domain);

    // ===== 비정규화 카운트 원자 갱신 (경합 안전 — 엔티티 dirty checking 금지) =====

    @Modifying
    @Query("UPDATE Collection c SET c.viewCount = c.viewCount + 1 WHERE c.id = :id")
    int incrementViewCount(@Param("id") Long id);

    @Modifying
    @Query("UPDATE Collection c SET c.likeCount = c.likeCount + 1 WHERE c.id = :id")
    int incrementLikeCount(@Param("id") Long id);

    @Modifying
    @Query("UPDATE Collection c SET c.likeCount = c.likeCount - 1 WHERE c.id = :id AND c.likeCount > 0")
    int decrementLikeCount(@Param("id") Long id);
}
