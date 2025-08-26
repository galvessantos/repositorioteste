package com.montreal.oauth.domain.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Schema(
    name = "PasswordResetGenerateRequest",
    description = "Dados para solicitar redefinição de senha",
    example = """
        {
          "login": "usuario@exemplo.com"
        }
        """
)
public class PasswordResetGenerateRequest {

    @Schema(
        description = "Login do usuário (username)",
        example = "usuario123",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotBlank(message = "Login is required")
    @Size(min = 3, max = 50, message = "Login must be between 3 and 50 characters")
    private String login;
}