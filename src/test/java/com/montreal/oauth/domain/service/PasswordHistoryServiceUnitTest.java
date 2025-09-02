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

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordHistoryServiceUnitTest {

    @Mock
    private PasswordHistoryRepository passwordHistoryRepository;

    @Mock
    private BCryptPasswordEncoder passwordEncoder;

    @InjectMocks
    private PasswordHistoryService passwordHistoryService;

    private UserInfo testUser;
    private String newPassword;
    private String hashedPassword;

    @BeforeEach
    void setUp() {
        testUser = UserInfo.builder()
                .id(1L)
                .username("testuser@example.com")
                .email("testuser@example.com")
                .enabled(true)
                .build();
        
        newPassword = "Test@123";
        hashedPassword = "$2a$10$hashed.password.for.testing";
    }

    @Test
    void validatePasswordHistory_NoPreviousPasswords_ShouldPass() {
        // Arrange
        when(passwordHistoryRepository.findLastPasswordsByUserId(1L, PageRequest.of(0, 3)))
                .thenReturn(Collections.emptyList());

        // Act & Assert
        assertDoesNotThrow(() -> 
            passwordHistoryService.validatePasswordHistory(testUser, newPassword)
        );

        verify(passwordHistoryRepository).findLastPasswordsByUserId(1L, PageRequest.of(0, 3));
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void validatePasswordHistory_PasswordNotInHistory_ShouldPass() {
        // Arrange
        List<PasswordHistory> previousPasswords = Arrays.asList(
            createPasswordHistory("$2a$10$old.password.1"),
            createPasswordHistory("$2a$10$old.password.2"),
            createPasswordHistory("$2a$10$old.password.3")
        );

        when(passwordHistoryRepository.findLastPasswordsByUserId(1L, PageRequest.of(0, 3)))
                .thenReturn(previousPasswords);
        when(passwordEncoder.matches(newPassword, "$2a$10$old.password.1")).thenReturn(false);
        when(passwordEncoder.matches(newPassword, "$2a$10$old.password.2")).thenReturn(false);
        when(passwordEncoder.matches(newPassword, "$2a$10$old.password.3")).thenReturn(false);

        // Act & Assert
        assertDoesNotThrow(() -> 
            passwordHistoryService.validatePasswordHistory(testUser, newPassword)
        );

        verify(passwordHistoryRepository).findLastPasswordsByUserId(1L, PageRequest.of(0, 3));
        verify(passwordEncoder, times(3)).matches(newPassword, anyString());
    }

    @Test
    void validatePasswordHistory_PasswordInHistory_ShouldThrowException() {
        // Arrange
        List<PasswordHistory> previousPasswords = Arrays.asList(
            createPasswordHistory("$2a$10$old.password.1"),
            createPasswordHistory("$2a$10$old.password.2")
        );

        when(passwordHistoryRepository.findLastPasswordsByUserId(1L, PageRequest.of(0, 3)))
                .thenReturn(previousPasswords);
        when(passwordEncoder.matches(newPassword, "$2a$10$old.password.1")).thenReturn(false);
        when(passwordEncoder.matches(newPassword, "$2a$10$old.password.2")).thenReturn(true);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            passwordHistoryService.validatePasswordHistory(testUser, newPassword)
        );

        assertEquals("Você não pode reutilizar uma das suas últimas 3 senhas", exception.getMessage());
        verify(passwordHistoryRepository).findLastPasswordsByUserId(1L, PageRequest.of(0, 3));
        verify(passwordEncoder, times(2)).matches(newPassword, anyString());
    }

    @Test
    void savePasswordToHistory_ValidPassword_ShouldSaveAndCleanup() {
        // Arrange
        when(passwordHistoryRepository.save(any(PasswordHistory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        passwordHistoryService.savePasswordToHistory(testUser, hashedPassword);

        // Assert
        verify(passwordHistoryRepository).save(any(PasswordHistory.class));
        verify(passwordHistoryRepository).cleanupOldPasswordHistory(1L, 3);
    }

    @Test
    void savePasswordToHistory_ShouldCreateCorrectPasswordHistory() {
        // Arrange
        when(passwordHistoryRepository.save(any(PasswordHistory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        passwordHistoryService.savePasswordToHistory(testUser, hashedPassword);

        // Assert
        verify(passwordHistoryRepository).save(argThat(passwordHistory -> {
            assertEquals(testUser, passwordHistory.getUser());
            assertEquals(hashedPassword, passwordHistory.getPasswordHash());
            assertNotNull(passwordHistory.getCreatedAt());
            return true;
        }));
    }

    private PasswordHistory createPasswordHistory(String passwordHash) {
        return PasswordHistory.builder()
                .id(1L)
                .user(testUser)
                .passwordHash(passwordHash)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
