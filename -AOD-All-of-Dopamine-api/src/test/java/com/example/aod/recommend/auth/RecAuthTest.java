package com.example.AOD.recommend.auth;

import com.example.AOD.security.JwtTokenProvider;
import com.example.AOD.user.model.User;
import com.example.AOD.user.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class RecAuthTest {

    private final JwtTokenProvider jwt = mock(JwtTokenProvider.class);
    private final UserRepository users = mock(UserRepository.class);
    private final RecAuth recAuth = new RecAuth(jwt, users);

    private void givenValidToken() {
        User user = new User();
        user.setId(7L);
        user.setUsername("tester");
        given(jwt.validateToken("good")).willReturn(true);
        given(jwt.getUsername("good")).willReturn("tester");
        given(users.findByUsername("tester")).willReturn(Optional.of(user));
    }

    @Test
    void noHeaderIsNone() {
        assertEquals(RecAuth.Status.NONE, recAuth.authenticate(null).status());
        assertEquals(RecAuth.Status.NONE, recAuth.authenticate("").status());
        assertEquals(RecAuth.Status.NONE, recAuth.authenticate("Basic abc").status());
        assertNull(recAuth.userIdOrNull(null));
    }

    @Test
    void expiredForgedOrThrowingTokenIsInvalid() {
        given(jwt.validateToken("expired")).willReturn(false);
        given(jwt.validateToken("boom")).willThrow(new IllegalArgumentException("malformed"));

        assertEquals(RecAuth.Status.INVALID, recAuth.authenticate("Bearer expired").status());
        assertEquals(RecAuth.Status.INVALID, recAuth.authenticate("Bearer boom").status());
        assertNull(recAuth.userIdOrNull("Bearer expired"));
    }

    @Test
    void validTokenOfMissingUserIsInvalid() {
        given(jwt.validateToken("ghost")).willReturn(true);
        given(jwt.getUsername("ghost")).willReturn("ghost");
        given(users.findByUsername("ghost")).willReturn(Optional.empty());

        assertEquals(RecAuth.Status.INVALID, recAuth.authenticate("Bearer ghost").status());
        assertNull(recAuth.userIdOrNull("Bearer ghost"));
    }

    @Test
    void validTokenResolvesUserIdAndCachesIt() {
        givenValidToken();

        RecAuth.Result first = recAuth.authenticate("Bearer good");
        RecAuth.Result second = recAuth.authenticate("Bearer good");

        assertEquals(RecAuth.Status.OK, first.status());
        assertEquals(7L, first.userId());
        assertEquals("tester", first.username());
        assertEquals(7L, second.userId());
        verify(users, times(1)).findByUsername("tester");   // 두 번째는 캐시
    }

    @Test
    void userIdOrNullKeepsBeaconBehaviour() {
        givenValidToken();
        assertEquals(7L, recAuth.userIdOrNull("Bearer good"));
    }
}
