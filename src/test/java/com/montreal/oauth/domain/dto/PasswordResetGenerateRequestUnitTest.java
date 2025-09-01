package com.montreal.oauth.domain.dto;

import com.montreal.oauth.domain.dto.request.PasswordResetGenerateRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PasswordResetGenerateRequestUnitTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void builder_CreatesValidPasswordResetGenerateRequest() {
        // Act
        PasswordResetGenerateRequest request = PasswordResetGenerateRequest.builder()
                .login("testuser")
                .build();

        // Assert
        assertNotNull(request);
        assertEquals("testuser", request.getLogin());
    }

    @Test
    void noArgsConstructor_CreatesEmptyRequest() {
        // Act
        PasswordResetGenerateRequest request = new PasswordResetGenerateRequest();

        // Assert
        assertNotNull(request);
        assertNull(request.getLogin());
    }

    @Test
    void allArgsConstructor_CreatesRequestWithAllFields() {
        // Act
        PasswordResetGenerateRequest request = new PasswordResetGenerateRequest("testuser");

        // Assert
        assertNotNull(request);
        assertEquals("testuser", request.getLogin());
    }

    @Test
    void settersAndGetters_WorkCorrectly() {
        // Arrange
        PasswordResetGenerateRequest request = new PasswordResetGenerateRequest();

        // Act
        request.setLogin("newuser");

        // Assert
        assertEquals("newuser", request.getLogin());
    }

    @Test
    void validation_WithValidLogin_PassesValidation() {
        // Arrange
        PasswordResetGenerateRequest request = PasswordResetGenerateRequest.builder()
                .login("testuser")
                .build();

        // Act
        Set<ConstraintViolation<PasswordResetGenerateRequest>> violations = validator.validate(request);

        // Assert
        assertTrue(violations.isEmpty());
    }

    @Test
    void validation_WithBlankLogin_FailsValidation() {
        // Arrange
        PasswordResetGenerateRequest request = PasswordResetGenerateRequest.builder()
                .login("")
                .build();

        // Act
        Set<ConstraintViolation<PasswordResetGenerateRequest>> violations = validator.validate(request);

        // Assert
        assertEquals(1, violations.size());
        assertEquals("Login is required", violations.iterator().next().getMessage());
    }

    @Test
    void validation_WithNullLogin_FailsValidation() {
        // Arrange
        PasswordResetGenerateRequest request = PasswordResetGenerateRequest.builder()
                .login(null)
                .build();

        // Act
        Set<ConstraintViolation<PasswordResetGenerateRequest>> violations = validator.validate(request);

        // Assert
        assertEquals(1, violations.size());
        assertEquals("Login is required", violations.iterator().next().getMessage());
    }

    @Test
    void validation_WithLoginTooShort_FailsValidation() {
        // Arrange
        PasswordResetGenerateRequest request = PasswordResetGenerateRequest.builder()
                .login("ab")
                .build();

        // Act
        Set<ConstraintViolation<PasswordResetGenerateRequest>> violations = validator.validate(request);

        // Assert
        assertEquals(1, violations.size());
        assertEquals("Login must be between 3 and 50 characters", violations.iterator().next().getMessage());
    }

    @Test
    void validation_WithLoginTooLong_FailsValidation() {
        // Arrange
        String longLogin = "a".repeat(51);
        PasswordResetGenerateRequest request = PasswordResetGenerateRequest.builder()
                .login(longLogin)
                .build();

        // Act
        Set<ConstraintViolation<PasswordResetGenerateRequest>> violations = validator.validate(request);

        // Assert
        assertEquals(1, violations.size());
        assertEquals("Login must be between 3 and 50 characters", violations.iterator().next().getMessage());
    }

    @Test
    void validation_WithLoginAtMinimumLength_PassesValidation() {
        // Arrange
        PasswordResetGenerateRequest request = PasswordResetGenerateRequest.builder()
                .login("abc")
                .build();

        // Act
        Set<ConstraintViolation<PasswordResetGenerateRequest>> violations = validator.validate(request);

        // Assert
        assertTrue(violations.isEmpty());
    }

    @Test
    void validation_WithLoginAtMaximumLength_PassesValidation() {
        // Arrange
        String maxLengthLogin = "a".repeat(50);
        PasswordResetGenerateRequest request = PasswordResetGenerateRequest.builder()
                .login(maxLengthLogin)
                .build();

        // Act
        Set<ConstraintViolation<PasswordResetGenerateRequest>> violations = validator.validate(request);

        // Assert
        assertTrue(violations.isEmpty());
    }

    @Test
    void validation_WithLoginContainingSpecialCharacters_PassesValidation() {
        // Arrange
        PasswordResetGenerateRequest request = PasswordResetGenerateRequest.builder()
                .login("test_user@domain.com")
                .build();

        // Act
        Set<ConstraintViolation<PasswordResetGenerateRequest>> violations = validator.validate(request);

        // Assert
        assertTrue(violations.isEmpty());
    }

    @Test
    void validation_WithLoginContainingNumbers_PassesValidation() {
        // Arrange
        PasswordResetGenerateRequest request = PasswordResetGenerateRequest.builder()
                .login("user123")
                .build();

        // Act
        Set<ConstraintViolation<PasswordResetGenerateRequest>> violations = validator.validate(request);

        // Assert
        assertTrue(violations.isEmpty());
    }

    @Test
    void validation_WithLoginContainingSpaces_PassesValidation() {
        // Arrange
        PasswordResetGenerateRequest request = PasswordResetGenerateRequest.builder()
                .login("test user")
                .build();

        // Act
        Set<ConstraintViolation<PasswordResetGenerateRequest>> violations = validator.validate(request);

        // Assert
        assertTrue(violations.isEmpty());
    }

    @Test
    void validation_WithLoginContainingOnlySpaces_FailsValidation() {
        // Arrange
        PasswordResetGenerateRequest request = PasswordResetGenerateRequest.builder()
                .login("   ")
                .build();

        // Act
        Set<ConstraintViolation<PasswordResetGenerateRequest>> violations = validator.validate(request);

        // Assert
        assertEquals(1, violations.size());
        assertEquals("Login is required", violations.iterator().next().getMessage());
    }

    @Test
    void equals_WithSameLogin_ReturnsTrue() {
        // Arrange
        PasswordResetGenerateRequest request1 = PasswordResetGenerateRequest.builder()
                .login("testuser")
                .build();

        PasswordResetGenerateRequest request2 = PasswordResetGenerateRequest.builder()
                .login("testuser")
                .build();

        // Act & Assert
        assertEquals(request1, request2);
    }

    @Test
    void equals_WithDifferentLogin_ReturnsFalse() {
        // Arrange
        PasswordResetGenerateRequest request1 = PasswordResetGenerateRequest.builder()
                .login("testuser")
                .build();

        PasswordResetGenerateRequest request2 = PasswordResetGenerateRequest.builder()
                .login("differentuser")
                .build();

        // Act & Assert
        assertNotEquals(request1, request2);
    }

    @Test
    void equals_WithNullLogin_ReturnsFalse() {
        // Arrange
        PasswordResetGenerateRequest request1 = PasswordResetGenerateRequest.builder()
                .login("testuser")
                .build();

        PasswordResetGenerateRequest request2 = PasswordResetGenerateRequest.builder()
                .login(null)
                .build();

        // Act & Assert
        assertNotEquals(request1, request2);
    }

    @Test
    void equals_WithSameObject_ReturnsTrue() {
        // Arrange
        PasswordResetGenerateRequest request = PasswordResetGenerateRequest.builder()
                .login("testuser")
                .build();

        // Act & Assert
        assertEquals(request, request);
    }

    @Test
    void equals_WithDifferentClass_ReturnsFalse() {
        // Arrange
        PasswordResetGenerateRequest request = PasswordResetGenerateRequest.builder()
                .login("testuser")
                .build();

        String differentObject = "not a PasswordResetGenerateRequest";

        // Act & Assert
        assertNotEquals(request, differentObject);
    }

    @Test
    void hashCode_WithSameLogin_ReturnsSameHash() {
        // Arrange
        PasswordResetGenerateRequest request1 = PasswordResetGenerateRequest.builder()
                .login("testuser")
                .build();

        PasswordResetGenerateRequest request2 = PasswordResetGenerateRequest.builder()
                .login("testuser")
                .build();

        // Act & Assert
        assertEquals(request1.hashCode(), request2.hashCode());
    }

    @Test
    void hashCode_WithNullLogin_DoesNotThrowException() {
        // Arrange
        PasswordResetGenerateRequest request = new PasswordResetGenerateRequest();
        request.setLogin(null);

        // Act & Assert
        assertDoesNotThrow(() -> {
            request.hashCode();
        });
    }

    @Test
    void toString_ContainsRequestInformation() {
        // Arrange
        PasswordResetGenerateRequest request = PasswordResetGenerateRequest.builder()
                .login("testuser")
                .build();

        // Act
        String toString = request.toString();

        // Assert
        assertNotNull(toString);
        assertTrue(toString.contains("PasswordResetGenerateRequest"));
    }

    @Test
    void toString_WithNullLogin_DoesNotThrowException() {
        // Arrange
        PasswordResetGenerateRequest request = new PasswordResetGenerateRequest();
        request.setLogin(null);

        // Act & Assert
        assertDoesNotThrow(() -> {
            request.toString();
        });
    }
}