# API de Redefinição de Senha - Documentação para Frontend

## Visão Geral

Esta API permite que usuários solicitem redefinição de senha, validem tokens e redefinam suas senhas de forma segura.

**Importante:** A validação é feita **APENAS pelo login (username)**, não por email. O email será usado apenas para envio do link de redefinição.

## Base URL

```
http://localhost:8080/api/auth/password-reset
```

## Endpoints

### 1. Solicitar Redefinição de Senha

**POST** `/generate`

Gera um token único para redefinição de senha e retorna um link de redefinição.

**Validação:** Apenas pelo login (username) do usuário.

#### Request Body
```json
{
  "login": "usuario123"
}
```

#### Response (200 - Sucesso)
```json
{
  "message": "Password reset token generated successfully",
  "resetLink": "https://localhost:5173/reset-password?token=uuid-123"
}
```

#### Response (404 - Login não encontrado)
```json
{
  "message": "Login informado inválido",
  "resetLink": null
}
```

#### Response (400 - Validação falhou)
```json
{
  "timestamp": "2024-01-15T10:30:00",
  "status": 400,
  "errors": ["Login is required"]
}
```

### 2. Validar Token

**GET** `/validate?token={token}`

Valida se um token de redefinição de senha é válido e não expirou.

#### Query Parameters
- `token` (string, obrigatório): Token recebido por e-mail

#### Response (200 - Token válido)
```json
{
  "valid": true,
  "message": "Token is valid"
}
```

#### Response (200 - Token inválido)
```json
{
  "valid": false,
  "message": "Token is invalid or expired"
}
```

### 3. Redefinir Senha

**POST** `/reset`

Redefine a senha do usuário usando um token válido e **retorna tokens de autenticação para login automático**.

#### Request Body
```json
{
  "token": "uuid-123-456-789",
  "newPassword": "Nova@123",
  "confirmPassword": "Nova@123"
}
```

#### Response (200 - Sucesso com Login Automático)
```json
{
  "message": "Senha redefinida com sucesso",
  "success": true,
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "tokenType": "Bearer",
  "expiresIn": 86400
}
```

**🎯 Login Automático:**
- Após redefinição bem-sucedida, o usuário recebe tokens JWT válidos
- Pode acessar o sistema diretamente **sem fazer login novamente**
- Tokens têm validade padrão de 24 horas (86400 segundos)
- Use o `accessToken` no header `Authorization: Bearer {token}`

#### Response (400 - Validação falhou)
```json
{
  "message": "As senhas não coincidem",
  "success": false,
  "accessToken": null,
  "refreshToken": null,
  "tokenType": null,
  "expiresIn": null
}
```

#### Response (404 - Token inválido)
```json
{
  "message": "Token inválido ou expirado",
  "success": false,
  "accessToken": null,
  "refreshToken": null,
  "tokenType": null,
  "expiresIn": null
}
```

## Critérios de Validação de Senha

A nova senha deve atender aos seguintes critérios:

- **Tamanho**: mínimo 4, máximo 8 caracteres
- **Composição**: pelo menos 1 letra maiúscula e 1 minúscula
- **Caracteres especiais**: pelo menos 1 dos seguintes: `_` `@` `#`
- **Números**: pelo menos 1 dígito

### Exemplos de Senhas Válidas
- `Nova@123`
- `Test_456`
- `A@b7`

### Exemplos de Senhas Inválidas
- `123` (muito curta, sem letras)
- `abcdefgh` (sem maiúsculas, números ou especiais)
- `ABCDEFGH` (sem minúsculas, números ou especiais)
- `12345678` (apenas números)

## Fluxo de Integração

### 1. Tela "Esqueci minha senha"
```typescript
// Usuário digita o login e clica em "Enviar"
const handleForgotPassword = async (login: string) => {
  try {
    const response = await fetch('/api/auth/password-reset/generate', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ login })
    });
    
    const data = await response.json();
    
    if (response.ok) {
      // Mostrar mensagem de sucesso
      showMessage('Link de redefinição enviado para seu e-mail');
    } else if (response.status === 404) {
      // Mostrar erro de login inválido
      showError('Login informado inválido');
    }
  } catch (error) {
    showError('Erro ao processar solicitação');
  }
};
```

### 2. Validação de Token (ao acessar link do e-mail)
```typescript
// Extrair token da URL
const urlParams = new URLSearchParams(window.location.search);
const token = urlParams.get('token');

// Validar token antes de mostrar tela de redefinição
const validateToken = async (token: string) => {
  try {
    const response = await fetch(`/api/auth/password-reset/validate?token=${token}`);
    const data = await response.json();
    
    if (data.valid) {
      // Mostrar tela de redefinição de senha
      showPasswordResetForm();
    } else {
      // Mostrar erro de token inválido
      showError('Link de redefinição inválido ou expirado');
    }
  } catch (error) {
    showError('Erro ao validar link');
  }
};
```

### 3. Redefinição de Senha
```typescript
const handlePasswordReset = async (token: string, newPassword: string, confirmPassword: string) => {
  try {
    const response = await fetch('/api/auth/password-reset/reset', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ token, newPassword, confirmPassword })
    });
    
    const data = await response.json();
    
    if (data.success) {
      // Mostrar sucesso e redirecionar para login
      showSuccess('Senha redefinida com sucesso!');
      redirectToLogin();
    } else {
      // Mostrar erro específico
      showError(data.message);
    }
  } catch (error) {
    showError('Erro ao redefinir senha');
  }
};
```

## Tratamento de Erros

### Códigos de Status HTTP
- **200**: Sucesso
- **400**: Dados inválidos ou validação falhou
- **404**: Recurso não encontrado (login ou token inválido)
- **500**: Erro interno do servidor

### Mensagens de Erro Comuns
- `"Login is required"` - Campo login está vazio
- `"Login informado inválido"` - Usuário não existe no sistema
- `"Token é obrigatório"` - Token não foi informado
- `"Nova senha é obrigatória"` - Campo nova senha está vazio
- `"Confirmação de senha é obrigatória"` - Campo confirmação está vazio
- `"As senhas não coincidem"` - Senha e confirmação são diferentes
- `"A senha deve ter entre 4 e 8 caracteres"` - Tamanho inválido
- `"A senha deve conter pelo menos uma letra minúscula"` - Falta minúscula
- `"A senha deve conter pelo menos uma letra maiúscula"` - Falta maiúscula
- `"A senha deve conter pelo menos um número"` - Falta número
- `"A senha deve conter pelo menos um dos caracteres especiais: _ @ #"` - Falta caractere especial

## Segurança

- Tokens expiram em 30 minutos
- Tokens são únicos e não reutilizáveis
- Tokens existentes são invalidados ao gerar novos
- Senhas são criptografadas antes de salvar
- Limpeza automática de tokens expirados (diariamente às 2h)

## Exemplo de Implementação Completa

```typescript
class PasswordResetService {
  private baseUrl = '/api/auth/password-reset';
  
  async requestReset(login: string): Promise<PasswordResetGenerateResponse> {
    const response = await fetch(`${this.baseUrl}/generate`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ login })
    });
    
    if (!response.ok) {
      throw new Error(`HTTP ${response.status}: ${response.statusText}`);
    }
    
    return response.json();
  }
  
  async validateToken(token: string): Promise<PasswordResetValidateResponse> {
    const response = await fetch(`${this.baseUrl}/validate?token=${token}`);
    
    if (!response.ok) {
      throw new Error(`HTTP ${response.status}: ${response.statusText}`);
    }
    
    return response.json();
  }
  
  async resetPassword(token: string, newPassword: string, confirmPassword: string): Promise<PasswordResetResponse> {
    const response = await fetch(`${this.baseUrl}/reset`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ token, newPassword, confirmPassword })
    });
    
    if (!response.ok) {
      throw new Error(`HTTP ${response.status}: ${response.statusText}`);
    }
    
    return response.json();
  }
}
```

## Suporte

Para dúvidas sobre a integração, consulte:
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Esta documentação
- Logs da aplicação para debugging