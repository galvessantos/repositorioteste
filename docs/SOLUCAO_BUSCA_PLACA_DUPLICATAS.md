# Solução para Busca por Placa e Problema de Duplicatas

## 🔍 **Problema Identificado**

Após análise dos logs, identificamos que o problema da busca por placa vazia **NÃO é relacionado a O/0**, mas sim a **DUPLICATAS** no banco de dados.

### **Evidências do Problema:**
1. ✅ **Cache funcionando** - salvou 11 registros com sucesso
2. ✅ **Migração OK** - não há mais erro de `varchar(255)`  
3. ❌ **Dados DUPLICADOS** - registros antigos + novos coexistindo
4. ❌ **Busca por placa falhando** por conflito entre versões dos dados

---

## 🛠️ **Soluções Implementadas**

### **1. Script de Diagnóstico de Duplicatas**
📁 **Arquivo:** `scripts/diagnose_and_clean_duplicates.sql`

**Funcionalidades:**
- Diagnóstico completo de duplicatas por contrato e placa
- Estratégias de limpeza (comentadas para segurança)
- Verificação pós-limpeza

**Como usar:**
```sql
-- 1. Executar apenas a PARTE 1 (DIAGNÓSTICO) primeiro
-- 2. Analisar os resultados
-- 3. Descomentar e executar PARTE 2 (LIMPEZA) se necessário
```

### **2. Endpoints de Debug**
Criamos 4 endpoints temporários para diagnosticar problemas:

#### **A. `/api/v1/vehicle/debug/crypto/{placa}`**
- Testa busca por placa específica
- Mostra estatísticas do cache
- Ativa logs de debug detalhados

#### **B. `/api/v1/vehicle/debug/decrypt-placas`**
- Lista placas descriptografadas do cache
- Verifica se a descriptografia está funcionando
- Mostra primeiros 10 registros

#### **C. `/api/v1/vehicle/debug/busca-alternativa?placa=XXX`**
- Compara busca normal vs alternativa
- Testa ambos os métodos lado a lado
- Mostra quantos registros cada método encontra

#### **D. `/api/v1/vehicle/debug/cache-summary`**
- Resumo completo do cache
- Estatísticas de duplicatas
- Distribuição por data de sync

### **3. Métodos de Debug no VehicleCacheService**
- `searchByPlacaNormal()` - busca atual (criptografada)
- `debugDecryptedPlates()` - lista placas descriptografadas
- `getDuplicateStats()` - estatísticas de duplicatas

---

## 🚀 **Próximos Passos**

### **Passo 1: Diagnosticar Duplicatas**
```sql
-- Execute no PostgreSQL:
\i scripts/diagnose_and_clean_duplicates.sql
```

### **Passo 2: Testar Endpoints de Debug**
```bash
# Verificar status geral
curl "{{base_url}}/api/v1/vehicle/debug/cache-summary"

# Testar placa específica
curl "{{base_url}}/api/v1/vehicle/debug/crypto/PSS0O37"

# Ver placas descriptografadas
curl "{{base_url}}/api/v1/vehicle/debug/decrypt-placas"

# Comparar buscas
curl "{{base_url}}/api/v1/vehicle/debug/busca-alternativa?placa=PSS0O37"
```

### **Passo 3: Limpar Duplicatas (após análise)**
```sql
-- Descomentar e executar PARTE 2 do script de diagnóstico
-- Recomendação: manter apenas registros mais recentes
```

### **Passo 4: Testar Busca Normal**
```bash
curl "{{base_url}}/api/v1/vehicle?placa=PSS0O37"
```

---

## 📋 **Estratégias de Limpeza Disponíveis**

### **Estratégia 1: Por Contrato (Recomendada)**
- Mantém apenas o registro mais recente por contrato
- Usa `api_sync_date` como critério
- Preserva integridade dos dados

### **Estratégia 2: Por Placa**
- Mantém apenas o registro mais recente por placa
- Pode remover contratos válidos se placas foram reutilizadas

### **Estratégia 3: Por Data**
- Remove registros antigos (>14 dias)
- Mais agressiva, pode perder dados importantes

---

## 🔧 **Resolução de Problemas**

### **Se busca ainda falhar após limpeza:**
1. Verificar logs de debug nos endpoints
2. Confirmar que busca alternativa está funcionando
3. Verificar se criptografia/descriptografia está correta
4. Considerar recriar índices do banco

### **Se duplicatas persistirem:**
1. Verificar lógica de limpeza de duplicatas no job
2. Ajustar intervalo do job de cache
3. Implementar constraint UNIQUE no banco (cuidado!)

---

## ⚠️ **Alertas Importantes**

1. **SEMPRE execute diagnóstico antes da limpeza**
2. **Faça backup da tabela** antes de executar limpeza
3. **Teste em ambiente dev primeiro**
4. **Remova endpoints de debug** em produção após resolução
5. **Monitore logs** durante e após a limpeza

---

## 📊 **Indicadores de Sucesso**

✅ **Busca por placa retorna resultados**  
✅ **Número de duplicatas = 0**  
✅ **Cache atualiza sem erros**  
✅ **Performance da busca melhorada**  

---

**Teste e confirme os resultados após seguir os passos!** 🚀