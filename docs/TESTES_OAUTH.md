# Testes Unitários e de Integração - Pacote OAuth

Este documento descreve os testes criados para o pacote `oauth`, especificamente para as funcionalidades de redefinição de senha e histórico de senhas.

## Estrutura dos Testes

### 1. Testes do PasswordResetController

#### Testes Unitários
- **Arquivo**: `src/test/java/com/montreal/oauth/controller/PasswordResetControllerUnitTest.java`
- **Tecnologia**: JUnit 5 + Mockito
- **Cobertura**: Geração de token, validação, limpeza, redefinição de senha

#### Testes de Integração
- **Arquivo**: `src/test/java/com/montreal/oauth/controller/PasswordResetControllerIntegrationTest.java`
- **Tecnologia**: @SpringBootTest + MockMvc + H2
- **Cobertura**: Testes end-to-end dos endpoints REST

### 2. Testes do PasswordHistoryService

#### Testes Unitários
- **Arquivo**: `src/test/java/com/montreal/oauth/domain/service/PasswordHistoryServiceUnitTest.java`
- **Tecnologia**: JUnit 5 + Mockito
- **Cobertura**: Validação de histórico, salvamento, limpeza

#### Testes de Integração
- **Arquivo**: `src/test/java/com/montreal/oauth/domain/service/PasswordHistoryServiceIntegrationTest.java`
- **Tecnologia**: @SpringBootTest + @DataJpaTest + H2
- **Cobertura**: Persistência real no banco

### 3. Testes do PasswordResetServiceImpl

#### Testes Unitários
- **Arquivo**: `src/test/java/com/montreal/oauth/domain/service/PasswordResetServiceImplUnitTest.java`
- **Tecnologia**: JUnit 5 + Mockito
- **Cobertura**: Lógica de negócio completa

#### Testes de Integração
- **Arquivo**: `src/test/java/com/montreal/oauth/domain/service/PasswordResetServiceImplIntegrationTest.java`
- **Tecnologia**: @SpringBootTest + H2
- **Cobertura**: Integração completa do serviço

### 4. Testes dos Repositórios

#### PasswordHistoryRepository
- **Arquivo**: `src/test/java/com/montreal/oauth/domain/repository/PasswordHistoryRepositoryIntegrationTest.java`
- **Tecnologia**: @DataJpaTest + H2
- **Cobertura**: Operações de banco de dados

#### PasswordResetTokenRepository
- **Arquivo**: `src/test/java/com/montreal/oauth/domain/repository/PasswordResetTokenRepositoryIntegrationTest.java`
- **Tecnologia**: @DataJpaTest + H2
- **Cobertura**: Gerenciamento de tokens

## Configuração de Teste

### Arquivo de Configuração
- **Arquivo**: `src/test/resources/application-test.properties`
- **Banco de Dados**: H2 em memória
- **Configurações**: DDL auto: create-drop, histórico habilitado

## Execução dos Testes

```bash
# Todos os testes de redefinição de senha
./mvnw test -Dtest="*PasswordReset*" -Dspring.profiles.active=test

# Todos os testes de histórico de senhas
./mvnw test -Dtest="*PasswordHistory*" -Dspring.profiles.active=test
```

## Boas Práticas Implementadas

1. **Nomenclatura Clara**: Padrão `methodName_scenario_expectedResult`
2. **Arrange-Act-Assert**: Estrutura clara dos testes
3. **Testes Independentes**: Isolamento entre testes
4. **Uso Correto de Mocks**: Apenas para dependências externas
5. **Cobertura Completa**: Sucesso, erro, exceções, edge cases

## Cobertura de Testes

- ✅ PasswordResetController (unitário + integração)
- ✅ PasswordHistoryService (unitário + integração)
- ✅ PasswordResetServiceImpl (unitário + integração)
- ✅ Repositórios (integração com banco)

## Próximos Passos

1. Executar testes para verificar compilação
2. Analisar cobertura de código
3. Integrar com CI/CD
4. Adicionar testes específicos conforme necessário
