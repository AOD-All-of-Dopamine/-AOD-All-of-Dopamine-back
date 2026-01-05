package com.example.AOD.domain;

import com.example.AOD.user.model.User;
import com.example.shared.entity.Content;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "reviews", 
       uniqueConstraints = @UniqueConstraint(columnNames = {"content_id", "user_id"}))
@Getter
@Setter
@NoArgsConstructor
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "review_id")
    private Long reviewId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "content_id", nullable = false)
    private Content content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private Double rating; // 0.0 ~ 5.0

    @Column(length = 100)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String reviewContent;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // 비즈니스 로직 메서드
    public void updateReview(Double rating, String title, String reviewContent) {
        if (rating != null && rating >= 0.0 && rating <= 5.0) {
            this.rating = rating;
        }
        if (title != null) {
            this.title = title;
        }
        if (reviewContent != null) {
            this.reviewContent = reviewContent;
        }
    }
}


