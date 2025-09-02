package com.montreal.oauth.domain.service;

import com.montreal.oauth.domain.entity.PasswordHistory;
import com.montreal.oauth.domain.entity.UserInfo;
import com.montreal.oauth.domain.repository.PasswordHistoryRepository;
import com.montreal.oauth.domain.repository.IUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PasswordHistoryServiceIntegrationTest {

    @Autowired
    private PasswordHistoryService passwordHistoryService;

    @Autowired
    private PasswordHistoryRepository passwordHistoryRepository;

    @Autowired
    private IUserRepository userRepository;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    private UserInfo testUser;

    @BeforeEach
    void setUp() {
        // Criar usuário de teste
        testUser = UserInfo.builder()
                .username("testuser@example.com")
                .email("testuser@example.com")
                .password(passwordEncoder.encode("Test@123"))
                .enabled(true)
                .build();
        testUser = userRepository.save(testUser);
    }

    @Test
    void validatePasswordHistory_NoPreviousPasswords_ShouldPass() {
        // Arrange
        String newPassword = "NewPass@123";

        // Act & Assert
        assertDoesNotThrow(() -> 
            passwordHistoryService.validatePasswordHistory(testUser, newPassword)
        );
    }

    @Test
    void validatePasswordHistory_PasswordNotInHistory_ShouldPass() {
        // Arrange
        String oldPassword1 = "OldPass@123";
        String oldPassword2 = "OldPass@456";
        String newPassword = "NewPass@123";

        // Salvar senhas antigas no histórico
        passwordHistoryService.savePasswordToHistory(testUser, passwordEncoder.encode(oldPassword1));
        passwordHistoryService.savePasswordToHistory(testUser, passwordEncoder.encode(oldPassword2));

        // Act & Assert
        assertDoesNotThrow(() -> 
            passwordHistoryService.validatePasswordHistory(testUser, newPassword)
        );
    }

    @Test
    void validatePasswordHistory_PasswordInHistory_ShouldThrowException() {
        // Arrange
        String oldPassword = "OldPass@123";
        String newPassword = oldPassword; // Mesma senha

        // Salvar senha antiga no histórico
        passwordHistoryService.savePasswordToHistory(testUser, passwordEncoder.encode(oldPassword));

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            passwordHistoryService.validatePasswordHistory(testUser, newPassword)
        );

        assertEquals("Você não pode reutilizar uma das suas últimas 3 senhas", exception.getMessage());
    }

    @Test
    void savePasswordToHistory_ShouldSaveToDatabase() {
        // Arrange
        String passwordHash = passwordEncoder.encode("Test@123");

        // Act
        passwordHistoryService.savePasswordToHistory(testUser, passwordHash);

        // Assert
        List<PasswordHistory> savedPasswords = passwordHistoryRepository.findLastPasswordsByUserId(
            testUser.getId(), PageRequest.of(0, 3)
        );
        
        assertEquals(1, savedPasswords.size());
        assertEquals(testUser.getId(), savedPasswords.get(0).getUser().getId());
        assertEquals(passwordHash, savedPasswords.get(0).getPasswordHash());
        assertNotNull(savedPasswords.get(0).getCreatedAt());
    }

    @Test
    void savePasswordToHistory_MultiplePasswords_ShouldMaintainOrder() {
        // Arrange
        String password1 = passwordEncoder.encode("Pass1@123");
        String password2 = passwordEncoder.encode("Pass2@123");
        String password3 = passwordEncoder.encode("Pass3@123");

        // Act
        passwordHistoryService.savePasswordToHistory(testUser, password1);
        passwordHistoryService.savePasswordToHistory(testUser, password2);
        passwordHistoryService.savePasswordToHistory(testUser, password3);

        // Assert
        List<PasswordHistory> savedPasswords = passwordHistoryRepository.findLastPasswordsByUserId(
            testUser.getId(), PageRequest.of(0, 5)
        );
        
        assertEquals(3, savedPasswords.size());
        assertEquals(password3, savedPasswords.get(0).getPasswordHash()); // Mais recente primeiro
        assertEquals(password2, savedPasswords.get(1).getPasswordHash());
        assertEquals(password1, savedPasswords.get(2).getPasswordHash()); // Mais antiga por último
    }

    @Test
    void savePasswordToHistory_ExceedsMaxHistory_ShouldCleanupOldPasswords() {
        // Arrange
        String password1 = passwordEncoder.encode("Pass1@123");
        String password2 = passwordEncoder.encode("Pass2@123");
        String password3 = passwordEncoder.encode("Pass3@123");
        String password4 = passwordEncoder.encode("Pass4@123");
        String password5 = passwordEncoder.encode("Pass5@123");

        // Act - Salvar mais senhas que o limite máximo (3)
        passwordHistoryService.savePasswordToHistory(testUser, password1);
        passwordHistoryService.savePasswordToHistory(testUser, password2);
        passwordHistoryService.savePasswordToHistory(testUser, password3);
        passwordHistoryService.savePasswordToHistory(testUser, password4);
        passwordHistoryService.savePasswordToHistory(testUser, password5);

        // Assert - Deve manter apenas as 3 mais recentes
        List<PasswordHistory> savedPasswords = passwordHistoryRepository.findLastPasswordsByUserId(
            testUser.getId(), PageRequest.of(0, 10)
        );
        
        assertEquals(3, savedPasswords.size());
        assertEquals(password5, savedPasswords.get(0).getPasswordHash()); // Mais recente
        assertEquals(password4, savedPasswords.get(1).getPasswordHash());
        assertEquals(password3, savedPasswords.get(2).getPasswordHash()); // Mais antiga das 3
    }

    @Test
    void validatePasswordHistory_WithMaxHistoryLimit_ShouldRespectLimit() {
        // Arrange
        String oldPassword1 = "OldPass@123";
        String oldPassword2 = "OldPass@456";
        String oldPassword3 = "OldPass@789";
        String oldPassword4 = "OldPass@012"; // Esta deve ser ignorada pois excede o limite
        String newPassword = oldPassword4; // Tentar reutilizar a senha que deveria ser ignorada

        // Salvar 4 senhas antigas (mais que o limite de 3)
        passwordHistoryService.savePasswordToHistory(testUser, passwordEncoder.encode(oldPassword1));
        passwordHistoryService.savePasswordToHistory(testUser, passwordEncoder.encode(oldPassword2));
        passwordHistoryService.savePasswordToHistory(testUser, passwordEncoder.encode(oldPassword3));
        passwordHistoryService.savePasswordToHistory(testUser, passwordEncoder.encode(oldPassword4));

        // Act & Assert - Deve passar pois a senha antiga foi removida pelo cleanup
        assertDoesNotThrow(() -> 
            passwordHistoryService.validatePasswordHistory(testUser, newPassword)
        );
    }
}
