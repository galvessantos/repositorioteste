package com.montreal.oauth.controller;

import com.montreal.oauth.domain.dto.request.PasswordResetGenerateRequest;
import com.montreal.oauth.domain.dto.request.PasswordResetRequest;
import com.montreal.oauth.domain.dto.response.PasswordResetGenerateResponse;
import com.montreal.oauth.domain.dto.response.PasswordResetResponse;
import com.montreal.oauth.domain.dto.response.PasswordResetValidateResponse;
import com.montreal.oauth.domain.service.IPasswordResetService;
import com.montreal.core.domain.exception.UserNotFoundException;
import org.springframework.security.access.prepost.PreAuthorize;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/auth/password-reset")
@RequiredArgsConstructor
@Tag(name = "Password Reset", description = "Funcionalidades de redefinição de senha")
public class PasswordResetController {

    private final IPasswordResetService passwordResetService;

    @Operation(
            summary = "Gerar token de redefinição de senha",
            description = "Gera um token único para redefinição de senha do usuário"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Token gerado com sucesso",
                    content = @Content(schema = @Schema(implementation = PasswordResetGenerateResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Dados de entrada inválidos"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Login informado inválido"
            )
    })
    @PostMapping("/generate")
    @PreAuthorize("permitAll()")
    public ResponseEntity<PasswordResetGenerateResponse> generatePasswordResetToken(
            @Valid @RequestBody PasswordResetGenerateRequest request) {

        log.info("Generating password reset token for login: {}", request.getLogin());

        try {
            String resetLink = passwordResetService.generatePasswordResetToken(request.getLogin());

            PasswordResetGenerateResponse response = PasswordResetGenerateResponse.builder()
                    .message("Password reset token generated successfully")
                    .resetLink(resetLink)
                    .build();

            log.info("Password reset token generated successfully for login: {}", request.getLogin());
            return ResponseEntity.ok(response);

        } catch (UserNotFoundException e) {
            log.warn("Login not found: {}", request.getLogin());
            PasswordResetGenerateResponse response = PasswordResetGenerateResponse.builder()
                    .message("Login informado inválido")
                    .resetLink(null)
                    .build();
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);

        } catch (Exception e) {
            log.error("Error generating password reset token for login: {}", request.getLogin(), e);
            throw e;
        }
    }

    @Operation(
            summary = "Validar token de redefinição de senha",
            description = "Valida se um token de redefinição de senha é válido e não expirou"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Token validado com sucesso",
                    content = @Content(schema = @Schema(implementation = PasswordResetValidateResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Token inválido ou expirado"
            )
    })
    @GetMapping("/validate")
    @PreAuthorize("permitAll()")
    public ResponseEntity<PasswordResetValidateResponse> validatePasswordResetToken(
            @Parameter(description = "Token de redefinição de senha", required = true)
            @RequestParam String token) {

        log.debug("Validating password reset token: {}", token);

        try {
            boolean isValid = passwordResetService.validatePasswordResetToken(token);

            PasswordResetValidateResponse response = PasswordResetValidateResponse.builder()
                    .valid(isValid)
                    .message(isValid ? "Token is valid" : "Token is invalid or expired")
                    .build();

            log.debug("Password reset token validation completed. Valid: {}", isValid);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error validating password reset token: {}", token, e);
            throw e;
        }
    }

    @Operation(
            summary = "Limpar tokens expirados",
            description = "Remove tokens de redefinição de senha expirados e já utilizados"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Limpeza realizada com sucesso"
            )
    })
    @PostMapping("/cleanup")
    public ResponseEntity<String> cleanupExpiredTokens() {
        log.info("Starting cleanup of expired password reset tokens");

        try {
            passwordResetService.cleanupExpiredTokens();

            log.info("Cleanup of expired password reset tokens completed successfully");
            return ResponseEntity.ok("Cleanup completed successfully");

        } catch (Exception e) {
            log.error("Error during cleanup of expired tokens", e);
            throw e;
        }
    }

    @Operation(
            summary = "Redefinir senha",
            description = "Redefine a senha do usuário usando um token válido"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Senha redefinida com sucesso",
                    content = @Content(schema = @Schema(implementation = PasswordResetResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Dados de entrada inválidos ou validação de senha falhou"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Token inválido ou expirado"
            )
    })
    @PostMapping("/reset")
    @PreAuthorize("permitAll()")
    public ResponseEntity<PasswordResetResponse> resetPassword(
            @Valid @RequestBody PasswordResetRequest request) {

        log.info("Attempting to reset password with token: {}", request.getToken());

        try {
            boolean success = passwordResetService.resetPassword(request.getToken(), request.getNewPassword(), request.getConfirmPassword());

            if (success) {
                PasswordResetResponse response = PasswordResetResponse.builder()
                        .message("Senha redefinida com sucesso")
                        .success(true)
                        .build();

                log.info("Password reset completed successfully");
                return ResponseEntity.ok(response);
            } else {
                PasswordResetResponse response = PasswordResetResponse.builder()
                        .message("Token inválido ou expirado")
                        .success(false)
                        .build();

                log.warn("Password reset failed - invalid or expired token");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }

        } catch (IllegalArgumentException e) {
            log.warn("Password validation failed: {}", e.getMessage());
            PasswordResetResponse response = PasswordResetResponse.builder()
                    .message(e.getMessage())
                    .success(false)
                    .build();
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);

        } catch (Exception e) {
            log.error("Error during password reset", e);
            PasswordResetResponse response = PasswordResetResponse.builder()
                    .message("Erro interno do servidor")
                    .success(false)
                    .build();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}
