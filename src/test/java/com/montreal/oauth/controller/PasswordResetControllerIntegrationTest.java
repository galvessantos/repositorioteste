package com.montreal.oauth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.montreal.oauth.domain.dto.request.PasswordResetGenerateRequest;
import com.montreal.oauth.domain.dto.response.PasswordResetGenerateResponse;
import com.montreal.oauth.domain.dto.response.PasswordResetValidateResponse;
import com.montreal.oauth.domain.service.IPasswordResetService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PasswordResetController.class)
class PasswordResetControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private IPasswordResetService passwordResetService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void generatePasswordResetToken_WithValidRequest_ShouldReturnSuccess() throws Exception {
        // Arrange
        PasswordResetGenerateRequest request = PasswordResetGenerateRequest.builder()
                .login("testuser")
                .build();

        String resetLink = "https://localhost/reset-password?token=test-token-123";
        when(passwordResetService.generatePasswordResetToken("testuser")).thenReturn(resetLink);

        // Act & Assert
        mockMvc.perform(post("/api/auth/password-reset/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password reset token generated successfully"))
                .andExpect(jsonPath("$.resetLink").value(resetLink));
    }

    @Test
    void generatePasswordResetToken_WithInvalidRequest_ShouldReturnBadRequest() throws Exception {
        // Arrange
        PasswordResetGenerateRequest request = PasswordResetGenerateRequest.builder()
                .login("") // Empty login
                .build();

        // Act & Assert
        mockMvc.perform(post("/api/auth/password-reset/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void generatePasswordResetToken_WithNullLogin_ShouldReturnBadRequest() throws Exception {
        // Arrange
        PasswordResetGenerateRequest request = PasswordResetGenerateRequest.builder()
                .login(null) // Null login
                .build();

        // Act & Assert
        mockMvc.perform(post("/api/auth/password-reset/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void validatePasswordResetToken_WithValidToken_ShouldReturnValidResponse() throws Exception {
        // Arrange
        String token = "valid-token-123";
        when(passwordResetService.validatePasswordResetToken(token)).thenReturn(true);

        // Act & Assert
        mockMvc.perform(get("/api/auth/password-reset/validate")
                        .param("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.message").value("Token is valid"));
    }

    @Test
    void validatePasswordResetToken_WithInvalidToken_ShouldReturnInvalidResponse() throws Exception {
        // Arrange
        String token = "invalid-token-123";
        when(passwordResetService.validatePasswordResetToken(token)).thenReturn(false);

        // Act & Assert
        mockMvc.perform(get("/api/auth/password-reset/validate")
                        .param("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.message").value("Token is invalid or expired"));
    }

    @Test
    void validatePasswordResetToken_WithMissingToken_ShouldReturnBadRequest() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/api/auth/password-reset/validate"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cleanupExpiredTokens_ShouldReturnSuccess() throws Exception {
        // Act & Assert
        mockMvc.perform(post("/api/auth/password-reset/cleanup"))
                .andExpect(status().isOk())
                .andExpect(content().string("Cleanup completed successfully"));
    }

    @Test
    void generatePasswordResetToken_WithServiceException_ShouldPropagateException() throws Exception {
        // Arrange
        PasswordResetGenerateRequest request = PasswordResetGenerateRequest.builder()
                .login("testuser")
                .build();

        when(passwordResetService.generatePasswordResetToken(anyString()))
                .thenThrow(new RuntimeException("Service error"));

        // Act & Assert
        mockMvc.perform(post("/api/auth/password-reset/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError());
    }
}