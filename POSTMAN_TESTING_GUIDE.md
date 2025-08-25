# Guia de Teste da Funcionalidade de Password Reset - Postman

## 📋 Pré-requisitos
- Postman instalado
- Aplicação Spring Boot rodando na porta 8080
- Banco de dados configurado (ou usar perfil de teste com H2)

## 🚀 Configuração da Aplicação

### 1. Iniciar a Aplicação
```bash
# Com perfil de teste (H2 em memória)
mvn spring-boot:run -Dspring-boot.run.profiles=test

# Com perfil local (PostgreSQL)
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

### 2. Verificar se está rodando
```bash
curl http://localhost:8080/actuator/health
```

## 🧪 Testes no Postman

### **1. Gerar Token de Redefinição de Senha**

**Endpoint:** `POST /api/password-reset/generate`

**Headers:**
```
Content-Type: application/json
```

**Body (JSON):**
```json
{
    "login": "testuser"
}
```

**Resposta Esperada (Sucesso):**
```json
{
    "resetLink": "http://localhost:5173/reset-password?token=UUID_GERADO",
    "message": "Token de redefinição de senha gerado com sucesso"
}
```

**Resposta Esperada (Usuário não encontrado):**
```json
{
    "message": "login informado inválido",
    "timestamp": "2025-08-25T20:50:00Z"
}
```

---

### **2. Validar Token de Redefinição**

**Endpoint:** `GET /api/password-reset/validate?token={TOKEN}`

**Headers:**
```
Content-Type: application/json
```

**Resposta Esperada (Token válido):**
```json
{
    "valid": true,
    "message": "Token válido"
}
```

**Resposta Esperada (Token inválido/expirado):**
```json
{
    "valid": false,
    "message": "Token inválido ou expirado"
}
```

---

### **3. Redefinir Senha**

**Endpoint:** `POST /api/password-reset/reset`

**Headers:**
```
Content-Type: application/json
```

**Body (JSON):**
```json
{
    "token": "UUID_DO_TOKEN",
    "newPassword": "Test@123",
    "confirmPassword": "Test@123"
}
```

**Resposta Esperada (Sucesso):**
```json
{
    "message": "Senha redefinida com sucesso",
    "success": true
}
```

**Respostas de Erro (Validação):**

**Senha muito curta:**
```json
{
    "message": "A senha deve ter entre 4 e 8 caracteres",
    "success": false
}
```

**Senha muito longa:**
```json
{
    "message": "A senha deve ter entre 4 e 8 caracteres",
    "success": false
}
```

**Sem letra minúscula:**
```json
{
    "message": "A senha deve conter pelo menos uma letra minúscula",
    "success": false
}
```

**Sem letra maiúscula:**
```json
{
    "message": "A senha deve conter pelo menos uma letra maiúscula",
    "success": false
}
```

**Sem número:**
```json
{
    "message": "A senha deve conter pelo menos um número",
    "success": false
}
```

**Sem caractere especial:**
```json
{
    "message": "A senha deve conter pelo menos um dos caracteres especiais: _ @ #",
    "success": false
}
```

**Senhas não coincidem:**
```json
{
    "message": "As senhas não coincidem",
    "success": false
}
```

**Confirmação vazia:**
```json
{
    "message": "Confirmação de senha é obrigatória",
    "success": false
}
```

---

### **4. Marcar Token como Usado**

**Endpoint:** `POST /api/password-reset/mark-used`

**Headers:**
```
Content-Type: application/json
```

**Body (JSON):**
```json
{
    "token": "UUID_DO_TOKEN"
}
```

**Resposta Esperada:**
```json
{
    "message": "Token marcado como usado com sucesso"
}
```

---

## 🔍 Cenários de Teste

### **Cenário 1: Fluxo Completo de Sucesso**
1. ✅ Gerar token para usuário válido
2. ✅ Validar token gerado
3. ✅ Redefinir senha com dados válidos
4. ✅ Verificar se token foi marcado como usado

### **Cenário 2: Validações de Senha**
1. ❌ Tentar senha muito curta (3 caracteres)
2. ❌ Tentar senha muito longa (9+ caracteres)
3. ❌ Tentar senha sem letra minúscula
4. ❌ Tentar senha sem letra maiúscula
5. ❌ Tentar senha sem número
6. ❌ Tentar senha sem caractere especial
7. ❌ Tentar senha com caracteres especiais inválidos
8. ✅ Tentar senha válida (Test@123)

### **Cenário 3: Validações de Confirmação**
1. ❌ Tentar com confirmação vazia
2. ❌ Tentar com confirmação nula
3. ❌ Tentar com senhas diferentes
4. ✅ Tentar com senhas iguais

### **Cenário 4: Validações de Token**
1. ❌ Tentar com token inexistente
2. ❌ Tentar com token expirado
3. ❌ Tentar com token já usado
4. ✅ Tentar com token válido

### **Cenário 5: Usuário Inexistente**
1. ❌ Tentar gerar token para usuário inexistente

---

## 📝 Exemplos de Senhas para Teste

### **Senhas Válidas:**
- `Test@12` (4 caracteres)
- `Test@123` (8 caracteres)
- `Ab@123` (6 caracteres)

### **Senhas Inválidas:**
- `Ab@` (3 caracteres - muito curta)
- `Ab@123456` (10 caracteres - muito longa)
- `TEST@12` (sem minúscula)
- `test@12` (sem maiúscula)
- `Test@ab` (sem número)
- `Test123` (sem caractere especial)
- `Test!12` (caractere especial inválido)

---

## 🚨 Tratamento de Erros

### **Códigos de Status HTTP:**
- `200 OK` - Operação realizada com sucesso
- `400 Bad Request` - Dados inválidos na requisição
- `404 Not Found` - Recurso não encontrado
- `500 Internal Server Error` - Erro interno do servidor

### **Logs da Aplicação:**
Verificar logs para debug:
```bash
tail -f logs/acelera-gestao-garantias-dev.log
```

---

## 🔧 Configurações Importantes

### **application-test.properties:**
- Banco H2 em memória
- OAuth desabilitado para testes
- Logs detalhados habilitados

### **application-local.properties:**
- Banco PostgreSQL
- OAuth habilitado
- Configurações de produção

---

## 📊 Métricas de Teste

### **Testes de Performance:**
- Tempo de resposta para geração de token
- Tempo de resposta para validação de senha
- Tempo de resposta para redefinição de senha

### **Testes de Segurança:**
- Validação de entrada (XSS, SQL Injection)
- Rate limiting (se implementado)
- Validação de token único

---

## 🎯 Checklist de Validação

- [ ] Aplicação inicia sem erros
- [ ] Endpoints respondem corretamente
- [ ] Validações de senha funcionam
- [ ] Validações de confirmação funcionam
- [ ] Tokens são gerados e validados
- [ ] Senhas são criptografadas com BCrypt
- [ ] Tokens são marcados como usados
- [ ] Tratamento de erros adequado
- [ ] Logs informativos
- [ ] Respostas JSON consistentes

---

## 🆘 Solução de Problemas

### **Aplicação não inicia:**
1. Verificar logs de erro
2. Verificar configuração do banco
3. Verificar dependências Maven
4. Verificar portas disponíveis

### **Endpoints não respondem:**
1. Verificar se aplicação está rodando
2. Verificar configuração de segurança
3. Verificar mapeamento de URLs
4. Verificar logs de erro

### **Validações não funcionam:**
1. Verificar implementação dos validators
2. Verificar anotações de validação
3. Verificar tratamento de exceções
4. Verificar logs de debug