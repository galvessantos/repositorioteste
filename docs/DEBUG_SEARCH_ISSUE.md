# Debug: Problema de Busca por Placa Criptografada

## 🐛 Problema Identificado
A busca por placa retorna vazio mesmo com dados no cache, provavelmente devido a problemas na criptografia/descriptografia.

## 🔍 Como Diagnosticar

### 1. Verificar se o Cache Tem Dados
```bash
# Buscar todos os veículos (sem filtro)
curl "{{base_url}}/api/v1/vehicle"
```

**Resultado esperado**: Deve retornar os 11 registros no cache.

### 2. Testar o Endpoint de Debug
```bash
# Novo endpoint para debug de criptografia
curl "{{base_url}}/api/v1/vehicle/debug/crypto/PSS0O37"
```

**Este endpoint vai**:
- Mostrar a placa original e normalizada
- Mostrar total de registros no cache
- Executar uma busca real e mostrar os logs detalhados

### 3. Verificar os Logs da Aplicação

Após executar a busca com placa, procure nos logs por:

```
🔍 DEBUG BUSCA POR PLACA:
  - Placa original: 'PSS0O37'
  - Placa normalizada: 'PSS0O37'
  - Placa criptografada: 'c30d04090302f9c8...'
  - Tamanho da placa criptografada: 128
  - Total de registros no cache: 11
  - Amostras de placas no cache:
    * Placa no banco: 'c30d04090302f9c8...' (tamanho: 128)
```

## 🕵️ Possíveis Causas

### 1. **Problema de Normalização**
- A placa buscada: `PSS0O37`
- Placas no banco podem estar salvas de forma diferente
- **Verificar**: Se há diferença entre `PSS0O37` vs `PSS0037` (O vs 0)

### 2. **Problema de Criptografia**
- A função de criptografia pode estar falhando
- Chaves de criptografia podem ter mudado

### 3. **Problema de Comparação no Banco**
- Query SQL pode não estar fazendo comparação correta
- Campos TEXT vs VARCHAR

## 🔧 Soluções

### Solução 1: Verificar Dados Reais no Banco
```sql
-- Conectar no PostgreSQL e verificar:
SELECT 
    id,
    credor,
    LEFT(placa, 20) || '...' as placa_sample,
    LENGTH(placa) as placa_length,
    modelo
FROM vehicle_cache 
LIMIT 5;
```

### Solução 2: Testar Descriptografia Manual
```sql
-- Verificar se a função de descriptografia funciona:
SELECT 
    id,
    descriptografar(decode(placa, 'hex')) as placa_descriptografada
FROM vehicle_cache 
WHERE LENGTH(placa) > 50
LIMIT 3;
```

### Solução 3: Busca Alternativa (Temporária)
Se o problema persistir, podemos implementar uma busca que descriptografa todos os registros e compara:

```java
// Implementação alternativa que descriptografa tudo (menos eficiente)
List<VehicleCache> allRecords = vehicleCacheRepository.findAll();
return allRecords.stream()
    .filter(record -> {
        String placaDescriptografada = cryptoService.decryptPlaca(record.getPlaca());
        return placa.equalsIgnoreCase(placaDescriptografada);
    })
    .collect(Collectors.toList());
```

## 📝 Comandos de Teste

### 1. Busca Normal (Problema)
```bash
curl "{{base_url}}/api/v1/vehicle?placa=PSS0O37"
```

### 2. Debug de Criptografia
```bash
curl "{{base_url}}/api/v1/vehicle/debug/crypto/PSS0O37"
```

### 3. Verificar Cache Geral
```bash
curl "{{base_url}}/api/v1/vehicle" | jq '.metadata'
```

### 4. Buscar por Outros Campos (Teste)
```bash
# Testar busca por credor (não criptografado)
curl "{{base_url}}/api/v1/vehicle?credor=Financeira"

# Testar busca por modelo (não criptografado)  
curl "{{base_url}}/api/v1/vehicle?modelo=Civic"
```

## 🎯 Próximos Passos

1. **Execute o endpoint de debug** e analise os logs
2. **Verifique se outras buscas funcionam** (credor, modelo)
3. **Compare as placas** no banco vs. a placa buscada
4. **Se necessário**, implemente busca alternativa temporária

## 📞 Resultado Esperado

Após identificar a causa raiz, você deve conseguir:
- ✅ Buscar por placa e encontrar o registro correto
- ✅ Ver logs claros sobre o processo de criptografia
- ✅ Entender se o problema é normalização, criptografia ou consulta SQL