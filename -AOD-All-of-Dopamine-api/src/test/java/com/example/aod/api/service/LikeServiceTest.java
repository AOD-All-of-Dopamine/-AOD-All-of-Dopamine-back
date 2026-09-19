package com.example.AOD.api.service;

import com.example.AOD.recommend.reaction.ReactionResult;
import com.example.AOD.recommend.reaction.ReactionService;
import com.example.AOD.recommend.reaction.ReactionState;
import com.example.AOD.repo.ContentLikeRepository;
import com.example.AOD.user.repository.UserRepository;
import com.example.shared.repository.ContentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class LikeServiceTest {

    @Mock ContentLikeRepository contentLikeRepository;
    @Mock ContentRepository contentRepository;
    @Mock UserRepository userRepository;
    @Mock ReactionService reactionService;
    @InjectMocks LikeService likeService;

    @Test
    void toggleLikeDelegatesAndKeepsLegacyResponseShape() {
        given(reactionService.toggle(10L, "tester", ReactionState.LIKE))
                .willReturn(new ReactionResult(ReactionState.LIKE, ReactionState.NONE, 15L, 2L));

        Map<String, Object> res = likeService.toggleLike(10L, "tester");

        assertEquals(Set.of("contentId", "likeCount", "dislikeCount", "userLikeType", "message"), res.keySet());
        assertEquals(10L, res.get("contentId"));
        assertEquals(15L, res.get("likeCount"));
        assertEquals(2L, res.get("dislikeCount"));
        assertEquals("LIKE", res.get("userLikeType"));
        assertEquals("좋아요!", res.get("message"));
    }

    @Test
    void toggleDislikeOffReportsNoneAndCancelMessage() {
        given(reactionService.toggle(10L, "tester", ReactionState.DISLIKE))
                .willReturn(new ReactionResult(ReactionState.NONE, ReactionState.DISLIKE, 15L, 1L));

        Map<String, Object> res = likeService.toggleDislike(10L, "tester");

        assertEquals("NONE", res.get("userLikeType"));
        assertEquals("취소되었습니다.", res.get("message"));
    }
}
