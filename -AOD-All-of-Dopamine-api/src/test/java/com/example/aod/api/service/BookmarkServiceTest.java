package com.example.AOD.api.service;

import com.example.AOD.domain.Bookmark;
import com.example.AOD.recommend.context.RecContext;
import com.example.AOD.recommend.log.RecEventRecorder;
import com.example.AOD.repo.BookmarkRepository;
import com.example.AOD.user.model.User;
import com.example.AOD.user.repository.UserRepository;
import com.example.shared.entity.Content;
import com.example.shared.repository.ContentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BookmarkServiceTest {

    @Mock BookmarkRepository bookmarkRepository;
    @Mock ContentRepository contentRepository;
    @Mock UserRepository userRepository;
    @Mock RecEventRecorder recorder;
    @InjectMocks BookmarkService service;

    private Content content;
    private User user;

    @BeforeEach
    void setUp() {
        content = new Content();
        content.setContentId(42L);
        user = new User();
        user.setId(7L);
        user.setUsername("tester");
        given(contentRepository.findById(42L)).willReturn(Optional.of(content));
        given(userRepository.findByUsername("tester")).willReturn(Optional.of(user));
    }

    @Test
    void addingBookmarkLogsOn() {
        given(bookmarkRepository.findByContentAndUser(content, user)).willReturn(Optional.empty());

        Map<String, Object> res = service.toggleBookmark(42L, "tester");

        assertEquals(true, res.get("bookmarked"));
        verify(recorder).bookmarkChanged(eq(7L), eq(42L), eq(true), any(RecContext.class));
    }

    @Test
    void removingBookmarkLogsOff() {
        given(bookmarkRepository.findByContentAndUser(content, user)).willReturn(Optional.of(new Bookmark()));

        Map<String, Object> res = service.toggleBookmark(42L, "tester");

        assertEquals(false, res.get("bookmarked"));
        verify(recorder).bookmarkChanged(eq(7L), eq(42L), eq(false), any(RecContext.class));
    }
}
