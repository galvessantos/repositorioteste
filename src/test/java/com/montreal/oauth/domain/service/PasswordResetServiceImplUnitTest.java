package com.montreal.oauth.domain.service;

import com.montreal.core.domain.exception.UserNotFoundException;
import com.montreal.oauth.domain.entity.PasswordResetToken;
import com.montreal.oauth.domain.entity.UserInfo;
import com.montreal.oauth.domain.repository.IPasswordResetTokenRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
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
class PasswordResetServiceImplUnitTest {

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

        ReflectionTestUtils.setField(passwordResetService, "tokenExpirationMinutes", 30);
        ReflectionTestUtils.setField(passwordResetService, "baseUrl", "https://localhost");
    }

    @Test
    void generatePasswordResetToken_WithValidUsername_ShouldReturnResetLink() {
        String login = "testuser";
        when(userRepository.findByUsername(login)).thenReturn(testUser);
        when(passwordResetTokenRepository.findValidTokensByUserId(anyLong(), any(LocalDateTime.class)))
                .thenReturn(Arrays.asList());
        when(passwordResetTokenRepository.save(any(PasswordResetToken.class))).thenReturn(testToken);

        String result = passwordResetService.generatePasswordResetToken(login);

        assertNotNull(result);
        assertTrue(result.contains("https://localhost/reset-password?token="));
        verify(userRepository).findByUsername(login);
        verify(passwordResetTokenRepository).save(any(PasswordResetToken.class));
    }



    @Test
    void generatePasswordResetToken_WithInvalidLogin_ShouldThrowUserNotFoundException() {
        String login = "invaliduser";
        when(userRepository.findByUsername(login)).thenReturn(null);

        assertThrows(UserNotFoundException.class, () -> {
            passwordResetService.generatePasswordResetToken(login);
        });
        verify(userRepository).findByUsername(login);
    }

    @Test
    void generatePasswordResetToken_ShouldInvalidateExistingTokens() {
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

        passwordResetService.generatePasswordResetToken(login);

        verify(passwordResetTokenRepository).save(existingToken);
        assertTrue(existingToken.isUsed());
        assertNotNull(existingToken.getUsedAt());
    }

    @Test
    void validatePasswordResetToken_WithValidToken_ShouldReturnTrue() {
        String token = "valid-token";
        when(passwordResetTokenRepository.findByToken(token)).thenReturn(Optional.of(testToken));

        boolean result = passwordResetService.validatePasswordResetToken(token);

        assertTrue(result);
        verify(passwordResetTokenRepository).findByToken(token);
    }

    @Test
    void validatePasswordResetToken_WithExpiredToken_ShouldReturnFalse() {
        String token = "expired-token";
        testToken.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(passwordResetTokenRepository.findByToken(token)).thenReturn(Optional.of(testToken));

        boolean result = passwordResetService.validatePasswordResetToken(token);

        assertFalse(result);
        verify(passwordResetTokenRepository).findByToken(token);
    }

    @Test
    void validatePasswordResetToken_WithUsedToken_ShouldReturnFalse() {
        String token = "used-token";
        testToken.setUsed(true);
        testToken.setUsedAt(LocalDateTime.now());
        when(passwordResetTokenRepository.findByToken(token)).thenReturn(Optional.of(testToken));

        boolean result = passwordResetService.validatePasswordResetToken(token);

        assertFalse(result);
        verify(passwordResetTokenRepository).findByToken(token);
    }

    @Test
    void validatePasswordResetToken_WithNonExistentToken_ShouldReturnFalse() {
        String token = "non-existent-token";
        when(passwordResetTokenRepository.findByToken(token)).thenReturn(Optional.empty());

        boolean result = passwordResetService.validatePasswordResetToken(token);

        assertFalse(result);
        verify(passwordResetTokenRepository).findByToken(token);
    }

    @Test
    void markTokenAsUsed_WithValidToken_ShouldMarkAsUsed() {
        String token = "valid-token";
        when(passwordResetTokenRepository.findByToken(token)).thenReturn(Optional.of(testToken));
        when(passwordResetTokenRepository.save(any(PasswordResetToken.class))).thenReturn(testToken);

        passwordResetService.markTokenAsUsed(token);

        assertTrue(testToken.isUsed());
        assertNotNull(testToken.getUsedAt());
        verify(passwordResetTokenRepository).save(testToken);
    }

    @Test
    void markTokenAsUsed_WithNonExistentToken_ShouldNotThrowException() {
        String token = "non-existent-token";
        when(passwordResetTokenRepository.findByToken(token)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> passwordResetService.markTokenAsUsed(token));
        verify(passwordResetTokenRepository).findByToken(token);
        verify(passwordResetTokenRepository, never()).save(any(PasswordResetToken.class));
    }

    @Test
    void cleanupExpiredTokens_ShouldCallRepository() {
        LocalDateTime now = LocalDateTime.now();

        passwordResetService.cleanupExpiredTokens();

        verify(passwordResetTokenRepository).deleteByExpiresAtBefore(any(LocalDateTime.class));
    }

    @Test
    void findByToken_ShouldReturnOptional() {
        String token = "test-token";
        when(passwordResetTokenRepository.findByToken(token)).thenReturn(Optional.of(testToken));

        Optional<PasswordResetToken> result = passwordResetService.findByToken(token);

        assertTrue(result.isPresent());
        assertEquals(testToken, result.get());
        verify(passwordResetTokenRepository).findByToken(token);
    }

    @Test
    void resetPassword_WithValidTokenAndValidPassword_ShouldReturnTrue() {
        String token = "valid-token";
        String newPassword = "Test@123";
        String confirmPassword = "Test@123";
        
        when(passwordResetTokenRepository.findByToken(token)).thenReturn(Optional.of(testToken));
        when(userRepository.save(any(UserInfo.class))).thenReturn(testUser);
        when(passwordResetTokenRepository.save(any(PasswordResetToken.class))).thenReturn(testToken);

        boolean result = passwordResetService.resetPassword(token, newPassword, confirmPassword);

        assertTrue(result);
        verify(userRepository).save(any(UserInfo.class));
        verify(passwordResetTokenRepository).save(any(PasswordResetToken.class));
    }

    @Test
    void resetPassword_WithInvalidToken_ShouldReturnFalse() {
        String token = "invalid-token";
        String newPassword = "Test@123";
        String confirmPassword = "Test@123";

        when(passwordResetTokenRepository.findByToken(token)).thenReturn(Optional.empty());

        boolean result = passwordResetService.resetPassword(token, newPassword, confirmPassword);

        assertFalse(result);
        verify(userRepository, never()).save(any(UserInfo.class));
    }

    @Test
    void resetPassword_WithExpiredToken_ShouldReturnFalse() {
        String token = "expired-token";
        String newPassword = "Test@123";
        String confirmPassword = "Test@123";
        
        testToken.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(passwordResetTokenRepository.findByToken(token)).thenReturn(Optional.of(testToken));

        boolean result = passwordResetService.resetPassword(token, newPassword, confirmPassword);

        assertFalse(result);
        verify(userRepository, never()).save(any(UserInfo.class));
    }

    @Test
    void resetPassword_WithUsedToken_ShouldReturnFalse() {
        String token = "used-token";
        String newPassword = "Test@123";
        String confirmPassword = "Test@123";
        
        testToken.setUsed(true);
        when(passwordResetTokenRepository.findByToken(token)).thenReturn(Optional.of(testToken));

        boolean result = passwordResetService.resetPassword(token, newPassword, confirmPassword);

        assertFalse(result);
        verify(userRepository, never()).save(any(UserInfo.class));
    }

    @Test
    void resetPassword_WithInvalidPassword_ShouldThrowException() {
        String token = "valid-token";
        String invalidPassword = "weak";
        String confirmPassword = "weak";

        // Criar um token novo para este teste
        PasswordResetToken freshToken = PasswordResetToken.builder()
                .id(1L)
                .token(token)
                .user(testUser)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(30))
                .isUsed(false)
                .build();

        when(passwordResetTokenRepository.findByToken(token)).thenReturn(Optional.of(freshToken));

        assertThrows(IllegalArgumentException.class, () -> {
            passwordResetService.resetPassword(token, invalidPassword, confirmPassword);
        });
        
        verify(userRepository, never()).save(any(UserInfo.class));
    }

    @Test
    void resetPassword_WithPasswordTooShort_ShouldThrowException() {
        String token = "valid-token";
        String shortPassword = "Ab@1";
        String confirmPassword = "Ab@1";

        // Criar um token novo para este teste
        PasswordResetToken freshToken = PasswordResetToken.builder()
                .id(1L)
                .token(token)
                .user(testUser)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(30))
                .isUsed(false)
                .build();

        when(passwordResetTokenRepository.findByToken(token)).thenReturn(Optional.of(freshToken));

        assertThrows(IllegalArgumentException.class, () -> {
            passwordResetService.resetPassword(token, shortPassword, confirmPassword);
        });
    }

    @Test
    void resetPassword_WithPasswordTooLong_ShouldThrowException() {
        String token = "valid-token";
        String longPassword = "Ab@12345";
        String confirmPassword = "Ab@12345";

        // Criar um token novo para este teste
        PasswordResetToken freshToken = PasswordResetToken.builder()
                .id(1L)
                .token(token)
                .user(testUser)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(30))
                .isUsed(false)
                .build();

        when(passwordResetTokenRepository.findByToken(token)).thenReturn(Optional.of(freshToken));

        assertThrows(IllegalArgumentException.class, () -> {
            passwordResetService.resetPassword(token, longPassword, confirmPassword);
        });
    }

    @Test
    void resetPassword_WithPasswordWithoutLowerCase_ShouldThrowException() {
        String token = "valid-token";
        String passwordWithoutLower = "TEST@12";
        String confirmPassword = "TEST@12";

        // Criar um token novo para este teste
        PasswordResetToken freshToken = PasswordResetToken.builder()
                .id(1L)
                .token(token)
                .user(testUser)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(30))
                .isUsed(false)
                .build();

        when(passwordResetTokenRepository.findByToken(token)).thenReturn(Optional.of(freshToken));

        assertThrows(IllegalArgumentException.class, () -> {
            passwordResetService.resetPassword(token, passwordWithoutLower, confirmPassword);
        });
    }

    @Test
    void resetPassword_WithPasswordWithoutUpperCase_ShouldThrowException() {
        String token = "valid-token";
        String passwordWithoutUpper = "test@12";
        String confirmPassword = "test@12";

        // Criar um token novo para este teste
        PasswordResetToken freshToken = PasswordResetToken.builder()
                .id(1L)
                .token(token)
                .user(testUser)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(30))
                .isUsed(false)
                .build();

        when(passwordResetTokenRepository.findByToken(token)).thenReturn(Optional.of(freshToken));

        assertThrows(IllegalArgumentException.class, () -> {
            passwordResetService.resetPassword(token, passwordWithoutUpper, confirmPassword);
        });
    }

    @Test
    void resetPassword_WithPasswordWithoutNumber_ShouldThrowException() {
        String token = "valid-token";
        String passwordWithoutNumber = "Test@ab";
        String confirmPassword = "Test@ab";

        // Criar um token novo para este teste
        PasswordResetToken freshToken = PasswordResetToken.builder()
                .id(1L)
                .token(token)
                .user(testUser)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(30))
                .isUsed(false)
                .build();

        when(passwordResetTokenRepository.findByToken(token)).thenReturn(Optional.of(freshToken));

        assertThrows(IllegalArgumentException.class, () -> {
            passwordResetService.resetPassword(token, passwordWithoutNumber, confirmPassword);
        });
    }

    @Test
    void resetPassword_WithPasswordWithoutSpecialChar_ShouldThrowException() {
        String token = "valid-token";
        String passwordWithoutSpecial = "Test123";
        String confirmPassword = "Test123";

        // Criar um token novo para este teste
        PasswordResetToken freshToken = PasswordResetToken.builder()
                .id(1L)
                .token(token)
                .user(testUser)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(30))
                .isUsed(false)
                .build();

        when(passwordResetTokenRepository.findByToken(token)).thenReturn(Optional.of(freshToken));

        assertThrows(IllegalArgumentException.class, () -> {
            passwordResetService.resetPassword(token, passwordWithoutSpecial, confirmPassword);
        });
    }

    @Test
    void resetPassword_ShouldUpdateUserPasswordAndMarkTokenAsUsed() {
        String token = "valid-token";
        String newPassword = "Test@123";
        String confirmPassword = "Test@123";
        
        when(passwordResetTokenRepository.findByToken(token)).thenReturn(Optional.of(testToken));
        when(userRepository.save(any(UserInfo.class))).thenReturn(testToken.getUser());
        when(passwordResetTokenRepository.save(any(PasswordResetToken.class))).thenReturn(testToken);

        boolean result = passwordResetService.resetPassword(token, newPassword, confirmPassword);

        assertTrue(result);
        
        // Verificar se o usuário foi salvo com a nova senha criptografada
        verify(userRepository).save(argThat(user -> {
            BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
            return encoder.matches(newPassword, user.getPassword()) &&
                   user.isPasswordChangedByUser() &&
                   user.isEnabled();
        }));
        
        // Verificar se o token foi marcado como usado
        verify(passwordResetTokenRepository).save(argThat(tokenEntity -> 
            tokenEntity.isUsed() && tokenEntity.getUsedAt() != null
        ));
    }

    @Test
    void resetPassword_WithPasswordMismatch_ShouldThrowException() {
        String token = "valid-token";
        String newPassword = "Test@123";
        String confirmPassword = "Test@456";

        // Criar um token novo para este teste
        PasswordResetToken freshToken = PasswordResetToken.builder()
                .id(1L)
                .token(token)
                .user(testUser)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(30))
                .isUsed(false)
                .build();

        when(passwordResetTokenRepository.findByToken(token)).thenReturn(Optional.of(freshToken));

        assertThrows(IllegalArgumentException.class, () -> {
            passwordResetService.resetPassword(token, newPassword, confirmPassword);
        });
        
        verify(userRepository, never()).save(any(UserInfo.class));
    }

    @Test
    void resetPassword_WithEmptyConfirmPassword_ShouldThrowException() {
        String token = "valid-token";
        String newPassword = "Test@123";
        String confirmPassword = "";

        // Criar um token novo para este teste
        PasswordResetToken freshToken = PasswordResetToken.builder()
                .id(1L)
                .token(token)
                .user(testUser)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(30))
                .isUsed(false)
                .build();

        when(passwordResetTokenRepository.findByToken(token)).thenReturn(Optional.of(freshToken));

        assertThrows(IllegalArgumentException.class, () -> {
            passwordResetService.resetPassword(token, newPassword, confirmPassword);
        });
        
        verify(userRepository, never()).save(any(UserInfo.class));
    }

    @Test
    void resetPassword_WithNullConfirmPassword_ShouldThrowException() {
        String token = "valid-token";
        String newPassword = "Test@123";
        String confirmPassword = null;

        // Criar um token novo para este teste
        PasswordResetToken freshToken = PasswordResetToken.builder()
                .id(1L)
                .token(token)
                .user(testUser)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(30))
                .isUsed(false)
                .build();

        when(passwordResetTokenRepository.findByToken(token)).thenReturn(Optional.of(freshToken));

        assertThrows(IllegalArgumentException.class, () -> {
            passwordResetService.resetPassword(token, newPassword, confirmPassword);
        });
        
        verify(userRepository, never()).save(any(UserInfo.class));
    }
}