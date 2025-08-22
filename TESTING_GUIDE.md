# Testing Guide - Password Reset Feature

## Overview
Este documento descreve como executar os testes da funcionalidade de redefinição de senha.

## Test Structure

### Unit Tests
- **PasswordResetServiceImplTest**: Testa a lógica de negócio do serviço
- **PasswordResetControllerIntegrationTest**: Testa os endpoints do controller
- **IPasswordResetTokenRepositoryIntegrationTest**: Testa as operações do repository

### Test Configuration
- **application-test.properties**: Configuração específica para testes
- **H2 Database**: Banco de dados em memória para testes

## Running Tests

### All Tests
```bash
mvn test
```

### Specific Test Class
```bash
mvn test -Dtest=PasswordResetServiceImplTest
```

### Specific Test Method
```bash
mvn test -Dtest=PasswordResetServiceImplTest#generatePasswordResetToken_WithValidUsername_ShouldReturnResetLink
```

### Tests with Coverage
```bash
mvn test jacoco:report
```

## Test Coverage

### Service Layer (PasswordResetServiceImpl)
- ✅ Geração de token com username válido
- ✅ Geração de token com email válido
- ✅ Validação de login inválido
- ✅ Invalidação de tokens existentes
- ✅ Validação de token válido
- ✅ Validação de token expirado
- ✅ Validação de token já utilizado
- ✅ Validação de token inexistente
- ✅ Marcação de token como utilizado
- ✅ Limpeza de tokens expirados

### Controller Layer (PasswordResetController)
- ✅ Geração de token com request válido
- ✅ Geração de token com request inválido
- ✅ Validação de token válido
- ✅ Validação de token inválido
- ✅ Validação sem token
- ✅ Limpeza de tokens expirados
- ✅ Tratamento de exceções

### Repository Layer (IPasswordResetTokenRepository)
- ✅ Busca por token
- ✅ Busca por usuário
- ✅ Busca de tokens válidos
- ✅ Busca de tokens expirados
- ✅ Verificação de existência
- ✅ Remoção por usuário
- ✅ Remoção por expiração

## Test Database

### H2 Configuration
- **URL**: `jdbc:h2:mem:testdb`
- **Username**: `sa`
- **Password**: (empty)
- **Console**: Disponível em `/h2-console` durante testes

### Schema
- Tabelas criadas automaticamente pelo Hibernate
- Dados limpos após cada teste
- Isolamento completo entre testes

## Best Practices

### Test Naming
- Use nomes descritivos: `methodName_WithCondition_ShouldReturnExpectedResult`
- Exemplo: `generatePasswordResetToken_WithValidUsername_ShouldReturnResetLink`

### Test Structure (AAA Pattern)
- **Arrange**: Preparar dados e mocks
- **Act**: Executar o método sendo testado
- **Assert**: Verificar o resultado esperado

### Mocking
- Use `@MockBean` para dependências externas
- Use `@InjectMocks` para injeção de mocks
- Evite mocks desnecessários

### Assertions
- Use assertions específicas do JUnit 5
- Verifique tanto o resultado quanto o comportamento
- Teste casos de sucesso e falha

## Debugging Tests

### Enable H2 Console
```properties
spring.h2.console.enabled=true
```

### View SQL Queries
```properties
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true
```

### Logging
```properties
logging.level.com.montreal.oauth=DEBUG
logging.level.org.springframework.security=DEBUG
```

## Common Issues

### Security Configuration
- Testes podem falhar se a configuração de segurança não estiver correta
- Use `@WebMvcTest` para testes de controller
- Use `@DataJpaTest` para testes de repository

### Database Transactions
- Cada teste roda em uma transação separada
- Dados são limpos automaticamente
- Evite dependências entre testes

### Mock Configuration
- Configure mocks no `@BeforeEach`
- Use `when().thenReturn()` para comportamento esperado
- Verifique interações com `verify()`

## Performance

### Test Execution Time
- Testes unitários: < 100ms cada
- Testes de integração: < 500ms cada
- Teste completo: < 5 segundos

### Memory Usage
- H2 em memória: ~50MB
- Total de testes: ~100MB
- Limpeza automática após cada teste

## Continuous Integration

### GitHub Actions
```yaml
- name: Run Tests
  run: mvn test
```

### Coverage Threshold
- Mínimo: 80%
- Meta: 90%
- Report: Gerado automaticamente

## Next Steps
1. Adicionar testes de performance
2. Implementar testes de stress
3. Adicionar testes de segurança
4. Implementar testes de API end-to-end