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
    void rejectsMissingNonPositiveTooLargeAndOverPreciseAmounts() {
        for (BigDecimal amount : new BigDecimal[] {
                null,
                BigDecimal.ZERO,
                new BigDecimal("-0.01"),
                new BigDecimal("100000.01"),
                new BigDecimal("1000000"),
                new BigDecimal("10.555")
        }) {
            Set<ConstraintViolation<CashInRequest>> violations =
                    validator.validate(new CashInRequest(amount));

            assertThat(violations).isNotEmpty();
        }
    }

    @Test
    void acceptsPositiveAmountsUpToTheLimit() {
        for (String amount : new String[] {"0.01", "10.00", "10.5", "100000.00"}) {
            Set<ConstraintViolation<CashInRequest>> violations =
                    validator.validate(new CashInRequest(new BigDecimal(amount)));

            assertThat(violations).as("amount %s", amount).isEmpty();
        }
    }
}
