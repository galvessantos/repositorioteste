package com.montreal.oauth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.montreal.oauth.domain.dto.request.PasswordResetGenerateRequest;
import com.montreal.oauth.domain.dto.request.PasswordResetRequest;
import com.montreal.oauth.domain.dto.response.PasswordResetGenerateResponse;
import com.montreal.oauth.domain.dto.response.PasswordResetResponse;
import com.montreal.oauth.domain.dto.response.PasswordResetValidateResponse;
import com.montreal.oauth.domain.dto.response.ResetPasswordResult;
import com.montreal.oauth.domain.service.IPasswordResetService;
import com.montreal.core.domain.exception.UserNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class PasswordResetControllerUnitTest {

    @Mock
    private IPasswordResetService passwordResetService;

    @InjectMocks
    private PasswordResetController passwordResetController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(passwordResetController).build();
        objectMapper = new ObjectMapper();
    }

    @Test
    void generatePasswordResetToken_ValidRequest_ReturnsOk() throws Exception {
        // Arrange
        PasswordResetGenerateRequest request = PasswordResetGenerateRequest.builder()
                .login("testuser")
                .build();

        String resetLink = "https://localhost/reset-password?token=uuid-123";
        when(passwordResetService.generatePasswordResetToken("testuser")).thenReturn(resetLink);

        // Act & Assert
        mockMvc.perform(post("/api/auth/password-reset/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password reset token generated successfully"))
                .andExpect(jsonPath("$.resetLink").value(resetLink));

        verify(passwordResetService).generatePasswordResetToken("testuser");
    }

    @Test
    void generatePasswordResetToken_UserNotFound_ReturnsNotFound() throws Exception {
        // Arrange
        PasswordResetGenerateRequest request = PasswordResetGenerateRequest.builder()
                .login("nonexistent")
                .build();

        when(passwordResetService.generatePasswordResetToken("nonexistent"))
                .thenThrow(new UserNotFoundException("Login informado inválido"));

        // Act & Assert
        mockMvc.perform(post("/api/auth/password-reset/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Login informado inválido"))
                .andExpect(jsonPath("$.resetLink").isEmpty());

        verify(passwordResetService).generatePasswordResetToken("nonexistent");
    }

    @Test
    void generatePasswordResetToken_InvalidRequest_ReturnsBadRequest() throws Exception {
        // Arrange
        PasswordResetGenerateRequest request = PasswordResetGenerateRequest.builder()
                .login("") // Invalid empty login
                .build();

        // Act & Assert
        mockMvc.perform(post("/api/auth/password-reset/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(passwordResetService, never()).generatePasswordResetToken(anyString());
    }

    @Test
    void generatePasswordResetToken_ServiceException_ThrowsException() throws Exception {
        // Arrange
        PasswordResetGenerateRequest request = PasswordResetGenerateRequest.builder()
                .login("testuser")
                .build();

        when(passwordResetService.generatePasswordResetToken("testuser"))
                .thenThrow(new RuntimeException("Database error"));

        // Act & Assert
        mockMvc.perform(post("/api/auth/password-reset/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError());

        verify(passwordResetService).generatePasswordResetToken("testuser");
    }

    @Test
    void validatePasswordResetToken_ValidToken_ReturnsValid() throws Exception {
        // Arrange
        String token = "valid-token";
        when(passwordResetService.validatePasswordResetToken(token)).thenReturn(true);

        // Act & Assert
        mockMvc.perform(get("/api/auth/password-reset/validate")
                        .param("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.message").value("Token is valid"));

        verify(passwordResetService).validatePasswordResetToken(token);
    }

    @Test
    void validatePasswordResetToken_InvalidToken_ReturnsInvalid() throws Exception {
        // Arrange
        String token = "invalid-token";
        when(passwordResetService.validatePasswordResetToken(token)).thenReturn(false);

        // Act & Assert
        mockMvc.perform(get("/api/auth/password-reset/validate")
                        .param("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.message").value("Token is invalid or expired"));

        verify(passwordResetService).validatePasswordResetToken(token);
    }

    @Test
    void validatePasswordResetToken_MissingToken_ReturnsBadRequest() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/api/auth/password-reset/validate"))
                .andExpect(status().isBadRequest());

        verify(passwordResetService, never()).validatePasswordResetToken(anyString());
    }

    @Test
    void validatePasswordResetToken_ServiceException_ThrowsException() throws Exception {
        // Arrange
        String token = "test-token";
        when(passwordResetService.validatePasswordResetToken(token))
                .thenThrow(new RuntimeException("Database error"));

        // Act & Assert
        mockMvc.perform(get("/api/auth/password-reset/validate")
                        .param("token", token))
                .andExpect(status().isInternalServerError());

        verify(passwordResetService).validatePasswordResetToken(token);
    }

    @Test
    void cleanupExpiredTokens_Success_ReturnsOk() throws Exception {
        // Arrange
        doNothing().when(passwordResetService).cleanupExpiredTokens();

        // Act & Assert
        mockMvc.perform(post("/api/auth/password-reset/cleanup"))
                .andExpect(status().isOk())
                .andExpect(content().string("Cleanup completed successfully"));

        verify(passwordResetService).cleanupExpiredTokens();
    }

    @Test
    void cleanupExpiredTokens_ServiceException_ThrowsException() throws Exception {
        // Arrange
        doThrow(new RuntimeException("Database error")).when(passwordResetService).cleanupExpiredTokens();

        // Act & Assert
        mockMvc.perform(post("/api/auth/password-reset/cleanup"))
                .andExpect(status().isInternalServerError());

        verify(passwordResetService).cleanupExpiredTokens();
    }

    @Test
    void resetPassword_ValidRequest_ReturnsOk() throws Exception {
        // Arrange
        PasswordResetRequest request = PasswordResetRequest.builder()
                .token("valid-token")
                .newPassword("Test@123")
                .confirmPassword("Test@123")
                .build();

        ResetPasswordResult result = ResetPasswordResult.builder()
                .success(true)
                .message("Senha redefinida com sucesso")
                .build();

        when(passwordResetService.resetPasswordWithTokens("valid-token", "Test@123", "Test@123"))
                .thenReturn(result);

        // Act & Assert
        mockMvc.perform(post("/api/auth/password-reset/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Senha redefinida com sucesso"))
                .andExpect(jsonPath("$.success").value(true));

        verify(passwordResetService).resetPasswordWithTokens("valid-token", "Test@123", "Test@123");
    }

    @Test
    void resetPassword_InvalidToken_ReturnsBadRequest() throws Exception {
        // Arrange
        PasswordResetRequest request = PasswordResetRequest.builder()
                .token("invalid-token")
                .newPassword("Test@123")
                .confirmPassword("Test@123")
                .build();

        ResetPasswordResult result = ResetPasswordResult.builder()
                .success(false)
                .message("Token inválido ou expirado")
                .build();

        when(passwordResetService.resetPasswordWithTokens("invalid-token", "Test@123", "Test@123"))
                .thenReturn(result);

        // Act & Assert
        mockMvc.perform(post("/api/auth/password-reset/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Token inválido ou expirado"))
                .andExpect(jsonPath("$.success").value(false));

        verify(passwordResetService).resetPasswordWithTokens("invalid-token", "Test@123", "Test@123");
    }

    @Test
    void resetPassword_InvalidPassword_ReturnsBadRequest() throws Exception {
        // Arrange
        PasswordResetRequest request = PasswordResetRequest.builder()
                .token("valid-token")
                .newPassword("invalid")
                .confirmPassword("invalid")
                .build();

        ResetPasswordResult result = ResetPasswordResult.builder()
                .success(false)
                .message("A senha deve conter pelo menos uma letra maiúscula")
                .build();

        when(passwordResetService.resetPasswordWithTokens("valid-token", "invalid", "invalid"))
                .thenReturn(result);

        // Act & Assert
        mockMvc.perform(post("/api/auth/password-reset/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("A senha deve conter pelo menos uma letra maiúscula"))
                .andExpect(jsonPath("$.success").value(false));

        verify(passwordResetService).resetPasswordWithTokens("valid-token", "invalid", "invalid");
    }

    @Test
    void resetPassword_InvalidRequest_ReturnsBadRequest() throws Exception {
        // Arrange
        PasswordResetRequest request = PasswordResetRequest.builder()
                .token("") // Invalid empty token
                .newPassword("Test@123")
                .confirmPassword("Test@123")
                .build();

        // Act & Assert
        mockMvc.perform(post("/api/auth/password-reset/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(passwordResetService, never()).resetPasswordWithTokens(anyString(), anyString(), anyString());
    }

    @Test
    void resetPassword_ServiceException_ReturnsInternalServerError() throws Exception {
        // Arrange
        PasswordResetRequest request = PasswordResetRequest.builder()
                .token("valid-token")
                .newPassword("Test@123")
                .confirmPassword("Test@123")
                .build();

        when(passwordResetService.resetPasswordWithTokens("valid-token", "Test@123", "Test@123"))
                .thenThrow(new RuntimeException("Database error"));

        // Act & Assert
        mockMvc.perform(post("/api/auth/password-reset/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Erro interno do servidor"))
                .andExpect(jsonPath("$.success").value(false));

        verify(passwordResetService).resetPasswordWithTokens("valid-token", "Test@123", "Test@123");
    }

    @Test
    void resetPassword_WithAutoLogin_ReturnsTokens() throws Exception {
        // Arrange
        PasswordResetRequest request = PasswordResetRequest.builder()
                .token("valid-token")
                .newPassword("Test@123")
                .confirmPassword("Test@123")
                .build();

        ResetPasswordResult result = ResetPasswordResult.builder()
                .success(true)
                .message("Senha redefinida com sucesso")
                .accessToken("access-token")
                .refreshToken("refresh-token")
                .build();

        when(passwordResetService.resetPasswordWithTokens("valid-token", "Test@123", "Test@123"))
                .thenReturn(result);

        // Act & Assert
        mockMvc.perform(post("/api/auth/password-reset/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Senha redefinida com sucesso"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-token"));

        verify(passwordResetService).resetPasswordWithTokens("valid-token", "Test@123", "Test@123");
    }
}