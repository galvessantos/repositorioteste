package com.montreal.oauth.domain.dto;

import com.montreal.oauth.domain.dto.request.PasswordResetRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PasswordResetRequestUnitTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void builder_CreatesValidPasswordResetRequest() {
        // Act
        PasswordResetRequest request = PasswordResetRequest.builder()
                .token("test-token-123")
                .newPassword("Test@123")
                .confirmPassword("Test@123")
                .build();

        // Assert
        assertNotNull(request);
        assertEquals("test-token-123", request.getToken());
        assertEquals("Test@123", request.getNewPassword());
        assertEquals("Test@123", request.getConfirmPassword());
    }

    @Test
    void noArgsConstructor_CreatesEmptyRequest() {
        // Act
        PasswordResetRequest request = new PasswordResetRequest();

        // Assert
        assertNotNull(request);
        assertNull(request.getToken());
        assertNull(request.getNewPassword());
        assertNull(request.getConfirmPassword());
    }

    @Test
    void allArgsConstructor_CreatesRequestWithAllFields() {
        // Act
        PasswordResetRequest request = new PasswordResetRequest(
                "test-token", "Test@123", "Test@123"
        );

        // Assert
        assertNotNull(request);
        assertEquals("test-token", request.getToken());
        assertEquals("Test@123", request.getNewPassword());
        assertEquals("Test@123", request.getConfirmPassword());
    }

    @Test
    void settersAndGetters_WorkCorrectly() {
        // Arrange
        PasswordResetRequest request = new PasswordResetRequest();

        // Act
        request.setToken("new-token");
        request.setNewPassword("New@456");
        request.setConfirmPassword("New@456");

        // Assert
        assertEquals("new-token", request.getToken());
        assertEquals("New@456", request.getNewPassword());
        assertEquals("New@456", request.getConfirmPassword());
    }

    @Test
    void validation_WithValidData_PassesValidation() {
        // Arrange
        PasswordResetRequest request = PasswordResetRequest.builder()
                .token("valid-token")
                .newPassword("Test@123")
                .confirmPassword("Test@123")
                .build();

        // Act
        Set<ConstraintViolation<PasswordResetRequest>> violations = validator.validate(request);

        // Assert
        assertTrue(violations.isEmpty());
    }

    @Test
    void validation_WithBlankToken_FailsValidation() {
        // Arrange
        PasswordResetRequest request = PasswordResetRequest.builder()
                .token("")
                .newPassword("Test@123")
                .confirmPassword("Test@123")
                .build();

        // Act
        Set<ConstraintViolation<PasswordResetRequest>> violations = validator.validate(request);

        // Assert
        assertEquals(1, violations.size());
        assertEquals("Token é obrigatório", violations.iterator().next().getMessage());
    }

    @Test
    void validation_WithNullToken_FailsValidation() {
        // Arrange
        PasswordResetRequest request = PasswordResetRequest.builder()
                .token(null)
                .newPassword("Test@123")
                .confirmPassword("Test@123")
                .build();

        // Act
        Set<ConstraintViolation<PasswordResetRequest>> violations = validator.validate(request);

        // Assert
        assertEquals(1, violations.size());
        assertEquals("Token é obrigatório", violations.iterator().next().getMessage());
    }

    @Test
    void validation_WithBlankNewPassword_FailsValidation() {
        // Arrange
        PasswordResetRequest request = PasswordResetRequest.builder()
                .token("valid-token")
                .newPassword("")
                .confirmPassword("Test@123")
                .build();

        // Act
        Set<ConstraintViolation<PasswordResetRequest>> violations = validator.validate(request);

        // Assert
        assertEquals(1, violations.size());
        assertEquals("Nova senha é obrigatória", violations.iterator().next().getMessage());
    }

    @Test
    void validation_WithNullNewPassword_FailsValidation() {
        // Arrange
        PasswordResetRequest request = PasswordResetRequest.builder()
                .token("valid-token")
                .newPassword(null)
                .confirmPassword("Test@123")
                .build();

        // Act
        Set<ConstraintViolation<PasswordResetRequest>> violations = validator.validate(request);

        // Assert
        assertEquals(1, violations.size());
        assertEquals("Nova senha é obrigatória", violations.iterator().next().getMessage());
    }

    @Test
    void validation_WithBlankConfirmPassword_FailsValidation() {
        // Arrange
        PasswordResetRequest request = PasswordResetRequest.builder()
                .token("valid-token")
                .newPassword("Test@123")
                .confirmPassword("")
                .build();

        // Act
        Set<ConstraintViolation<PasswordResetRequest>> violations = validator.validate(request);

        // Assert
        assertEquals(1, violations.size());
        assertEquals("Confirmação de senha é obrigatória", violations.iterator().next().getMessage());
    }

    @Test
    void validation_WithNullConfirmPassword_FailsValidation() {
        // Arrange
        PasswordResetRequest request = PasswordResetRequest.builder()
                .token("valid-token")
                .newPassword("Test@123")
                .confirmPassword(null)
                .build();

        // Act
        Set<ConstraintViolation<PasswordResetRequest>> violations = validator.validate(request);

        // Assert
        assertEquals(1, violations.size());
        assertEquals("Confirmação de senha é obrigatória", violations.iterator().next().getMessage());
    }

    @Test
    void validation_WithNewPasswordTooShort_FailsValidation() {
        // Arrange
        PasswordResetRequest request = PasswordResetRequest.builder()
                .token("valid-token")
                .newPassword("Ab@1")
                .confirmPassword("Ab@1")
                .build();

        // Act
        Set<ConstraintViolation<PasswordResetRequest>> violations = validator.validate(request);

        // Assert
        assertEquals(1, violations.size());
        assertEquals("A senha deve ter entre 4 e 8 caracteres", violations.iterator().next().getMessage());
    }

    @Test
    void validation_WithNewPasswordTooLong_FailsValidation() {
        // Arrange
        PasswordResetRequest request = PasswordResetRequest.builder()
                .token("valid-token")
                .newPassword("Test@123456")
                .confirmPassword("Test@123456")
                .build();

        // Act
        Set<ConstraintViolation<PasswordResetRequest>> violations = validator.validate(request);

        // Assert
        assertEquals(1, violations.size());
        assertEquals("A senha deve ter entre 4 e 8 caracteres", violations.iterator().next().getMessage());
    }

    @Test
    void validation_WithNewPasswordAtMinimumLength_PassesValidation() {
        // Arrange
        PasswordResetRequest request = PasswordResetRequest.builder()
                .token("valid-token")
                .newPassword("Ab@1")
                .confirmPassword("Ab@1")
                .build();

        // Act
        Set<ConstraintViolation<PasswordResetRequest>> violations = validator.validate(request);

        // Assert
        assertTrue(violations.isEmpty());
    }

    @Test
    void validation_WithNewPasswordAtMaximumLength_PassesValidation() {
        // Arrange
        PasswordResetRequest request = PasswordResetRequest.builder()
                .token("valid-token")
                .newPassword("Test@123")
                .confirmPassword("Test@123")
                .build();

        // Act
        Set<ConstraintViolation<PasswordResetRequest>> violations = validator.validate(request);

        // Assert
        assertTrue(violations.isEmpty());
    }

    @Test
    void validation_WithMultipleViolations_ReturnsAllViolations() {
        // Arrange
        PasswordResetRequest request = PasswordResetRequest.builder()
                .token("")
                .newPassword("")
                .confirmPassword("")
                .build();

        // Act
        Set<ConstraintViolation<PasswordResetRequest>> violations = validator.validate(request);

        // Assert
        assertEquals(3, violations.size());
        assertTrue(violations.stream().anyMatch(v -> "Token é obrigatório".equals(v.getMessage())));
        assertTrue(violations.stream().anyMatch(v -> "Nova senha é obrigatória".equals(v.getMessage())));
        assertTrue(violations.stream().anyMatch(v -> "Confirmação de senha é obrigatória".equals(v.getMessage())));
    }

    @Test
    void equals_WithSameData_ReturnsTrue() {
        // Arrange
        PasswordResetRequest request1 = PasswordResetRequest.builder()
                .token("test-token")
                .newPassword("Test@123")
                .confirmPassword("Test@123")
                .build();

        PasswordResetRequest request2 = PasswordResetRequest.builder()
                .token("test-token")
                .newPassword("Test@123")
                .confirmPassword("Test@123")
                .build();

        // Act & Assert
        assertEquals(request1, request2);
    }

    @Test
    void equals_WithDifferentData_ReturnsFalse() {
        // Arrange
        PasswordResetRequest request1 = PasswordResetRequest.builder()
                .token("test-token")
                .newPassword("Test@123")
                .confirmPassword("Test@123")
                .build();

        PasswordResetRequest request2 = PasswordResetRequest.builder()
                .token("different-token")
                .newPassword("Test@123")
                .confirmPassword("Test@123")
                .build();

        // Act & Assert
        assertNotEquals(request1, request2);
    }

    @Test
    void hashCode_WithSameData_ReturnsSameHash() {
        // Arrange
        PasswordResetRequest request1 = PasswordResetRequest.builder()
                .token("test-token")
                .newPassword("Test@123")
                .confirmPassword("Test@123")
                .build();

        PasswordResetRequest request2 = PasswordResetRequest.builder()
                .token("test-token")
                .newPassword("Test@123")
                .confirmPassword("Test@123")
                .build();

        // Act & Assert
        assertEquals(request1.hashCode(), request2.hashCode());
    }

    @Test
    void toString_ContainsRequestInformation() {
        // Arrange
        PasswordResetRequest request = PasswordResetRequest.builder()
                .token("test-token")
                .newPassword("Test@123")
                .confirmPassword("Test@123")
                .build();

        // Act
        String toString = request.toString();

        // Assert
        assertNotNull(toString);
        assertTrue(toString.contains("PasswordResetRequest"));
    }
}