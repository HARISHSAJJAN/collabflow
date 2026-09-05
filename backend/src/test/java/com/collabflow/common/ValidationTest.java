package com.collabflow.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.collabflow.auth.dto.RegisterRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * A pure unit test of Bean Validation constraints - no Spring context needed, since
 * {@code jakarta.validation}'s {@link Validator} works standalone. Covers {@code
 * RegisterRequest} as a representative example; every other request DTO in this codebase
 * (`CreateTeamRequest`, `CreateTaskRequest`, etc.) follows the identical `@NotBlank`/`@Size`/
 * `@Email` pattern and is exercised end-to-end through the integration tests instead of
 * repeating this same style of check per DTO.
 */
class ValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @Test
    void acceptsAValidRegisterRequest() {
        var request = new RegisterRequest("alice@example.com", "SuperSecret123", "Alice Anderson");
        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void rejectsAMalformedEmail() {
        var request = new RegisterRequest("not-an-email", "SuperSecret123", "Alice Anderson");
        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("email"));
    }

    @Test
    void rejectsATooShortPassword() {
        var request = new RegisterRequest("alice@example.com", "short", "Alice Anderson");
        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("password"));
    }

    @Test
    void rejectsABlankFullName() {
        var request = new RegisterRequest("alice@example.com", "SuperSecret123", "   ");
        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("fullName"));
    }

    @Test
    void reportsAllViolationsAtOnceRatherThanFailingFast() {
        var request = new RegisterRequest("not-an-email", "short", "");
        assertThat(validator.validate(request)).hasSize(3);
    }
}
