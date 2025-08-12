# Solução Simplificada para Duplicatas em Dados Criptografados

## Problema

O sistema armazenava dados sensíveis (contrato e placa) criptografados no banco de dados PostgreSQL. Como a criptografia gerava valores diferentes a cada vez (devido ao uso de salt/IV aleatório), não era possível detectar duplicatas usando constraints únicas tradicionais no banco de dados.

### Exemplo do Problema
```
ID 121: contrato criptografado = c30d04090302717f3d87919bb784...
ID 132: contrato criptografado = c30d04090302473e8161c4e4c8b565...
```
Ambos representam o mesmo contrato, mas têm valores criptografados diferentes.

## Solução Implementada

### 1. Colunas de Hash na Tabela Existente

Adicionamos 3 colunas na tabela `vehicle_cache` existente para armazenar hashes SHA-256 dos valores descriptografados:

```sql
ALTER TABLE vehicle_cache ADD COLUMN contrato_hash VARCHAR(64);
ALTER TABLE vehicle_cache ADD COLUMN placa_hash VARCHAR(64);
ALTER TABLE vehicle_cache ADD COLUMN contrato_placa_hash VARCHAR(64);

-- Constraints únicas para prevenir duplicatas
ALTER TABLE vehicle_cache ADD CONSTRAINT unique_contrato_hash UNIQUE (contrato_hash);
ALTER TABLE vehicle_cache ADD CONSTRAINT unique_placa_hash UNIQUE (placa_hash);
ALTER TABLE vehicle_cache ADD CONSTRAINT unique_contrato_placa_hash UNIQUE (contrato_placa_hash);
```

### 2. Processo de Detecção de Duplicatas

1. **Antes de inserir**: O sistema descriptografa os valores, gera hashes SHA-256 e verifica se já existem no banco
2. **Durante a inserção**: Se não houver duplicata, salva o registro com os hashes preenchidos
3. **Dados existentes**: Um job popula automaticamente os hashes em registros antigos após o deploy

### 3. Implementação Simples

- Modificamos apenas a entidade `VehicleCache` e o serviço `VehicleCacheService`
- Nenhuma tabela adicional foi criada
- Nenhum endpoint administrativo foi necessário
- O sistema funciona automaticamente após aplicar a migration

## Como Usar

### 1. Aplicar a Migration

Execute o script SQL para adicionar as colunas:
```bash
flyway migrate
```

### 2. Deploy da Aplicação

Ao iniciar, a aplicação automaticamente:
- Detecta registros sem hashes
- Popula os hashes para registros existentes
- Remove duplicatas em um job agendado (1:30 AM diariamente)

### 3. Monitoramento

O sistema agora previne duplicatas automaticamente. Os logs mostram:
```
➕ NOVO veículo inserido: contrato=***, placa=***
✓ Veículo ATUALIZADO: contrato=***, placa=***
```

## Benefícios

1. **Simplicidade**: Solução minimalista sem complexidade adicional
2. **Performance**: Busca por hash é O(1) com índices únicos
3. **Integridade**: Constraints no banco garantem zero duplicatas
4. **Transparência**: Funciona automaticamente sem intervenção manual

## Manutenção

### Verificar Duplicatas
```sql
-- Contar registros sem hashes (deveria ser 0 após migração)
SELECT COUNT(*) FROM vehicle_cache 
WHERE contrato_hash IS NULL OR placa_hash IS NULL;

-- Verificar possíveis duplicatas por contrato descriptografado
SELECT COUNT(*), contrato_hash 
FROM vehicle_cache 
GROUP BY contrato_hash 
HAVING COUNT(*) > 1;
```

## Considerações de Segurança

- Os hashes SHA-256 são unidirecionais - não é possível recuperar o valor original
- Os dados sensíveis continuam criptografados nas colunas originais
- Apenas os hashes são usados para detecção de duplicatas