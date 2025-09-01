package com.montreal.oauth.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.montreal.oauth.domain.dto.request.PasswordResetGenerateRequest;
import com.montreal.oauth.domain.dto.request.PasswordResetRequest;
import com.montreal.oauth.domain.dto.response.PasswordResetGenerateResponse;
import com.montreal.oauth.domain.dto.response.PasswordResetResponse;
import com.montreal.oauth.domain.dto.response.PasswordResetValidateResponse;
import com.montreal.oauth.domain.entity.PasswordResetToken;
import com.montreal.oauth.domain.entity.UserInfo;
import com.montreal.oauth.domain.entity.Role;
import com.montreal.oauth.domain.enumerations.RoleEnum;
import com.montreal.oauth.domain.repository.IPasswordResetTokenRepository;
import com.montreal.oauth.domain.repository.IUserRepository;
import com.montreal.oauth.domain.repository.PasswordHistoryRepository;
import com.montreal.oauth.domain.service.PasswordHistoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureWebMvc
@ActiveProfiles("test")
@Transactional
class PasswordResetIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private IUserRepository userRepository;

    @Autowired
    private IPasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private PasswordHistoryRepository passwordHistoryRepository;

    @Autowired
    private PasswordHistoryService passwordHistoryService;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private UserInfo testUser;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        objectMapper = new ObjectMapper();

        // Create test user
        testUser = new UserInfo();
        testUser.setUsername("integrationtestuser");
        testUser.setEmail("integration@test.com");
        testUser.setPassword(passwordEncoder.encode("Old@123"));
        testUser.setEnabled(false);
        testUser.setPasswordChangedByUser(false);

        Role userRole = new Role();
        userRole.setName(RoleEnum.ROLE_USER);
        Set<Role> roles = new HashSet<>();
        roles.add(userRole);
        testUser.setRoles(roles);

        testUser = userRepository.save(testUser);
    }

    @Test
    void completePasswordResetFlow_WithValidData_Success() throws Exception {
        // Step 1: Generate password reset token
        PasswordResetGenerateRequest generateRequest = PasswordResetGenerateRequest.builder()
                .login("integrationtestuser")
                .build();

        String generateResponse = mockMvc.perform(post("/api/auth/password-reset/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(generateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password reset token generated successfully"))
                .andExpect(jsonPath("$.resetLink").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();

        PasswordResetGenerateResponse generateResponseObj = objectMapper.readValue(
                generateResponse, PasswordResetGenerateResponse.class);
        String resetLink = generateResponseObj.getResetLink();
        String token = extractTokenFromLink(resetLink);

        // Verify token was created in database
        assertTrue(passwordResetTokenRepository.findByToken(token).isPresent());
        PasswordResetToken createdToken = passwordResetTokenRepository.findByToken(token).get();
        assertEquals(testUser.getId(), createdToken.getUser().getId());
        assertFalse(createdToken.getIsUsed());
        assertFalse(createdToken.isExpired());

        // Step 2: Validate token
        mockMvc.perform(get("/api/auth/password-reset/validate")
                        .param("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.message").value("Token is valid"));

        // Step 3: Reset password
        PasswordResetRequest resetRequest = PasswordResetRequest.builder()
                .token(token)
                .newPassword("New@456")
                .confirmPassword("New@456")
                .build();

        mockMvc.perform(post("/api/auth/password-reset/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resetRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Senha redefinida com sucesso"))
                .andExpect(jsonPath("$.success").value(true));

        // Verify password was changed
        UserInfo updatedUser = userRepository.findByUsername("integrationtestuser");
        assertTrue(passwordEncoder.matches("New@456", updatedUser.getPassword()));
        assertTrue(updatedUser.isEnabled());
        assertTrue(updatedUser.isPasswordChangedByUser());

        // Verify token was marked as used
        PasswordResetToken usedToken = passwordResetTokenRepository.findByToken(token).get();
        assertTrue(usedToken.getIsUsed());
        assertNotNull(usedToken.getUsedAt());

        // Verify password was saved to history (if service is enabled)
        if (passwordHistoryService != null) {
            assertTrue(passwordHistoryRepository.findLastPasswordsByUserId(
                    testUser.getId(), org.springframework.data.domain.PageRequest.of(0, 1))
                    .stream()
                    .anyMatch(ph -> passwordEncoder.matches("New@456", ph.getPasswordHash())));
        }
    }

    @Test
    void passwordResetFlow_WithInvalidUser_ReturnsNotFound() throws Exception {
        // Step 1: Try to generate token for non-existent user
        PasswordResetGenerateRequest generateRequest = PasswordResetGenerateRequest.builder()
                .login("nonexistentuser")
                .build();

        mockMvc.perform(post("/api/auth/password-reset/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(generateRequest)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Login informado inválido"))
                .andExpect(jsonPath("$.resetLink").isEmpty());
    }

    @Test
    void passwordResetFlow_WithExpiredToken_ReturnsInvalid() throws Exception {
        // Step 1: Create an expired token manually
        PasswordResetToken expiredToken = PasswordResetToken.builder()
                .token("expired-token")
                .user(testUser)
                .createdAt(LocalDateTime.now().minusHours(2))
                .expiresAt(LocalDateTime.now().minusMinutes(30))
                .isUsed(false)
                .build();
        passwordResetTokenRepository.save(expiredToken);

        // Step 2: Try to validate expired token
        mockMvc.perform(get("/api/auth/password-reset/validate")
                        .param("token", "expired-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.message").value("Token is invalid or expired"));

        // Step 3: Try to reset password with expired token
        PasswordResetRequest resetRequest = PasswordResetRequest.builder()
                .token("expired-token")
                .newPassword("New@456")
                .confirmPassword("New@456")
                .build();

        mockMvc.perform(post("/api/auth/password-reset/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resetRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Token inválido ou expirado"))
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void passwordResetFlow_WithUsedToken_ReturnsInvalid() throws Exception {
        // Step 1: Create a used token manually
        PasswordResetToken usedToken = PasswordResetToken.builder()
                .token("used-token")
                .user(testUser)
                .createdAt(LocalDateTime.now().minusMinutes(10))
                .expiresAt(LocalDateTime.now().plusMinutes(20))
                .isUsed(true)
                .usedAt(LocalDateTime.now().minusMinutes(5))
                .build();
        passwordResetTokenRepository.save(usedToken);

        // Step 2: Try to validate used token
        mockMvc.perform(get("/api/auth/password-reset/validate")
                        .param("token", "used-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.message").value("Token is invalid or expired"));

        // Step 3: Try to reset password with used token
        PasswordResetRequest resetRequest = PasswordResetRequest.builder()
                .token("used-token")
                .newPassword("New@456")
                .confirmPassword("New@456")
                .build();

        mockMvc.perform(post("/api/auth/password-reset/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resetRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Token inválido ou expirado"))
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void passwordResetFlow_WithInvalidPassword_ReturnsValidationError() throws Exception {
        // Step 1: Generate valid token
        PasswordResetGenerateRequest generateRequest = PasswordResetGenerateRequest.builder()
                .login("integrationtestuser")
                .build();

        String generateResponse = mockMvc.perform(post("/api/auth/password-reset/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(generateRequest)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String token = extractTokenFromLink(
                objectMapper.readValue(generateResponse, PasswordResetGenerateResponse.class).getResetLink());

        // Step 2: Try to reset with invalid password (too short)
        PasswordResetRequest resetRequest = PasswordResetRequest.builder()
                .token(token)
                .newPassword("Ab@1")
                .confirmPassword("Ab@1")
                .build();

        mockMvc.perform(post("/api/auth/password-reset/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resetRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("A senha deve ter entre 4 e 8 caracteres"))
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void passwordResetFlow_WithPasswordMismatch_ReturnsValidationError() throws Exception {
        // Step 1: Generate valid token
        PasswordResetGenerateRequest generateRequest = PasswordResetGenerateRequest.builder()
                .login("integrationtestuser")
                .build();

        String generateResponse = mockMvc.perform(post("/api/auth/password-reset/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(generateRequest)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String token = extractTokenFromLink(
                objectMapper.readValue(generateResponse, PasswordResetGenerateResponse.class).getResetLink());

        // Step 2: Try to reset with mismatched passwords
        PasswordResetRequest resetRequest = PasswordResetRequest.builder()
                .token(token)
                .newPassword("New@456")
                .confirmPassword("Different@789")
                .build();

        mockMvc.perform(post("/api/auth/password-reset/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resetRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("As senhas não coincidem"))
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void passwordResetFlow_WithSamePasswordAsCurrent_ReturnsValidationError() throws Exception {
        // Step 1: Generate valid token
        PasswordResetGenerateRequest generateRequest = PasswordResetGenerateRequest.builder()
                .login("integrationtestuser")
                .build();

        String generateResponse = mockMvc.perform(post("/api/auth/password-reset/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(generateRequest)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String token = extractTokenFromLink(
                objectMapper.readValue(generateResponse, PasswordResetGenerateResponse.class).getResetLink());

        // Step 2: Try to reset with same password as current
        PasswordResetRequest resetRequest = PasswordResetRequest.builder()
                .token(token)
                .newPassword("Old@123")
                .confirmPassword("Old@123")
                .build();

        mockMvc.perform(post("/api/auth/password-reset/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resetRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("A nova senha não pode ser igual à senha atual"))
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void cleanupExpiredTokens_RemovesExpiredTokens() throws Exception {
        // Step 1: Create expired tokens
        PasswordResetToken expiredToken1 = PasswordResetToken.builder()
                .token("expired-token-1")
                .user(testUser)
                .createdAt(LocalDateTime.now().minusHours(2))
                .expiresAt(LocalDateTime.now().minusMinutes(30))
                .isUsed(false)
                .build();

        PasswordResetToken expiredToken2 = PasswordResetToken.builder()
                .token("expired-token-2")
                .user(testUser)
                .createdAt(LocalDateTime.now().minusHours(3))
                .expiresAt(LocalDateTime.now().minusMinutes(60))
                .isUsed(false)
                .build();

        passwordResetTokenRepository.save(expiredToken1);
        passwordResetTokenRepository.save(expiredToken2);

        // Verify tokens exist
        assertTrue(passwordResetTokenRepository.findByToken("expired-token-1").isPresent());
        assertTrue(passwordResetTokenRepository.findByToken("expired-token-2").isPresent());

        // Step 2: Run cleanup
        mockMvc.perform(post("/api/auth/password-reset/cleanup"))
                .andExpect(status().isOk())
                .andExpect(content().string("Cleanup completed successfully"));

        // Step 3: Verify expired tokens were removed
        assertFalse(passwordResetTokenRepository.findByToken("expired-token-1").isPresent());
        assertFalse(passwordResetTokenRepository.findByToken("expired-token-2").isPresent());
    }

    @Test
    void multiplePasswordResetRequests_InvalidatesPreviousTokens() throws Exception {
        // Step 1: Generate first token
        PasswordResetGenerateRequest generateRequest = PasswordResetGenerateRequest.builder()
                .login("integrationtestuser")
                .build();

        String firstResponse = mockMvc.perform(post("/api/auth/password-reset/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(generateRequest)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String firstToken = extractTokenFromLink(
                objectMapper.readValue(firstResponse, PasswordResetGenerateResponse.class).getResetLink());

        // Step 2: Generate second token (should invalidate first)
        String secondResponse = mockMvc.perform(post("/api/auth/password-reset/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(generateRequest)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String secondToken = extractTokenFromLink(
                objectMapper.readValue(secondResponse, PasswordResetGenerateResponse.class).getResetLink());

        // Step 3: Verify first token is now invalid
        mockMvc.perform(get("/api/auth/password-reset/validate")
                        .param("token", firstToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false));

        // Step 4: Verify second token is valid
        mockMvc.perform(get("/api/auth/password-reset/validate")
                        .param("token", secondToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true));
    }

    private String extractTokenFromLink(String resetLink) {
        return resetLink.substring(resetLink.lastIndexOf("=") + 1);
    }
}