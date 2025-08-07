# Guia de Invalidação de Cache para Testes

Este documento explica como usar o endpoint de invalidação de cache de forma segura em testes.

## Endpoint de Invalidação

```
DELETE /api/v1/vehicle/cache/invalidate
```

## Proteção de Segurança

O endpoint requer um header de confirmação para evitar uso acidental:

```
X-Confirm-Action: CONFIRM_INVALIDATE
```

## Exemplos de Uso

### 1. Usando cURL

```bash
# Exemplo básico
curl -X DELETE http://localhost:8080/api/v1/vehicle/cache/invalidate \
  -H "X-Confirm-Action: CONFIRM_INVALIDATE" \
  -H "Content-Type: application/json"

# Com autenticação (se necessário)
curl -X DELETE http://localhost:8080/api/v1/vehicle/cache/invalidate \
  -H "X-Confirm-Action: CONFIRM_INVALIDATE" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json"
```

### 2. Usando em Testes Java (JUnit + TestRestTemplate)

```java
@Test
public void testCacheInvalidation() {
    // Configurar headers
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Confirm-Action", "CONFIRM_INVALIDATE");
    
    HttpEntity<Void> entity = new HttpEntity<>(headers);
    
    // Fazer chamada
    ResponseEntity<Map> response = restTemplate.exchange(
        "/api/v1/vehicle/cache/invalidate",
        HttpMethod.DELETE,
        entity,
        Map.class
    );
    
    // Verificar resposta
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().get("status")).isEqualTo("success");
}
```

### 3. Usando em Testes de Integração

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class VehicleCacheIntegrationTest {
    
    @Autowired
    private TestRestTemplate restTemplate;
    
    @BeforeEach
    void clearCache() {
        invalidateCache();
    }
    
    @AfterEach
    void clearCacheAfterTest() {
        invalidateCache();
    }
    
    private void invalidateCache() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Confirm-Action", "CONFIRM_INVALIDATE");
        
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        
        ResponseEntity<Map> response = restTemplate.exchange(
            "/api/v1/vehicle/cache/invalidate",
            HttpMethod.DELETE,
            entity,
            Map.class
        );
        
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Falha ao limpar cache: " + response.getBody());
        }
    }
    
    @Test
    void testVehicleSearchWithFreshCache() {
        // Teste começa com cache limpo
        // ... seu teste aqui
    }
}
```

### 4. Usando em Scripts de Setup/Teardown

```bash
#!/bin/bash
# setup-test-environment.sh

echo "Limpando cache para testes..."

RESPONSE=$(curl -s -X DELETE http://localhost:8080/api/v1/vehicle/cache/invalidate \
  -H "X-Confirm-Action: CONFIRM_INVALIDATE" \
  -H "Content-Type: application/json")

if echo "$RESPONSE" | grep -q '"status":"success"'; then
    echo "✅ Cache limpo com sucesso"
    echo "$RESPONSE" | jq '.recordsRemoved' | xargs echo "Registros removidos:"
else
    echo "❌ Falha ao limpar cache"
    echo "$RESPONSE"
    exit 1
fi
```

## Respostas do Endpoint

### ✅ Sucesso
```json
{
  "status": "success",
  "message": "Cache invalidado com sucesso",
  "recordsRemoved": 150,
  "warning": "Próxima consulta forçará atualização via API"
}
```

### ❌ Sem Header de Confirmação
```json
{
  "status": "error",
  "message": "Ação não confirmada. Adicione o header 'X-Confirm-Action: CONFIRM_INVALIDATE'",
  "warning": "Esta operação remove TODOS os dados do cache"
}
```

### ❌ Erro no Servidor
```json
{
  "status": "error",
  "message": "Falha ao invalidar o cache",
  "error": "Detalhes do erro..."
}
```

## Casos de Uso Recomendados

### ✅ Apropriado Para:
- **Testes automatizados**: Limpeza do estado entre testes
- **Testes de integração**: Garantir estado limpo
- **Debugging**: Forçar atualização do cache
- **Desenvolvimento**: Reset rápido do cache

### ⚠️ Usar Com Cuidado Em:
- **Ambiente de produção**: Pode impactar performance
- **Horários de pico**: Causará consultas diretas à API

### ❌ Não Recomendado Para:
- **Uso rotineiro**: Prefira aguardar expiração natural
- **Automação sem controle**: Sempre use o header de confirmação

## Monitoramento

Após a invalidação, observe os logs:

```
WARN c.m.m.controller.VehicleController - INVALIDAÇÃO DE CACHE SOLICITADA - Removendo todos os dados do cache
INFO c.m.m.service.VehicleCacheService - Invalidando todo o cache de veículos
INFO c.m.m.service.VehicleCacheService - Cache invalidado com sucesso - 0 registros removidos
```

## Verificação do Estado do Cache

Após invalidar, você pode verificar o status via endpoint principal:

```bash
curl -X GET http://localhost:8080/api/v1/vehicle \
  -H "Content-Type: application/json" \
  | jq '.metadata'
```

Resposta esperada com cache vazio:
```json
{
  "source": "PostgreSQL",
  "strategy": "Database-First",
  "cacheValid": false,
  "cacheAgeMinutes": 0,
  "totalRecordsInCache": 0
}
```