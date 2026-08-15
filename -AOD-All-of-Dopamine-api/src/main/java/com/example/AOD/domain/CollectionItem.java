package com.example.AOD.domain;

import com.example.shared.entity.Content;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

/**
 * 컬렉션에 담긴 작품 한 건.
 * 정렬은 큐레이터가 정한 position(오름차순), 작품마다 한 줄 코멘트(선택, 200자).
 */
@Entity
@Table(name = "collection_items",
       uniqueConstraints = @UniqueConstraint(
               name = "uk_collection_items_collection_content",
               columnNames = {"collection_id", "content_id"}))
@Getter
@Setter
@NoArgsConstructor
public class CollectionItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "collection_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE) // DDL: 컬렉션 삭제 시 아이템도 삭제
    private Collection collection;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "content_id", nullable = false)
    private Content content;

    /** 큐레이터 한 줄 코멘트 (선택) */
    @Column(length = 200)
    private String comment;

    @Column(nullable = false)
    private Integer position;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
