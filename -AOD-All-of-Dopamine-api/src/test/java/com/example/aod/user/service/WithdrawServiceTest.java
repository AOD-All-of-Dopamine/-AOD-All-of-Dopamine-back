package com.example.AOD.user.service;

import com.example.AOD.recommend.auth.RecAuth;
import com.example.AOD.recommend.log.RecLogJdbc;
import com.example.AOD.user.model.User;
import com.example.AOD.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 무엇을 지우는가는 실제 DB 로 확인한다(RecLogIntegrationTest). 여기서는 DB 없이 확인할 수 있는 순서 규칙만 본다.
 */
class WithdrawServiceTest {

    private final JdbcTemplate accountJdbc = mock(JdbcTemplate.class);
    private final TransactionTemplate accountTx = mock(TransactionTemplate.class);
    private final RecLogJdbc recLogJdbc = mock(RecLogJdbc.class);
    private final UserRepository users = mock(UserRepository.class);
    private final RecAuth recAuth = mock(RecAuth.class);
    private final WithdrawService service = new WithdrawService(accountJdbc, accountTx, recLogJdbc, users, recAuth);

    /** 로그를 못 지웠는데 계정이 사라지면, 어떤 익명 ID 가 그 회원 것이었는지 다시는 찾을 수 없다. */
    @Test
    void accountIsLeftUntouchedWhenLogDeletionFails() {
        TransactionOperations logTx = mock(TransactionOperations.class);
        given(recLogJdbc.jdbc()).willReturn(mock(JdbcTemplate.class));
        given(recLogJdbc.tx()).willReturn(logTx);
        willThrow(new DataAccessResourceFailureException("로그 풀 장애")).given(logTx).executeWithoutResult(any());
        User user = new User();
        user.setId(7L);
        user.setUsername("kim");

        assertThrows(DataAccessResourceFailureException.class, () -> service.withdraw(user));

        verifyNoInteractions(accountJdbc, accountTx, users, recAuth);
    }
}
