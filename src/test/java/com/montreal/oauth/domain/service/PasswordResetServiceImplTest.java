package com.montreal.oauth.domain.service;

import com.montreal.core.domain.exception.UserNotFoundException;
import com.montreal.oauth.domain.entity.PasswordResetToken;
import com.montreal.oauth.domain.entity.UserInfo;
import com.montreal.oauth.domain.repository.IPasswordResetTokenRepository;
import com.montreal.oauth.domain.repository.IUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceImplTest {

    @Mock
    private IPasswordResetTokenRepository passwordResetTokenRepository;

    @Mock
    private IUserRepository userRepository;

    @InjectMocks
    private PasswordResetServiceImpl passwordResetService;

    private UserInfo testUser;
    private PasswordResetToken testToken;

    @BeforeEach
    void setUp() {
        testUser = new UserInfo();
        testUser.setId(1L);
        testUser.setUsername("testuser");
        testUser.setEmail("test@example.com");

        testToken = PasswordResetToken.builder()
                .id(1L)
                .token("test-token-123")
                .user(testUser)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(30))
                .isUsed(false)
                .build();

        // Set default configuration values
        ReflectionTestUtils.setField(passwordResetService, "tokenExpirationMinutes", 30);
        ReflectionTestUtils.setField(passwordResetService, "baseUrl", "https://localhost");
    }

    @Test
    void generatePasswordResetToken_WithValidUsername_ShouldReturnResetLink() {
        // Arrange
        String login = "testuser";
        when(userRepository.findByUsername(login)).thenReturn(testUser);
        when(passwordResetTokenRepository.findValidTokensByUserId(anyLong(), any(LocalDateTime.class)))
                .thenReturn(Arrays.asList());
        when(passwordResetTokenRepository.save(any(PasswordResetToken.class))).thenReturn(testToken);

        // Act
        String result = passwordResetService.generatePasswordResetToken(login);

        // Assert
        assertNotNull(result);
        assertTrue(result.contains("https://localhost/reset-password?token="));
        verify(userRepository).findByUsername(login);
        verify(passwordResetTokenRepository).save(any(PasswordResetToken.class));
    }



    @Test
    void generatePasswordResetToken_WithInvalidLogin_ShouldThrowUserNotFoundException() {
        // Arrange
        String login = "invaliduser";
        when(userRepository.findByUsername(login)).thenReturn(null);

        // Act & Assert
        assertThrows(UserNotFoundException.class, () -> {
            passwordResetService.generatePasswordResetToken(login);
        });
        verify(userRepository).findByUsername(login);
    }

    @Test
    void generatePasswordResetToken_ShouldInvalidateExistingTokens() {
        // Arrange
        String login = "testuser";
        PasswordResetToken existingToken = PasswordResetToken.builder()
                .id(2L)
                .token("existing-token")
                .user(testUser)
                .isUsed(false)
                .build();

        when(userRepository.findByUsername(login)).thenReturn(testUser);
        when(passwordResetTokenRepository.findValidTokensByUserId(anyLong(), any(LocalDateTime.class)))
                .thenReturn(Arrays.asList(existingToken));
        when(passwordResetTokenRepository.save(any(PasswordResetToken.class))).thenReturn(testToken);

        // Act
        passwordResetService.generatePasswordResetToken(login);

        // Assert
        verify(passwordResetTokenRepository).save(existingToken);
        assertTrue(existingToken.isUsed());
        assertNotNull(existingToken.getUsedAt());
    }

    @Test
    void validatePasswordResetToken_WithValidToken_ShouldReturnTrue() {
        // Arrange
        String token = "valid-token";
        when(passwordResetTokenRepository.findByToken(token)).thenReturn(Optional.of(testToken));

        // Act
        boolean result = passwordResetService.validatePasswordResetToken(token);

        // Assert
        assertTrue(result);
        verify(passwordResetTokenRepository).findByToken(token);
    }

    @Test
    void validatePasswordResetToken_WithExpiredToken_ShouldReturnFalse() {
        // Arrange
        String token = "expired-token";
        testToken.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(passwordResetTokenRepository.findByToken(token)).thenReturn(Optional.of(testToken));

        // Act
        boolean result = passwordResetService.validatePasswordResetToken(token);

        // Assert
        assertFalse(result);
        verify(passwordResetTokenRepository).findByToken(token);
    }

    @Test
    void validatePasswordResetToken_WithUsedToken_ShouldReturnFalse() {
        // Arrange
        String token = "used-token";
        testToken.setUsed(true);
        testToken.setUsedAt(LocalDateTime.now());
        when(passwordResetTokenRepository.findByToken(token)).thenReturn(Optional.of(testToken));

        // Act
        boolean result = passwordResetService.validatePasswordResetToken(token);

        // Assert
        assertFalse(result);
        verify(passwordResetTokenRepository).findByToken(token);
    }

    @Test
    void validatePasswordResetToken_WithNonExistentToken_ShouldReturnFalse() {
        // Arrange
        String token = "non-existent-token";
        when(passwordResetTokenRepository.findByToken(token)).thenReturn(Optional.empty());

        // Act
        boolean result = passwordResetService.validatePasswordResetToken(token);

        // Assert
        assertFalse(result);
        verify(passwordResetTokenRepository).findByToken(token);
    }

    @Test
    void markTokenAsUsed_WithValidToken_ShouldMarkAsUsed() {
        // Arrange
        String token = "valid-token";
        when(passwordResetTokenRepository.findByToken(token)).thenReturn(Optional.of(testToken));
        when(passwordResetTokenRepository.save(any(PasswordResetToken.class))).thenReturn(testToken);

        // Act
        passwordResetService.markTokenAsUsed(token);

        // Assert
        assertTrue(testToken.isUsed());
        assertNotNull(testToken.getUsedAt());
        verify(passwordResetTokenRepository).save(testToken);
    }

    @Test
    void markTokenAsUsed_WithNonExistentToken_ShouldNotThrowException() {
        // Arrange
        String token = "non-existent-token";
        when(passwordResetTokenRepository.findByToken(token)).thenReturn(Optional.empty());

        // Act & Assert
        assertDoesNotThrow(() -> passwordResetService.markTokenAsUsed(token));
        verify(passwordResetTokenRepository).findByToken(token);
        verify(passwordResetTokenRepository, never()).save(any(PasswordResetToken.class));
    }

    @Test
    void cleanupExpiredTokens_ShouldCallRepository() {
        // Arrange
        LocalDateTime now = LocalDateTime.now();

        // Act
        passwordResetService.cleanupExpiredTokens();

        // Assert
        verify(passwordResetTokenRepository).deleteByExpiresAtBefore(any(LocalDateTime.class));
    }

    @Test
    void findByToken_ShouldReturnOptional() {
        // Arrange
        String token = "test-token";
        when(passwordResetTokenRepository.findByToken(token)).thenReturn(Optional.of(testToken));

        // Act
        Optional<PasswordResetToken> result = passwordResetService.findByToken(token);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(testToken, result.get());
        verify(passwordResetTokenRepository).findByToken(token);
    }
}