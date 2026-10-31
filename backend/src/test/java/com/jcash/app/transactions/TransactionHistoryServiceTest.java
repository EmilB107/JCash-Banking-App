package com.jcash.app.transactions;

import com.jcash.app.accounts.Account;
import com.jcash.app.accounts.AccountRepository;
import com.jcash.app.transactions.dto.TransactionHistoryPage;
import com.jcash.app.transactions.exception.InvalidPageRequestException;
import com.jcash.app.users.User;
import com.jcash.app.users.exception.UserNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionHistoryServiceTest {

    @Mock
    private TransactionEntryRepository transactionEntryRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private User user;

    private TransactionHistoryService transactionHistoryService;

    @BeforeEach
    void setUp() {
        transactionHistoryService = new TransactionHistoryService(
                transactionEntryRepository,
                accountRepository
        );
    }

    @Test
    void returnsOnlyEntriesForTheAuthenticatedUserAsDtos() {
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
                new BigDecimal("125.50"),
                "Cash in"
        );
        ReflectionTestUtils.setField(entry, "id", entryId);
        ReflectionTestUtils.setField(
                entry,
                "createdAt",
                Instant.parse("2025-01-01T00:00:00Z")
        );
        when(accountRepository.findIdByUserEmail("demo@example.com"))
                .thenReturn(Optional.of(accountId));
        PageRequest pageRequest = PageRequest.of(1, 5, Sort.by(
                Sort.Order.desc("createdAt"),
                Sort.Order.desc("id")
        ));
        when(transactionEntryRepository
                .findByAccountId(accountId, pageRequest))
                .thenReturn(new PageImpl<>(List.of(entry), pageRequest, 6));

        TransactionHistoryPage result =
                transactionHistoryService.findAuthenticatedUserHistory(
                        "demo@example.com", 1, 5
                );

        assertThat(result.page()).isEqualTo(1);
        assertThat(result.size()).isEqualTo(5);
        assertThat(result.totalElements()).isEqualTo(6);
        assertThat(result.totalPages()).isEqualTo(2);
        assertThat(result.content()).singleElement().satisfies(response -> {
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
            assertThat(response.details()).isEqualTo("Cash in");
            assertThat(response.transferReference()).isNull();
        });
        verify(transactionEntryRepository)
                .findByAccountId(accountId, pageRequest);
    }

    @ParameterizedTest
    @CsvSource({"-1, 20", "0, 0", "0, 101"})
    void rejectsInvalidPageOrSize(int page, int size) {
        assertThatThrownBy(() ->
                transactionHistoryService.findAuthenticatedUserHistory(
                        "demo@example.com", page, size
                )
        ).isInstanceOf(InvalidPageRequestException.class);
        verifyNoInteractions(accountRepository, transactionEntryRepository);
    }

    @Test
    void reportsMissingAuthenticatedUser() {
        when(accountRepository.findIdByUserEmail("missing@example.com"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                transactionHistoryService.findAuthenticatedUserHistory(
                        "missing@example.com", 0, 20
                )
        )
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining("missing@example.com");
    }
}
