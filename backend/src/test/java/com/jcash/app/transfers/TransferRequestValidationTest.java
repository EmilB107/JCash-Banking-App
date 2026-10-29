package com.jcash.app.transfers;

import com.jcash.app.transfers.dto.TransferRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TransferRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void rejectsMissingOrInvalidRecipientEmail() {
        for (String recipientEmail : new String[] {
                null,
                "",
                "   ",
                "not-an-email"
        }) {
            Set<ConstraintViolation<TransferRequest>> violations =
                    validator.validate(new TransferRequest(
                            recipientEmail,
                            new BigDecimal("10.00")
                    ));

            assertThat(violations).isNotEmpty();
        }
    }

    @Test
    void rejectsNullZeroAndNegativeAmounts() {
        for (BigDecimal amount : new BigDecimal[] {
                null,
                BigDecimal.ZERO,
                new BigDecimal("-0.01")
        }) {
            Set<ConstraintViolation<TransferRequest>> violations =
                    validator.validate(new TransferRequest(
                            "recipient@example.com",
                            amount
                    ));

            assertThat(violations).isNotEmpty();
        }
    }

    @Test
    void rejectsAmountsThatDoNotFitTheDatabaseScale() {
        for (BigDecimal amount : new BigDecimal[] {
                new BigDecimal("1.001"),
                new BigDecimal("123456789012345678.00")
        }) {
            Set<ConstraintViolation<TransferRequest>> violations =
                    validator.validate(new TransferRequest(
                            "recipient@example.com",
                            amount
                    ));

            assertThat(violations).isNotEmpty();
        }
    }

    @Test
    void acceptsValidRecipientAndAmount() {
        Set<ConstraintViolation<TransferRequest>> violations =
                validator.validate(new TransferRequest(
                        "recipient@example.com",
                        new BigDecimal("10.00")
                ));

        assertThat(violations).isEmpty();
    }
}
