# Feature: Password Reset Functionality

## Branch Name
```
feature/password-reset-token
```

## Overview
Implementação da funcionalidade de redefinição de senha no backend Spring Boot, incluindo geração, validação e gerenciamento de tokens seguros.

## Requirements
- ✅ Validação de login do usuário
- ✅ Geração de token único (UUID)
- ✅ Expiração automática (30 minutos)
- ✅ Armazenamento seguro em tabela própria
- ✅ Validação de token (existência, expiração, uso único)
- ✅ Link de redefinição no formato: `https://<dominio>/reset-password?token=<UUID>`
- ✅ Segurança: sem dados sensíveis no link
- ✅ Uso único do token

## Implementation Plan

### Phase 1: Database & Entity Setup
- Create `PasswordResetToken` entity
- Create `PasswordResetTokenRepository`
- Create database migration script

### Phase 2: Service Layer
- Create `PasswordResetService` interface
- Implement `PasswordResetServiceImpl`
- Add token generation logic
- Add token validation logic

### Phase 3: Controller & DTOs
- Create `PasswordResetController`
- Create request/response DTOs
- Implement endpoints for token generation and validation

### Phase 4: Security & Configuration
- Add security configurations
- Configure token expiration
- Add validation annotations

### Phase 5: Testing & Documentation
- Add unit tests
- Add integration tests
- Update API documentation

## Commit Structure (Conventional Commits)

### Phase 1: Database & Entity Setup
```
feat(auth): add PasswordResetToken entity and repository
feat(db): create password reset token table migration
```

### Phase 2: Service Layer
```
feat(auth): implement password reset token service
feat(auth): add token generation and validation logic
```

### Phase 3: Controller & DTOs
```
feat(auth): add password reset controller and endpoints
feat(auth): create password reset DTOs
```

### Phase 4: Security & Configuration
```
feat(security): configure password reset security settings
feat(config): add password reset token configuration
```

### Phase 5: Testing & Documentation
```
test(auth): add password reset service unit tests
test(auth): add password reset controller integration tests
docs(api): update password reset endpoint documentation
```

## API Endpoints

### 1. Generate Reset Token
```
POST /api/auth/password-reset/generate
Content-Type: application/json

{
  "login": "user@example.com"
}
```

**Response:**
```json
{
  "message": "Password reset token generated successfully",
  "resetLink": "https://<dominio>/reset-password?token=<UUID>"
}
```

### 2. Validate Reset Token
```
GET /api/auth/password-reset/validate?token=<UUID>
```

**Response:**
```json
{
  "valid": true,
  "message": "Token is valid"
}
```

### 3. Reset Password (Future Implementation)
```
POST /api/auth/password-reset/reset
Content-Type: application/json

{
  "token": "<UUID>",
  "newPassword": "newSecurePassword123"
}
```

## Database Schema

### Table: password_reset_tokens
```sql
CREATE TABLE password_reset_tokens (
    id BIGSERIAL PRIMARY KEY,
    token VARCHAR(255) UNIQUE NOT NULL,
    user_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    used_at TIMESTAMP NULL,
    is_used BOOLEAN DEFAULT FALSE,
    FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX idx_password_reset_tokens_token ON password_reset_tokens(token);
CREATE INDEX idx_password_reset_tokens_user_id ON password_reset_tokens(user_id);
CREATE INDEX idx_password_reset_tokens_expires_at ON password_reset_tokens(expires_at);
```

## Security Considerations
- ✅ Tokens são UUIDs únicos e não previsíveis
- ✅ Expiração automática após 30 minutos
- ✅ Uso único (não reutilizável)
- ✅ Sem dados sensíveis no link
- ✅ Validação de usuário existente
- ✅ Rate limiting (futuro)
- ✅ Logging de tentativas (futuro)

## Configuration Properties
```yaml
app:
  password-reset:
    token:
      expiration-minutes: 30
      length: 36 # UUID length
```

## Dependencies Required
- ✅ Spring Boot Starter Web (já presente)
- ✅ Spring Boot Starter Data JPA (já presente)
- ✅ Spring Boot Starter Security (já presente)
- ✅ Spring Boot Starter Validation (já presente)
- ✅ PostgreSQL (já presente)

## Next Steps
1. Create feature branch
2. Implement Phase 1 (Database & Entity)
3. Implement Phase 2 (Service Layer)
4. Implement Phase 3 (Controller & DTOs)
5. Implement Phase 4 (Security & Configuration)
6. Implement Phase 5 (Testing & Documentation)
7. Create Pull Request
8. Code Review
9. Merge to main branch

## Notes
- Esta implementação foca apenas na geração e validação de tokens
- A funcionalidade de redefinição de senha será implementada em uma feature separada
- Todos os endpoints seguem padrões REST
- Logs de auditoria serão adicionados para compliance