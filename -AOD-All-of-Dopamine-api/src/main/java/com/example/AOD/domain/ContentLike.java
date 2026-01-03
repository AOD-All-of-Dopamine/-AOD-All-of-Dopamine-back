package com.example.AOD.domain;

import com.example.AOD.user.model.User;
import com.example.shared.entity.Content;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "content_likes",
       uniqueConstraints = @UniqueConstraint(columnNames = {"content_id", "user_id"}))
@Getter
@Setter
@NoArgsConstructor
public class ContentLike {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "like_id")
    private Long likeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "content_id", nullable = false)
    private Content content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "like_type", nullable = false, length = 10)
    private LikeType likeType;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public enum LikeType {
        LIKE, DISLIKE
    }
}


