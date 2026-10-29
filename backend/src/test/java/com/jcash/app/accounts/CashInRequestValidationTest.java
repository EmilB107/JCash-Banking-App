package com.jcash.app.accounts;

import com.jcash.app.accounts.dto.CashInRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CashInRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void rejectsNullZeroAndNegativeAmounts() {
        for (BigDecimal amount : new BigDecimal[] {
                null,
                BigDecimal.ZERO,
                new BigDecimal("-0.01")
        }) {
            Set<ConstraintViolation<CashInRequest>> violations =
                    validator.validate(new CashInRequest(amount));

            assertThat(violations).isNotEmpty();
        }
    }

    @Test
    void acceptsPositiveBigDecimalAmount() {
        Set<ConstraintViolation<CashInRequest>> violations =
                validator.validate(new CashInRequest(new BigDecimal("10.00")));

        assertThat(violations).isEmpty();
    }
}
