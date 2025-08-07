# Solução para Duplicação de Registros no Cache de Veículos

## Problema Identificado

O job executado a cada 10 minutos estava criando **registros duplicados** no PostgreSQL ao invés de atualizar os existentes. A causa raiz foi identificada como **dupla criptografia** e **comparação inadequada** de campos criptografados.

### Causa Raiz

1. **PostgreSQL `pgcrypto` usa criptografia não-determinística**: A mesma string produz valores criptografados diferentes a cada execução
2. **Dupla criptografia**: Campos eram criptografados no `VehicleInquiryMapper` e novamente no `VehicleCacheMapper` e `updateExistingVehicle`
3. **Comparação inadequada**: Métodos de busca tentavam descriptografar campos já criptografados

## Correções Implementadas

### 1. VehicleCacheService.findExistingVehicle()

**Problema**: Tentava descriptografar campos do DTO que já estavam criptografados.

**Solução**: 
- Busca direta por campos criptografados primeiro (mais eficiente)
- Fallback para busca descriptografada (compatibilidade)

```java
// ANTES - INCORRETO
String dtoPlacaDecrypted = cryptoService.decryptPlaca(dto.placa());
Optional<VehicleCache> byPlaca = findByDecryptedPlaca(dtoPlacaDecrypted);

// DEPOIS - CORRETO
String dtoPlacaEncrypted = dto.placa(); // Já criptografado!
Optional<VehicleCache> byPlaca = vehicleCacheRepository.findByPlaca(dtoPlacaEncrypted);
```

### 2. VehicleCacheService.hasDataChanges()

**Problema**: Descriptografava campos já criptografados, causando erro.

**Solução**:
- Compara campos criptografados diretamente primeiro
- Só descriptografa se houver diferença (para verificar se é só diferença de criptografia)

```java
// Compara campos criptografados diretamente primeiro
boolean contratoChanged = !Objects.equals(existing.getContrato(), dto.contrato());

// Só descriptografa se necessário
if (contratoChanged) {
    String existingDecrypted = cryptoService.decryptContrato(existing.getContrato());
    String dtoDecrypted = cryptoService.decryptContrato(dto.contrato());
    contratoChanged = !Objects.equals(existingDecrypted, dtoDecrypted);
}
```

### 3. VehicleCacheService.updateExistingVehicle()

**Problema**: Re-criptografava campos já criptografados.

**Solução**: Usa campos do DTO diretamente (já criptografados).

```java
// ANTES - INCORRETO (dupla criptografia)
existing.setContrato(cryptoService.encryptContrato(dto.contrato()));
existing.setPlaca(cryptoService.encryptPlaca(dto.placa()));

// DEPOIS - CORRETO
existing.setContrato(dto.contrato()); // Já criptografado
existing.setPlaca(dto.placa());       // Já criptografado
```

### 4. VehicleCacheMapper.toEntity()

**Problema**: Re-criptografava campos já criptografados do DTO.

**Solução**: Usa mapeamento direto sem re-criptografia.

```java
// ANTES - INCORRETO
@Mapping(target = "placa", expression = "java(cryptoService.encryptPlaca(dto.placa()))")
@Mapping(target = "contrato", expression = "java(cryptoService.encryptContrato(dto.contrato()))")

// DEPOIS - CORRETO
@Mapping(target = "placa", source = "dto.placa")
@Mapping(target = "contrato", source = "dto.contrato")
```

## Fluxo Correto de Criptografia

### Entrada da API → DTO
- `VehicleInquiryMapper.mapToVeiculoDTO()` criptografa os campos quando `shouldEncrypt=true`
- DTO sai com campos `placa` e `contrato` **já criptografados**

### DTO → Banco de Dados
- `VehicleCacheService` usa campos já criptografados diretamente
- Nenhuma re-criptografia é necessária
- Comparações são feitas entre valores criptografados ou descriptografados conforme necessário

### Banco de Dados → Response
- `VehicleCacheService.decryptAndMapToDTO()` descriptografa para retornar dados limpos
- `VehicleCacheMapper.toDTO()` descriptografa automaticamente

## Benefícios da Solução

1. **Elimina duplicatas**: Busca correta identifica registros existentes
2. **Performance**: Busca direta por campos criptografados é mais rápida
3. **Compatibilidade**: Fallback para busca descriptografada mantém compatibilidade
4. **Robustez**: Tratamento de erros preserva dados em caso de falha

## Logs Melhorados

- Logs mais claros sobre o que está sendo buscado e encontrado
- Diferenciação entre busca direta e fallback
- Identificação clara de registros novos vs. atualizados vs. sem mudanças

## Resultado Esperado

- **0 duplicatas**: Mesmo registro da API sempre encontra o mesmo registro no banco
- **Sincronização perfeita**: Mensagem "🎯 SINCRONIZAÇÃO PERFEITA: Dados já estavam em sincronia com a API!"
- **Performance melhor**: Menos descriptografações desnecessárias
- **Logs informativos**: Clareza total sobre o que o job está fazendo