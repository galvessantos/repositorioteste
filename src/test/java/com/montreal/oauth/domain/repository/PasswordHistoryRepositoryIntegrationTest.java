package com.montreal.oauth.domain.repository;

import com.montreal.oauth.domain.entity.PasswordHistory;
import com.montreal.oauth.domain.entity.UserInfo;
import com.montreal.oauth.domain.entity.Role;
import com.montreal.oauth.domain.enumerations.RoleEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class PasswordHistoryRepositoryIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private PasswordHistoryRepository passwordHistoryRepository;

    private UserInfo testUser;
    private PasswordHistory passwordHistory1;
    private PasswordHistory passwordHistory2;
    private PasswordHistory passwordHistory3;
    private PasswordHistory passwordHistory4;

    @BeforeEach
    void setUp() {
        // Create test user
        testUser = new UserInfo();
        testUser.setUsername("testuser");
        testUser.setEmail("test@example.com");
        testUser.setPassword("$2a$10$encodedpassword");
        testUser.setEnabled(true);
        testUser.setPasswordChangedByUser(false);

        Role userRole = new Role();
        userRole.setName(RoleEnum.ROLE_USER);
        Set<Role> roles = new HashSet<>();
        roles.add(userRole);
        testUser.setRoles(roles);

        testUser = entityManager.persistAndFlush(testUser);

        // Create password history entries
        passwordHistory1 = PasswordHistory.builder()
                .user(testUser)
                .passwordHash("$2a$10$hash1")
                .createdAt(LocalDateTime.now().minusDays(1))
                .build();
        passwordHistory1 = entityManager.persistAndFlush(passwordHistory1);

        passwordHistory2 = PasswordHistory.builder()
                .user(testUser)
                .passwordHash("$2a$10$hash2")
                .createdAt(LocalDateTime.now().minusDays(2))
                .build();
        passwordHistory2 = entityManager.persistAndFlush(passwordHistory2);

        passwordHistory3 = PasswordHistory.builder()
                .user(testUser)
                .passwordHash("$2a$10$hash3")
                .createdAt(LocalDateTime.now().minusDays(3))
                .build();
        passwordHistory3 = entityManager.persistAndFlush(passwordHistory3);

        passwordHistory4 = PasswordHistory.builder()
                .user(testUser)
                .passwordHash("$2a$10$hash4")
                .createdAt(LocalDateTime.now().minusDays(4))
                .build();
        passwordHistory4 = entityManager.persistAndFlush(passwordHistory4);

        entityManager.clear();
    }

    @Test
    void findLastPasswordsByUserId_WithValidUserId_ReturnsPasswordsInDescendingOrder() {
        // Act
        List<PasswordHistory> result = passwordHistoryRepository.findLastPasswordsByUserId(
                testUser.getId(), PageRequest.of(0, 10));

        // Assert
        assertEquals(4, result.size());
        // Should be ordered by createdAt DESC (most recent first)
        assertEquals("$2a$10$hash1", result.get(0).getPasswordHash());
        assertEquals("$2a$10$hash2", result.get(1).getPasswordHash());
        assertEquals("$2a$10$hash3", result.get(2).getPasswordHash());
        assertEquals("$2a$10$hash4", result.get(3).getPasswordHash());
    }

    @Test
    void findLastPasswordsByUserId_WithPageSizeLimit_ReturnsLimitedResults() {
        // Act
        List<PasswordHistory> result = passwordHistoryRepository.findLastPasswordsByUserId(
                testUser.getId(), PageRequest.of(0, 2));

        // Assert
        assertEquals(2, result.size());
        assertEquals("$2a$10$hash1", result.get(0).getPasswordHash());
        assertEquals("$2a$10$hash2", result.get(1).getPasswordHash());
    }

    @Test
    void findLastPasswordsByUserId_WithSecondPage_ReturnsCorrectResults() {
        // Act
        List<PasswordHistory> result = passwordHistoryRepository.findLastPasswordsByUserId(
                testUser.getId(), PageRequest.of(1, 2));

        // Assert
        assertEquals(2, result.size());
        assertEquals("$2a$10$hash3", result.get(0).getPasswordHash());
        assertEquals("$2a$10$hash4", result.get(1).getPasswordHash());
    }

    @Test
    void findLastPasswordsByUserId_WithNonExistentUserId_ReturnsEmpty() {
        // Act
        List<PasswordHistory> result = passwordHistoryRepository.findLastPasswordsByUserId(
                999L, PageRequest.of(0, 10));

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    void findLastPasswordsByUserId_WithPageBeyondAvailableData_ReturnsEmpty() {
        // Act
        List<PasswordHistory> result = passwordHistoryRepository.findLastPasswordsByUserId(
                testUser.getId(), PageRequest.of(10, 10));

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    void cleanupOldPasswordHistory_WithMaxHistory3_KeepsOnly3MostRecent() {
        // Act
        passwordHistoryRepository.cleanupOldPasswordHistory(testUser.getId(), 3);

        // Assert
        List<PasswordHistory> remainingHistory = passwordHistoryRepository.findLastPasswordsByUserId(
                testUser.getId(), PageRequest.of(0, 10));
        assertEquals(3, remainingHistory.size());
        assertEquals("$2a$10$hash1", remainingHistory.get(0).getPasswordHash());
        assertEquals("$2a$10$hash2", remainingHistory.get(1).getPasswordHash());
        assertEquals("$2a$10$hash3", remainingHistory.get(2).getPasswordHash());
    }

    @Test
    void cleanupOldPasswordHistory_WithMaxHistory2_KeepsOnly2MostRecent() {
        // Act
        passwordHistoryRepository.cleanupOldPasswordHistory(testUser.getId(), 2);

        // Assert
        List<PasswordHistory> remainingHistory = passwordHistoryRepository.findLastPasswordsByUserId(
                testUser.getId(), PageRequest.of(0, 10));
        assertEquals(2, remainingHistory.size());
        assertEquals("$2a$10$hash1", remainingHistory.get(0).getPasswordHash());
        assertEquals("$2a$10$hash2", remainingHistory.get(1).getPasswordHash());
    }

    @Test
    void cleanupOldPasswordHistory_WithMaxHistoryGreaterThanTotal_KeepsAll() {
        // Act
        passwordHistoryRepository.cleanupOldPasswordHistory(testUser.getId(), 10);

        // Assert
        List<PasswordHistory> remainingHistory = passwordHistoryRepository.findLastPasswordsByUserId(
                testUser.getId(), PageRequest.of(0, 10));
        assertEquals(4, remainingHistory.size());
    }

    @Test
    void cleanupOldPasswordHistory_WithMaxHistoryZero_RemovesAll() {
        // Act
        passwordHistoryRepository.cleanupOldPasswordHistory(testUser.getId(), 0);

        // Assert
        List<PasswordHistory> remainingHistory = passwordHistoryRepository.findLastPasswordsByUserId(
                testUser.getId(), PageRequest.of(0, 10));
        assertTrue(remainingHistory.isEmpty());
    }

    @Test
    void cleanupOldPasswordHistory_WithNonExistentUserId_DoesNothing() {
        // Act
        passwordHistoryRepository.cleanupOldPasswordHistory(999L, 2);

        // Assert
        List<PasswordHistory> allHistory = passwordHistoryRepository.findLastPasswordsByUserId(
                testUser.getId(), PageRequest.of(0, 10));
        assertEquals(4, allHistory.size());
    }

    @Test
    void save_WithNewPasswordHistory_PersistsPasswordHistory() {
        // Arrange
        PasswordHistory newPasswordHistory = PasswordHistory.builder()
                .user(testUser)
                .passwordHash("$2a$10$newhash")
                .createdAt(LocalDateTime.now())
                .build();

        // Act
        PasswordHistory savedPasswordHistory = passwordHistoryRepository.save(newPasswordHistory);

        // Assert
        assertNotNull(savedPasswordHistory.getId());
        assertEquals("$2a$10$newhash", savedPasswordHistory.getPasswordHash());
        assertEquals(testUser.getId(), savedPasswordHistory.getUser().getId());

        List<PasswordHistory> allHistory = passwordHistoryRepository.findLastPasswordsByUserId(
                testUser.getId(), PageRequest.of(0, 10));
        assertEquals(5, allHistory.size());
        assertTrue(allHistory.stream().anyMatch(ph -> "$2a$10$newhash".equals(ph.getPasswordHash())));
    }

    @Test
    void save_WithUpdatedPasswordHistory_UpdatesPasswordHistory() {
        // Arrange
        passwordHistory1.setPasswordHash("$2a$10$updatedhash");

        // Act
        PasswordHistory updatedPasswordHistory = passwordHistoryRepository.save(passwordHistory1);

        // Assert
        assertEquals("$2a$10$updatedhash", updatedPasswordHistory.getPasswordHash());

        List<PasswordHistory> allHistory = passwordHistoryRepository.findLastPasswordsByUserId(
                testUser.getId(), PageRequest.of(0, 10));
        assertEquals(4, allHistory.size());
        assertTrue(allHistory.stream().anyMatch(ph -> "$2a$10$updatedhash".equals(ph.getPasswordHash())));
    }

    @Test
    void findLastPasswordsByUserId_WithMultipleUsers_ReturnsOnlyRequestedUserHistory() {
        // Arrange - Create another user with password history
        UserInfo anotherUser = new UserInfo();
        anotherUser.setUsername("anotheruser");
        anotherUser.setEmail("another@example.com");
        anotherUser.setPassword("$2a$10$encodedpassword");
        anotherUser.setEnabled(true);

        Role userRole = new Role();
        userRole.setName(RoleEnum.ROLE_USER);
        Set<Role> roles = new HashSet<>();
        roles.add(userRole);
        anotherUser.setRoles(roles);

        anotherUser = entityManager.persistAndFlush(anotherUser);

        PasswordHistory anotherUserHistory = PasswordHistory.builder()
                .user(anotherUser)
                .passwordHash("$2a$10$anotherhash")
                .createdAt(LocalDateTime.now().minusMinutes(30))
                .build();
        entityManager.persistAndFlush(anotherUserHistory);

        // Act
        List<PasswordHistory> testUserHistory = passwordHistoryRepository.findLastPasswordsByUserId(
                testUser.getId(), PageRequest.of(0, 10));
        List<PasswordHistory> anotherUserHistoryResult = passwordHistoryRepository.findLastPasswordsByUserId(
                anotherUser.getId(), PageRequest.of(0, 10));

        // Assert
        assertEquals(4, testUserHistory.size());
        assertEquals(1, anotherUserHistoryResult.size());
        assertTrue(testUserHistory.stream().noneMatch(ph -> "$2a$10$anotherhash".equals(ph.getPasswordHash())));
        assertTrue(anotherUserHistoryResult.stream().anyMatch(ph -> "$2a$10$anotherhash".equals(ph.getPasswordHash())));
    }

    @Test
    void cleanupOldPasswordHistory_WithMultipleUsers_OnlyAffectsRequestedUser() {
        // Arrange - Create another user with password history
        UserInfo anotherUser = new UserInfo();
        anotherUser.setUsername("anotheruser");
        anotherUser.setEmail("another@example.com");
        anotherUser.setPassword("$2a$10$encodedpassword");
        anotherUser.setEnabled(true);

        Role userRole = new Role();
        userRole.setName(RoleEnum.ROLE_USER);
        Set<Role> roles = new HashSet<>();
        roles.add(userRole);
        anotherUser.setRoles(roles);

        anotherUser = entityManager.persistAndFlush(anotherUser);

        PasswordHistory anotherUserHistory1 = PasswordHistory.builder()
                .user(anotherUser)
                .passwordHash("$2a$10$anotherhash1")
                .createdAt(LocalDateTime.now().minusMinutes(30))
                .build();
        entityManager.persistAndFlush(anotherUserHistory1);

        PasswordHistory anotherUserHistory2 = PasswordHistory.builder()
                .user(anotherUser)
                .passwordHash("$2a$10$anotherhash2")
                .createdAt(LocalDateTime.now().minusMinutes(60))
                .build();
        entityManager.persistAndFlush(anotherUserHistory2);

        // Act
        passwordHistoryRepository.cleanupOldPasswordHistory(testUser.getId(), 2);

        // Assert
        List<PasswordHistory> testUserHistory = passwordHistoryRepository.findLastPasswordsByUserId(
                testUser.getId(), PageRequest.of(0, 10));
        List<PasswordHistory> anotherUserHistory = passwordHistoryRepository.findLastPasswordsByUserId(
                anotherUser.getId(), PageRequest.of(0, 10));

        assertEquals(2, testUserHistory.size());
        assertEquals(2, anotherUserHistory.size()); // Should not be affected
    }
}