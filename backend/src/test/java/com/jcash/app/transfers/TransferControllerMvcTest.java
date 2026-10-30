package com.jcash.app.transfers;

import com.jcash.app.security.SecurityConfiguration;
import com.jcash.app.transfers.dto.TransferRequest;
import com.jcash.app.transfers.dto.TransferResponse;
import com.jcash.app.transfers.exception.IdempotencyKeyConflictException;
import com.jcash.app.transfers.exception.InsufficientFundsException;
import com.jcash.app.transfers.exception.InvalidIdempotencyKeyException;
import com.jcash.app.transfers.exception.RecipientNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TransferController.class)
@Import(SecurityConfiguration.class)
class TransferControllerMvcTest {

    private static final String SENDER = "sender@example.com";
    private static final String VALID_BODY =
            "{\"recipient\":\"recipient@example.com\",\"amount\":25.00}";
    private static final TransferRequest VALID_REQUEST =
            new TransferRequest("recipient@example.com", new BigDecimal("25.00"));

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TransferService transferService;

    @Test
    void unauthenticatedRequestIsRejectedWith401() throws Exception {
        mockMvc.perform(post("/api/transfers")
                        .with(csrf())
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHENTICATED"));

        verifyNoInteractions(transferService);
    }

    @Test
    void requestWithoutCsrfTokenIsRejectedWith403() throws Exception {
        mockMvc.perform(post("/api/transfers")
                        .with(user(SENDER))
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isForbidden());

        verifyNoInteractions(transferService);
    }

    @Test
    void successReturnsTransferDtoAndIgnoresClientSuppliedSender() throws Exception {
        UUID senderAccountId = UUID.randomUUID();
        UUID recipientAccountId = UUID.randomUUID();
        when(transferService.transfer(SENDER, "key-1", VALID_REQUEST))
                .thenReturn(new TransferResponse(
                        senderAccountId,
                        recipientAccountId,
                        "recipient@example.com",
                        new BigDecimal("25.00"),
                        "PHP",
                        new BigDecimal("75.00"),
                        new BigDecimal("125.00")
                ));

        mockMvc.perform(authenticatedTransfer("key-1")
                        .content("{\"senderEmail\":\"attacker@example.com\","
                                + "\"recipient\":\"recipient@example.com\",\"amount\":25.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.*", hasSize(7)))
                .andExpect(jsonPath("$.senderAccountId").value(senderAccountId.toString()))
                .andExpect(jsonPath("$.recipientAccountId").value(recipientAccountId.toString()))
                .andExpect(jsonPath("$.recipientEmail").value("recipient@example.com"))
                .andExpect(jsonPath("$.amount").value(25.00))
                .andExpect(jsonPath("$.currency").value("PHP"))
                .andExpect(jsonPath("$.senderBalanceAfter").value(75.00))
                .andExpect(jsonPath("$.recipientBalanceAfter").value(125.00));

        verify(transferService).transfer(SENDER, "key-1", VALID_REQUEST);
    }

    @Test
    void missingIdempotencyKeyReturns400() throws Exception {
        when(transferService.transfer(eq(SENDER), isNull(), any()))
                .thenThrow(new InvalidIdempotencyKeyException());

        mockMvc.perform(authenticatedTransfer(null).content(VALID_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_IDEMPOTENCY_KEY"));
    }

    @Test
    void blankIdempotencyKeyReturns400() throws Exception {
        when(transferService.transfer(eq(SENDER), eq("   "), any()))
                .thenThrow(new InvalidIdempotencyKeyException());

        mockMvc.perform(authenticatedTransfer("   ").content(VALID_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_IDEMPOTENCY_KEY"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"recipient\":\"recipient@example.com\"}",
            "{\"recipient\":\"recipient@example.com\",\"amount\":0}",
            "{\"recipient\":\"recipient@example.com\",\"amount\":-5.00}",
            "{\"recipient\":\"recipient@example.com\",\"amount\":1.001}",
            "{\"amount\":25.00}",
            "{\"recipient\":\"\",\"amount\":25.00}",
            "{\"recipient\":\"  \",\"amount\":25.00}"
    })
    void invalidBodyReturnsValidationError(String body) throws Exception {
        mockMvc.perform(authenticatedTransfer("key-1").content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));

        verifyNoInteractions(transferService);
    }

    @Test
    void malformedJsonReturns400() throws Exception {
        mockMvc.perform(authenticatedTransfer("key-1").content("{\"amount\":"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(transferService);
    }

    @Test
    void idempotencyConflictReturns409() throws Exception {
        when(transferService.transfer(SENDER, "key-1", VALID_REQUEST))
                .thenThrow(new IdempotencyKeyConflictException());

        mockMvc.perform(authenticatedTransfer("key-1").content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("IDEMPOTENCY_KEY_CONFLICT"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void optimisticLockConflictReturns409() throws Exception {
        when(transferService.transfer(SENDER, "key-1", VALID_REQUEST))
                .thenThrow(new ObjectOptimisticLockingFailureException(
                        "Account", UUID.randomUUID()));

        mockMvc.perform(authenticatedTransfer("key-1").content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("OPTIMISTIC_LOCK_CONFLICT"));
    }

    @Test
    void lockAcquisitionFailureReturns409() throws Exception {
        when(transferService.transfer(SENDER, "key-1", VALID_REQUEST))
                .thenThrow(new CannotAcquireLockException("deadlock detected"));

        mockMvc.perform(authenticatedTransfer("key-1").content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("LOCK_CONFLICT"));
    }

    @Test
    void mobileNumberRecipientIsPassedToService() throws Exception {
        TransferRequest mobileRequest =
                new TransferRequest("+639171234567", new BigDecimal("25.00"));
        when(transferService.transfer(SENDER, "key-1", mobileRequest))
                .thenReturn(new TransferResponse(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "recipient@example.com",
                        new BigDecimal("25.00"),
                        "PHP",
                        new BigDecimal("75.00"),
                        new BigDecimal("125.00")
                ));

        mockMvc.perform(authenticatedTransfer("key-1")
                        .content("{\"recipient\":\"+639171234567\",\"amount\":25.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipientEmail").value("recipient@example.com"));

        verify(transferService).transfer(SENDER, "key-1", mobileRequest);
    }

    @Test
    void unknownRecipientReturns404() throws Exception {
        when(transferService.transfer(SENDER, "key-1", VALID_REQUEST))
                .thenThrow(new RecipientNotFoundException("recipient@example.com"));

        mockMvc.perform(authenticatedTransfer("key-1").content(VALID_BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void insufficientFundsReturns400() throws Exception {
        when(transferService.transfer(SENDER, "key-1", VALID_REQUEST))
                .thenThrow(new InsufficientFundsException());

        mockMvc.perform(authenticatedTransfer("key-1").content(VALID_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INSUFFICIENT_FUNDS"));
    }

    private MockHttpServletRequestBuilder authenticatedTransfer(String idempotencyKey) {
        MockHttpServletRequestBuilder request = post("/api/transfers")
                .with(user(SENDER))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON);
        if (idempotencyKey != null) {
            request.header("Idempotency-Key", idempotencyKey);
        }
        return request;
    }
}
