package com.montreal.oauth.domain.service;

import com.montreal.oauth.domain.entity.PasswordHistory;
import com.montreal.oauth.domain.entity.UserInfo;
import com.montreal.oauth.domain.repository.PasswordHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordHistoryServiceUnitTest {

    @Mock
    private PasswordHistoryRepository passwordHistoryRepository;

    @Mock
    private BCryptPasswordEncoder passwordEncoder;

    @InjectMocks
    private PasswordHistoryService passwordHistoryService;

    private UserInfo mockUser;
    private PasswordHistory mockPasswordHistory1;
    private PasswordHistory mockPasswordHistory2;
    private PasswordHistory mockPasswordHistory3;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(passwordHistoryService, "maxPasswordHistory", 3);

        mockUser = new UserInfo();
        mockUser.setId(1L);
        mockUser.setUsername("testuser");

        mockPasswordHistory1 = PasswordHistory.builder()
                .id(1L)
                .user(mockUser)
                .passwordHash("$2a$10$hash1")
                .createdAt(LocalDateTime.now().minusDays(1))
                .build();

        mockPasswordHistory2 = PasswordHistory.builder()
                .id(2L)
                .user(mockUser)
                .passwordHash("$2a$10$hash2")
                .createdAt(LocalDateTime.now().minusDays(2))
                .build();

        mockPasswordHistory3 = PasswordHistory.builder()
                .id(3L)
                .user(mockUser)
                .passwordHash("$2a$10$hash3")
                .createdAt(LocalDateTime.now().minusDays(3))
                .build();
    }

    @Test
    void validatePasswordHistory_NoPreviousPasswords_DoesNotThrowException() {
        // Arrange
        String newPassword = "New@123";
        when(passwordHistoryRepository.findLastPasswordsByUserId(eq(1L), any(PageRequest.class)))
                .thenReturn(Arrays.asList());

        // Act & Assert
        assertDoesNotThrow(() -> {
            passwordHistoryService.validatePasswordHistory(mockUser, newPassword);
        });

        verify(passwordHistoryRepository).findLastPasswordsByUserId(eq(1L), any(PageRequest.class));
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    void validatePasswordHistory_NewPasswordNotInHistory_DoesNotThrowException() {
        // Arrange
        String newPassword = "New@123";
        List<PasswordHistory> previousPasswords = Arrays.asList(mockPasswordHistory1, mockPasswordHistory2);
        
        when(passwordHistoryRepository.findLastPasswordsByUserId(eq(1L), any(PageRequest.class)))
                .thenReturn(previousPasswords);
        when(passwordEncoder.matches(newPassword, "$2a$10$hash1")).thenReturn(false);
        when(passwordEncoder.matches(newPassword, "$2a$10$hash2")).thenReturn(false);

        // Act & Assert
        assertDoesNotThrow(() -> {
            passwordHistoryService.validatePasswordHistory(mockUser, newPassword);
        });

        verify(passwordHistoryRepository).findLastPasswordsByUserId(eq(1L), any(PageRequest.class));
        verify(passwordEncoder).matches(newPassword, "$2a$10$hash1");
        verify(passwordEncoder).matches(newPassword, "$2a$10$hash2");
    }

    @Test
    void validatePasswordHistory_NewPasswordMatchesPreviousPassword_ThrowsException() {
        // Arrange
        String newPassword = "New@123";
        List<PasswordHistory> previousPasswords = Arrays.asList(mockPasswordHistory1, mockPasswordHistory2);
        
        when(passwordHistoryRepository.findLastPasswordsByUserId(eq(1L), any(PageRequest.class)))
                .thenReturn(previousPasswords);
        when(passwordEncoder.matches(newPassword, "$2a$10$hash1")).thenReturn(false);
        when(passwordEncoder.matches(newPassword, "$2a$10$hash2")).thenReturn(true);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            passwordHistoryService.validatePasswordHistory(mockUser, newPassword);
        });

        assertEquals("Você não pode reutilizar uma das suas últimas 3 senhas", exception.getMessage());
        verify(passwordHistoryRepository).findLastPasswordsByUserId(eq(1L), any(PageRequest.class));
        verify(passwordEncoder).matches(newPassword, "$2a$10$hash1");
        verify(passwordEncoder).matches(newPassword, "$2a$10$hash2");
    }

    @Test
    void validatePasswordHistory_NewPasswordMatchesFirstPassword_ThrowsException() {
        // Arrange
        String newPassword = "New@123";
        List<PasswordHistory> previousPasswords = Arrays.asList(mockPasswordHistory1, mockPasswordHistory2);
        
        when(passwordHistoryRepository.findLastPasswordsByUserId(eq(1L), any(PageRequest.class)))
                .thenReturn(previousPasswords);
        when(passwordEncoder.matches(newPassword, "$2a$10$hash1")).thenReturn(true);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            passwordHistoryService.validatePasswordHistory(mockUser, newPassword);
        });

        assertEquals("Você não pode reutilizar uma das suas últimas 3 senhas", exception.getMessage());
        verify(passwordEncoder).matches(newPassword, "$2a$10$hash1");
        verify(passwordEncoder, never()).matches(newPassword, "$2a$10$hash2");
    }

    @Test
    void validatePasswordHistory_WithMaxHistoryLimit_OnlyChecksLastPasswords() {
        // Arrange
        String newPassword = "New@123";
        List<PasswordHistory> previousPasswords = Arrays.asList(
                mockPasswordHistory1, mockPasswordHistory2, mockPasswordHistory3);
        
        when(passwordHistoryRepository.findLastPasswordsByUserId(eq(1L), any(PageRequest.class)))
                .thenReturn(previousPasswords);
        when(passwordEncoder.matches(newPassword, "$2a$10$hash1")).thenReturn(false);
        when(passwordEncoder.matches(newPassword, "$2a$10$hash2")).thenReturn(false);
        when(passwordEncoder.matches(newPassword, "$2a$10$hash3")).thenReturn(false);

        // Act
        passwordHistoryService.validatePasswordHistory(mockUser, newPassword);

        // Assert
        verify(passwordHistoryRepository).findLastPasswordsByUserId(eq(1L), any(PageRequest.class));
        verify(passwordEncoder).matches(newPassword, "$2a$10$hash1");
        verify(passwordEncoder).matches(newPassword, "$2a$10$hash2");
        verify(passwordEncoder).matches(newPassword, "$2a$10$hash3");
    }

    @Test
    void savePasswordToHistory_ValidInput_SavesPasswordAndCleansUp() {
        // Arrange
        String passwordHash = "$2a$10$newhash";
        when(passwordHistoryRepository.save(any(PasswordHistory.class))).thenReturn(mockPasswordHistory1);
        doNothing().when(passwordHistoryRepository).cleanupOldPasswordHistory(eq(1L), eq(3));

        // Act
        passwordHistoryService.savePasswordToHistory(mockUser, passwordHash);

        // Assert
        verify(passwordHistoryRepository).save(argThat(passwordHistory -> 
                passwordHistory.getUser().equals(mockUser) && 
                passwordHistory.getPasswordHash().equals(passwordHash)
        ));
        verify(passwordHistoryRepository).cleanupOldPasswordHistory(1L, 3);
    }

    @Test
    void savePasswordToHistory_WithCustomMaxHistory_UsesCustomLimit() {
        // Arrange
        ReflectionTestUtils.setField(passwordHistoryService, "maxPasswordHistory", 5);
        String passwordHash = "$2a$10$newhash";
        when(passwordHistoryRepository.save(any(PasswordHistory.class))).thenReturn(mockPasswordHistory1);
        doNothing().when(passwordHistoryRepository).cleanupOldPasswordHistory(eq(1L), eq(5));

        // Act
        passwordHistoryService.savePasswordToHistory(mockUser, passwordHash);

        // Assert
        verify(passwordHistoryRepository).save(any(PasswordHistory.class));
        verify(passwordHistoryRepository).cleanupOldPasswordHistory(1L, 5);
    }

    @Test
    void savePasswordToHistory_RepositoryException_PropagatesException() {
        // Arrange
        String passwordHash = "$2a$10$newhash";
        when(passwordHistoryRepository.save(any(PasswordHistory.class)))
                .thenThrow(new RuntimeException("Database error"));

        // Act & Assert
        assertThrows(RuntimeException.class, () -> {
            passwordHistoryService.savePasswordToHistory(mockUser, passwordHash);
        });

        verify(passwordHistoryRepository).save(any(PasswordHistory.class));
        verify(passwordHistoryRepository, never()).cleanupOldPasswordHistory(anyLong(), anyInt());
    }

    @Test
    void savePasswordToHistory_CleanupException_StillSavesPassword() {
        // Arrange
        String passwordHash = "$2a$10$newhash";
        when(passwordHistoryRepository.save(any(PasswordHistory.class))).thenReturn(mockPasswordHistory1);
        doThrow(new RuntimeException("Cleanup error"))
                .when(passwordHistoryRepository).cleanupOldPasswordHistory(eq(1L), eq(3));

        // Act & Assert
        assertThrows(RuntimeException.class, () -> {
            passwordHistoryService.savePasswordToHistory(mockUser, passwordHash);
        });

        verify(passwordHistoryRepository).save(any(PasswordHistory.class));
        verify(passwordHistoryRepository).cleanupOldPasswordHistory(1L, 3);
    }

    @Test
    void validatePasswordHistory_WithNullUser_ThrowsException() {
        // Arrange
        String newPassword = "New@123";

        // Act & Assert
        assertThrows(NullPointerException.class, () -> {
            passwordHistoryService.validatePasswordHistory(null, newPassword);
        });
    }

    @Test
    void validatePasswordHistory_WithNullPassword_ThrowsException() {
        // Arrange
        when(passwordHistoryRepository.findLastPasswordsByUserId(eq(1L), any(PageRequest.class)))
                .thenReturn(Arrays.asList(mockPasswordHistory1));
        when(passwordEncoder.matches(null, "$2a$10$hash1")).thenReturn(false);

        // Act & Assert
        assertDoesNotThrow(() -> {
            passwordHistoryService.validatePasswordHistory(mockUser, null);
        });

        verify(passwordEncoder).matches(null, "$2a$10$hash1");
    }

    @Test
    void savePasswordToHistory_WithNullUser_ThrowsException() {
        // Arrange
        String passwordHash = "$2a$10$newhash";

        // Act & Assert
        assertThrows(NullPointerException.class, () -> {
            passwordHistoryService.savePasswordToHistory(null, passwordHash);
        });
    }

    @Test
    void savePasswordToHistory_WithNullPasswordHash_ThrowsException() {
        // Act & Assert
        assertThrows(NullPointerException.class, () -> {
            passwordHistoryService.savePasswordToHistory(mockUser, null);
        });
    }

    @Test
    void validatePasswordHistory_WithEmptyPasswordHistory_DoesNotThrowException() {
        // Arrange
        String newPassword = "New@123";
        when(passwordHistoryRepository.findLastPasswordsByUserId(eq(1L), any(PageRequest.class)))
                .thenReturn(Arrays.asList());

        // Act & Assert
        assertDoesNotThrow(() -> {
            passwordHistoryService.validatePasswordHistory(mockUser, newPassword);
        });

        verify(passwordHistoryRepository).findLastPasswordsByUserId(eq(1L), any(PageRequest.class));
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    void validatePasswordHistory_WithSinglePasswordInHistory_ChecksOnlyThatPassword() {
        // Arrange
        String newPassword = "New@123";
        List<PasswordHistory> singlePassword = Arrays.asList(mockPasswordHistory1);
        
        when(passwordHistoryRepository.findLastPasswordsByUserId(eq(1L), any(PageRequest.class)))
                .thenReturn(singlePassword);
        when(passwordEncoder.matches(newPassword, "$2a$10$hash1")).thenReturn(false);

        // Act
        passwordHistoryService.validatePasswordHistory(mockUser, newPassword);

        // Assert
        verify(passwordHistoryRepository).findLastPasswordsByUserId(eq(1L), any(PageRequest.class));
        verify(passwordEncoder).matches(newPassword, "$2a$10$hash1");
        verify(passwordEncoder, never()).matches(newPassword, "$2a$10$hash2");
        verify(passwordEncoder, never()).matches(newPassword, "$2a$10$hash3");
    }
}