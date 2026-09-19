package com.example.AOD.recommend.reaction;

import com.example.AOD.domain.ContentLike;
import com.example.AOD.recommend.context.RecContextHolder;
import com.example.AOD.recommend.log.RecEventRecorder;
import com.example.AOD.repo.ContentLikeRepository;
import com.example.AOD.user.model.User;
import com.example.AOD.user.repository.UserRepository;
import com.example.shared.entity.Content;
import com.example.shared.repository.ContentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * 좋아요·싫어요의 단일 쓰기 경로 (REC_TAB_DESIGN §4-1·§6-3).
 * 상태 지정(멱등)이 기본이고, 기존 토글 API 도 toggle() 로 여기를 거친다 — reaction_changed 가 빠짐없이 남는다.
 */
@Service
@RequiredArgsConstructor
public class ReactionService {

    private final ContentLikeRepository contentLikeRepository;
    private final ContentRepository contentRepository;
    private final UserRepository userRepository;
    private final RecEventRecorder recorder;

    /** 상태 지정. 같은 상태를 다시 보내면 변화도 이벤트도 없다. */
    @Transactional
    public ReactionResult setReaction(Long contentId, String username, ReactionState target) {
        return apply(contentId, username, previous -> target);
    }

    /** 토글 의미: 누른 것이 현재 상태와 같으면 NONE, 다르면 누른 상태. 기존 POST /like·/dislike 용. */
    @Transactional
    public ReactionResult toggle(Long contentId, String username, ReactionState pressed) {
        return apply(contentId, username, previous -> previous == pressed ? ReactionState.NONE : pressed);
    }

    private ReactionResult apply(Long contentId, String username, UnaryOperator<ReactionState> decide) {
        Content content = contentRepository.findById(contentId)
                .orElseThrow(() -> new ContentNotFoundException(contentId));
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UnauthenticatedException("User not found: " + username));

        Optional<ContentLike> existing = contentLikeRepository.findByContentAndUser(content, user);
        ReactionState previous = existing.map(cl -> ReactionState.fromLikeType(cl.getLikeType()))
                .orElse(ReactionState.NONE);
        ReactionState target = decide.apply(previous);

        if (target != previous) {
            if (target == ReactionState.NONE) {
                contentLikeRepository.delete(existing.get());
            } else if (existing.isPresent()) {
                existing.get().setLikeType(target.toLikeType());
                contentLikeRepository.save(existing.get());
            } else {
                ContentLike created = new ContentLike();
                created.setContent(content);
                created.setUser(user);
                created.setLikeType(target.toLikeType());
                contentLikeRepository.save(created);
            }
            recorder.reactionChanged(user.getId(), contentId, previous.name(), target.name(),
                    RecContextHolder.current());
        }

        return new ReactionResult(target, previous,
                contentLikeRepository.countLikesByContentId(contentId),
                contentLikeRepository.countDislikesByContentId(contentId));
    }
}
