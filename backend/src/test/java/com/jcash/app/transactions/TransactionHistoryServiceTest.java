package com.jcash.app.transactions;

import com.jcash.app.accounts.Account;
import com.jcash.app.transactions.dto.TransactionEntryResponse;
import com.jcash.app.users.User;
import com.jcash.app.users.UserRepository;
import com.jcash.app.users.exception.UserNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionHistoryServiceTest {

    @Mock
    private TransactionEntryRepository transactionEntryRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private User user;

    private TransactionHistoryService transactionHistoryService;

    @BeforeEach
    void setUp() {
        transactionHistoryService = new TransactionHistoryService(
                transactionEntryRepository,
                userRepository
        );
    }

    @Test
    void returnsOnlyEntriesForTheAuthenticatedUserAsDtos() {
        UUID userId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID entryId = UUID.randomUUID();
        Account account = new Account(user);
        ReflectionTestUtils.setField(account, "id", accountId);
        TransactionEntry entry = new TransactionEntry(
                account,
                TransactionType.CASH_IN,
                TransactionDirection.CREDIT,
                new BigDecimal("125.50"),
                "PHP",
                new BigDecimal("125.50")
        );
        ReflectionTestUtils.setField(entry, "id", entryId);
        ReflectionTestUtils.setField(
                entry,
                "createdAt",
                Instant.parse("2025-01-01T00:00:00Z")
        );
        when(userRepository.findByEmail("demo@example.com"))
                .thenReturn(Optional.of(user));
        when(user.getId()).thenReturn(userId);
        when(transactionEntryRepository
                .findByAccountUserIdOrderByCreatedAtDesc(userId))
                .thenReturn(List.of(entry));

        List<TransactionEntryResponse> result =
                transactionHistoryService.findAuthenticatedUserHistory(
                        "demo@example.com"
                );

        assertThat(result).singleElement().satisfies(response -> {
            assertThat(response.id()).isEqualTo(entryId);
            assertThat(response.account()).isEqualTo(accountId);
            assertThat(response.transactionType())
                    .isEqualTo(TransactionType.CASH_IN);
            assertThat(response.direction())
                    .isEqualTo(TransactionDirection.CREDIT);
            assertThat(response.amount()).isEqualByComparingTo("125.50");
            assertThat(response.currency()).isEqualTo("PHP");
            assertThat(response.balanceAfter())
                    .isEqualByComparingTo("125.50");
        });
        verify(transactionEntryRepository)
                .findByAccountUserIdOrderByCreatedAtDesc(userId);
    }

    @Test
    void reportsMissingAuthenticatedUser() {
        when(userRepository.findByEmail("missing@example.com"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                transactionHistoryService.findAuthenticatedUserHistory(
                        "missing@example.com"
                )
        )
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining("missing@example.com");
    }
}
