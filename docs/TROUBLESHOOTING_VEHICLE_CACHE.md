# Troubleshooting Vehicle Cache Update

Este documento explica como diagnosticar e resolver problemas com o job de atualização do cache de veículos.

## Problema Comum: "Período não encontrado" (404)

### Sintomas
- Logs mostram: `ERROR c.m.msiav_bh.service.ApiQueryService - Erro na requisição: 404 NOT_FOUND - {"success":false,"message":"Per\u00edodo n\u00e3o encontrado"}`
- Job retorna: `WARN c.m.m.job.VehicleCacheUpdateJob - API retornou lista vazia - nenhuma atualização realizada`

### Causas Possíveis
1. **Período sem dados**: A API externa pode não ter dados para o período solicitado
2. **Sincronização de datas**: Diferença entre data do servidor e dados disponíveis na API
3. **Configuração incorreta**: Período de busca muito restrito

## Soluções Implementadas

### 1. Fallback Automático de Períodos
O sistema agora tenta múltiplos períodos automaticamente:

```
Período principal (30 dias) → Período de fallback (60 dias) → Períodos históricos (até 6 meses)
```

### 2. Novas Configurações
Configurações disponíveis no `application.properties`:

```properties
# Período principal de busca (padrão: 30 dias)
vehicle.cache.update.days-to-fetch=30

# Período de fallback quando o principal falha (padrão: 60 dias)
vehicle.cache.update.fallback-days=60

# Limite máximo para busca histórica (padrão: 180 dias)
vehicle.cache.update.max-historical-days=180

# Intervalo entre execuções do job (padrão: 10 minutos)
vehicle.cache.update.interval=600000
```

### 3. Logs Melhorados
O sistema agora fornece logs mais detalhados sobre:
- Qual período está sendo testado
- Quantos registros foram encontrados em cada tentativa
- Qual período foi efetivamente usado para atualizar o cache

## Interpretando os Logs

### ✅ Funcionamento Normal
```
INFO c.m.m.job.VehicleCacheUpdateJob - ==== INICIANDO JOB DE ATUALIZAÇÃO DO CACHE ====
INFO c.m.m.job.VehicleCacheUpdateJob - Tentando período principal - Período de busca: 2025-07-08 a 2025-08-07
INFO c.m.m.service.ApiQueryService - Período não encontrado na API: 2025-07-08 a 2025-08-07
INFO c.m.m.job.VehicleCacheUpdateJob - Tentando período de fallback de 60 dias
INFO c.m.m.job.VehicleCacheUpdateJob - Tentando períodos históricos...
INFO c.m.m.job.VehicleCacheUpdateJob - ✓ Dados encontrados no período histórico de 2 meses atrás
INFO c.m.m.job.VehicleCacheUpdateJob - API retornou 11 notificações
INFO c.m.m.job.VehicleCacheUpdateJob - ==== JOB CONCLUÍDO COM SUCESSO ====
```

### ⚠️ Aviso (Pode ser Normal)
```
INFO c.m.m.service.ApiQueryService - Período não encontrado na API: 2025-07-08 a 2025-08-07
WARN c.m.m.job.VehicleCacheUpdateJob - ==== NENHUM DADO ENCONTRADO EM TODOS OS PERÍODOS TENTADOS ====
```

### ❌ Erro que Requer Atenção
```
ERROR c.m.m.service.ApiQueryService - Erro na requisição: 401 - Token de autenticação inválido
ERROR c.m.m.job.VehicleCacheUpdateJob - ==== FALHA NO JOB DE ATUALIZAÇÃO ====
```

## Configurações Recomendadas

### Para Ambiente de Produção
```properties
vehicle.cache.update.days-to-fetch=30
vehicle.cache.update.fallback-days=90
vehicle.cache.update.max-historical-days=180
vehicle.cache.update.interval=600000  # 10 minutos
```

### Para Ambiente de Desenvolvimento
```properties
vehicle.cache.update.days-to-fetch=7
vehicle.cache.update.fallback-days=30
vehicle.cache.update.max-historical-days=90
vehicle.cache.update.interval=300000  # 5 minutos
```

## Monitoramento

### Logs Importantes a Observar
- `==== INICIANDO JOB DE ATUALIZAÇÃO DO CACHE ====`
- `Tentando período principal`
- `Tentando período de fallback`
- `Tentando períodos históricos`
- `==== JOB CONCLUÍDO COM SUCESSO ====`
- `==== NENHUM DADO ENCONTRADO EM TODOS OS PERÍODOS TENTADOS ====`

### Métricas a Acompanhar
- Frequência de uso do período de fallback
- Períodos históricos mais bem-sucedidos
- Tempo de execução do job
- Quantidade de registros atualizados

## Resolução de Problemas Específicos

### 1. Sistema sempre usa períodos históricos
**Causa**: API não tem dados recentes
**Solução**: 
- Verificar com fornecedor da API sobre disponibilidade de dados atuais
- Ajustar `days-to-fetch` para um período menor se necessário

### 2. Nenhum período retorna dados
**Causa**: Problema de conectividade ou credenciais
**Solução**: 
1. Verificar configurações de API (`montreal.api.*`)
2. Verificar logs de autenticação
3. Testar conectividade de rede

### 3. Performance degradada
**Causa**: Muitas tentativas de períodos
**Solução**: Ajustar configurações:
- Reduzir `max-historical-days`
- Aumentar `interval` entre execuções

### 4. Cache nunca atualiza
**Causa**: Job pode estar desabilitado
**Solução**:
- Verificar `vehicle.cache.update.enabled=true`
- Verificar se há erros de criptografia nos logs

## Endpoints Disponíveis

### Busca de Veículos
```
GET /api/v1/vehicle
```
Este endpoint retorna informações sobre o status do cache nos headers HTTP:
- `X-Cache-Status`: Valid/Outdated
- `X-Cache-Age-Minutes`: Idade do cache em minutos

### Busca por Contrato
```
GET /api/v1/vehicle/contract?contrato=NUMERO_CONTRATO
```
Busca informações detalhadas de um contrato específico.

### Health Check
```
GET /api/v1/vehicle/health
```
Verificação básica da saúde da API.

### Invalidação de Cache (Para Testes)
```
DELETE /api/v1/vehicle/cache/invalidate
```
**⚠️ CUIDADO**: Remove TODOS os dados do cache.

**Proteção obrigatória**: Requer header de confirmação:
```bash
curl -X DELETE http://localhost:8080/api/v1/vehicle/cache/invalidate \
  -H "X-Confirm-Action: CONFIRM_INVALIDATE" \
  -H "Authorization: Bearer YOUR_TOKEN"
```

**Uso recomendado**:
- Testes automatizados
- Limpeza forçada do cache em casos específicos
- Reset do estado do cache para debugging

**Resposta de sucesso**:
```json
{
  "status": "success",
  "message": "Cache invalidado com sucesso",
  "recordsRemoved": 150,
  "warning": "Próxima consulta forçará atualização via API"
}
```

**Resposta sem confirmação**:
```json
{
  "status": "error",
  "message": "Ação não confirmada. Adicione o header 'X-Confirm-Action: CONFIRM_INVALIDATE'",
  "warning": "Esta operação remove TODOS os dados do cache"
}
```

## Contato
Para problemas persistentes, verificar logs completos e contatar a equipe de desenvolvimento.