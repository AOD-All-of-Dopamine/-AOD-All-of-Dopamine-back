package com.example.AOD.api.service;

import com.example.AOD.api.dto.PageResponse;
import com.example.AOD.api.dto.WorkSummaryDTO;
import com.example.AOD.domain.ContentLike;
import com.example.shared.entity.Content;
import com.example.AOD.repo.ContentLikeRepository;
import com.example.shared.repository.ContentRepository;
import com.example.AOD.user.model.User;
import com.example.AOD.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LikeService {

    private final ContentLikeRepository contentLikeRepository;
    private final ContentRepository contentRepository;
    private final UserRepository userRepository;

    /**
     * 좋아요 토글
     */
    @Transactional
    public Map<String, Object> toggleLike(Long contentId, String username) {
        return toggleLikeType(contentId, username, ContentLike.LikeType.LIKE);
    }

    /**
     * 싫어요 토글
     */
    @Transactional
    public Map<String, Object> toggleDislike(Long contentId, String username) {
        return toggleLikeType(contentId, username, ContentLike.LikeType.DISLIKE);
    }

    private Map<String, Object> toggleLikeType(Long contentId, String username, ContentLike.LikeType targetType) {
        Content content = contentRepository.findById(contentId)
                .orElseThrow(() -> new RuntimeException("Content not found: " + contentId));

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));

        Optional<ContentLike> existing = contentLikeRepository.findByContentAndUser(content, user);

        if (existing.isPresent()) {
            ContentLike contentLike = existing.get();
            if (contentLike.getLikeType() == targetType) {
                // 같은 타입이면 삭제 (토글 off)
                contentLikeRepository.delete(contentLike);
                return buildResponse(contentId, null);
            } else {
                // 다른 타입이면 변경 (좋아요 <-> 싫어요)
                contentLike.setLikeType(targetType);
                contentLikeRepository.save(contentLike);
                return buildResponse(contentId, targetType);
            }
        } else {
            // 새로 생성
            ContentLike contentLike = new ContentLike();
            contentLike.setContent(content);
            contentLike.setUser(user);
            contentLike.setLikeType(targetType);
            contentLikeRepository.save(contentLike);
            return buildResponse(contentId, targetType);
        }
    }

    /**
     * 작품의 좋아요/싫어요 통계 조회
     */
    public Map<String, Object> getLikeStats(Long contentId, String username) {
        long likeCount = contentLikeRepository.countLikesByContentId(contentId);
        long dislikeCount = contentLikeRepository.countDislikesByContentId(contentId);

        ContentLike.LikeType userLikeType = null;
        if (username != null) {
            Content content = contentRepository.findById(contentId).orElse(null);
            User user = userRepository.findByUsername(username).orElse(null);
            if (content != null && user != null) {
                Optional<ContentLike> userLike = contentLikeRepository.findByContentAndUser(content, user);
                userLikeType = userLike.map(ContentLike::getLikeType).orElse(null);
            }
        }

        return Map.of(
                "contentId", contentId,
                "likeCount", likeCount,
                "dislikeCount", dislikeCount,
                "userLikeType", userLikeType != null ? userLikeType.name() : "NONE"
        );
    }

    private Map<String, Object> buildResponse(Long contentId, ContentLike.LikeType currentType) {
        long likeCount = contentLikeRepository.countLikesByContentId(contentId);
        long dislikeCount = contentLikeRepository.countDislikesByContentId(contentId);

        return Map.of(
                "contentId", contentId,
                "likeCount", likeCount,
                "dislikeCount", dislikeCount,
                "userLikeType", currentType != null ? currentType.name() : "NONE",
                "message", currentType == null ? "취소되었습니다." : 
                           (currentType == ContentLike.LikeType.LIKE ? "좋아요!" : "싫어요")
        );
    }

    /**
     * 내가 좋아요한 작품 목록 조회
     */
    public PageResponse<WorkSummaryDTO> getMyLikes(String username, Pageable pageable) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));

        Page<ContentLike> likePage = contentLikeRepository.findByUserAndLikeType(
                user, ContentLike.LikeType.LIKE, pageable);

        java.util.List<WorkSummaryDTO> content = likePage.getContent().stream()
                .map(like -> {
                    Content c = like.getContent();
                    return WorkSummaryDTO.builder()
                            .id(c.getContentId())
                            .title(c.getMasterTitle())
                            .thumbnail(c.getPosterImageUrl())
                            .domain(c.getDomain() != null ? c.getDomain().name() : null)
                            .releaseDate(c.getReleaseDate() != null ? c.getReleaseDate().toString() : null)
                            .build();
                })
                .collect(java.util.stream.Collectors.toList());

        return PageResponse.<WorkSummaryDTO>builder()
                .content(content)
                .page(likePage.getNumber())
                .size(likePage.getSize())
                .totalElements(likePage.getTotalElements())
                .totalPages(likePage.getTotalPages())
                .first(likePage.isFirst())
                .last(likePage.isLast())
                .build();
    }
}


