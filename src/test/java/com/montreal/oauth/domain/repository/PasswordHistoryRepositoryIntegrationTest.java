package com.montreal.oauth.domain.repository;

import com.montreal.oauth.domain.entity.PasswordHistory;
import com.montreal.oauth.domain.entity.UserInfo;
import com.montreal.oauth.domain.repository.IUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@Transactional
class PasswordHistoryRepositoryIntegrationTest {

    @Autowired
    private PasswordHistoryRepository passwordHistoryRepository;

    @Autowired
    private IUserRepository userRepository;

    private UserInfo testUser;
    private BCryptPasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        
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
    void findLastPasswordsByUserId_ShouldReturnOrderedByCreatedAtDesc() {
        // Arrange
        PasswordHistory password1 = createPasswordHistory("hash1", LocalDateTime.now().minusHours(2));
        PasswordHistory password2 = createPasswordHistory("hash2", LocalDateTime.now().minusHours(1));
        PasswordHistory password3 = createPasswordHistory("hash3", LocalDateTime.now());

        passwordHistoryRepository.save(password1);
        passwordHistoryRepository.save(password2);
        passwordHistoryRepository.save(password3);

        // Act
        List<PasswordHistory> result = passwordHistoryRepository.findLastPasswordsByUserId(
            testUser.getId(), PageRequest.of(0, 3)
        );

        // Assert
        assertEquals(3, result.size());
        assertEquals("hash3", result.get(0).getPasswordHash()); // Mais recente primeiro
        assertEquals("hash2", result.get(1).getPasswordHash());
        assertEquals("hash1", result.get(2).getPasswordHash()); // Mais antiga por último
    }

    @Test
    void findLastPasswordsByUserId_WithPagination_ShouldRespectLimit() {
        // Arrange
        for (int i = 1; i <= 5; i++) {
            PasswordHistory password = createPasswordHistory("hash" + i, LocalDateTime.now().minusHours(i));
            passwordHistoryRepository.save(password);
        }

        // Act
        List<PasswordHistory> result = passwordHistoryRepository.findLastPasswordsByUserId(
            testUser.getId(), PageRequest.of(0, 3)
        );

        // Assert
        assertEquals(3, result.size());
        assertEquals("hash5", result.get(0).getPasswordHash()); // Mais recente
        assertEquals("hash4", result.get(1).getPasswordHash());
        assertEquals("hash3", result.get(2).getPasswordHash()); // Mais antiga das 3
    }

    @Test
    void findLastPasswordsByUserId_EmptyHistory_ShouldReturnEmptyList() {
        // Act
        List<PasswordHistory> result = passwordHistoryRepository.findLastPasswordsByUserId(
            testUser.getId(), PageRequest.of(0, 3)
        );

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    void cleanupOldPasswordHistory_ShouldRemoveExcessPasswords() {
        // Arrange
        for (int i = 1; i <= 5; i++) {
            PasswordHistory password = createPasswordHistory("hash" + i, LocalDateTime.now().minusHours(i));
            passwordHistoryRepository.save(password);
        }

        // Verificar que temos 5 senhas antes da limpeza
        List<PasswordHistory> beforeCleanup = passwordHistoryRepository.findLastPasswordsByUserId(
            testUser.getId(), PageRequest.of(0, 10)
        );
        assertEquals(5, beforeCleanup.size());

        // Act
        passwordHistoryRepository.cleanupOldPasswordHistory(testUser.getId(), 3);

        // Assert
        List<PasswordHistory> afterCleanup = passwordHistoryRepository.findLastPasswordsByUserId(
            testUser.getId(), PageRequest.of(0, 10)
        );
        assertEquals(3, afterCleanup.size());
        
        // Verificar que as 3 mais recentes foram mantidas
        assertEquals("hash5", afterCleanup.get(0).getPasswordHash());
        assertEquals("hash4", afterCleanup.get(1).getPasswordHash());
        assertEquals("hash3", afterCleanup.get(2).getPasswordHash());
    }

    @Test
    void cleanupOldPasswordHistory_WithExactMaxHistory_ShouldNotRemoveAny() {
        // Arrange
        for (int i = 1; i <= 3; i++) {
            PasswordHistory password = createPasswordHistory("hash" + i, LocalDateTime.now().minusHours(i));
            passwordHistoryRepository.save(password);
        }

        // Act
        passwordHistoryRepository.cleanupOldPasswordHistory(testUser.getId(), 3);

        // Assert
        List<PasswordHistory> result = passwordHistoryRepository.findLastPasswordsByUserId(
            testUser.getId(), PageRequest.of(0, 10)
        );
        assertEquals(3, result.size());
    }

    @Test
    void cleanupOldPasswordHistory_WithLessThanMaxHistory_ShouldNotRemoveAny() {
        // Arrange
        for (int i = 1; i <= 2; i++) {
            PasswordHistory password = createPasswordHistory("hash" + i, LocalDateTime.now().minusHours(i));
            passwordHistoryRepository.save(password);
        }

        // Act
        passwordHistoryRepository.cleanupOldPasswordHistory(testUser.getId(), 3);

        // Assert
        List<PasswordHistory> result = passwordHistoryRepository.findLastPasswordsByUserId(
            testUser.getId(), PageRequest.of(0, 10)
        );
        assertEquals(2, result.size());
    }

    @Test
    void findLastPasswordsByUserId_DifferentUsers_ShouldNotInterfere() {
        // Arrange
        UserInfo user2 = UserInfo.builder()
                .username("user2@example.com")
                .email("user2@example.com")
                .password(passwordEncoder.encode("Test@123"))
                .enabled(true)
                .build();
        user2 = userRepository.save(user2);

        // Criar senhas para ambos os usuários
        PasswordHistory user1Password = createPasswordHistory("hash1", LocalDateTime.now());
        PasswordHistory user2Password = createPasswordHistory("hash2", LocalDateTime.now());
        user2Password.setUser(user2);

        passwordHistoryRepository.save(user1Password);
        passwordHistoryRepository.save(user2Password);

        // Act
        List<PasswordHistory> user1Result = passwordHistoryRepository.findLastPasswordsByUserId(
            testUser.getId(), PageRequest.of(0, 3)
        );
        List<PasswordHistory> user2Result = passwordHistoryRepository.findLastPasswordsByUserId(
            user2.getId(), PageRequest.of(0, 3)
        );

        // Assert
        assertEquals(1, user1Result.size());
        assertEquals("hash1", user1Result.get(0).getPasswordHash());
        
        assertEquals(1, user2Result.size());
        assertEquals("hash2", user2Result.get(0).getPasswordHash());
    }

    private PasswordHistory createPasswordHistory(String passwordHash, LocalDateTime createdAt) {
        PasswordHistory passwordHistory = PasswordHistory.builder()
                .user(testUser)
                .passwordHash(passwordHash)
                .createdAt(createdAt)
                .build();
        return passwordHistory;
    }
}
