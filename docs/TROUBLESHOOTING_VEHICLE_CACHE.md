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
Adicione no `application.properties`:

```properties
# Período de fallback quando o principal falha
vehicle.cache.update.fallback-days=60

# Limite máximo para busca histórica
vehicle.cache.update.max-historical-days=180
```

### 3. Endpoint de Diagnóstico
Use o endpoint para testar manualmente:

```bash
POST /api/v1/vehicle/admin/diagnostic
```

## Como Usar o Diagnóstico

### Via API
```bash
curl -X POST http://localhost:8080/api/v1/vehicle/admin/diagnostic \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN"
```

### Resposta Esperada
```json
{
  "status": "success",
  "message": "Verificação diagnóstica executada. Verifique os logs para detalhes.",
  "timestamp": "2025-08-07T19:30:00"
}
```

### Logs de Diagnóstico
O diagnóstico testará:
1. Período atual (30 dias)
2. Períodos históricos (1-3 meses atrás)
3. Mostrará amostras de dados encontrados

## Interpretando os Logs

### ✅ Sucesso
```
INFO c.m.m.job.VehicleCacheUpdateJob - ✓ Dados encontrados no período histórico de 2 meses atrás
INFO c.m.m.job.VehicleCacheUpdateJob - Exemplo de registro: Contrato=PA250625, Data=2025-06-24
```

### ⚠️ Aviso (Normal)
```
INFO c.m.m.service.ApiQueryService - Período não encontrado na API: 2025-07-08 a 2025-08-07
```

### ❌ Erro
```
ERROR c.m.m.service.ApiQueryService - Erro na requisição: 401 - Token de autenticação inválido
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

### Logs Importantes
- `==== INICIANDO JOB DE ATUALIZAÇÃO DO CACHE ====`
- `Tentando período principal`
- `Tentando período de fallback`
- `Tentando períodos históricos`
- `==== JOB CONCLUÍDO COM SUCESSO ====`

### Métricas a Observar
- Frequência de uso do fallback
- Períodos históricos mais bem-sucedidos
- Tempo de execução do job

## Resolução de Problemas Específicos

### 1. Sempre usa período histórico
**Causa**: API não tem dados recentes
**Solução**: Verificar com fornecedor da API sobre disponibilidade de dados

### 2. Nenhum período retorna dados
**Causa**: Problema de conectividade ou credenciais
**Solução**: 
1. Verificar configurações de API (`montreal.api.*`)
2. Testar endpoint de diagnóstico
3. Verificar logs de autenticação

### 3. Performance degradada
**Causa**: Muitas tentativas de períodos
**Solução**: Ajustar configurações:
- Reduzir `max-historical-days`
- Aumentar `cache.update.interval`

## Contato
Para problemas persistentes, verificar logs completos e contatar a equipe de desenvolvimento.