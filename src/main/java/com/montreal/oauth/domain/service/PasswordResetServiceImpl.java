package com.montreal.oauth.domain.service;

import com.montreal.oauth.domain.entity.PasswordResetToken;
import com.montreal.oauth.domain.entity.UserInfo;
import com.montreal.core.domain.exception.UserNotFoundException;
import com.montreal.oauth.domain.repository.IPasswordResetTokenRepository;
import com.montreal.oauth.domain.repository.IUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordResetServiceImpl implements IPasswordResetService {

    private final IPasswordResetTokenRepository passwordResetTokenRepository;
    private final IUserRepository userRepository;

    @Value("${app.password-reset.token.expiration-minutes:30}")
    private int tokenExpirationMinutes;

    @Value("${app.password-reset.base-url:https://localhost}")
    private String baseUrl;

    @Override
    @Transactional
    public String generatePasswordResetToken(String login) {
        log.info("Generating password reset token for login: {}", login);

        // Buscar usuário APENAS por login (conforme user story)
        UserInfo user = userRepository.findByUsername(login);
        if (user == null) {
            log.warn("Login not found: {}", login);
            throw new UserNotFoundException("Login informado inválido");
        }

        // Invalidate any existing valid tokens for this user
        invalidateExistingTokens(user.getId());

        // Generate new token
        PasswordResetToken token = createNewToken(user);
        passwordResetTokenRepository.save(token);

        // Generate reset link
        String resetLink = generateResetLink(token.getToken());
        
        log.info("Password reset token generated successfully for user: {}", user.getUsername());
        return resetLink;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean validatePasswordResetToken(String token) {
        log.debug("Validating password reset token: {}", token);

        Optional<PasswordResetToken> tokenOpt = passwordResetTokenRepository.findByToken(token);
        if (tokenOpt.isEmpty()) {
            log.warn("Password reset token not found: {}", token);
            return false;
        }

        PasswordResetToken resetToken = tokenOpt.get();
        
        if (resetToken.isExpired()) {
            log.warn("Password reset token expired: {}", token);
            return false;
        }

        if (resetToken.isUsed()) {
            log.warn("Password reset token already used: {}", token);
            return false;
        }

        log.debug("Password reset token is valid: {}", token);
        return true;
    }

    @Override
    @Transactional
    public void markTokenAsUsed(String token) {
        log.info("Marking password reset token as used: {}", token);

        Optional<PasswordResetToken> tokenOpt = passwordResetTokenRepository.findByToken(token);
        if (tokenOpt.isPresent()) {
            PasswordResetToken resetToken = tokenOpt.get();
            resetToken.setUsed(true);
            resetToken.setUsedAt(LocalDateTime.now());
            passwordResetTokenRepository.save(resetToken);
            log.info("Password reset token marked as used: {}", token);
        } else {
            log.warn("Password reset token not found for marking as used: {}", token);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PasswordResetToken> findByToken(String token) {
        return passwordResetTokenRepository.findByToken(token);
    }

    @Override
    @Transactional
    public void cleanupExpiredTokens() {
        log.info("Starting cleanup of expired password reset tokens");
        
        LocalDateTime now = LocalDateTime.now();
        passwordResetTokenRepository.deleteByExpiresAtBefore(now);
        
        log.info("Cleanup of expired password reset tokens completed");
    }



    private void invalidateExistingTokens(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        var validTokens = passwordResetTokenRepository.findValidTokensByUserId(userId, now);
        
        for (PasswordResetToken token : validTokens) {
            token.setUsed(true);
            token.setUsedAt(now);
            passwordResetTokenRepository.save(token);
        }
        
        if (!validTokens.isEmpty()) {
            log.debug("Invalidated {} existing valid tokens for user: {}", validTokens.size(), userId);
        }
    }

    private PasswordResetToken createNewToken(UserInfo user) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusMinutes(tokenExpirationMinutes);

        return PasswordResetToken.builder()
                .token(UUID.randomUUID().toString())
                .user(user)
                .expiresAt(expiresAt)
                .isUsed(false)
                .build();
    }

    private String generateResetLink(String token) {
        return String.format("%s/reset-password?token=%s", baseUrl, token);
    }
}