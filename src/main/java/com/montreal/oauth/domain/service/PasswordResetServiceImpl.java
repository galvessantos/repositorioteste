package com.montreal.oauth.domain.service;

import com.montreal.oauth.domain.entity.PasswordResetToken;
import com.montreal.oauth.domain.entity.UserInfo;
import com.montreal.core.domain.exception.UserNotFoundException;
import com.montreal.oauth.domain.repository.IPasswordResetTokenRepository;
import com.montreal.oauth.domain.repository.IUserRepository;
import com.montreal.oauth.domain.dto.response.PasswordResetResponse;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
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
    private final JwtService jwtService;

    @Value("${app.password-reset.token.expiration-minutes:30}")
    private int tokenExpirationMinutes;

    @Value("${app.password-reset.base-url:https://localhost}")
    private String baseUrl;

    @Value("${montreal.oauth.jwtExpirationMs:86400000}")
    private Long jwtExpirationMs;

    @Override
    @Transactional
    public String generatePasswordResetToken(String login) {
        log.info("Generating password reset token for login: {}", login);

        UserInfo user = userRepository.findByUsername(login);
        if (user == null) {
            log.warn("Login not found: {}", login);
            throw new UserNotFoundException("Login informado inválido");
        }

        invalidateExistingTokens(user.getId());

        PasswordResetToken token = createNewToken(user);
        passwordResetTokenRepository.save(token);

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

    @Override
    @Transactional
    public PasswordResetResponse resetPassword(String token, String newPassword, String confirmPassword) {
        log.info("Attempting to reset password with token: {}", token);

        if (!validatePasswordResetToken(token)) {
            log.warn("Invalid or expired token for password reset: {}", token);
            return PasswordResetResponse.builder()
                    .message("Token inválido ou expirado")
                    .success(false)
                    .build();
        }

        Optional<PasswordResetToken> tokenOpt = passwordResetTokenRepository.findByToken(token);
        if (tokenOpt.isEmpty()) {
            log.warn("Token not found for password reset: {}", token);
            return PasswordResetResponse.builder()
                    .message("Token inválido ou expirado")
                    .success(false)
                    .build();
        }

        PasswordResetToken resetToken = tokenOpt.get();
        UserInfo user = resetToken.getUser();

        try {
            validatePassword(newPassword);
            validatePasswordConfirmation(newPassword, confirmPassword);

            BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
            String encodedPassword = encoder.encode(newPassword);

            user.setPassword(encodedPassword);
            user.setPasswordChangedByUser(true);
            user.setEnabled(true);

            userRepository.save(user);

            markTokenAsUsed(token);

            // Gerar tokens de autenticação
            String accessToken = jwtService.generateToken(user);
            String refreshToken = jwtService.generateRefreshToken(user);

            log.info("Password reset successfully for user: {}", user.getUsername());

            return PasswordResetResponse.builder()
                    .message("Senha redefinida com sucesso")
                    .success(true)
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .tokenType("Bearer")
                    .expiresIn(jwtExpirationMs / 1000) // Converter para segundos
                    .build();

        } catch (IllegalArgumentException e) {
            log.warn("Password validation failed: {}", e.getMessage());
            return PasswordResetResponse.builder()
                    .message(e.getMessage())
                    .success(false)
                    .build();
        } catch (Exception e) {
            log.error("Error during password reset", e);
            return PasswordResetResponse.builder()
                    .message("Erro interno do servidor")
                    .success(false)
                    .build();
        }
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

    private void validatePassword(String password) {
        log.debug("Validating password criteria");

        if (password == null || password.length() < 4 || password.length() > 8) {
            throw new IllegalArgumentException("A senha deve ter entre 4 e 8 caracteres");
        }

        if (!password.matches(".*[a-z].*")) {
            throw new IllegalArgumentException("A senha deve conter pelo menos uma letra minúscula");
        }

        if (!password.matches(".*[A-Z].*")) {
            throw new IllegalArgumentException("A senha deve conter pelo menos uma letra maiúscula");
        }

        if (!password.matches(".*\\d.*")) {
            throw new IllegalArgumentException("A senha deve conter pelo menos um número");
        }

        if (!password.matches(".*[_@#].*")) {
            throw new IllegalArgumentException("A senha deve conter pelo menos um dos caracteres especiais: _ @ #");
        }

        log.debug("Password validation passed");
    }

    private void validatePasswordConfirmation(String password, String confirmPassword) {
        log.debug("Validating password confirmation");

        if (confirmPassword == null || confirmPassword.trim().isEmpty()) {
            throw new IllegalArgumentException("Confirmação de senha é obrigatória");
        }

        if (!password.equals(confirmPassword)) {
            throw new IllegalArgumentException("As senhas não coincidem");
        }

        log.debug("Password confirmation validation passed");
    }
}