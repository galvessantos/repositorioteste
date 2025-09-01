package com.montreal.oauth.domain.repository;

import com.montreal.oauth.domain.entity.PasswordResetToken;
import com.montreal.oauth.domain.entity.UserInfo;
import com.montreal.oauth.domain.entity.Role;
import com.montreal.oauth.domain.enumerations.RoleEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class PasswordResetTokenRepositoryIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private IPasswordResetTokenRepository passwordResetTokenRepository;

    private UserInfo testUser;
    private PasswordResetToken validToken;
    private PasswordResetToken expiredToken;
    private PasswordResetToken usedToken;

    @BeforeEach
    void setUp() {
        // Create test user
        testUser = new UserInfo();
        testUser.setUsername("testuser");
        testUser.setEmail("test@example.com");
        testUser.setPassword("$2a$10$encodedpassword");
        testUser.setEnabled(true);
        testUser.setPasswordChangedByUser(false);

        Role userRole = new Role();
        userRole.setName(RoleEnum.ROLE_USER);
        Set<Role> roles = new HashSet<>();
        roles.add(userRole);
        testUser.setRoles(roles);

        testUser = entityManager.persistAndFlush(testUser);

        // Create valid token
        validToken = PasswordResetToken.builder()
                .token("valid-token")
                .user(testUser)
                .createdAt(LocalDateTime.now().minusMinutes(5))
                .expiresAt(LocalDateTime.now().plusMinutes(25))
                .isUsed(false)
                .build();
        validToken = entityManager.persistAndFlush(validToken);

        // Create expired token
        expiredToken = PasswordResetToken.builder()
                .token("expired-token")
                .user(testUser)
                .createdAt(LocalDateTime.now().minusHours(2))
                .expiresAt(LocalDateTime.now().minusMinutes(30))
                .isUsed(false)
                .build();
        expiredToken = entityManager.persistAndFlush(expiredToken);

        // Create used token
        usedToken = PasswordResetToken.builder()
                .token("used-token")
                .user(testUser)
                .createdAt(LocalDateTime.now().minusMinutes(10))
                .expiresAt(LocalDateTime.now().plusMinutes(20))
                .isUsed(true)
                .usedAt(LocalDateTime.now().minusMinutes(5))
                .build();
        usedToken = entityManager.persistAndFlush(usedToken);

        entityManager.clear();
    }

    @Test
    void findByToken_WithValidToken_ReturnsToken() {
        // Act
        Optional<PasswordResetToken> result = passwordResetTokenRepository.findByToken("valid-token");

        // Assert
        assertTrue(result.isPresent());
        assertEquals("valid-token", result.get().getToken());
        assertEquals(testUser.getId(), result.get().getUser().getId());
        assertFalse(result.get().getIsUsed());
    }

    @Test
    void findByToken_WithExpiredToken_ReturnsToken() {
        // Act
        Optional<PasswordResetToken> result = passwordResetTokenRepository.findByToken("expired-token");

        // Assert
        assertTrue(result.isPresent());
        assertEquals("expired-token", result.get().getToken());
        assertTrue(result.get().isExpired());
    }

    @Test
    void findByToken_WithUsedToken_ReturnsToken() {
        // Act
        Optional<PasswordResetToken> result = passwordResetTokenRepository.findByToken("used-token");

        // Assert
        assertTrue(result.isPresent());
        assertEquals("used-token", result.get().getToken());
        assertTrue(result.get().getIsUsed());
        assertNotNull(result.get().getUsedAt());
    }

    @Test
    void findByToken_WithNonExistentToken_ReturnsEmpty() {
        // Act
        Optional<PasswordResetToken> result = passwordResetTokenRepository.findByToken("non-existent-token");

        // Assert
        assertFalse(result.isPresent());
    }

    @Test
    void findByUser_Id_WithValidUserId_ReturnsAllTokens() {
        // Act
        List<PasswordResetToken> result = passwordResetTokenRepository.findByUser_Id(testUser.getId());

        // Assert
        assertEquals(3, result.size());
        assertTrue(result.stream().anyMatch(token -> "valid-token".equals(token.getToken())));
        assertTrue(result.stream().anyMatch(token -> "expired-token".equals(token.getToken())));
        assertTrue(result.stream().anyMatch(token -> "used-token".equals(token.getToken())));
    }

    @Test
    void findByUser_Id_WithNonExistentUserId_ReturnsEmpty() {
        // Act
        List<PasswordResetToken> result = passwordResetTokenRepository.findByUser_Id(999L);

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    void findValidTokensByUserId_WithValidUserId_ReturnsOnlyValidTokens() {
        // Act
        List<PasswordResetToken> result = passwordResetTokenRepository.findValidTokensByUserId(
                testUser.getId(), LocalDateTime.now());

        // Assert
        assertEquals(1, result.size());
        assertEquals("valid-token", result.get(0).getToken());
        assertFalse(result.get(0).getIsUsed());
        assertFalse(result.get(0).isExpired());
    }

    @Test
    void findValidTokensByUserId_WithNonExistentUserId_ReturnsEmpty() {
        // Act
        List<PasswordResetToken> result = passwordResetTokenRepository.findValidTokensByUserId(
                999L, LocalDateTime.now());

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    void findExpiredUnusedTokens_ReturnsOnlyExpiredUnusedTokens() {
        // Act
        List<PasswordResetToken> result = passwordResetTokenRepository.findExpiredUnusedTokens(LocalDateTime.now());

        // Assert
        assertEquals(1, result.size());
        assertEquals("expired-token", result.get(0).getToken());
        assertTrue(result.get(0).isExpired());
        assertFalse(result.get(0).getIsUsed());
    }

    @Test
    void existsValidTokenByUserId_WithValidToken_ReturnsTrue() {
        // Act
        boolean result = passwordResetTokenRepository.existsValidTokenByUserId(testUser.getId(), LocalDateTime.now());

        // Assert
        assertTrue(result);
    }

    @Test
    void existsValidTokenByUserId_WithNoValidTokens_ReturnsFalse() {
        // Create another user with no valid tokens
        UserInfo anotherUser = new UserInfo();
        anotherUser.setUsername("anotheruser");
        anotherUser.setEmail("another@example.com");
        anotherUser.setPassword("$2a$10$encodedpassword");
        anotherUser.setEnabled(true);

        Role userRole = new Role();
        userRole.setName(RoleEnum.ROLE_USER);
        Set<Role> roles = new HashSet<>();
        roles.add(userRole);
        anotherUser.setRoles(roles);

        anotherUser = entityManager.persistAndFlush(anotherUser);

        // Act
        boolean result = passwordResetTokenRepository.existsValidTokenByUserId(anotherUser.getId(), LocalDateTime.now());

        // Assert
        assertFalse(result);
    }

    @Test
    void deleteByUser_Id_WithValidUserId_DeletesAllTokens() {
        // Act
        passwordResetTokenRepository.deleteByUser_Id(testUser.getId());

        // Assert
        List<PasswordResetToken> remainingTokens = passwordResetTokenRepository.findByUser_Id(testUser.getId());
        assertTrue(remainingTokens.isEmpty());
    }

    @Test
    void deleteByUser_Id_WithNonExistentUserId_DoesNothing() {
        // Act
        passwordResetTokenRepository.deleteByUser_Id(999L);

        // Assert
        List<PasswordResetToken> allTokens = passwordResetTokenRepository.findByUser_Id(testUser.getId());
        assertEquals(3, allTokens.size());
    }

    @Test
    void deleteByExpiresAtBefore_WithValidDate_DeletesExpiredTokens() {
        // Act
        passwordResetTokenRepository.deleteByExpiresAtBefore(LocalDateTime.now());

        // Assert
        List<PasswordResetToken> remainingTokens = passwordResetTokenRepository.findByUser_Id(testUser.getId());
        assertEquals(2, remainingTokens.size()); // Only valid and used tokens remain
        assertTrue(remainingTokens.stream().noneMatch(token -> "expired-token".equals(token.getToken())));
    }

    @Test
    void deleteByExpiresAtBefore_WithFutureDate_DeletesNothing() {
        // Act
        passwordResetTokenRepository.deleteByExpiresAtBefore(LocalDateTime.now().minusDays(1));

        // Assert
        List<PasswordResetToken> allTokens = passwordResetTokenRepository.findByUser_Id(testUser.getId());
        assertEquals(3, allTokens.size());
    }

    @Test
    void save_WithNewToken_PersistsToken() {
        // Arrange
        PasswordResetToken newToken = PasswordResetToken.builder()
                .token("new-token")
                .user(testUser)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(30))
                .isUsed(false)
                .build();

        // Act
        PasswordResetToken savedToken = passwordResetTokenRepository.save(newToken);

        // Assert
        assertNotNull(savedToken.getId());
        assertEquals("new-token", savedToken.getToken());
        assertEquals(testUser.getId(), savedToken.getUser().getId());

        Optional<PasswordResetToken> retrievedToken = passwordResetTokenRepository.findByToken("new-token");
        assertTrue(retrievedToken.isPresent());
        assertEquals(savedToken.getId(), retrievedToken.get().getId());
    }

    @Test
    void save_WithUpdatedToken_UpdatesToken() {
        // Arrange
        validToken.setIsUsed(true);
        validToken.setUsedAt(LocalDateTime.now());

        // Act
        PasswordResetToken updatedToken = passwordResetTokenRepository.save(validToken);

        // Assert
        assertTrue(updatedToken.getIsUsed());
        assertNotNull(updatedToken.getUsedAt());

        Optional<PasswordResetToken> retrievedToken = passwordResetTokenRepository.findByToken("valid-token");
        assertTrue(retrievedToken.isPresent());
        assertTrue(retrievedToken.get().getIsUsed());
        assertNotNull(retrievedToken.get().getUsedAt());
    }

    @Test
    void findValidTokensByUserId_WithMultipleValidTokens_ReturnsAllValidTokens() {
        // Arrange - Create another valid token for the same user
        PasswordResetToken anotherValidToken = PasswordResetToken.builder()
                .token("another-valid-token")
                .user(testUser)
                .createdAt(LocalDateTime.now().minusMinutes(2))
                .expiresAt(LocalDateTime.now().plusMinutes(28))
                .isUsed(false)
                .build();
        entityManager.persistAndFlush(anotherValidToken);

        // Act
        List<PasswordResetToken> result = passwordResetTokenRepository.findValidTokensByUserId(
                testUser.getId(), LocalDateTime.now());

        // Assert
        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(token -> "valid-token".equals(token.getToken())));
        assertTrue(result.stream().anyMatch(token -> "another-valid-token".equals(token.getToken())));
    }
}