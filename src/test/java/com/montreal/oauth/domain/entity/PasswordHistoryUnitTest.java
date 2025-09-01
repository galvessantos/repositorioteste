package com.montreal.oauth.domain.entity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class PasswordHistoryUnitTest {

    private PasswordHistory passwordHistory;
    private UserInfo mockUser;

    @BeforeEach
    void setUp() {
        mockUser = new UserInfo();
        mockUser.setId(1L);
        mockUser.setUsername("testuser");

        passwordHistory = PasswordHistory.builder()
                .id(1L)
                .user(mockUser)
                .passwordHash("$2a$10$encodedpasswordhash")
                .createdAt(LocalDateTime.now().minusDays(1))
                .build();
    }

    @Test
    void builder_CreatesValidPasswordHistory() {
        // Act
        PasswordHistory history = PasswordHistory.builder()
                .id(1L)
                .user(mockUser)
                .passwordHash("$2a$10$hash")
                .createdAt(LocalDateTime.now())
                .build();

        // Assert
        assertNotNull(history);
        assertEquals(1L, history.getId());
        assertEquals(mockUser, history.getUser());
        assertEquals("$2a$10$hash", history.getPasswordHash());
        assertNotNull(history.getCreatedAt());
    }

    @Test
    void noArgsConstructor_CreatesEmptyPasswordHistory() {
        // Act
        PasswordHistory history = new PasswordHistory();

        // Assert
        assertNotNull(history);
        assertNull(history.getId());
        assertNull(history.getUser());
        assertNull(history.getPasswordHash());
        assertNull(history.getCreatedAt());
    }

    @Test
    void allArgsConstructor_CreatesPasswordHistoryWithAllFields() {
        // Arrange
        LocalDateTime createdAt = LocalDateTime.now().minusDays(1);

        // Act
        PasswordHistory history = new PasswordHistory(
                1L, mockUser, "$2a$10$hash", createdAt
        );

        // Assert
        assertNotNull(history);
        assertEquals(1L, history.getId());
        assertEquals(mockUser, history.getUser());
        assertEquals("$2a$10$hash", history.getPasswordHash());
        assertEquals(createdAt, history.getCreatedAt());
    }

    @Test
    void settersAndGetters_WorkCorrectly() {
        // Arrange
        PasswordHistory history = new PasswordHistory();
        LocalDateTime now = LocalDateTime.now();
        UserInfo newUser = new UserInfo();
        newUser.setId(2L);

        // Act
        history.setId(2L);
        history.setUser(newUser);
        history.setPasswordHash("$2a$10$newhash");
        history.setCreatedAt(now);

        // Assert
        assertEquals(2L, history.getId());
        assertEquals(newUser, history.getUser());
        assertEquals("$2a$10$newhash", history.getPasswordHash());
        assertEquals(now, history.getCreatedAt());
    }

    @Test
    void equals_WithSamePasswordHistory_ReturnsTrue() {
        // Arrange
        PasswordHistory history1 = PasswordHistory.builder()
                .id(1L)
                .user(mockUser)
                .passwordHash("$2a$10$hash")
                .build();

        PasswordHistory history2 = PasswordHistory.builder()
                .id(1L)
                .user(mockUser)
                .passwordHash("$2a$10$hash")
                .build();

        // Act & Assert
        assertEquals(history1, history2);
    }

    @Test
    void equals_WithDifferentPasswordHistory_ReturnsFalse() {
        // Arrange
        PasswordHistory history1 = PasswordHistory.builder()
                .id(1L)
                .user(mockUser)
                .passwordHash("$2a$10$hash1")
                .build();

        PasswordHistory history2 = PasswordHistory.builder()
                .id(1L)
                .user(mockUser)
                .passwordHash("$2a$10$hash2")
                .build();

        // Act & Assert
        assertNotEquals(history1, history2);
    }

    @Test
    void hashCode_WithSamePasswordHistory_ReturnsSameHash() {
        // Arrange
        PasswordHistory history1 = PasswordHistory.builder()
                .id(1L)
                .user(mockUser)
                .passwordHash("$2a$10$hash")
                .build();

        PasswordHistory history2 = PasswordHistory.builder()
                .id(1L)
                .user(mockUser)
                .passwordHash("$2a$10$hash")
                .build();

        // Act & Assert
        assertEquals(history1.hashCode(), history2.hashCode());
    }

    @Test
    void toString_ContainsPasswordHistoryInformation() {
        // Act
        String toString = passwordHistory.toString();

        // Assert
        assertNotNull(toString);
        assertTrue(toString.contains("PasswordHistory"));
    }

    @Test
    void builder_WithNullUser_AllowsNullUser() {
        // Act
        PasswordHistory history = PasswordHistory.builder()
                .id(1L)
                .user(null)
                .passwordHash("$2a$10$hash")
                .createdAt(LocalDateTime.now())
                .build();

        // Assert
        assertNotNull(history);
        assertNull(history.getUser());
        assertEquals("$2a$10$hash", history.getPasswordHash());
    }

    @Test
    void builder_WithNullPasswordHash_AllowsNullPasswordHash() {
        // Act
        PasswordHistory history = PasswordHistory.builder()
                .id(1L)
                .user(mockUser)
                .passwordHash(null)
                .createdAt(LocalDateTime.now())
                .build();

        // Assert
        assertNotNull(history);
        assertEquals(mockUser, history.getUser());
        assertNull(history.getPasswordHash());
    }

    @Test
    void builder_WithNullCreatedAt_AllowsNullCreatedAt() {
        // Act
        PasswordHistory history = PasswordHistory.builder()
                .id(1L)
                .user(mockUser)
                .passwordHash("$2a$10$hash")
                .createdAt(null)
                .build();

        // Assert
        assertNotNull(history);
        assertEquals(mockUser, history.getUser());
        assertNull(history.getCreatedAt());
    }

    @Test
    void builder_WithEmptyPasswordHash_AllowsEmptyPasswordHash() {
        // Act
        PasswordHistory history = PasswordHistory.builder()
                .id(1L)
                .user(mockUser)
                .passwordHash("")
                .createdAt(LocalDateTime.now())
                .build();

        // Assert
        assertNotNull(history);
        assertEquals("", history.getPasswordHash());
    }

    @Test
    void equals_WithNullObject_ReturnsFalse() {
        // Arrange
        PasswordHistory history = PasswordHistory.builder()
                .id(1L)
                .user(mockUser)
                .passwordHash("$2a$10$hash")
                .build();

        // Act & Assert
        assertNotEquals(history, null);
    }

    @Test
    void equals_WithDifferentClass_ReturnsFalse() {
        // Arrange
        PasswordHistory history = PasswordHistory.builder()
                .id(1L)
                .user(mockUser)
                .passwordHash("$2a$10$hash")
                .build();

        String differentObject = "not a PasswordHistory";

        // Act & Assert
        assertNotEquals(history, differentObject);
    }

    @Test
    void equals_WithSameObject_ReturnsTrue() {
        // Act & Assert
        assertEquals(passwordHistory, passwordHistory);
    }

    @Test
    void hashCode_WithNullFields_DoesNotThrowException() {
        // Arrange
        PasswordHistory history = new PasswordHistory();
        history.setId(null);
        history.setUser(null);
        history.setPasswordHash(null);
        history.setCreatedAt(null);

        // Act & Assert
        assertDoesNotThrow(() -> {
            history.hashCode();
        });
    }

    @Test
    void toString_WithNullFields_DoesNotThrowException() {
        // Arrange
        PasswordHistory history = new PasswordHistory();
        history.setId(null);
        history.setUser(null);
        history.setPasswordHash(null);
        history.setCreatedAt(null);

        // Act & Assert
        assertDoesNotThrow(() -> {
            history.toString();
        });
    }
}