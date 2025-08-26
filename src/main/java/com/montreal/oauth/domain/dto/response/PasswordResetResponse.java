package com.montreal.oauth.domain.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Schema(
    name = "PasswordResetResponse",
    description = "Resposta da operação de redefinição de senha com token de acesso"
)
public class PasswordResetResponse {

    @Schema(
        description = "Mensagem informativa sobre o resultado da operação",
        example = "Senha redefinida com sucesso"
    )
    private String message;

    @Schema(
        description = "Indica se a operação foi bem-sucedida",
        example = "true"
    )
    private boolean success;

    @Schema(
        description = "Token JWT para acesso automático ao sistema (apenas em caso de sucesso)",
        example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
        nullable = true
    )
    private String accessToken;

    @Schema(
        description = "Token de refresh para renovar o acesso (apenas em caso de sucesso)",
        example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
        nullable = true
    )
    private String refreshToken;

    @Schema(
        description = "Tipo do token (sempre 'Bearer')",
        example = "Bearer"
    )
    private String tokenType = "Bearer";

    @Schema(
        description = "Tempo de expiração do token em segundos",
        example = "86400"
    )
    private Long expiresIn;
}