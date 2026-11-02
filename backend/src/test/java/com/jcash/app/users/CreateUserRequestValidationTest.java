package com.jcash.app.users;

import com.jcash.app.users.dto.CreateUserRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CreateUserRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void acceptsWellFormedEmails() {
        for (String email : new String[] {"demo@example.com", "juan.dela-cruz+jcash@mail.example.ph"}) {
            assertThat(validateEmail(email)).as("email %s", email).isEmpty();
        }
    }

    @Test
    void rejectsMissingOrMalformedEmails() {
        for (String email : new String[] {
                null,
                "",
                "demo",
                "demo@",
                "@example.com",
                "demo@example",
                "demo@@example.com",
                "demo @example.com"
        }) {
            assertThat(validateEmail(email))
                    .as("email %s", email)
                    .isNotEmpty();
        }
    }

    @Test
    void acceptsLocalAndInternationalMobileNumbers() {
        for (String mobileNumber : new String[] {"09171234567", "+639171234567"}) {
            assertThat(validate(mobileNumber, "123456")).isEmpty();
        }
    }

    @Test
    void rejectsMissingOrMalformedMobileNumbers() {
        for (String mobileNumber : new String[] {
                null,
                "",
                "9171234567",
                "0917123456",
                "091712345678",
                "08171234567",
                "639171234567",
                "+63917123456",
                "0917-123-4567",
                "0917123456a"
        }) {
            assertThat(validate(mobileNumber, "123456"))
                    .as("mobile number %s", mobileNumber)
                    .isNotEmpty();
        }
    }

    @Test
    void rejectsPinsThatAreNotExactlySixDigits() {
        for (String pin : new String[] {
                null,
                "",
                "12345",
                "1234567",
                "12345a",
                "12 456",
                "１２３４５６"
        }) {
            assertThat(validate("09171234567", pin))
                    .as("pin %s", pin)
                    .isNotEmpty();
        }
    }

    private static Set<ConstraintViolation<CreateUserRequest>> validateEmail(String email) {
        return validator.validate(new CreateUserRequest(email, "09171234567", "Demo User", "123456"));
    }

    private static Set<ConstraintViolation<CreateUserRequest>> validate(
            String mobileNumber,
            String pin) {
        return validator.validate(new CreateUserRequest(
                "demo@example.com",
                mobileNumber,
                "Demo User",
                pin
        ));
    }
}
