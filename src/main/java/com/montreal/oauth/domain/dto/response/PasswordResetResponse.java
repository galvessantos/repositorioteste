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
        description = "Resposta da operação de redefinição de senha"
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
}