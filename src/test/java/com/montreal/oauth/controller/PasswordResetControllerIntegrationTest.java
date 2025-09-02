package com.montreal.oauth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.montreal.oauth.domain.dto.request.PasswordResetGenerateRequest;
import com.montreal.oauth.domain.dto.request.PasswordResetRequest;
import com.montreal.oauth.domain.entity.PasswordResetToken;
import com.montreal.oauth.domain.entity.UserInfo;
import com.montreal.oauth.domain.repository.IPasswordResetTokenRepository;
import com.montreal.oauth.domain.repository.IUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureWebMvc
@ActiveProfiles("test")
@Transactional
class PasswordResetControllerIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private IPasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private IUserRepository userRepository;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private UserInfo testUser;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        objectMapper = new ObjectMapper();
        
        // Criar usuário de teste
        testUser = UserInfo.builder()
                .username("testuser@example.com")
                .email("testuser@example.com")
                .password("$2a$10$test.hash.for.testing")
                .enabled(true)
                .build();
        testUser = userRepository.save(testUser);
    }

    @Test
    void generatePasswordResetToken_ValidLogin_ReturnsSuccess() throws Exception {
        // Arrange
        PasswordResetGenerateRequest request = new PasswordResetGenerateRequest();
        request.setLogin("testuser@example.com");

        // Act & Assert
        mockMvc.perform(post("/api/auth/password-reset/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password reset token generated successfully"))
                .andExpect(jsonPath("$.resetLink").isNotEmpty());

        // Verificar se o token foi salvo no banco
        assertTrue(passwordResetTokenRepository.findByTokenContaining("testuser@example.com").isPresent());
    }

    @Test
    void generatePasswordResetToken_InvalidLogin_ReturnsNotFound() throws Exception {
        // Arrange
        PasswordResetGenerateRequest request = new PasswordResetGenerateRequest();
        request.setLogin("nonexistent@example.com");

        // Act & Assert
        mockMvc.perform(post("/api/auth/password-reset/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Login informado inválido"));
    }

    @Test
    void validatePasswordResetToken_ValidToken_ReturnsSuccess() throws Exception {
        // Arrange
        String token = UUID.randomUUID().toString();
        PasswordResetToken resetToken = PasswordResetToken.builder()
                .token(token)
                .user(testUser)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(30))
                .isUsed(false)
                .build();
        passwordResetTokenRepository.save(resetToken);

        // Act & Assert
        mockMvc.perform(get("/api/auth/password-reset/validate")
                        .param("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.message").value("Token is valid"));
    }

    @Test
    void validatePasswordResetToken_ExpiredToken_ReturnsInvalid() throws Exception {
        // Arrange
        String token = UUID.randomUUID().toString();
        PasswordResetToken resetToken = PasswordResetToken.builder()
                .token(token)
                .user(testUser)
                .createdAt(LocalDateTime.now().minusHours(1))
                .expiresAt(LocalDateTime.now().minusMinutes(30))
                .isUsed(false)
                .build();
        passwordResetTokenRepository.save(resetToken);

        // Act & Assert
        mockMvc.perform(get("/api/auth/password-reset/validate")
                        .param("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.message").value("Token is invalid or expired"));
    }

    @Test
    void resetPassword_ValidTokenAndPassword_ReturnsSuccess() throws Exception {
        // Arrange
        String token = UUID.randomUUID().toString();
        PasswordResetToken resetToken = PasswordResetToken.builder()
                .token(token)
                .user(testUser)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(30))
                .isUsed(false)
                .build();
        passwordResetTokenRepository.save(resetToken);

        PasswordResetRequest request = PasswordResetRequest.builder()
                .token(token)
                .newPassword("Test@123")
                .confirmPassword("Test@123")
                .build();

        // Act & Assert
        mockMvc.perform(post("/api/auth/password-reset/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Senha redefinida com sucesso"));

        // Verificar se o token foi marcado como usado
        PasswordResetToken usedToken = passwordResetTokenRepository.findByToken(token).orElse(null);
        assertNotNull(usedToken);
        assertTrue(usedToken.isUsed());
        assertNotNull(usedToken.getUsedAt());
    }

    @Test
    void resetPassword_PasswordMismatch_ReturnsBadRequest() throws Exception {
        // Arrange
        String token = UUID.randomUUID().toString();
        PasswordResetToken resetToken = PasswordResetToken.builder()
                .token(token)
                .user(testUser)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(30))
                .isUsed(false)
                .build();
        passwordResetTokenRepository.save(resetToken);

        PasswordResetRequest request = PasswordResetRequest.builder()
                .token(token)
                .newPassword("Test@123")
                .confirmPassword("Test@456")
                .build();

        // Act & Assert
        mockMvc.perform(post("/api/auth/password-reset/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("As senhas não coincidem"));
    }
}
