# Testes para Funcionalidade de Redefinição de Senha

Este documento descreve a cobertura de testes implementada para a funcionalidade de redefinição de senha e histórico de senhas.

## Estrutura dos Testes

### 1. Testes Unitários

#### Controller
- **PasswordResetControllerUnitTest**: Testa todos os endpoints do controller
  - Geração de token de redefinição
  - Validação de token
  - Redefinição de senha
  - Limpeza de tokens expirados
  - Tratamento de erros e validações

#### Services
- **PasswordResetServiceImplUnitTest**: Testa a lógica de negócio do serviço
  - Geração e validação de tokens
  - Redefinição de senha com validações
  - Integração com histórico de senhas
  - Auto-login após redefinição
  - Tratamento de diferentes cenários de erro

- **PasswordHistoryServiceUnitTest**: Testa o serviço de histórico de senhas
  - Validação contra senhas anteriores
  - Salvamento de novas senhas no histórico
  - Limpeza de histórico antigo
  - Configuração de limite de histórico

#### Entities
- **PasswordResetTokenUnitTest**: Testa a entidade de token
  - Validação de expiração
  - Validação de uso
  - Métodos de negócio
  - Construtores e builders

- **PasswordHistoryUnitTest**: Testa a entidade de histórico
  - Persistência de dados
  - Relacionamentos
  - Métodos de negócio

#### DTOs
- **PasswordResetRequestUnitTest**: Testa validações do DTO de request
  - Validação de campos obrigatórios
  - Validação de tamanho de senha
  - Validação de formato

- **PasswordResetGenerateRequestUnitTest**: Testa validações do DTO de geração
  - Validação de login
  - Validação de tamanho
  - Validação de formato

### 2. Testes de Integração

#### Repositories
- **PasswordResetTokenRepositoryIntegrationTest**: Testa operações do repositório
  - Busca por token
  - Busca por usuário
  - Busca de tokens válidos
  - Limpeza de tokens expirados
  - Operações CRUD

- **PasswordHistoryRepositoryIntegrationTest**: Testa operações do repositório
  - Busca de histórico por usuário
  - Limpeza de histórico antigo
  - Paginação de resultados
  - Operações CRUD

#### End-to-End
- **PasswordResetIntegrationTest**: Testa o fluxo completo
  - Geração de token
  - Validação de token
  - Redefinição de senha
  - Integração com histórico
  - Cenários de erro
  - Limpeza de tokens

## Cobertura de Testes

### Cenários Testados

#### Fluxo Principal
1. ✅ Geração de token de redefinição
2. ✅ Validação de token
3. ✅ Redefinição de senha com validações
4. ✅ Salvamento no histórico de senhas
5. ✅ Auto-login após redefinição
6. ✅ Limpeza de tokens expirados

#### Cenários de Erro
1. ✅ Usuário não encontrado
2. ✅ Token expirado
3. ✅ Token já utilizado
4. ✅ Token inválido
5. ✅ Senha inválida (critérios não atendidos)
6. ✅ Senhas não coincidem
7. ✅ Senha igual à atual
8. ✅ Reutilização de senha anterior
9. ✅ Erros de banco de dados
10. ✅ Erros de validação

#### Validações de Segurança
1. ✅ Critérios de complexidade de senha
2. ✅ Histórico de senhas (não reutilização)
3. ✅ Expiração de tokens
4. ✅ Invalidação de tokens anteriores
5. ✅ Limpeza automática de tokens expirados

## Como Executar os Testes

### Executar Todos os Testes
```bash
mvn test
```

### Executar Testes Específicos
```bash
# Testes unitários do controller
mvn test -Dtest=PasswordResetControllerUnitTest

# Testes unitários do service
mvn test -Dtest=PasswordResetServiceImplUnitTest

# Testes de integração
mvn test -Dtest=*IntegrationTest

# Testes de repositório
mvn test -Dtest=*RepositoryIntegrationTest
```

### Executar com Cobertura
```bash
mvn clean test jacoco:report
```

## Configuração de Teste

### Perfil de Teste
Os testes utilizam o perfil `test` com as seguintes configurações:

- **Banco de Dados**: H2 em memória
- **Logging**: Nível DEBUG para debugging
- **Histórico de Senhas**: Habilitado com limite de 3 senhas
- **Auto-login**: Habilitado após redefinição

### Dependências de Teste
- Spring Boot Test Starter
- Spring Security Test
- H2 Database (para testes)
- Mockito
- JUnit 5

## Métricas de Qualidade

### Cobertura Esperada
- **Linhas de Código**: > 90%
- **Branches**: > 85%
- **Métodos**: > 95%
- **Classes**: > 90%

### Tipos de Teste
- **Unitários**: 70% dos testes
- **Integração**: 25% dos testes
- **End-to-End**: 5% dos testes

## Manutenção dos Testes

### Adicionando Novos Testes
1. Seguir o padrão AAA (Arrange, Act, Assert)
2. Usar nomes descritivos para os métodos de teste
3. Testar cenários positivos e negativos
4. Incluir testes de validação
5. Documentar casos especiais

### Atualizando Testes
1. Manter compatibilidade com mudanças de API
2. Atualizar mocks quando necessário
3. Verificar cobertura após mudanças
4. Executar todos os testes antes de commit

## Troubleshooting

### Problemas Comuns
1. **Falha de conexão com banco**: Verificar configuração do H2
2. **Timeout em testes**: Aumentar timeout ou otimizar queries
3. **Falha de validação**: Verificar configuração do Bean Validation
4. **Problemas de transação**: Verificar anotações @Transactional

### Logs de Debug
Para debug detalhado, adicionar ao `application-test.yml`:
```yaml
logging:
  level:
    com.montreal: TRACE
    org.springframework.security: TRACE
    org.hibernate.SQL: DEBUG
```