package com.montreal.oauth.domain.repository;

import com.montreal.oauth.domain.entity.PasswordResetToken;
import com.montreal.oauth.domain.entity.UserInfo;
import com.montreal.oauth.domain.repository.IUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@Transactional
class PasswordResetTokenRepositoryIntegrationTest {

    @Autowired
    private IPasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private IUserRepository userRepository;

    private UserInfo testUser;
    private BCryptPasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        
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
    void findByToken_ValidToken_ReturnsToken() {
        // Arrange
        PasswordResetToken token = createPasswordResetToken("test-token-123");
        passwordResetTokenRepository.save(token);

        // Act
        Optional<PasswordResetToken> result = passwordResetTokenRepository.findByToken("test-token-123");

        // Assert
        assertTrue(result.isPresent());
        assertEquals("test-token-123", result.get().getToken());
        assertEquals(testUser.getId(), result.get().getUser().getId());
    }

    @Test
    void findByToken_InvalidToken_ReturnsEmpty() {
        // Act
        Optional<PasswordResetToken> result = passwordResetTokenRepository.findByToken("invalid-token");

        // Assert
        assertFalse(result.isPresent());
    }

    @Test
    void findByTokenContaining_ValidLogin_ReturnsToken() {
        // Arrange
        PasswordResetToken token = createPasswordResetToken("test-token-123");
        passwordResetTokenRepository.save(token);

        // Act
        Optional<PasswordResetToken> result = passwordResetTokenRepository.findByTokenContaining("testuser@example.com");

        // Assert
        assertTrue(result.isPresent());
        assertEquals("test-token-123", result.get().getToken());
    }

    @Test
    void findByTokenContaining_InvalidLogin_ReturnsEmpty() {
        // Act
        Optional<PasswordResetToken> result = passwordResetTokenRepository.findByTokenContaining("nonexistent@example.com");

        // Assert
        assertFalse(result.isPresent());
    }

    @Test
    void invalidateTokensByUserId_ShouldMarkTokensAsUsed() {
        // Arrange
        PasswordResetToken token1 = createPasswordResetToken("token-1");
        PasswordResetToken token2 = createPasswordResetToken("token-2");
        passwordResetTokenRepository.save(token1);
        passwordResetTokenRepository.save(token2);

        // Act
        passwordResetTokenRepository.invalidateTokensByUserId(testUser.getId());

        // Assert
        Optional<PasswordResetToken> invalidatedToken1 = passwordResetTokenRepository.findByToken("token-1");
        Optional<PasswordResetToken> invalidatedToken2 = passwordResetTokenRepository.findByToken("token-2");
        
        assertTrue(invalidatedToken1.isPresent());
        assertTrue(invalidatedToken1.get().isUsed());
        assertNotNull(invalidatedToken1.get().getUsedAt());
        
        assertTrue(invalidatedToken2.isPresent());
        assertTrue(invalidatedToken2.get().isUsed());
        assertNotNull(invalidatedToken2.get().getUsedAt());
    }

    @Test
    void deleteExpiredTokens_ShouldRemoveExpiredTokens() {
        // Arrange
        PasswordResetToken validToken = createPasswordResetToken("valid-token");
        PasswordResetToken expiredToken = createExpiredToken("expired-token");
        
        passwordResetTokenRepository.save(validToken);
        passwordResetTokenRepository.save(expiredToken);

        // Act
        passwordResetTokenRepository.deleteExpiredTokens();

        // Assert
        Optional<PasswordResetToken> remainingValidToken = passwordResetTokenRepository.findByToken("valid-token");
        Optional<PasswordResetToken> removedExpiredToken = passwordResetTokenRepository.findByToken("expired-token");
        
        assertTrue(remainingValidToken.isPresent());
        assertFalse(removedExpiredToken.isPresent());
    }

    @Test
    void save_ShouldPersistTokenWithCorrectData() {
        // Arrange
        PasswordResetToken token = createPasswordResetToken("new-token");

        // Act
        PasswordResetToken savedToken = passwordResetTokenRepository.save(token);

        // Assert
        assertNotNull(savedToken.getId());
        assertEquals("new-token", savedToken.getToken());
        assertEquals(testUser.getId(), savedToken.getUser().getId());
        assertNotNull(savedToken.getCreatedAt());
        assertNotNull(savedToken.getExpiresAt());
        assertFalse(savedToken.isUsed());
        assertNull(savedToken.getUsedAt());
    }

    @Test
    void isExpired_ValidToken_ReturnsFalse() {
        // Arrange
        PasswordResetToken token = createPasswordResetToken("valid-token");
        passwordResetTokenRepository.save(token);

        // Act
        Optional<PasswordResetToken> result = passwordResetTokenRepository.findByToken("valid-token");

        // Assert
        assertTrue(result.isPresent());
        assertFalse(result.get().isExpired());
    }

    @Test
    void isExpired_ExpiredToken_ReturnsTrue() {
        // Arrange
        PasswordResetToken expiredToken = createExpiredToken("expired-token");
        passwordResetTokenRepository.save(expiredToken);

        // Act
        Optional<PasswordResetToken> result = passwordResetTokenRepository.findByToken("expired-token");

        // Assert
        assertTrue(result.isPresent());
        assertTrue(result.get().isExpired());
    }

    @Test
    void isValid_ValidToken_ReturnsTrue() {
        // Arrange
        PasswordResetToken token = createPasswordResetToken("valid-token");
        passwordResetTokenRepository.save(token);

        // Act
        Optional<PasswordResetToken> result = passwordResetTokenRepository.findByToken("valid-token");

        // Assert
        assertTrue(result.isPresent());
        assertTrue(result.get().isValid());
    }

    @Test
    void isValid_UsedToken_ReturnsFalse() {
        // Arrange
        PasswordResetToken token = createPasswordResetToken("used-token");
        token.setUsed(true);
        token.setUsedAt(LocalDateTime.now());
        passwordResetTokenRepository.save(token);

        // Act
        Optional<PasswordResetToken> result = passwordResetTokenRepository.findByToken("used-token");

        // Assert
        assertTrue(result.isPresent());
        assertFalse(result.get().isValid());
    }

    @Test
    void isValid_ExpiredToken_ReturnsFalse() {
        // Arrange
        PasswordResetToken expiredToken = createExpiredToken("expired-token");
        passwordResetTokenRepository.save(expiredToken);

        // Act
        Optional<PasswordResetToken> result = passwordResetTokenRepository.findByToken("expired-token");

        // Assert
        assertTrue(result.isPresent());
        assertFalse(result.get().isValid());
    }

    private PasswordResetToken createPasswordResetToken(String tokenValue) {
        return PasswordResetToken.builder()
                .token(tokenValue)
                .user(testUser)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(30))
                .isUsed(false)
                .build();
    }

    private PasswordResetToken createExpiredToken(String tokenValue) {
        return PasswordResetToken.builder()
                .token(tokenValue)
                .user(testUser)
                .createdAt(LocalDateTime.now().minusHours(1))
                .expiresAt(LocalDateTime.now().minusMinutes(30))
                .isUsed(false)
                .build();
    }
}
