package com.example.AOD.recommend.reaction;

import com.example.AOD.domain.ContentLike;
import com.example.AOD.recommend.context.RecContext;
import com.example.AOD.recommend.log.RecEventRecorder;
import com.example.AOD.repo.ContentLikeRepository;
import com.example.AOD.user.model.User;
import com.example.AOD.user.repository.UserRepository;
import com.example.shared.entity.Content;
import com.example.shared.repository.ContentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReactionServiceTest {

    @Mock ContentLikeRepository contentLikeRepository;
    @Mock ContentRepository contentRepository;
    @Mock UserRepository userRepository;
    @Mock RecEventRecorder recorder;
    @InjectMocks ReactionService service;

    private Content content;
    private User user;

    @BeforeEach
    void setUp() {
        content = new Content();
        content.setContentId(42L);
        user = new User();
        user.setId(7L);
        user.setUsername("tester");
    }

    private void givenContentAndUser() {
        given(contentRepository.findById(42L)).willReturn(Optional.of(content));
        given(userRepository.findByUsername("tester")).willReturn(Optional.of(user));
    }

    private ContentLike existing(ContentLike.LikeType type) {
        ContentLike cl = new ContentLike();
        cl.setContent(content);
        cl.setUser(user);
        cl.setLikeType(type);
        return cl;
    }

    @Test
    void noneToLikeCreatesRowAndLogs() {
        givenContentAndUser();
        given(contentLikeRepository.findByContentAndUser(content, user)).willReturn(Optional.empty());
        given(contentLikeRepository.countLikesByContentId(42L)).willReturn(1L);

        ReactionResult r = service.setReaction(42L, "tester", ReactionState.LIKE);

        assertEquals(ReactionState.LIKE, r.state());
        assertEquals(ReactionState.NONE, r.previousState());
        assertEquals(1L, r.likeCount());
        ArgumentCaptor<ContentLike> saved = ArgumentCaptor.forClass(ContentLike.class);
        verify(contentLikeRepository).save(saved.capture());
        assertEquals(ContentLike.LikeType.LIKE, saved.getValue().getLikeType());
        verify(recorder).reactionChanged(eq(7L), eq(42L), eq("NONE"), eq("LIKE"), any(RecContext.class));
    }

    @Test
    void sameStateIsIdempotentAndSilent() {
        givenContentAndUser();
        given(contentLikeRepository.findByContentAndUser(content, user))
                .willReturn(Optional.of(existing(ContentLike.LikeType.LIKE)));

        ReactionResult r = service.setReaction(42L, "tester", ReactionState.LIKE);

        assertEquals(ReactionState.LIKE, r.state());
        assertEquals(ReactionState.LIKE, r.previousState());
        verify(contentLikeRepository, never()).save(any());
        verify(contentLikeRepository, never()).delete(any());
        verify(recorder, never()).reactionChanged(anyLong(), anyLong(), anyString(), anyString(), any());
    }

    @Test
    void likeToDislikeOverwritesSameRowAndReportsPrevious() {
        givenContentAndUser();
        ContentLike row = existing(ContentLike.LikeType.LIKE);
        given(contentLikeRepository.findByContentAndUser(content, user)).willReturn(Optional.of(row));

        ReactionResult r = service.setReaction(42L, "tester", ReactionState.DISLIKE);

        assertEquals(ReactionState.LIKE, r.previousState(), "되돌리기가 좋아요로 돌아가려면 이전 상태가 필요하다");
        assertEquals(ContentLike.LikeType.DISLIKE, row.getLikeType());
        verify(contentLikeRepository).save(row);
        verify(recorder).reactionChanged(eq(7L), eq(42L), eq("LIKE"), eq("DISLIKE"), any(RecContext.class));
    }

    @Test
    void toNoneDeletesRow() {
        givenContentAndUser();
        ContentLike row = existing(ContentLike.LikeType.DISLIKE);
        given(contentLikeRepository.findByContentAndUser(content, user)).willReturn(Optional.of(row));

        ReactionResult r = service.setReaction(42L, "tester", ReactionState.NONE);

        assertEquals(ReactionState.NONE, r.state());
        verify(contentLikeRepository).delete(row);
        verify(recorder).reactionChanged(eq(7L), eq(42L), eq("DISLIKE"), eq("NONE"), any(RecContext.class));
    }

    @Test
    void togglePressedSameStateTurnsOff() {
        givenContentAndUser();
        given(contentLikeRepository.findByContentAndUser(content, user))
                .willReturn(Optional.of(existing(ContentLike.LikeType.LIKE)));

        ReactionResult r = service.toggle(42L, "tester", ReactionState.LIKE);

        assertEquals(ReactionState.NONE, r.state());
    }

    @Test
    void togglePressedOtherStateSwitches() {
        givenContentAndUser();
        given(contentLikeRepository.findByContentAndUser(content, user))
                .willReturn(Optional.of(existing(ContentLike.LikeType.LIKE)));

        ReactionResult r = service.toggle(42L, "tester", ReactionState.DISLIKE);

        assertEquals(ReactionState.DISLIKE, r.state());
        assertEquals(ReactionState.LIKE, r.previousState());
    }

    @Test
    void missingContentThrowsNotFound() {
        given(contentRepository.findById(99L)).willReturn(Optional.empty());

        ContentNotFoundException e = assertThrows(ContentNotFoundException.class,
                () -> service.setReaction(99L, "tester", ReactionState.LIKE));
        assertEquals("Content not found: 99", e.getMessage());
    }

    @Test
    void unknownUserThrowsUnauthenticated() {
        given(contentRepository.findById(42L)).willReturn(Optional.of(content));
        given(userRepository.findByUsername("ghost")).willReturn(Optional.empty());

        assertThrows(UnauthenticatedException.class,
                () -> service.setReaction(42L, "ghost", ReactionState.LIKE));
    }
}
