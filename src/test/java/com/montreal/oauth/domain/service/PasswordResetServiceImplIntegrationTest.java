package com.montreal.oauth.domain.service;

import com.montreal.oauth.domain.dto.response.ResetPasswordResult;
import com.montreal.oauth.domain.entity.PasswordResetToken;
import com.montreal.oauth.domain.entity.UserInfo;
import com.montreal.oauth.domain.repository.IPasswordResetTokenRepository;
import com.montreal.oauth.domain.repository.IUserRepository;
import com.montreal.core.domain.exception.UserNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PasswordResetServiceImplIntegrationTest {

    @Autowired
    private PasswordResetServiceImpl passwordResetService;

    @Autowired
    private IPasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private IUserRepository userRepository;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    private UserInfo testUser;

    @BeforeEach
    void setUp() {
        // Criar usuário de teste
        testUser = UserInfo.builder()
                .username("testuser@example.com")
                .email("testuser@example.com")
                .password(passwordEncoder.encode("Test@123"))
                .enabled(true)
                .build();
        testUser = userRepository.save(testUser);
    }

    @Test
    void generatePasswordResetToken_ValidLogin_ReturnsResetLink() {
        // Arrange
        String login = "testuser@example.com";

        // Act
        String result = passwordResetService.generatePasswordResetToken(login);

        // Assert
        assertNotNull(result);
        assertTrue(result.contains("token="));
        assertTrue(result.startsWith("https://localhost:5173"));

        // Verificar se o token foi salvo no banco
        Optional<PasswordResetToken> savedToken = passwordResetTokenRepository.findByTokenContaining("testuser@example.com");
        assertTrue(savedToken.isPresent());
        assertEquals(testUser.getId(), savedToken.get().getUser().getId());
        assertFalse(savedToken.get().isUsed());
        assertNotNull(savedToken.get().getCreatedAt());
        assertNotNull(savedToken.get().getExpiresAt());
    }

    @Test
    void generatePasswordResetToken_InvalidLogin_ThrowsUserNotFoundException() {
        // Arrange
        String login = "nonexistent@example.com";

        // Act & Assert
        UserNotFoundException exception = assertThrows(UserNotFoundException.class, () ->
            passwordResetService.generatePasswordResetToken(login)
        );

        assertEquals("Login informado inválido", exception.getMessage());
    }

    @Test
    void generatePasswordResetToken_InvalidatesExistingTokens() {
        // Arrange
        String login = "testuser@example.com";

        // Gerar primeiro token
        String firstLink = passwordResetService.generatePasswordResetToken(login);
        String firstToken = extractTokenFromLink(firstLink);

        // Gerar segundo token (deve invalidar o primeiro)
        String secondLink = passwordResetService.generatePasswordResetToken(login);
        String secondToken = extractTokenFromLink(secondLink);

        // Assert
        assertNotEquals(firstToken, secondToken);

        // Verificar se o primeiro token foi invalidado
        Optional<PasswordResetToken> firstTokenEntity = passwordResetTokenRepository.findByToken(firstToken);
        assertTrue(firstTokenEntity.isPresent());
        assertTrue(firstTokenEntity.get().isUsed());

        // Verificar se o segundo token está ativo
        Optional<PasswordResetToken> secondTokenEntity = passwordResetTokenRepository.findByToken(secondToken);
        assertTrue(secondTokenEntity.isPresent());
        assertFalse(secondTokenEntity.get().isUsed());
    }

    @Test
    void validatePasswordResetToken_ValidToken_ReturnsTrue() {
        // Arrange
        String login = "testuser@example.com";
        String resetLink = passwordResetService.generatePasswordResetToken(login);
        String token = extractTokenFromLink(resetLink);

        // Act
        boolean result = passwordResetService.validatePasswordResetToken(token);

        // Assert
        assertTrue(result);
    }

    @Test
    void validatePasswordResetToken_InvalidToken_ReturnsFalse() {
        // Arrange
        String token = "invalid-token-123";

        // Act
        boolean result = passwordResetService.validatePasswordResetToken(token);

        // Assert
        assertFalse(result);
    }

    @Test
    void validatePasswordResetToken_ExpiredToken_ReturnsFalse() {
        // Arrange
        String login = "testuser@example.com";
        String resetLink = passwordResetService.generatePasswordResetToken(login);
        String token = extractTokenFromLink(resetLink);

        // Manually expire the token
        Optional<PasswordResetToken> tokenEntity = passwordResetTokenRepository.findByToken(token);
        assertTrue(tokenEntity.isPresent());
        
        PasswordResetToken expiredToken = tokenEntity.get();
        expiredToken.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        passwordResetTokenRepository.save(expiredToken);

        // Act
        boolean result = passwordResetService.validatePasswordResetToken(token);

        // Assert
        assertFalse(result);
    }

    @Test
    void validatePasswordResetToken_UsedToken_ReturnsFalse() {
        // Arrange
        String login = "testuser@example.com";
        String resetLink = passwordResetService.generatePasswordResetToken(login);
        String token = extractTokenFromLink(resetLink);

        // Manually mark token as used
        passwordResetService.markTokenAsUsed(token);

        // Act
        boolean result = passwordResetService.validatePasswordResetToken(token);

        // Assert
        assertFalse(result);
    }

    @Test
    void markTokenAsUsed_ValidToken_ShouldMarkAsUsed() {
        // Arrange
        String login = "testuser@example.com";
        String resetLink = passwordResetService.generatePasswordResetToken(login);
        String token = extractTokenFromLink(resetLink);

        // Act
        passwordResetService.markTokenAsUsed(token);

        // Assert
        Optional<PasswordResetToken> usedToken = passwordResetTokenRepository.findByToken(token);
        assertTrue(usedToken.isPresent());
        assertTrue(usedToken.get().isUsed());
        assertNotNull(usedToken.get().getUsedAt());
    }

    @Test
    void cleanupExpiredTokens_ShouldRemoveExpiredTokens() {
        // Arrange
        String login = "testuser@example.com";
        String resetLink = passwordResetService.generatePasswordResetToken(login);
        String token = extractTokenFromLink(resetLink);

        // Manually expire the token
        Optional<PasswordResetToken> tokenEntity = passwordResetTokenRepository.findByToken(token);
        assertTrue(tokenEntity.isPresent());
        
        PasswordResetToken expiredToken = tokenEntity.get();
        expiredToken.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        passwordResetTokenRepository.save(expiredToken);

        // Act
        passwordResetService.cleanupExpiredTokens();

        // Assert
        Optional<PasswordResetToken> cleanedToken = passwordResetTokenRepository.findByToken(token);
        assertFalse(cleanedToken.isPresent());
    }

    @Test
    void resetPasswordWithTokens_ValidRequest_ReturnsSuccess() {
        // Arrange
        String login = "testuser@example.com";
        String resetLink = passwordResetService.generatePasswordResetToken(login);
        String token = extractTokenFromLink(resetLink);
        String newPassword = "NewPass@123";
        String confirmPassword = "NewPass@123";

        // Act
        ResetPasswordResult result = passwordResetService.resetPasswordWithTokens(token, newPassword, confirmPassword);

        // Assert
        assertTrue(result.isSuccess());
        assertEquals("Senha redefinida com sucesso", result.getMessage());

        // Verificar se o token foi marcado como usado
        Optional<PasswordResetToken> usedToken = passwordResetTokenRepository.findByToken(token);
        assertTrue(usedToken.isPresent());
        assertTrue(usedToken.get().isUsed());
        assertNotNull(usedToken.get().getUsedAt());

        // Verificar se a senha foi atualizada
        UserInfo updatedUser = userRepository.findById(testUser.getId()).orElse(null);
        assertNotNull(updatedUser);
        assertTrue(passwordEncoder.matches(newPassword, updatedUser.getPassword()));
    }

    @Test
    void resetPasswordWithTokens_InvalidToken_ReturnsFailure() {
        // Arrange
        String token = "invalid-token-123";
        String newPassword = "NewPass@123";
        String confirmPassword = "NewPass@123";

        // Act
        ResetPasswordResult result = passwordResetService.resetPasswordWithTokens(token, newPassword, confirmPassword);

        // Assert
        assertFalse(result.isSuccess());
        assertEquals("Token inválido ou expirado", result.getMessage());
    }

    @Test
    void resetPasswordWithTokens_PasswordMismatch_ReturnsFailure() {
        // Arrange
        String login = "testuser@example.com";
        String resetLink = passwordResetService.generatePasswordResetToken(login);
        String token = extractTokenFromLink(resetLink);
        String newPassword = "NewPass@123";
        String confirmPassword = "DifferentPass@123";

        // Act
        ResetPasswordResult result = passwordResetService.resetPasswordWithTokens(token, newPassword, confirmPassword);

        // Assert
        assertFalse(result.isSuccess());
        assertEquals("As senhas não coincidem", result.getMessage());
    }

    @Test
    void resetPasswordWithTokens_WeakPassword_ReturnsFailure() {
        // Arrange
        String login = "testuser@example.com";
        String resetLink = passwordResetService.generatePasswordResetToken(login);
        String token = extractTokenFromLink(resetLink);
        String newPassword = "weak";
        String confirmPassword = "weak";

        // Act
        ResetPasswordResult result = passwordResetService.resetPasswordWithTokens(token, newPassword, confirmPassword);

        // Assert
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("senha"));
    }

    private String extractTokenFromLink(String resetLink) {
        return resetLink.substring(resetLink.indexOf("token=") + 6);
    }
}
