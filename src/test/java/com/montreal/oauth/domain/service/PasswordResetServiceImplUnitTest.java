package com.montreal.oauth.domain.service;

import com.montreal.oauth.domain.dto.response.LoginResponseDTO;
import com.montreal.oauth.domain.dto.response.ResetPasswordResult;
import com.montreal.oauth.domain.entity.PasswordResetToken;
import com.montreal.oauth.domain.entity.RefreshToken;
import com.montreal.oauth.domain.entity.UserInfo;
import com.montreal.core.domain.exception.UserNotFoundException;
import com.montreal.oauth.domain.repository.IPasswordResetTokenRepository;
import com.montreal.oauth.domain.repository.IUserRepository;
import com.montreal.msiav_bh.entity.Company;
import com.montreal.msiav_bh.repository.CompanyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceImplUnitTest {

    @Mock
    private IPasswordResetTokenRepository passwordResetTokenRepository;

    @Mock
    private IUserRepository userRepository;

    @Mock
    private JwtService jwtService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private CompanyRepository companyRepository;

    @Mock
    private UserService userService;

    @Mock
    private PasswordHistoryService passwordHistoryService;

    @InjectMocks
    private PasswordResetServiceImpl passwordResetService;

    private UserInfo testUser;
    private PasswordResetToken testToken;

    @BeforeEach
    void setUp() {
        testUser = UserInfo.builder()
                .id(1L)
                .username("testuser@example.com")
                .email("testuser@example.com")
                .password("$2a$10$test.hash")
                .enabled(true)
                .build();

        testToken = PasswordResetToken.builder()
                .id(1L)
                .token("test-token-123")
                .user(testUser)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(30))
                .isUsed(false)
                .build();

        // Configurar valores padrão
        ReflectionTestUtils.setField(passwordResetService, "tokenExpirationMinutes", 30);
        ReflectionTestUtils.setField(passwordResetService, "baseUrl", "https://localhost:5173");
        ReflectionTestUtils.setField(passwordResetService, "autoLoginAfterReset", true);
    }

    @Test
    void generatePasswordResetToken_ValidLogin_ReturnsResetLink() {
        // Arrange
        String login = "testuser@example.com";
        when(userRepository.findByUsername(login)).thenReturn(testUser);
        when(passwordResetTokenRepository.save(any(PasswordResetToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        String result = passwordResetService.generatePasswordResetToken(login);

        // Assert
        assertNotNull(result);
        assertTrue(result.contains("token="));
        assertTrue(result.startsWith("https://localhost:5173"));

        verify(userRepository).findByUsername(login);
        verify(passwordResetTokenRepository).save(any(PasswordResetToken.class));
    }

    @Test
    void generatePasswordResetToken_InvalidLogin_ThrowsUserNotFoundException() {
        // Arrange
        String login = "nonexistent@example.com";
        when(userRepository.findByUsername(login)).thenReturn(null);

        // Act & Assert
        UserNotFoundException exception = assertThrows(UserNotFoundException.class, () ->
            passwordResetService.generatePasswordResetToken(login)
        );

        assertEquals("Login informado inválido", exception.getMessage());
        verify(userRepository).findByUsername(login);
        verifyNoInteractions(passwordResetTokenRepository);
    }

    @Test
    void generatePasswordResetToken_InvalidatesExistingTokens() {
        // Arrange
        String login = "testuser@example.com";
        when(userRepository.findByUsername(login)).thenReturn(testUser);
        when(passwordResetTokenRepository.save(any(PasswordResetToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        passwordResetService.generatePasswordResetToken(login);

        // Assert
        verify(passwordResetTokenRepository).invalidateTokensByUserId(testUser.getId());
    }

    @Test
    void validatePasswordResetToken_ValidToken_ReturnsTrue() {
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
    void validatePasswordResetToken_InvalidToken_ReturnsFalse() {
        // Arrange
        String token = "invalid-token";
        when(passwordResetTokenRepository.findByToken(token)).thenReturn(Optional.empty());

        // Act
        boolean result = passwordResetService.validatePasswordResetToken(token);

        // Assert
        assertFalse(result);
        verify(passwordResetTokenRepository).findByToken(token);
    }

    @Test
    void validatePasswordResetToken_ExpiredToken_ReturnsFalse() {
        // Arrange
        String token = "expired-token";
        PasswordResetToken expiredToken = PasswordResetToken.builder()
                .token(token)
                .user(testUser)
                .createdAt(LocalDateTime.now().minusHours(1))
                .expiresAt(LocalDateTime.now().minusMinutes(30))
                .isUsed(false)
                .build();
        when(passwordResetTokenRepository.findByToken(token)).thenReturn(Optional.of(expiredToken));

        // Act
        boolean result = passwordResetService.validatePasswordResetToken(token);

        // Assert
        assertFalse(result);
    }

    @Test
    void validatePasswordResetToken_UsedToken_ReturnsFalse() {
        // Arrange
        String token = "used-token";
        PasswordResetToken usedToken = PasswordResetToken.builder()
                .token(token)
                .user(testUser)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(30))
                .isUsed(true)
                .usedAt(LocalDateTime.now())
                .build();
        when(passwordResetTokenRepository.findByToken(token)).thenReturn(Optional.of(usedToken));

        // Act
        boolean result = passwordResetService.validatePasswordResetToken(token);

        // Assert
        assertFalse(result);
    }

    @Test
    void markTokenAsUsed_ValidToken_ShouldMarkAsUsed() {
        // Arrange
        String token = "test-token";
        when(passwordResetTokenRepository.findByToken(token)).thenReturn(Optional.of(testToken));

        // Act
        passwordResetService.markTokenAsUsed(token);

        // Assert
        verify(passwordResetTokenRepository).findByToken(token);
        verify(passwordResetTokenRepository).save(testToken);
        assertTrue(testToken.isUsed());
        assertNotNull(testToken.getUsedAt());
    }

    @Test
    void markTokenAsUsed_InvalidToken_ShouldNotThrowException() {
        // Arrange
        String token = "invalid-token";
        when(passwordResetTokenRepository.findByToken(token)).thenReturn(Optional.empty());

        // Act & Assert
        assertDoesNotThrow(() -> passwordResetService.markTokenAsUsed(token));
        verify(passwordResetTokenRepository).findByToken(token);
        verifyNoMoreInteractions(passwordResetTokenRepository);
    }

    @Test
    void cleanupExpiredTokens_ShouldCallRepository() {
        // Act
        passwordResetService.cleanupExpiredTokens();

        // Assert
        verify(passwordResetTokenRepository).deleteExpiredTokens();
    }

    @Test
    void resetPasswordWithTokens_ValidRequest_ReturnsSuccess() {
        // Arrange
        String token = "valid-token";
        String newPassword = "Test@123";
        String confirmPassword = "Test@123";

        when(passwordResetTokenRepository.findByToken(token)).thenReturn(Optional.of(testToken));
        when(userService.validatePasswordComplexity(newPassword)).thenReturn(true);
        when(userService.validatePasswordConfirmation(newPassword, confirmPassword)).thenReturn(true);
        when(userService.updateUserPassword(testUser, newPassword)).thenReturn(testUser);
        when(jwtService.generateToken(testUser)).thenReturn("access-token");
        when(refreshTokenService.createRefreshToken(testUser)).thenReturn("refresh-token");

        // Act
        ResetPasswordResult result = passwordResetService.resetPasswordWithTokens(token, newPassword, confirmPassword);

        // Assert
        assertTrue(result.isSuccess());
        assertEquals("Senha redefinida com sucesso", result.getMessage());
        assertEquals("access-token", result.getAccessToken());
        assertEquals("refresh-token", result.getRefreshToken());

        verify(passwordResetTokenRepository).findByToken(token);
        verify(userService).validatePasswordComplexity(newPassword);
        verify(userService).validatePasswordConfirmation(newPassword, confirmPassword);
        verify(userService).updateUserPassword(testUser, newPassword);
        verify(passwordResetTokenRepository).save(testToken);
        verify(jwtService).generateToken(testUser);
        verify(refreshTokenService).createRefreshToken(testUser);
    }

    @Test
    void resetPasswordWithTokens_InvalidToken_ReturnsFailure() {
        // Arrange
        String token = "invalid-token";
        String newPassword = "Test@123";
        String confirmPassword = "Test@123";

        when(passwordResetTokenRepository.findByToken(token)).thenReturn(Optional.empty());

        // Act
        ResetPasswordResult result = passwordResetService.resetPasswordWithTokens(token, newPassword, confirmPassword);

        // Assert
        assertFalse(result.isSuccess());
        assertEquals("Token inválido ou expirado", result.getMessage());
        assertNull(result.getAccessToken());
        assertNull(result.getRefreshToken());

        verify(passwordResetTokenRepository).findByToken(token);
        verifyNoMoreInteractions(userService, jwtService, refreshTokenService);
    }

    @Test
    void resetPasswordWithTokens_PasswordMismatch_ReturnsFailure() {
        // Arrange
        String token = "valid-token";
        String newPassword = "Test@123";
        String confirmPassword = "Test@456";

        when(passwordResetTokenRepository.findByToken(token)).thenReturn(Optional.of(testToken));
        when(userService.validatePasswordConfirmation(newPassword, confirmPassword)).thenReturn(false);

        // Act
        ResetPasswordResult result = passwordResetService.resetPasswordWithTokens(token, newPassword, confirmPassword);

        // Assert
        assertFalse(result.isSuccess());
        assertEquals("As senhas não coincidem", result.getMessage());

        verify(passwordResetTokenRepository).findByToken(token);
        verify(userService).validatePasswordConfirmation(newPassword, confirmPassword);
        verifyNoMoreInteractions(userService);
    }

    @Test
    void resetPasswordWithTokens_WeakPassword_ReturnsFailure() {
        // Arrange
        String token = "valid-token";
        String newPassword = "weak";
        String confirmPassword = "weak";

        when(passwordResetTokenRepository.findByToken(token)).thenReturn(Optional.of(testToken));
        when(userService.validatePasswordComplexity(newPassword)).thenReturn(false);

        // Act
        ResetPasswordResult result = passwordResetService.resetPasswordWithTokens(token, newPassword, confirmPassword);

        // Assert
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("senha"));

        verify(passwordResetTokenRepository).findByToken(token);
        verify(userService).validatePasswordComplexity(newPassword);
        verifyNoMoreInteractions(userService);
    }
}
