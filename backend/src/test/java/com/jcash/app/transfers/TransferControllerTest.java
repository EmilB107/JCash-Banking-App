package com.jcash.app.transfers;

import com.jcash.app.transfers.dto.TransferRequest;
import com.jcash.app.transfers.dto.TransferResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransferControllerTest {

    @Mock
    private TransferService transferService;

    @Mock
    private Authentication authentication;

    @Test
    void transferUsesAuthenticatedSenderIdentityAndReturnsDto() {
        TransferRequest request = new TransferRequest(
                "recipient@example.com",
                new BigDecimal("25.00")
        );
        TransferResponse response = new TransferResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "recipient@example.com",
                new BigDecimal("25.00"),
                "PHP",
                new BigDecimal("75.00"),
                new BigDecimal("125.00")
        );

        when(authentication.getName()).thenReturn("sender@example.com");
        when(transferService.transfer("sender@example.com", "idem-123", request))
                .thenReturn(response);

        TransferController controller = new TransferController(transferService);

        TransferResponse result = controller.transfer(
                authentication,
                "idem-123",
                request
        );

        assertThat(result).isEqualTo(response);
        verify(transferService).transfer("sender@example.com", "idem-123", request);
    }

    @Test
    void transferPassesAnOmittedIdempotencyHeaderToTheService() {
        TransferRequest request = new TransferRequest(
                "recipient@example.com",
                new BigDecimal("25.00")
        );
        TransferResponse response = new TransferResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "recipient@example.com",
                new BigDecimal("25.00"),
                "PHP",
                new BigDecimal("75.00"),
                new BigDecimal("125.00")
        );

        when(authentication.getName()).thenReturn("sender@example.com");
        when(transferService.transfer("sender@example.com", null, request))
                .thenReturn(response);

        TransferResponse result = new TransferController(transferService).transfer(
                authentication,
                null,
                request
        );

        assertThat(result).isEqualTo(response);
        verify(transferService).transfer("sender@example.com", null, request);
    }
}
