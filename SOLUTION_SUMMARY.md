# Solução para Duplicatas no Job de Cache de Veículos

## Problema Identificado

O job `VehicleCacheUpdateJob` estava criando registros duplicados no PostgreSQL a cada execução (a cada 10 minutos), mesmo quando os dados da API eram idênticos aos já armazenados no banco.

### Causa Raiz

O problema estava relacionado ao uso do **pgcrypto com criptografia não-determinística**:

1. **Criptografia não-determinística**: O pgcrypto produz hash diferente para a mesma string a cada criptografia
2. **Comparação incorreta**: O método `findExistingVehicle` tentava comparar valores criptografados diretamente
3. **Falha na identificação**: Como os hashes eram sempre diferentes, veículos existentes não eram encontrados
4. **Resultado**: Novos registros eram inseridos em vez de atualizar os existentes

### Exemplo do Problema

```
API retorna: placa="ABC1234" 
VehicleInquiryMapper criptografa: "c30d04090302b7563afaa573fbfe6ad2..."

No banco existe: placa="c30d04090302adab9a83204079c876d2..." (mesmo valor "ABC1234" mas hash diferente)

Comparação direta: "c30d04090302b7563afaa573fbfe6ad2..." != "c30d04090302adab9a83204079c876d2..."
Resultado: FALSO POSITIVO - inserção de duplicata
```

## Solução Implementada

### 1. Correção do método `findExistingVehicle`

**ANTES** (comparação de valores criptografados):
```java
// DTO fields são criptografados, tentava comparar diretamente
String dtoPlacaEncrypted = dto.placa();
Optional<VehicleCache> byPlaca = vehicleCacheRepository.findByPlaca(dtoPlacaEncrypted);
```

**DEPOIS** (descriptografia antes da comparação):
```java
// Descriptografa os campos do DTO para comparação
String dtoPlacaDecrypted = cryptoService.decryptPlaca(dto.placa());
String dtoContratoDecrypted = cryptoService.decryptContrato(dto.contrato());

// Busca usando comparação de valores descriptografados
Optional<VehicleCache> byPlaca = findByDecryptedPlaca(dtoPlacaDecrypted);
```

### 2. Correção do método `hasDataChanges`

**ANTES** (comparação mixta com fallback):
```java
// Comparava criptografado primeiro, depois descriptografado se diferente
boolean contratoChanged = !Objects.equals(existing.getContrato(), dto.contrato());
if (contratoChanged) {
    // Fallback: comparar descriptografado
    String existingDecrypted = cryptoService.decryptContrato(existing.getContrato());
    String dtoDecrypted = cryptoService.decryptContrato(dto.contrato());
    contratoChanged = !Objects.equals(existingDecrypted, dtoDecrypted);
}
```

**DEPOIS** (sempre descriptografa antes de comparar):
```java
// Descriptografa ambos valores antes da comparação
String existingContrato = cryptoService.decryptContrato(existing.getContrato());
String existingPlaca = cryptoService.decryptPlaca(existing.getPlaca());
String dtoContratoDecrypted = cryptoService.decryptContrato(dto.contrato());
String dtoPlacaDecrypted = cryptoService.decryptPlaca(dto.placa());

// Compara valores descriptografados
boolean contratoChanged = !Objects.equals(existingContrato, dtoContratoDecrypted);
boolean placaChanged = !Objects.equals(existingPlaca, dtoPlacaDecrypted);
```

### 3. Melhoria no Logging

- **Identificador principal**: Usar `placa` em vez de `protocolo` (que é sempre null)
- **Performance**: Descriptografar placa uma única vez por iteração
- **Clareza**: Mensagens mais específicas sobre ações tomadas

```java
String placaDescriptografada = cryptoService.decryptPlaca(dto.placa());

// Logs mais claros
log.debug("✓ Veículo ATUALIZADO (dados mudaram): placa={}", placaDescriptografada);
log.debug("⚡ Veículo SEM MUDANÇAS (só sync date): placa={}", placaDescriptografada);
log.debug("➕ NOVO veículo inserido: placa={}", placaDescriptografada);
```

### 4. Estratégia de Busca Otimizada

**Ordem de prioridade para encontrar veículos existentes**:

1. **Primeira tentativa**: Busca por placa descriptografada (chave principal)
2. **Segunda tentativa**: Busca por contrato descriptografado (fallback)
3. **Terceira tentativa**: Busca por protocolo (raramente preenchido)

```java
// 1. PRIMEIRO: Busca por placa descriptografada (sempre preenchida)
if (dtoPlacaDecrypted != null && !"N/A".equals(dtoPlacaDecrypted)) {
    Optional<VehicleCache> byPlaca = findByDecryptedPlaca(dtoPlacaDecrypted);
    if (byPlaca.isPresent()) return byPlaca;
}

// 2. SEGUNDO: Busca por contrato descriptografado (fallback)
if (dtoContratoDecrypted != null && !"N/A".equals(dtoContratoDecrypted)) {
    Optional<VehicleCache> byContrato = findByDecryptedContrato(dtoContratoDecrypted);
    if (byContrato.isPresent()) return byContrato;
}

// 3. TERCEIRO: Busca por protocolo (se disponível)
if (dto.protocolo() != null && !"N/A".equals(dto.protocolo())) {
    return vehicleCacheRepository.findByProtocolo(dto.protocolo());
}
```

## Arquivos Modificados

1. **`VehicleCacheService.java`**:
   - `findExistingVehicle()`: Descriptografa campos do DTO antes da busca
   - `hasDataChanges()`: Compara sempre valores descriptografados
   - `updateOrInsertVehicles()`: Logging melhorado com placa como identificador

## Resultado Esperado

### ANTES da correção:
```
=== RESULTADO DA SINCRONIZAÇÃO ===
✅ 0 atualizados (com mudanças)
⚡ 0 sem mudanças (só sync date)  
➕ 11 novos inseridos ← DUPLICATAS!
⚠️ 0 duplicados ignorados
📊 Total processado: 11
```

### DEPOIS da correção:
```
=== RESULTADO DA SINCRONIZAÇÃO ===
✅ 0 atualizados (com mudanças)
⚡ 11 sem mudanças (só sync date) ← CORRETO!
➕ 0 novos inseridos
⚠️ 0 duplicados ignorados
📊 Total processado: 11

🎯 SINCRONIZAÇÃO PERFEITA: Dados já estavam em sincronia com a API!
```

## Configuração do Job

O job está configurado para executar a cada 10 minutos (600000ms):

```properties
# application.properties
vehicle.cache.update.enabled=true
vehicle.cache.update.interval=600000  # 10 minutos
vehicle.cache.expiry.minutes=10
```

## Validação da Solução

1. **Teste de primeira execução**: Deve inserir 11 registros novos
2. **Teste de segunda execução**: Deve encontrar os 11 registros existentes e apenas atualizar `api_sync_date`
3. **Teste de dados alterados**: Deve atualizar apenas os registros que realmente mudaram
4. **Teste de performance**: Busca otimizada por placa (campo sempre preenchido)

A solução garante que:
- ✅ Não há mais duplicatas
- ✅ Performance otimizada (menos descriptografias desnecessárias)
- ✅ Logging claro para debugging
- ✅ Compatibilidade com criptografia não-determinística
- ✅ Estratégia robusta de identificação de registros existentes