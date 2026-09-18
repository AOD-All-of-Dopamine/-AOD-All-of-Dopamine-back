package com.example.AOD.api.service;

import com.example.AOD.api.dto.PageResponse;
import com.example.AOD.api.dto.WorkSummaryDTO;
import com.example.AOD.domain.ContentLike;
import com.example.shared.entity.Content;
import com.example.AOD.recommend.reaction.ReactionResult;
import com.example.AOD.recommend.reaction.ReactionService;
import com.example.AOD.recommend.reaction.ReactionState;
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
    private final ReactionService reactionService;

    /**
     * 좋아요 토글 — 쓰기는 ReactionService 가 한다 (reaction_changed 로그가 남는다).
     */
    @Transactional
    public Map<String, Object> toggleLike(Long contentId, String username) {
        return legacyResponse(contentId, reactionService.toggle(contentId, username, ReactionState.LIKE));
    }

    /**
     * 싫어요 토글
     */
    @Transactional
    public Map<String, Object> toggleDislike(Long contentId, String username) {
        return legacyResponse(contentId, reactionService.toggle(contentId, username, ReactionState.DISLIKE));
    }

    /** 기존 프론트가 쓰는 응답 형태 — 키와 메시지를 바꾸지 않는다. */
    private Map<String, Object> legacyResponse(Long contentId, ReactionResult result) {
        ReactionState state = result.state();
        return Map.of(
                "contentId", contentId,
                "likeCount", result.likeCount(),
                "dislikeCount", result.dislikeCount(),
                "userLikeType", state.name(),
                "message", state == ReactionState.NONE ? "취소되었습니다."
                        : (state == ReactionState.LIKE ? "좋아요!" : "싫어요")
        );
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


