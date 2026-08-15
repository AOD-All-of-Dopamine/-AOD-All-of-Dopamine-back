package com.example.AOD.domain;

import com.example.AOD.user.model.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

/**
 * 컬렉션 좋아요 (사용자당 1건).
 * (collection_id, user_id) 유니크 제약이 멱등 좋아요의 기반 —
 * 삽입은 리포지토리의 ON CONFLICT DO NOTHING 쿼리로만 수행한다.
 */
@Entity
@Table(name = "collection_likes",
       uniqueConstraints = @UniqueConstraint(
               name = "uk_collection_likes_collection_user",
               columnNames = {"collection_id", "user_id"}))
@Getter
@Setter
@NoArgsConstructor
public class CollectionLike {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "collection_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Collection collection;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
