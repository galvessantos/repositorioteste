package com.montreal.oauth.controller;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.montreal.oauth.domain.dto.request.PasswordResetGenerateRequest;
import com.montreal.oauth.domain.dto.request.PasswordResetRequest;
import com.montreal.oauth.domain.dto.response.PasswordResetGenerateResponse;
import com.montreal.oauth.domain.dto.response.PasswordResetResponse;
import com.montreal.oauth.domain.dto.response.PasswordResetValidateResponse;
import com.montreal.oauth.domain.service.IPasswordResetService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;


import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class PasswordResetControllerUnitTest {


    @Mock
    private IPasswordResetService passwordResetService;


    @InjectMocks
    private PasswordResetController passwordResetController;


    private ObjectMapper objectMapper;


    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        reset(passwordResetService);
    }


    @Test
    void generatePasswordResetToken_ValidLogin_ReturnsSuccess() {
        String login = "test@example.com";
        String resetLink = "https://example.com/reset-password?token=uuid-123";
        PasswordResetGenerateRequest request = new PasswordResetGenerateRequest();
        request.setLogin(login);


        when(passwordResetService.generatePasswordResetToken(login)).thenReturn(resetLink);

        ResponseEntity<PasswordResetGenerateResponse> response = passwordResetController.generatePasswordResetToken(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Password reset token generated successfully", response.getBody().getMessage());
        assertEquals(resetLink, response.getBody().getResetLink());


        verify(passwordResetService).generatePasswordResetToken(login);
    }


    @Test
    void generatePasswordResetToken_InvalidLogin_ThrowsException() {
        String login = "invalid@example.com";
        PasswordResetGenerateRequest request = new PasswordResetGenerateRequest();
        request.setLogin(login);


        when(passwordResetService.generatePasswordResetToken(login))
                .thenThrow(new RuntimeException("User not found"));


        assertThrows(RuntimeException.class, () -> {
            passwordResetController.generatePasswordResetToken(request);
        });


        verify(passwordResetService).generatePasswordResetToken(login);
    }


    @Test
    void validatePasswordResetToken_ValidToken_ReturnsSuccess() {
        String token = "valid-token-123";
        when(passwordResetService.validatePasswordResetToken(token)).thenReturn(true);

        ResponseEntity<PasswordResetValidateResponse> response = passwordResetController.validatePasswordResetToken(token);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isValid());
        assertEquals("Token is valid", response.getBody().getMessage());


        verify(passwordResetService).validatePasswordResetToken(token);
    }


    @Test
    void validatePasswordResetToken_InvalidToken_ReturnsFalse() {
        String token = "invalid-token-123";
        when(passwordResetService.validatePasswordResetToken(token)).thenReturn(false);

        ResponseEntity<PasswordResetValidateResponse> response = passwordResetController.validatePasswordResetToken(token);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isValid());
        assertEquals("Token is invalid or expired", response.getBody().getMessage());


        verify(passwordResetService).validatePasswordResetToken(token);
    }


    @Test
    void cleanupExpiredTokens_Success() {
        ResponseEntity<String> response = passwordResetController.cleanupExpiredTokens();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Cleanup completed successfully", response.getBody());


        verify(passwordResetService).cleanupExpiredTokens();
    }


    @Test
    void cleanupExpiredTokens_ThrowsException() {
        doThrow(new RuntimeException("Database error"))
                .when(passwordResetService).cleanupExpiredTokens();


        assertThrows(RuntimeException.class, () -> {
            passwordResetController.cleanupExpiredTokens();
        });


        verify(passwordResetService).cleanupExpiredTokens();
    }

    @Test
    void resetPassword_ValidRequest_ReturnsSuccess() {
        String token = "valid-token-123";
        String newPassword = "Test@123";
        String confirmPassword = "Test@123";
        PasswordResetRequest request = PasswordResetRequest.builder()
                .token(token)
                .newPassword(newPassword)
                .confirmPassword(confirmPassword)
                .build();

        when(passwordResetService.resetPassword(token, newPassword, confirmPassword)).thenReturn(true);

        ResponseEntity<PasswordResetResponse> response = passwordResetController.resetPassword(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().isSuccess());
        assertEquals("Senha redefinida com sucesso", response.getBody().getMessage());
        verify(passwordResetService).resetPassword(token, newPassword, confirmPassword);
    }

    @Test
    void resetPassword_InvalidToken_ReturnsNotFound() {
        String token = "invalid-token";
        String newPassword = "Test@123";
        String confirmPassword = "Test@123";
        PasswordResetRequest request = PasswordResetRequest.builder()
                .token(token)
                .newPassword(newPassword)
                .confirmPassword(confirmPassword)
                .build();

        when(passwordResetService.resetPassword(token, newPassword, confirmPassword)).thenReturn(false);

        ResponseEntity<PasswordResetResponse> response = passwordResetController.resetPassword(request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertFalse(response.getBody().isSuccess());
        assertEquals("Token inválido ou expirado", response.getBody().getMessage());
        verify(passwordResetService).resetPassword(token, newPassword, confirmPassword);
    }

    @Test
    void resetPassword_InvalidPassword_ReturnsBadRequest() {
        String token = "valid-token";
        String invalidPassword = "weak";
        String confirmPassword = "weak";
        PasswordResetRequest request = PasswordResetRequest.builder()
                .token(token)
                .newPassword(invalidPassword)
                .confirmPassword(confirmPassword)
                .build();

        when(passwordResetService.resetPassword(token, invalidPassword, confirmPassword))
                .thenThrow(new IllegalArgumentException("A senha deve ter entre 4 e 8 caracteres"));

        ResponseEntity<PasswordResetResponse> response = passwordResetController.resetPassword(request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertFalse(response.getBody().isSuccess());
        assertEquals("A senha deve ter entre 4 e 8 caracteres", response.getBody().getMessage());
        verify(passwordResetService).resetPassword(token, invalidPassword, confirmPassword);
    }

    @Test
    void resetPassword_ServiceThrowsException_ReturnsInternalServerError() {
        String token = "valid-token";
        String newPassword = "Test@123";
        String confirmPassword = "Test@123";
        PasswordResetRequest request = PasswordResetRequest.builder()
                .token(token)
                .newPassword(newPassword)
                .confirmPassword(confirmPassword)
                .build();

        when(passwordResetService.resetPassword(token, newPassword, confirmPassword))
                .thenThrow(new RuntimeException("Database error"));

        ResponseEntity<PasswordResetResponse> response = passwordResetController.resetPassword(request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertFalse(response.getBody().isSuccess());
        assertEquals("Erro interno do servidor", response.getBody().getMessage());
        verify(passwordResetService).resetPassword(token, newPassword, confirmPassword);
    }

    @Test
    void resetPassword_PasswordMismatch_ReturnsBadRequest() {
        String token = "valid-token";
        String newPassword = "Test@123";
        String confirmPassword = "Test@456";
        PasswordResetRequest request = PasswordResetRequest.builder()
                .token(token)
                .newPassword(newPassword)
                .confirmPassword(confirmPassword)
                .build();

        when(passwordResetService.resetPassword(token, newPassword, confirmPassword))
                .thenThrow(new IllegalArgumentException("As senhas não coincidem"));

        ResponseEntity<PasswordResetResponse> response = passwordResetController.resetPassword(request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertFalse(response.getBody().isSuccess());
        assertEquals("As senhas não coincidem", response.getBody().getMessage());
        verify(passwordResetService).resetPassword(token, newPassword, confirmPassword);
    }

    @Test
    void resetPassword_EmptyConfirmPassword_ReturnsBadRequest() {
        String token = "valid-token";
        String newPassword = "Test@123";
        String confirmPassword = "";
        PasswordResetRequest request = PasswordResetRequest.builder()
                .token(token)
                .newPassword(newPassword)
                .confirmPassword(confirmPassword)
                .build();

        when(passwordResetService.resetPassword(token, newPassword, confirmPassword))
                .thenThrow(new IllegalArgumentException("Confirmação de senha é obrigatória"));

        ResponseEntity<PasswordResetResponse> response = passwordResetController.resetPassword(request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertFalse(response.getBody().isSuccess());
        assertEquals("Confirmação de senha é obrigatória", response.getBody().getMessage());
        verify(passwordResetService).resetPassword(token, newPassword, confirmPassword);
    }
}