package com.montreal.oauth.controller;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.montreal.oauth.domain.dto.request.PasswordResetGenerateRequest;
import com.montreal.oauth.domain.dto.response.PasswordResetGenerateResponse;
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

}