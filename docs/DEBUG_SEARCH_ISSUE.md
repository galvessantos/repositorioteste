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

### ✅ Solução Implementada: Busca Alternativa Automática

O sistema agora detecta automaticamente quando a busca normal por placa falha e executa uma **busca alternativa** que:

1. **Descriptografa todas as placas** do cache
2. **Testa variações comuns** da placa buscada:
   - `PSS0O37` (original)
   - `PSS0037` (O → 0)
   - `PSS0O37` (0 → O) 
   - `PSS1I37` (I → 1)
   - `PSS1I37` (1 → I)
3. **Retorna resultados** mesmo com problemas de normalização

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

## 📝 Comandos de Teste

### 1. ✅ Busca Normal (Agora com Fallback Automático)
```bash
curl "{{base_url}}/api/v1/vehicle?placa=PSS0O37"
```
**Resultado esperado**: Deve encontrar o veículo automaticamente usando busca alternativa se a normal falhar.

### 2. 🆕 Teste Específico de Busca Alternativa
```bash
curl "{{base_url}}/api/v1/vehicle/debug/busca-alternativa?placa=PSS0O37"
```
**Este endpoint**:
- Executa busca normal E busca alternativa
- Compara os resultados
- Mostra detalhes dos veículos encontrados

### 3. 🆕 Debug de Descriptografia
```bash
curl "{{base_url}}/api/v1/vehicle/debug/decrypt-placas"
```
**Mostra**:
- Todas as placas descriptografadas do cache
- Total de veículos
- Permite verificar se `PSS0O37` existe

### 4. Debug de Criptografia Individual
```bash
curl "{{base_url}}/api/v1/vehicle/debug/crypto/PSS0O37"
```

### 5. Verificar Cache Geral
```bash
curl "{{base_url}}/api/v1/vehicle" | jq '.metadata'
```

### 6. Buscar por Outros Campos (Controle)
```bash
# Testar busca por credor (não criptografado)
curl "{{base_url}}/api/v1/vehicle?credor=Financeira"

# Testar busca por modelo (não criptografado)  
curl "{{base_url}}/api/v1/vehicle?modelo=Civic"
```

## 🎯 Como Testar a Solução

### Passo 1: Teste a Busca Normal
```bash
curl "{{base_url}}/api/v1/vehicle?placa=PSS0O37"
```
**Deve encontrar o veículo** automaticamente!

### Passo 2: Veja os Logs
Procure por:
```
WARN - Busca normal por placa 'PSS0O37' não retornou resultados. Tentando busca alternativa...
INFO - 🔍 BUSCA ALTERNATIVA POR PLACA: 'PSS0O37'
INFO - ✅ MATCH ENCONTRADO: 'PSS0O37' == 'PSS0037' (variação: 'PSS0037')
```

### Passo 3: Teste o Debug Específico
```bash
curl "{{base_url}}/api/v1/vehicle/debug/busca-alternativa?placa=PSS0O37"
```

## 📞 Resultado Esperado

### ✅ Agora você deve conseguir:
- ✅ **Buscar por `PSS0O37`** e encontrar o veículo
- ✅ **Ver logs claros** sobre qual variação foi encontrada
- ✅ **Entender** se o problema era `O` vs `0` ou outra normalização
- ✅ **Usar qualquer variação** da placa (`PSS0O37`, `PSS0037`, etc.)

### 🔍 Se ainda não funcionar:
1. Execute `/debug/decrypt-placas` para ver todas as placas
2. Execute `/debug/busca-alternativa?placa=PSS0O37` para teste detalhado
3. Verifique os logs para entender onde falha