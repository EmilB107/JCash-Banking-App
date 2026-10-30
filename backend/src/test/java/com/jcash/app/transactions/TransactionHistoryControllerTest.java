package com.jcash.app.transactions;

import com.jcash.app.transactions.dto.TransactionEntryResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionHistoryControllerTest {

    @Mock
    private TransactionHistoryService transactionHistoryService;

    @Mock
    private Authentication authentication;

    @Test
    void currentUserHistoryUsesAuthenticatedIdentityAndReturnsDtos() {
        TransactionEntryResponse response = new TransactionEntryResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                TransactionType.CASH_IN,
                TransactionDirection.CREDIT,
                new BigDecimal("10.00"),
                "PHP",
                new BigDecimal("10.00"),
                "Cash in",
                null,
                Instant.parse("2025-01-01T00:00:00Z")
        );
        when(authentication.getName()).thenReturn("demo@example.com");
        when(transactionHistoryService.findAuthenticatedUserHistory(
                "demo@example.com"
        )).thenReturn(List.of(response));

        TransactionHistoryController controller =
                new TransactionHistoryController(transactionHistoryService);

        assertThat(controller.currentUserHistory(authentication))
                .containsExactly(response);
        verify(transactionHistoryService)
                .findAuthenticatedUserHistory("demo@example.com");
    }
}
