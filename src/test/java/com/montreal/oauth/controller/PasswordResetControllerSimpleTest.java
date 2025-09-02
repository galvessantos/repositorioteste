package com.montreal.oauth.controller;

import com.montreal.oauth.domain.dto.request.PasswordResetGenerateRequest;
import com.montreal.oauth.domain.dto.response.PasswordResetGenerateResponse;
import com.montreal.oauth.domain.service.IPasswordResetService;
import com.montreal.core.domain.exception.UserNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class PasswordResetControllerSimpleTest {

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
    void cleanupExpiredTokens_Success_ReturnsOk() throws Exception {
        // Arrange
        doNothing().when(passwordResetService).cleanupExpiredTokens();

        // Act & Assert
        mockMvc.perform(post("/api/auth/password-reset/cleanup"))
                .andExpect(status().isOk())
                .andExpect(content().string("Cleanup completed successfully"));

        verify(passwordResetService).cleanupExpiredTokens();
    }
}