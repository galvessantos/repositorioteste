# Solução para Duplicatas em Dados Criptografados

## Problema

O sistema armazenava dados sensíveis (contrato e placa) criptografados no banco de dados PostgreSQL. Como a criptografia gerava valores diferentes a cada vez (devido ao uso de salt/IV aleatório), não era possível detectar duplicatas usando constraints únicas tradicionais no banco de dados.

### Exemplo do Problema
```
ID 121: contrato criptografado = c30d04090302717f3d87919bb784...
ID 132: contrato criptografado = c30d04090302473e8161c4e4c8b565...
```
Ambos representam o mesmo contrato, mas têm valores criptografados diferentes.

## Solução Implementada

### 1. Tabela de Índices Únicos

Criamos uma nova tabela `vehicle_cache_unique_index` que armazena hashes SHA-256 dos valores descriptografados:

```sql
CREATE TABLE vehicle_cache_unique_index (
    id BIGSERIAL PRIMARY KEY,
    vehicle_cache_id BIGINT NOT NULL UNIQUE,
    contrato_hash VARCHAR(64),
    placa_hash VARCHAR(64),
    contrato_placa_hash VARCHAR(64),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

### 2. Processo de Detecção de Duplicatas

1. **Antes de inserir**: O sistema descriptografa os valores, gera hashes SHA-256 e verifica se já existem na tabela de índices
2. **Durante a inserção**: Se não houver duplicata, cria o registro e seu índice único correspondente
3. **Durante a atualização**: Verifica e cria índice se necessário

### 3. Componentes Implementados

#### Entidades
- `VehicleCacheUniqueIndex`: Entidade JPA para a tabela de índices únicos

#### Repositórios
- `VehicleCacheUniqueIndexRepository`: Repositório para gerenciar índices únicos

#### Serviços Atualizados
- `VehicleCacheService`: 
  - `findExistingVehicleOptimized()`: Busca por duplicatas usando índices únicos
  - `createUniqueIndexesForExistingData()`: Cria índices para dados existentes
  - `removeDuplicateVehicles()`: Remove veículos duplicados mantendo o mais recente

#### Controller Administrativo
- `VehicleCacheAdminController`: Endpoints para gerenciar índices e duplicatas

## Como Usar

### 1. Aplicar a Migration

Execute o script SQL para criar a tabela de índices únicos:
```bash
flyway migrate
```

### 2. Limpar Duplicatas Existentes

Via API (requer role ADMIN):
```bash
# Remover duplicatas e criar índices
curl -X POST http://localhost:8080/api/v1/admin/vehicle-cache/cleanup-and-reindex \
  -H "Authorization: Bearer {token}"
```

### 3. Monitorar o Sistema

O job automático agora:
- Detecta duplicatas antes de inserir
- Usa índices únicos para busca eficiente
- Mantém integridade dos dados

## Benefícios

1. **Prevenção de Duplicatas**: Impossível inserir registros com mesmo contrato/placa
2. **Performance**: Busca por hash é mais rápida que descriptografar todos os registros
3. **Integridade**: Constraints únicas garantem consistência dos dados
4. **Migração Suave**: Sistema funciona com dados existentes

## Manutenção

### Verificar Status
```bash
curl http://localhost:8080/api/v1/admin/vehicle-cache/status \
  -H "Authorization: Bearer {token}"
```

### Recriar Índices (se necessário)
```bash
curl -X POST http://localhost:8080/api/v1/admin/vehicle-cache/create-unique-indexes \
  -H "Authorization: Bearer {token}"
```

## Considerações de Segurança

- Os hashes SHA-256 não comprometem a segurança dos dados originais
- A tabela de índices contém apenas hashes, não dados sensíveis
- O acesso aos endpoints administrativos requer role ADMIN