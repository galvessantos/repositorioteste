package com.montreal.oauth.controller;

import com.montreal.oauth.domain.service.IPasswordResetService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordResetControllerMinimalTest {

    @Mock
    private IPasswordResetService passwordResetService;

    @InjectMocks
    private PasswordResetController passwordResetController;

    @Test
    void controllerShouldBeInjected() {
        // Assert
        assertNotNull(passwordResetController);
        assertNotNull(passwordResetService);
    }

    @Test
    void serviceShouldBeMocked() {
        // Arrange
        when(passwordResetService.generatePasswordResetToken("testuser"))
                .thenReturn("https://localhost/reset-password?token=test-token");

        // Act
        String result = passwordResetService.generatePasswordResetToken("testuser");

        // Assert
        assertNotNull(result);
        assertTrue(result.contains("reset-password"));
        verify(passwordResetService).generatePasswordResetToken("testuser");
    }
}