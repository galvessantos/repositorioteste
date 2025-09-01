package com.montreal.oauth.domain.entity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class PasswordResetTokenUnitTest {

    private PasswordResetToken passwordResetToken;
    private UserInfo mockUser;

    @BeforeEach
    void setUp() {
        mockUser = new UserInfo();
        mockUser.setId(1L);
        mockUser.setUsername("testuser");

        passwordResetToken = PasswordResetToken.builder()
                .id(1L)
                .token("test-token-123")
                .user(mockUser)
                .createdAt(LocalDateTime.now().minusMinutes(10))
                .expiresAt(LocalDateTime.now().plusMinutes(20))
                .isUsed(false)
                .build();
    }

    @Test
    void isExpired_WhenExpiresAtIsInFuture_ReturnsFalse() {
        // Arrange
        passwordResetToken.setExpiresAt(LocalDateTime.now().plusMinutes(10));

        // Act
        boolean result = passwordResetToken.isExpired();

        // Assert
        assertFalse(result);
    }

    @Test
    void isExpired_WhenExpiresAtIsInPast_ReturnsTrue() {
        // Arrange
        passwordResetToken.setExpiresAt(LocalDateTime.now().minusMinutes(10));

        // Act
        boolean result = passwordResetToken.isExpired();

        // Assert
        assertTrue(result);
    }

    @Test
    void isExpired_WhenExpiresAtIsNow_ReturnsTrue() {
        // Arrange
        passwordResetToken.setExpiresAt(LocalDateTime.now());

        // Act
        boolean result = passwordResetToken.isExpired();

        // Assert
        assertTrue(result);
    }

    @Test
    void isValid_WhenNotExpiredAndNotUsed_ReturnsTrue() {
        // Arrange
        passwordResetToken.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        passwordResetToken.setIsUsed(false);

        // Act
        boolean result = passwordResetToken.isValid();

        // Assert
        assertTrue(result);
    }

    @Test
    void isValid_WhenExpiredAndNotUsed_ReturnsFalse() {
        // Arrange
        passwordResetToken.setExpiresAt(LocalDateTime.now().minusMinutes(10));
        passwordResetToken.setIsUsed(false);

        // Act
        boolean result = passwordResetToken.isValid();

        // Assert
        assertFalse(result);
    }

    @Test
    void isValid_WhenNotExpiredButUsed_ReturnsFalse() {
        // Arrange
        passwordResetToken.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        passwordResetToken.setIsUsed(true);

        // Act
        boolean result = passwordResetToken.isValid();

        // Assert
        assertFalse(result);
    }

    @Test
    void isValid_WhenExpiredAndUsed_ReturnsFalse() {
        // Arrange
        passwordResetToken.setExpiresAt(LocalDateTime.now().minusMinutes(10));
        passwordResetToken.setIsUsed(true);

        // Act
        boolean result = passwordResetToken.isValid();

        // Assert
        assertFalse(result);
    }

    @Test
    void builder_CreatesValidToken() {
        // Act
        PasswordResetToken token = PasswordResetToken.builder()
                .id(1L)
                .token("test-token")
                .user(mockUser)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(30))
                .isUsed(false)
                .build();

        // Assert
        assertNotNull(token);
        assertEquals(1L, token.getId());
        assertEquals("test-token", token.getToken());
        assertEquals(mockUser, token.getUser());
        assertNotNull(token.getCreatedAt());
        assertNotNull(token.getExpiresAt());
        assertFalse(token.getIsUsed());
        assertNull(token.getUsedAt());
    }

    @Test
    void builder_WithUsedToken_CreatesValidToken() {
        // Arrange
        LocalDateTime usedAt = LocalDateTime.now().minusMinutes(5);

        // Act
        PasswordResetToken token = PasswordResetToken.builder()
                .id(1L)
                .token("test-token")
                .user(mockUser)
                .createdAt(LocalDateTime.now().minusMinutes(10))
                .expiresAt(LocalDateTime.now().plusMinutes(20))
                .isUsed(true)
                .usedAt(usedAt)
                .build();

        // Assert
        assertNotNull(token);
        assertTrue(token.getIsUsed());
        assertEquals(usedAt, token.getUsedAt());
    }

    @Test
    void noArgsConstructor_CreatesEmptyToken() {
        // Act
        PasswordResetToken token = new PasswordResetToken();

        // Assert
        assertNotNull(token);
        assertNull(token.getId());
        assertNull(token.getToken());
        assertNull(token.getUser());
        assertNull(token.getCreatedAt());
        assertNull(token.getExpiresAt());
        assertFalse(token.getIsUsed()); // Default value
        assertNull(token.getUsedAt());
    }

    @Test
    void allArgsConstructor_CreatesTokenWithAllFields() {
        // Arrange
        LocalDateTime createdAt = LocalDateTime.now().minusMinutes(10);
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(20);
        LocalDateTime usedAt = LocalDateTime.now().minusMinutes(5);

        // Act
        PasswordResetToken token = new PasswordResetToken(
                1L, "test-token", mockUser, createdAt, expiresAt, usedAt, true
        );

        // Assert
        assertNotNull(token);
        assertEquals(1L, token.getId());
        assertEquals("test-token", token.getToken());
        assertEquals(mockUser, token.getUser());
        assertEquals(createdAt, token.getCreatedAt());
        assertEquals(expiresAt, token.getExpiresAt());
        assertEquals(usedAt, token.getUsedAt());
        assertTrue(token.getIsUsed());
    }

    @Test
    void settersAndGetters_WorkCorrectly() {
        // Arrange
        PasswordResetToken token = new PasswordResetToken();
        LocalDateTime now = LocalDateTime.now();

        // Act
        token.setId(2L);
        token.setToken("new-token");
        token.setUser(mockUser);
        token.setCreatedAt(now);
        token.setExpiresAt(now.plusMinutes(30));
        token.setUsedAt(now.plusMinutes(15));
        token.setIsUsed(true);

        // Assert
        assertEquals(2L, token.getId());
        assertEquals("new-token", token.getToken());
        assertEquals(mockUser, token.getUser());
        assertEquals(now, token.getCreatedAt());
        assertEquals(now.plusMinutes(30), token.getExpiresAt());
        assertEquals(now.plusMinutes(15), token.getUsedAt());
        assertTrue(token.getIsUsed());
    }

    @Test
    void toString_ContainsTokenInformation() {
        // Act
        String toString = passwordResetToken.toString();

        // Assert
        assertNotNull(toString);
        assertTrue(toString.contains("PasswordResetToken"));
        assertTrue(toString.contains("test-token-123"));
    }

    @Test
    void equals_WithSameToken_ReturnsTrue() {
        // Arrange
        PasswordResetToken token1 = PasswordResetToken.builder()
                .id(1L)
                .token("same-token")
                .user(mockUser)
                .build();

        PasswordResetToken token2 = PasswordResetToken.builder()
                .id(1L)
                .token("same-token")
                .user(mockUser)
                .build();

        // Act & Assert
        assertEquals(token1, token2);
    }

    @Test
    void equals_WithDifferentTokens_ReturnsFalse() {
        // Arrange
        PasswordResetToken token1 = PasswordResetToken.builder()
                .id(1L)
                .token("token1")
                .user(mockUser)
                .build();

        PasswordResetToken token2 = PasswordResetToken.builder()
                .id(1L)
                .token("token2")
                .user(mockUser)
                .build();

        // Act & Assert
        assertNotEquals(token1, token2);
    }

    @Test
    void hashCode_WithSameToken_ReturnsSameHash() {
        // Arrange
        PasswordResetToken token1 = PasswordResetToken.builder()
                .id(1L)
                .token("same-token")
                .user(mockUser)
                .build();

        PasswordResetToken token2 = PasswordResetToken.builder()
                .id(1L)
                .token("same-token")
                .user(mockUser)
                .build();

        // Act & Assert
        assertEquals(token1.hashCode(), token2.hashCode());
    }

    @Test
    void isExpired_WithNullExpiresAt_ThrowsException() {
        // Arrange
        passwordResetToken.setExpiresAt(null);

        // Act & Assert
        assertThrows(NullPointerException.class, () -> {
            passwordResetToken.isExpired();
        });
    }

    @Test
    void isValid_WithNullExpiresAt_ThrowsException() {
        // Arrange
        passwordResetToken.setExpiresAt(null);

        // Act & Assert
        assertThrows(NullPointerException.class, () -> {
            passwordResetToken.isValid();
        });
    }
}