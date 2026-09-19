package com.example.AOD.repo;

import com.example.AOD.domain.ContentLike;
import com.example.shared.entity.Content;
import com.example.AOD.user.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ContentLikeRepository extends JpaRepository<ContentLike, Long> {

    // 특정 사용자의 특정 작품에 대한 좋아요/싫어요 조회
    Optional<ContentLike> findByContentAndUser(Content content, User user);

    // 좋아요/싫어요 존재 여부
    boolean existsByContentAndUser(Content content, User user);

    // 특정 작품의 좋아요 개수
    @Query("SELECT COUNT(cl) FROM ContentLike cl WHERE cl.content.contentId = :contentId AND cl.likeType = 'LIKE'")
    long countLikesByContentId(@Param("contentId") Long contentId);

    // 특정 작품의 싫어요 개수
    @Query("SELECT COUNT(cl) FROM ContentLike cl WHERE cl.content.contentId = :contentId AND cl.likeType = 'DISLIKE'")
    long countDislikesByContentId(@Param("contentId") Long contentId);

    // 사용자가 좋아요한 작품 목록 (페이징)
    Page<ContentLike> findByUserAndLikeType(User user, ContentLike.LikeType likeType, Pageable pageable);

    /**
     * 추천 시드·싫어요용 경량 조회 (User 엔티티를 불러오지 않는다).
     * 최근순 · 동률이면 content_id 작은 것 먼저 — 시드 순서가 추천 결과에 영향을 주므로 DB 에서 못박는다.
     * pageable 은 스캔 상한만 준다(PageRequest.of(0, N), 정렬 없음).
     */
    @Query("SELECT new com.example.AOD.repo.UserContentRow(cl.content.contentId, cl.createdAt) FROM ContentLike cl "
         + "WHERE cl.user.id = :userId AND cl.likeType = :likeType "
         + "ORDER BY cl.createdAt DESC, cl.content.contentId ASC")
    List<UserContentRow> findReactionRowsByUserId(@Param("userId") Long userId,
                                                  @Param("likeType") ContentLike.LikeType likeType,
                                                  Pageable pageable);
}


