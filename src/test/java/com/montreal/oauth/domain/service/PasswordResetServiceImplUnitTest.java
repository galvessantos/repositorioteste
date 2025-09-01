package com.montreal.oauth.domain.service;

import com.montreal.oauth.domain.dto.response.LoginResponseDTO;
import com.montreal.oauth.domain.dto.response.ResetPasswordResult;
import com.montreal.oauth.domain.entity.PasswordResetToken;
import com.montreal.oauth.domain.entity.UserInfo;
import com.montreal.oauth.domain.entity.Role;
import com.montreal.oauth.domain.entity.RefreshToken;
import com.montreal.oauth.domain.enumerations.RoleEnum;
import com.montreal.oauth.domain.repository.IPasswordResetTokenRepository;
import com.montreal.oauth.domain.repository.IUserRepository;
import com.montreal.core.domain.exception.UserNotFoundException;
import com.montreal.msiav_bh.entity.Company;
import com.montreal.msiav_bh.repository.CompanyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
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

    private UserInfo mockUser;
    private PasswordResetToken mockToken;
    private PasswordResetToken mockExpiredToken;
    private PasswordResetToken mockUsedToken;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(passwordResetService, "tokenExpirationMinutes", 30);
        ReflectionTestUtils.setField(passwordResetService, "baseUrl", "https://localhost");
        ReflectionTestUtils.setField(passwordResetService, "autoLoginAfterReset", true);

        mockUser = new UserInfo();
        mockUser.setId(1L);
        mockUser.setUsername("testuser");
        mockUser.setEmail("test@example.com");
        mockUser.setPassword("$2a$10$encoded.password.hash");
        mockUser.setEnabled(false);
        mockUser.setPasswordChangedByUser(false);

        Role userRole = new Role();
        userRole.setId(1);
        userRole.setName(RoleEnum.ROLE_USER);
        Set<Role> roles = new HashSet<>();
        roles.add(userRole);
        mockUser.setRoles(roles);

        mockToken = PasswordResetToken.builder()
                .id(1L)
                .token("valid-token")
                .user(mockUser)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(30))
                .isUsed(false)
                .build();

        mockExpiredToken = PasswordResetToken.builder()
                .id(2L)
                .token("expired-token")
                .user(mockUser)
                .createdAt(LocalDateTime.now().minusHours(2))
                .expiresAt(LocalDateTime.now().minusHours(1))
                .isUsed(false)
                .build();

        mockUsedToken = PasswordResetToken.builder()
                .id(3L)
                .token("used-token")
                .user(mockUser)
                .createdAt(LocalDateTime.now().minusMinutes(10))
                .expiresAt(LocalDateTime.now().plusMinutes(20))
                .isUsed(true)
                .usedAt(LocalDateTime.now().minusMinutes(5))
                .build();
    }

    @Test
    void generatePasswordResetToken_ValidUser_ReturnsResetLink() {
        String login = "testuser";
        when(userRepository.findByUsername(login)).thenReturn(mockUser);
        when(passwordResetTokenRepository.findValidTokensByUserId(eq(1L), any(LocalDateTime.class))).thenReturn(java.util.List.of());
        when(passwordResetTokenRepository.save(any(PasswordResetToken.class))).thenReturn(mockToken);

        String result = passwordResetService.generatePasswordResetToken(login);

        assertNotNull(result);
        assertTrue(result.startsWith("https://localhost/reset-password?token="));
        verify(userRepository).findByUsername(login);
        verify(passwordResetTokenRepository).save(any(PasswordResetToken.class));
    }

    @Test
    void generatePasswordResetToken_InvalidUser_ThrowsUserNotFoundException() {
        String login = "invaliduser";
        when(userRepository.findByUsername(login)).thenReturn(null);

        assertThrows(UserNotFoundException.class, () -> {
            passwordResetService.generatePasswordResetToken(login);
        });

        verify(userRepository).findByUsername(login);
        verify(passwordResetTokenRepository, never()).save(any(PasswordResetToken.class));
    }

    @Test
    void validatePasswordResetToken_ValidToken_ReturnsTrue() {
        when(passwordResetTokenRepository.findByToken("valid-token")).thenReturn(Optional.of(mockToken));

        boolean result = passwordResetService.validatePasswordResetToken("valid-token");

        assertTrue(result);
        verify(passwordResetTokenRepository).findByToken("valid-token");
    }

    @Test
    void validatePasswordResetToken_ExpiredToken_ReturnsFalse() {
        when(passwordResetTokenRepository.findByToken("expired-token")).thenReturn(Optional.of(mockExpiredToken));

        boolean result = passwordResetService.validatePasswordResetToken("expired-token");

        assertFalse(result);
        verify(passwordResetTokenRepository).findByToken("expired-token");
    }

    @Test
    void validatePasswordResetToken_UsedToken_ReturnsFalse() {
        when(passwordResetTokenRepository.findByToken("used-token")).thenReturn(Optional.of(mockUsedToken));

        boolean result = passwordResetService.validatePasswordResetToken("used-token");

        assertFalse(result);
        verify(passwordResetTokenRepository).findByToken("used-token");
    }

    @Test
    void validatePasswordResetToken_NonExistentToken_ReturnsFalse() {
        when(passwordResetTokenRepository.findByToken("non-existent-token")).thenReturn(Optional.empty());

        boolean result = passwordResetService.validatePasswordResetToken("non-existent-token");

        assertFalse(result);
        verify(passwordResetTokenRepository).findByToken("non-existent-token");
    }

    @Test
    void resetPassword_WithValidToken_ReturnsTrue() {
        String token = "valid-token";
        String newPassword = "Test@123";
        String confirmPassword = "Test@123";

        when(passwordResetTokenRepository.findByToken(token)).thenReturn(Optional.of(mockToken));
        when(userRepository.save(any(UserInfo.class))).thenReturn(mockUser);

        boolean result = passwordResetService.resetPassword(token, newPassword, confirmPassword);

        assertTrue(result);

        verify(passwordResetTokenRepository, times(3)).findByToken(token);
        verify(userRepository).save(any(UserInfo.class));
    }

    @Test
    void resetPassword_WithInvalidPassword_ReturnsFailure() {
        String token = "valid-token";
        String invalidPassword = "invalid";

        when(passwordResetTokenRepository.findByToken(token)).thenReturn(Optional.of(mockToken));

        ResetPasswordResult result = passwordResetService.resetPasswordWithTokens(token, invalidPassword, invalidPassword);

        assertFalse(result.isSuccess());
        assertEquals("A senha deve conter pelo menos uma letra maiúscula", result.getMessage());
    }

    @Test
    void resetPassword_WithPasswordTooShort_ReturnsFailure() {
        String token = "valid-token";
        String shortPassword = "ab@";

        when(passwordResetTokenRepository.findByToken(token)).thenReturn(Optional.of(mockToken));

        ResetPasswordResult result = passwordResetService.resetPasswordWithTokens(token, shortPassword, shortPassword);

        assertFalse(result.isSuccess());
        assertEquals("A senha deve ter entre 4 e 8 caracteres", result.getMessage());
    }

    @Test
    void resetPassword_WithPasswordTooLong_ReturnsFailure() {
        String token = "valid-token";
        String longPassword = "Test@123456";

        when(passwordResetTokenRepository.findByToken(token)).thenReturn(Optional.of(mockToken));

        ResetPasswordResult result = passwordResetService.resetPasswordWithTokens(token, longPassword, longPassword);

        assertFalse(result.isSuccess());
        assertEquals("A senha deve ter entre 4 e 8 caracteres", result.getMessage());
    }

    @Test
    void resetPassword_WithPasswordWithoutLowerCase_ReturnsFailure() {
        String token = "valid-token";
        String password = "TEST@123";

        when(passwordResetTokenRepository.findByToken(token)).thenReturn(Optional.of(mockToken));

        ResetPasswordResult result = passwordResetService.resetPasswordWithTokens(token, password, password);

        assertFalse(result.isSuccess());
        assertEquals("A senha deve conter pelo menos uma letra minúscula", result.getMessage());
    }

    @Test
    void resetPassword_WithPasswordWithoutUpperCase_ReturnsFailure() {
        String token = "valid-token";
        String password = "test@123";

        when(passwordResetTokenRepository.findByToken(token)).thenReturn(Optional.of(mockToken));

        ResetPasswordResult result = passwordResetService.resetPasswordWithTokens(token, password, password);

        assertFalse(result.isSuccess());
        assertEquals("A senha deve conter pelo menos uma letra maiúscula", result.getMessage());
    }

    @Test
    void resetPassword_WithPasswordWithoutNumber_ReturnsFailure() {
        String token = "valid-token";
        String password = "Test@abc";

        when(passwordResetTokenRepository.findByToken(token)).thenReturn(Optional.of(mockToken));

        ResetPasswordResult result = passwordResetService.resetPasswordWithTokens(token, password, password);

        assertFalse(result.isSuccess());
        assertEquals("A senha deve conter pelo menos um número", result.getMessage());
    }

    @Test
    void resetPassword_WithPasswordWithoutSpecialChar_ReturnsFailure() {
        String token = "valid-token";
        String password = "Test123";

        when(passwordResetTokenRepository.findByToken(token)).thenReturn(Optional.of(mockToken));

        ResetPasswordResult result = passwordResetService.resetPasswordWithTokens(token, password, password);

        assertFalse(result.isSuccess());
        assertEquals("A senha deve conter pelo menos um dos caracteres especiais: _ @ #", result.getMessage());
    }

    @Test
    void resetPassword_WithInvalidToken_ReturnsFailure() {
        String token = "invalid-token";
        String newPassword = "Test@123";
        String confirmPassword = "Test@123";

        when(passwordResetTokenRepository.findByToken(token)).thenReturn(Optional.empty());

        ResetPasswordResult result = passwordResetService.resetPasswordWithTokens(token, newPassword, confirmPassword);

        assertFalse(result.isSuccess());
        assertEquals("Token inválido ou expirado", result.getMessage());
        verify(passwordResetTokenRepository).findByToken(token);
    }

    @Test
    void resetPassword_WithExpiredToken_ReturnsFailure() {
        String token = "expired-token";
        String newPassword = "Test@123";
        String confirmPassword = "Test@123";

        when(passwordResetTokenRepository.findByToken(token)).thenReturn(Optional.of(mockExpiredToken));

        ResetPasswordResult result = passwordResetService.resetPasswordWithTokens(token, newPassword, confirmPassword);

        assertFalse(result.isSuccess());
        assertEquals("Token inválido ou expirado", result.getMessage());
    }

    @Test
    void resetPassword_WithUsedToken_ReturnsFailure() {
        String token = "used-token";
        String newPassword = "Test@123";
        String confirmPassword = "Test@123";

        when(passwordResetTokenRepository.findByToken(token)).thenReturn(Optional.of(mockUsedToken));

        ResetPasswordResult result = passwordResetService.resetPasswordWithTokens(token, newPassword, confirmPassword);

        assertFalse(result.isSuccess());
        assertEquals("Token inválido ou expirado", result.getMessage());
    }

    @Test
    void resetPassword_WithPasswordMismatch_ReturnsFailure() {
        String token = "valid-token";
        String newPassword = "Test@123";
        String confirmPassword = "Different@123";

        when(passwordResetTokenRepository.findByToken(token)).thenReturn(Optional.of(mockToken));

        ResetPasswordResult result = passwordResetService.resetPasswordWithTokens(token, newPassword, confirmPassword);

        assertFalse(result.isSuccess());
        assertEquals("As senhas não coincidem", result.getMessage());
    }

    @Test
    void resetPassword_WithEmptyConfirmPassword_ReturnsFailure() {
        String token = "valid-token";
        String newPassword = "Test@123";
        String confirmPassword = "";

        when(passwordResetTokenRepository.findByToken(token)).thenReturn(Optional.of(mockToken));

        ResetPasswordResult result = passwordResetService.resetPasswordWithTokens(token, newPassword, confirmPassword);

        assertFalse(result.isSuccess());
        assertEquals("Confirmação de senha é obrigatória", result.getMessage());
    }

    @Test
    void resetPassword_WithNullConfirmPassword_ReturnsFailure() {
        String token = "valid-token";
        String newPassword = "Test@123";
        String confirmPassword = null;

        when(passwordResetTokenRepository.findByToken(token)).thenReturn(Optional.of(mockToken));

        ResetPasswordResult result = passwordResetService.resetPasswordWithTokens(token, newPassword, confirmPassword);

        assertFalse(result.isSuccess());
        assertEquals("Confirmação de senha é obrigatória", result.getMessage());
    }

    @Test
    void markTokenAsUsed_ExistingToken_MarksTokenAsUsed() {
        String token = "valid-token";
        when(passwordResetTokenRepository.findByToken(token)).thenReturn(Optional.of(mockToken));
        when(passwordResetTokenRepository.save(any(PasswordResetToken.class))).thenReturn(mockToken);

        passwordResetService.markTokenAsUsed(token);

        verify(passwordResetTokenRepository).findByToken(token);
        verify(passwordResetTokenRepository).save(any(PasswordResetToken.class));
    }

    @Test
    void markTokenAsUsed_NonExistentToken_DoesNothing() {
        String token = "non-existent-token";
        when(passwordResetTokenRepository.findByToken(token)).thenReturn(Optional.empty());

        passwordResetService.markTokenAsUsed(token);

        verify(passwordResetTokenRepository).findByToken(token);
        verify(passwordResetTokenRepository, never()).save(any(PasswordResetToken.class));
    }

    @Test
    void findByToken_ExistingToken_ReturnsToken() {
        String token = "valid-token";
        when(passwordResetTokenRepository.findByToken(token)).thenReturn(Optional.of(mockToken));

        Optional<PasswordResetToken> result = passwordResetService.findByToken(token);

        assertTrue(result.isPresent());
        assertEquals(mockToken, result.get());
        verify(passwordResetTokenRepository).findByToken(token);
    }

    @Test
    void cleanupExpiredTokens_CallsRepository() {
        passwordResetService.cleanupExpiredTokens();

        verify(passwordResetTokenRepository).deleteByExpiresAtBefore(any(LocalDateTime.class));
    }

    @Test
    void generatePasswordResetToken_WithExistingValidTokens_InvalidatesExistingTokens() {
        // Arrange
        String login = "testuser";
        List<PasswordResetToken> existingTokens = List.of(mockToken);
        
        when(userRepository.findByUsername(login)).thenReturn(mockUser);
        when(passwordResetTokenRepository.findValidTokensByUserId(eq(1L), any(LocalDateTime.class)))
                .thenReturn(existingTokens);
        when(passwordResetTokenRepository.save(any(PasswordResetToken.class))).thenReturn(mockToken);

        // Act
        passwordResetService.generatePasswordResetToken(login);

        // Assert
        verify(passwordResetTokenRepository).findValidTokensByUserId(eq(1L), any(LocalDateTime.class));
        verify(passwordResetTokenRepository, times(2)).save(any(PasswordResetToken.class)); // One for invalidation, one for new token
    }

    @Test
    void resetPassword_WithAutoLoginEnabled_GeneratesTokens() {
        // Arrange
        String token = "valid-token";
        String newPassword = "Test@123";
        String confirmPassword = "Test@123";
        
        Company mockCompany = new Company();
        mockCompany.setIsActive(true);
        
        when(passwordResetTokenRepository.findByToken(token)).thenReturn(Optional.of(mockToken));
        when(userRepository.save(any(UserInfo.class))).thenReturn(mockUser);
        when(userService.decryptSensitiveFields(mockUser)).thenReturn(mockUser);
        when(companyRepository.findById(any())).thenReturn(Optional.of(mockCompany));
        when(jwtService.GenerateToken("testuser")).thenReturn("access-token");
        when(refreshTokenService.getTokenByUserId(1L)).thenReturn("existing-refresh-token");

        // Act
        ResetPasswordResult result = passwordResetService.resetPasswordWithTokens(token, newPassword, confirmPassword);

        // Assert
        assertTrue(result.isSuccess());
        assertEquals("Senha redefinida com sucesso", result.getMessage());
        assertEquals("access-token", result.getAccessToken());
        assertEquals("existing-refresh-token", result.getRefreshToken());
        assertNotNull(result.getUserDetails());
    }

    @Test
    void resetPassword_WithAutoLoginEnabled_AdminUser_GeneratesTokens() {
        // Arrange
        String token = "valid-token";
        String newPassword = "Test@123";
        String confirmPassword = "Test@123";
        
        Role adminRole = new Role();
        adminRole.setName(RoleEnum.ROLE_ADMIN);
        Set<Role> adminRoles = new HashSet<>();
        adminRoles.add(adminRole);
        mockUser.setRoles(adminRoles);
        
        when(passwordResetTokenRepository.findByToken(token)).thenReturn(Optional.of(mockToken));
        when(userRepository.save(any(UserInfo.class))).thenReturn(mockUser);
        when(userService.decryptSensitiveFields(mockUser)).thenReturn(mockUser);
        when(jwtService.GenerateToken("testuser")).thenReturn("access-token");
        when(refreshTokenService.getTokenByUserId(1L)).thenReturn("");

        RefreshToken newRefreshToken = new RefreshToken();
        newRefreshToken.setToken("new-refresh-token");
        when(refreshTokenService.createRefreshToken("testuser")).thenReturn(newRefreshToken);

        // Act
        ResetPasswordResult result = passwordResetService.resetPasswordWithTokens(token, newPassword, confirmPassword);

        // Assert
        assertTrue(result.isSuccess());
        assertEquals("access-token", result.getAccessToken());
        assertEquals("new-refresh-token", result.getRefreshToken());
        verify(companyRepository, never()).findById(any());
    }

    @Test
    void resetPassword_WithAutoLoginEnabled_InactiveCompany_ReturnsWarning() {
        // Arrange
        String token = "valid-token";
        String newPassword = "Test@123";
        String confirmPassword = "Test@123";
        
        Company inactiveCompany = new Company();
        inactiveCompany.setIsActive(false);
        
        when(passwordResetTokenRepository.findByToken(token)).thenReturn(Optional.of(mockToken));
        when(userRepository.save(any(UserInfo.class))).thenReturn(mockUser);
        when(userService.decryptSensitiveFields(mockUser)).thenReturn(mockUser);
        when(companyRepository.findById(any())).thenReturn(Optional.of(inactiveCompany));

        // Act
        ResetPasswordResult result = passwordResetService.resetPasswordWithTokens(token, newPassword, confirmPassword);

        // Assert
        assertTrue(result.isSuccess());
        assertTrue(result.getMessage().contains("empresa está inativa"));
        assertNull(result.getAccessToken());
        assertNull(result.getRefreshToken());
    }

    @Test
    void resetPassword_WithAutoLoginEnabled_CompanyNotFound_ReturnsWarning() {
        // Arrange
        String token = "valid-token";
        String newPassword = "Test@123";
        String confirmPassword = "Test@123";
        
        when(passwordResetTokenRepository.findByToken(token)).thenReturn(Optional.of(mockToken));
        when(userRepository.save(any(UserInfo.class))).thenReturn(mockUser);
        when(userService.decryptSensitiveFields(mockUser)).thenReturn(mockUser);
        when(companyRepository.findById(any())).thenReturn(Optional.empty());

        // Act
        ResetPasswordResult result = passwordResetService.resetPasswordWithTokens(token, newPassword, confirmPassword);

        // Assert
        assertTrue(result.isSuccess());
        assertTrue(result.getMessage().contains("empresa está inativa"));
        assertNull(result.getAccessToken());
        assertNull(result.getRefreshToken());
    }

    @Test
    void resetPassword_WithAutoLoginEnabled_JwtServiceException_ReturnsSuccessWithWarning() {
        // Arrange
        String token = "valid-token";
        String newPassword = "Test@123";
        String confirmPassword = "Test@123";
        
        Company mockCompany = new Company();
        mockCompany.setIsActive(true);
        
        when(passwordResetTokenRepository.findByToken(token)).thenReturn(Optional.of(mockToken));
        when(userRepository.save(any(UserInfo.class))).thenReturn(mockUser);
        when(userService.decryptSensitiveFields(mockUser)).thenReturn(mockUser);
        when(companyRepository.findById(any())).thenReturn(Optional.of(mockCompany));
        when(jwtService.GenerateToken("testuser")).thenThrow(new RuntimeException("JWT error"));

        // Act
        ResetPasswordResult result = passwordResetService.resetPasswordWithTokens(token, newPassword, confirmPassword);

        // Assert
        assertTrue(result.isSuccess());
        assertTrue(result.getMessage().contains("erro no login automático"));
        assertNull(result.getAccessToken());
        assertNull(result.getRefreshToken());
    }

    @Test
    void resetPassword_WithPasswordHistoryService_SavesPasswordToHistory() {
        // Arrange
        String token = "valid-token";
        String newPassword = "Test@123";
        String confirmPassword = "Test@123";
        
        when(passwordResetTokenRepository.findByToken(token)).thenReturn(Optional.of(mockToken));
        when(userRepository.save(any(UserInfo.class))).thenReturn(mockUser);
        doNothing().when(passwordHistoryService).savePasswordToHistory(any(UserInfo.class), anyString());
        doNothing().when(passwordHistoryService).validatePasswordHistory(any(UserInfo.class), anyString());

        // Act
        ResetPasswordResult result = passwordResetService.resetPasswordWithTokens(token, newPassword, confirmPassword);

        // Assert
        assertTrue(result.isSuccess());
        verify(passwordHistoryService).validatePasswordHistory(mockUser, newPassword);
        verify(passwordHistoryService).savePasswordToHistory(mockUser, anyString());
    }

    @Test
    void resetPassword_WithPasswordHistoryService_ReusedPassword_ReturnsFailure() {
        // Arrange
        String token = "valid-token";
        String newPassword = "Test@123";
        String confirmPassword = "Test@123";
        
        when(passwordResetTokenRepository.findByToken(token)).thenReturn(Optional.of(mockToken));
        doThrow(new IllegalArgumentException("Você não pode reutilizar uma das suas últimas 3 senhas"))
                .when(passwordHistoryService).validatePasswordHistory(any(UserInfo.class), anyString());

        // Act
        ResetPasswordResult result = passwordResetService.resetPasswordWithTokens(token, newPassword, confirmPassword);

        // Assert
        assertFalse(result.isSuccess());
        assertEquals("Você não pode reutilizar uma das suas últimas 3 senhas", result.getMessage());
        verify(passwordHistoryService).validatePasswordHistory(mockUser, newPassword);
        verify(passwordHistoryService, never()).savePasswordToHistory(any(), any());
    }

    @Test
    void resetPassword_WithSamePasswordAsCurrent_ReturnsFailure() {
        // Arrange
        String token = "valid-token";
        String newPassword = "currentpassword";
        String confirmPassword = "currentpassword";
        
        when(passwordResetTokenRepository.findByToken(token)).thenReturn(Optional.of(mockToken));

        // Act
        ResetPasswordResult result = passwordResetService.resetPasswordWithTokens(token, newPassword, confirmPassword);

        // Assert
        assertFalse(result.isSuccess());
        assertEquals("A nova senha não pode ser igual à senha atual", result.getMessage());
    }

    @Test
    void resetPassword_WithAutoLoginDisabled_ReturnsSuccessWithoutTokens() {
        // Arrange
        ReflectionTestUtils.setField(passwordResetService, "autoLoginAfterReset", false);
        
        String token = "valid-token";
        String newPassword = "Test@123";
        String confirmPassword = "Test@123";
        
        when(passwordResetTokenRepository.findByToken(token)).thenReturn(Optional.of(mockToken));
        when(userRepository.save(any(UserInfo.class))).thenReturn(mockUser);

        // Act
        ResetPasswordResult result = passwordResetService.resetPasswordWithTokens(token, newPassword, confirmPassword);

        // Assert
        assertTrue(result.isSuccess());
        assertEquals("Senha redefinida com sucesso", result.getMessage());
        assertNull(result.getAccessToken());
        assertNull(result.getRefreshToken());
        assertNull(result.getUserDetails());
    }

    @Test
    void resetPassword_WithPasswordHistoryServiceNull_WorksWithoutHistory() {
        // Arrange
        ReflectionTestUtils.setField(passwordResetService, "passwordHistoryService", null);
        
        String token = "valid-token";
        String newPassword = "Test@123";
        String confirmPassword = "Test@123";
        
        when(passwordResetTokenRepository.findByToken(token)).thenReturn(Optional.of(mockToken));
        when(userRepository.save(any(UserInfo.class))).thenReturn(mockUser);

        // Act
        ResetPasswordResult result = passwordResetService.resetPasswordWithTokens(token, newPassword, confirmPassword);

        // Assert
        assertTrue(result.isSuccess());
        assertEquals("Senha redefinida com sucesso", result.getMessage());
    }

    @Test
    void resetPassword_WithDatabaseError_ReturnsInternalServerError() {
        // Arrange
        String token = "valid-token";
        String newPassword = "Test@123";
        String confirmPassword = "Test@123";
        
        when(passwordResetTokenRepository.findByToken(token)).thenReturn(Optional.of(mockToken));
        when(userRepository.save(any(UserInfo.class))).thenThrow(new RuntimeException("Database error"));

        // Act
        ResetPasswordResult result = passwordResetService.resetPasswordWithTokens(token, newPassword, confirmPassword);

        // Assert
        assertFalse(result.isSuccess());
        assertEquals("Erro interno do servidor", result.getMessage());
    }
}