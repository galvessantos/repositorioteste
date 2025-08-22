package com.montreal.oauth.domain.repository;

import com.montreal.oauth.domain.entity.PasswordResetToken;
import com.montreal.oauth.domain.entity.UserInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class IPasswordResetTokenRepositoryIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private IPasswordResetTokenRepository passwordResetTokenRepository;

    private UserInfo testUser;
    private PasswordResetToken testToken;
    private PasswordResetToken expiredToken;
    private PasswordResetToken usedToken;

    @BeforeEach
    void setUp() {
        // Create test user
        testUser = new UserInfo();
        testUser.setUsername("testuser");
        testUser.setEmail("test@example.com");
        testUser.setPassword("password");
        testUser.setCpf("12345678901");
        testUser.setFullName("Test User");
        testUser.setEnabled(true);
        
        testUser = entityManager.persistAndFlush(testUser);

        // Create valid token
        testToken = PasswordResetToken.builder()
                .token("valid-token-123")
                .user(testUser)
                .expiresAt(LocalDateTime.now().plusMinutes(30))
                .isUsed(false)
                .build();

        // Create expired token
        expiredToken = PasswordResetToken.builder()
                .token("expired-token-456")
                .user(testUser)
                .expiresAt(LocalDateTime.now().minusMinutes(30))
                .isUsed(false)
                .build();

        // Create used token
        usedToken = PasswordResetToken.builder()
                .token("used-token-789")
                .user(testUser)
                .expiresAt(LocalDateTime.now().plusMinutes(30))
                .isUsed(true)
                .usedAt(LocalDateTime.now())
                .build();

        // Persist all tokens
        entityManager.persistAndFlush(testToken);
        entityManager.persistAndFlush(expiredToken);
        entityManager.persistAndFlush(usedToken);
    }

    @Test
    void findByToken_WithValidToken_ShouldReturnToken() {
        // Act
        Optional<PasswordResetToken> result = passwordResetTokenRepository.findByToken("valid-token-123");

        // Assert
        assertTrue(result.isPresent());
        assertEquals("valid-token-123", result.get().getToken());
        assertEquals(testUser.getId(), result.get().getUser().getId());
    }

    @Test
    void findByToken_WithInvalidToken_ShouldReturnEmpty() {
        // Act
        Optional<PasswordResetToken> result = passwordResetTokenRepository.findByToken("invalid-token");

        // Assert
        assertFalse(result.isPresent());
    }

    @Test
    void findByUser_Id_ShouldReturnAllTokensForUser() {
        // Act
        List<PasswordResetToken> result = passwordResetTokenRepository.findByUser_Id(testUser.getId());

        // Assert
        assertEquals(3, result.size());
        assertTrue(result.stream().allMatch(token -> token.getUser().getId().equals(testUser.getId())));
    }

    @Test
    void findValidTokensByUserId_ShouldReturnOnlyValidTokens() {
        // Act
        List<PasswordResetToken> result = passwordResetTokenRepository.findValidTokensByUserId(
                testUser.getId(), LocalDateTime.now());

        // Assert
        assertEquals(1, result.size());
        assertEquals("valid-token-123", result.get(0).getToken());
        assertFalse(result.get(0).isUsed());
        assertTrue(result.get(0).getExpiresAt().isAfter(LocalDateTime.now()));
    }

    @Test
    void findExpiredUnusedTokens_ShouldReturnExpiredUnusedTokens() {
        // Act
        List<PasswordResetToken> result = passwordResetTokenRepository.findExpiredUnusedTokens(LocalDateTime.now());

        // Assert
        assertEquals(1, result.size());
        assertEquals("expired-token-456", result.get(0).getToken());
        assertFalse(result.get(0).isUsed());
        assertTrue(result.get(0).getExpiresAt().isBefore(LocalDateTime.now()));
    }

    @Test
    void existsValidTokenByUserId_WithValidToken_ShouldReturnTrue() {
        // Act
        boolean result = passwordResetTokenRepository.existsValidTokenByUserId(
                testUser.getId(), LocalDateTime.now());

        // Assert
        assertTrue(result);
    }

    @Test
    void existsValidTokenByUserId_WithoutValidToken_ShouldReturnFalse() {
        // Arrange - Delete valid token
        entityManager.remove(testToken);
        entityManager.flush();

        // Act
        boolean result = passwordResetTokenRepository.existsValidTokenByUserId(
                testUser.getId(), LocalDateTime.now());

        // Assert
        assertFalse(result);
    }

    @Test
    void deleteByUser_Id_ShouldRemoveAllTokensForUser() {
        // Act
        passwordResetTokenRepository.deleteByUser_Id(testUser.getId());

        // Assert
        List<PasswordResetToken> remainingTokens = passwordResetTokenRepository.findByUser_Id(testUser.getId());
        assertEquals(0, remainingTokens.size());
    }

    @Test
    void deleteByExpiresAtBefore_ShouldRemoveExpiredTokens() {
        // Act
        passwordResetTokenRepository.deleteByExpiresAtBefore(LocalDateTime.now());

        // Assert
        List<PasswordResetToken> remainingTokens = passwordResetTokenRepository.findByUser_Id(testUser.getId());
        assertEquals(2, remainingTokens.size()); // Only expired token should be removed
        assertFalse(remainingTokens.stream().anyMatch(token -> token.getToken().equals("expired-token-456")));
    }
}