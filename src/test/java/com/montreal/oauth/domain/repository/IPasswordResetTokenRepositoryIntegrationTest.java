package com.montreal.oauth.domain.repository;

import com.montreal.oauth.domain.entity.PasswordResetToken;
import com.montreal.oauth.domain.entity.UserInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IPasswordResetTokenRepositoryIntegrationTest {

    @Mock
    private IPasswordResetTokenRepository passwordResetTokenRepository;

    @Mock
    private IUserRepository userRepository;

    private UserInfo testUser;
    private PasswordResetToken testToken;

    @BeforeEach
    void setUp() {
        // Create test user
        testUser = new UserInfo();
        testUser.setId(1L);
        testUser.setUsername("test@example.com");
        testUser.setPassword("password123");
        testUser.setFullName("Test User");
        testUser.setCpf("12345678901");
        testUser.setEmail("test@example.com");
        testUser.setEnabled(true);

        // Create test token
        testToken = PasswordResetToken.builder()
                .id(1L)
                .token("test-token-123")
                .user(testUser)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(30))
                .isUsed(false)
                .build();
    }

    @Test
    void findByToken_ExistingToken_ReturnsToken() {
        // Arrange
        when(passwordResetTokenRepository.findByToken("test-token-123"))
                .thenReturn(Optional.of(testToken));

        // Act
        Optional<PasswordResetToken> result = passwordResetTokenRepository.findByToken("test-token-123");

        // Assert
        assertTrue(result.isPresent());
        assertEquals("test-token-123", result.get().getToken());
        assertEquals(testUser, result.get().getUser());

        verify(passwordResetTokenRepository).findByToken("test-token-123");
    }

    @Test
    void findByToken_NonExistingToken_ReturnsEmpty() {
        // Arrange
        when(passwordResetTokenRepository.findByToken("non-existing-token"))
                .thenReturn(Optional.empty());

        // Act
        Optional<PasswordResetToken> result = passwordResetTokenRepository.findByToken("non-existing-token");

        // Assert
        assertFalse(result.isPresent());

        verify(passwordResetTokenRepository).findByToken("non-existing-token");
    }

    @Test
    void findByUser_Id_ExistingUser_ReturnsTokens() {
        // Arrange
        List<PasswordResetToken> tokens = List.of(testToken);
        when(passwordResetTokenRepository.findByUser_Id(1L))
                .thenReturn(tokens);

        // Act
        List<PasswordResetToken> result = passwordResetTokenRepository.findByUser_Id(1L);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(testToken, result.get(0));

        verify(passwordResetTokenRepository).findByUser_Id(1L);
    }

    @Test
    void findValidTokensByUserId_ValidTokens_ReturnsTokens() {
        // Arrange
        LocalDateTime now = LocalDateTime.now();
        List<PasswordResetToken> tokens = List.of(testToken);
        when(passwordResetTokenRepository.findValidTokensByUserId(1L, now))
                .thenReturn(tokens);

        // Act
        List<PasswordResetToken> result = passwordResetTokenRepository.findValidTokensByUserId(1L, now);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(testToken, result.get(0));

        verify(passwordResetTokenRepository).findValidTokensByUserId(1L, now);
    }

    @Test
    void findExpiredUnusedTokens_ExpiredTokens_ReturnsTokens() {
        // Arrange
        LocalDateTime now = LocalDateTime.now();
        PasswordResetToken expiredToken = PasswordResetToken.builder()
                .id(2L)
                .token("expired-token")
                .user(testUser)
                .createdAt(now.minusHours(2))
                .expiresAt(now.minusHours(1))
                .isUsed(false)
                .build();

        List<PasswordResetToken> tokens = List.of(expiredToken);
        when(passwordResetTokenRepository.findExpiredUnusedTokens(now))
                .thenReturn(tokens);

        // Act
        List<PasswordResetToken> result = passwordResetTokenRepository.findExpiredUnusedTokens(now);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(expiredToken, result.get(0));

        verify(passwordResetTokenRepository).findExpiredUnusedTokens(now);
    }

    @Test
    void existsValidTokenByUserId_ValidTokenExists_ReturnsTrue() {
        // Arrange
        LocalDateTime now = LocalDateTime.now();
        when(passwordResetTokenRepository.existsValidTokenByUserId(1L, now))
                .thenReturn(true);

        // Act
        boolean result = passwordResetTokenRepository.existsValidTokenByUserId(1L, now);

        // Assert
        assertTrue(result);

        verify(passwordResetTokenRepository).existsValidTokenByUserId(1L, now);
    }

    @Test
    void existsValidTokenByUserId_NoValidToken_ReturnsFalse() {
        // Arrange
        LocalDateTime now = LocalDateTime.now();
        when(passwordResetTokenRepository.existsValidTokenByUserId(1L, now))
                .thenReturn(false);

        // Act
        boolean result = passwordResetTokenRepository.existsValidTokenByUserId(1L, now);

        // Assert
        assertFalse(result);

        verify(passwordResetTokenRepository).existsValidTokenByUserId(1L, now);
    }

    @Test
    void deleteByUser_Id_ExistingUser_DeletesAllTokens() {
        // Arrange
        doNothing().when(passwordResetTokenRepository).deleteByUser_Id(1L);

        // Act
        passwordResetTokenRepository.deleteByUser_Id(1L);

        // Assert
        verify(passwordResetTokenRepository).deleteByUser_Id(1L);
    }

    @Test
    void deleteByExpiresAtBefore_ExpiredTokens_DeletesExpiredTokens() {
        // Arrange
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(1);
        doNothing().when(passwordResetTokenRepository).deleteByExpiresAtBefore(cutoffDate);

        // Act
        passwordResetTokenRepository.deleteByExpiresAtBefore(cutoffDate);

        // Assert
        verify(passwordResetTokenRepository).deleteByExpiresAtBefore(cutoffDate);
    }
}