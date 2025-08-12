# Tutorial Completo: Implementação da Solução Anti-Duplicatas

## Pré-requisitos
- PostgreSQL instalado e rodando
- DBeaver ou outro cliente SQL
- Java 17+
- Maven
- Acesso ao banco de dados da aplicação

## Passo 1: Backup do Banco (IMPORTANTE!)

Antes de qualquer alteração, faça um backup:

```sql
-- No terminal ou DBeaver
pg_dump -U seu_usuario -d nome_do_banco > backup_antes_duplicatas.sql
```

## Passo 2: Verificar Duplicatas Existentes

No DBeaver, execute estas queries para entender a situação atual:

```sql
-- Conectar ao banco correto
\c nome_do_seu_banco;

-- Verificar quantos registros existem
SELECT COUNT(*) as total_registros FROM vehicle_cache;

-- Verificar se existem duplicatas (vai mostrar dados criptografados)
SELECT contrato, COUNT(*) as quantidade
FROM vehicle_cache
GROUP BY contrato
HAVING COUNT(*) > 1;

-- Verificar estrutura atual da tabela
\d vehicle_cache;
```

## Passo 3: Criar e Executar a Migration SQL

### 3.1 Criar o arquivo SQL

Crie o arquivo `V999__add_hash_columns_to_vehicle_cache.sql` na pasta `src/main/resources/db/migration/`:

```sql
-- Adicionar colunas de hash na tabela vehicle_cache
ALTER TABLE vehicle_cache ADD COLUMN IF NOT EXISTS contrato_hash VARCHAR(64);
ALTER TABLE vehicle_cache ADD COLUMN IF NOT EXISTS placa_hash VARCHAR(64);
ALTER TABLE vehicle_cache ADD COLUMN IF NOT EXISTS contrato_placa_hash VARCHAR(64);

-- Criar índices para melhorar performance de busca
CREATE INDEX IF NOT EXISTS idx_contrato_hash ON vehicle_cache(contrato_hash);
CREATE INDEX IF NOT EXISTS idx_placa_hash ON vehicle_cache(placa_hash);
CREATE INDEX IF NOT EXISTS idx_contrato_placa_hash ON vehicle_cache(contrato_placa_hash);

-- Remover constraint única antiga que usava campos criptografados (se existir)
ALTER TABLE vehicle_cache DROP CONSTRAINT IF EXISTS idx_unique_vehicle;

-- IMPORTANTE: Não adicionar as constraints únicas ainda!
-- Vamos adicionar depois de limpar as duplicatas

-- Comentários para documentação
COMMENT ON COLUMN vehicle_cache.contrato_hash IS 'Hash SHA-256 do contrato descriptografado';
COMMENT ON COLUMN vehicle_cache.placa_hash IS 'Hash SHA-256 da placa descriptografada';
COMMENT ON COLUMN vehicle_cache.contrato_placa_hash IS 'Hash SHA-256 da combinação contrato|placa';
```

### 3.2 Executar no DBeaver

```sql
-- Copie e cole o conteúdo do SQL acima no DBeaver e execute
-- Ou execute diretamente o arquivo
```

### 3.3 Verificar se as colunas foram criadas

```sql
-- Verificar estrutura atualizada
\d vehicle_cache;

-- Deve mostrar as novas colunas:
-- contrato_hash        | character varying(64)
-- placa_hash           | character varying(64)
-- contrato_placa_hash  | character varying(64)
```

## Passo 4: Implementar o Código Java

### 4.1 Atualizar a Entidade VehicleCache

Adicione as novas colunas em `src/main/java/com/montreal/msiav_bh/entity/VehicleCache.java`:

```java
// Adicionar após a última coluna (apiSyncDate)

@Column(name = "contrato_hash", length = 64)
private String contratoHash;

@Column(name = "placa_hash", length = 64)
private String placaHash;

@Column(name = "contrato_placa_hash", length = 64)
private String contratoPlacaHash;
```

### 4.2 Remover a constraint única antiga

Atualize a anotação @Table removendo o índice único antigo:

```java
@Table(name = "vehicle_cache", 
       indexes = {
        @Index(name = "idx_placa", columnList = "placa"),
        @Index(name = "idx_contrato", columnList = "contrato"),
        @Index(name = "idx_protocolo", columnList = "protocolo"),
        @Index(name = "idx_api_sync_date", columnList = "api_sync_date"),
        @Index(name = "idx_contrato_hash", columnList = "contrato_hash"),
        @Index(name = "idx_placa_hash", columnList = "placa_hash"),
        @Index(name = "idx_contrato_placa_hash", columnList = "contrato_placa_hash")
       })
// Remover uniqueConstraints por enquanto
```

### 4.3 Atualizar o Repositório

Adicione métodos em `VehicleCacheRepository.java`:

```java
Optional<VehicleCache> findByContratoHash(String contratoHash);
Optional<VehicleCache> findByPlacaHash(String placaHash);
Optional<VehicleCache> findByContratoPlacaHash(String contratoPlacaHash);
long countByContratoHashIsNotNullAndPlacaHashIsNotNull();
List<VehicleCache> findByContratoHashIsNullOrPlacaHashIsNull();
```

### 4.4 Atualizar o VehicleCacheService

Adicione os métodos de hash e as modificações conforme o código que criei anteriormente.

## Passo 5: Deploy e População dos Hashes

### 5.1 Compilar o projeto

```bash
mvn clean package -DskipTests
```

### 5.2 Executar a aplicação

```bash
java -jar target/garantias-*.jar
```

### 5.3 Monitorar os logs

A aplicação vai automaticamente:
1. Detectar registros sem hashes
2. Popular os hashes
3. Logs esperados:

```
Verificando se é necessário popular hashes em registros existentes...
Encontrados 1523 registros sem hashes. Iniciando população...
Progresso: 100 registros atualizados
Progresso: 200 registros atualizados
...
População de hashes concluída - Atualizados: 1523, Erros: 0
```

## Passo 6: Limpar Duplicatas e Adicionar Constraints

### 6.1 Verificar duplicatas por hash

No DBeaver:

```sql
-- Ver quantas duplicatas existem por contrato
SELECT contrato_hash, COUNT(*) as qtd
FROM vehicle_cache
WHERE contrato_hash IS NOT NULL
GROUP BY contrato_hash
HAVING COUNT(*) > 1
ORDER BY qtd DESC;

-- Ver quantas duplicatas existem por placa
SELECT placa_hash, COUNT(*) as qtd
FROM vehicle_cache
WHERE placa_hash IS NOT NULL
GROUP BY placa_hash
HAVING COUNT(*) > 1
ORDER BY qtd DESC;

-- Listar IDs das duplicatas para revisão
WITH duplicatas AS (
    SELECT contrato_hash, 
           COUNT(*) as qtd,
           MIN(id) as id_manter,
           ARRAY_AGG(id ORDER BY id) as todos_ids
    FROM vehicle_cache
    WHERE contrato_hash IS NOT NULL
    GROUP BY contrato_hash
    HAVING COUNT(*) > 1
)
SELECT * FROM duplicatas;
```

### 6.2 Remover duplicatas (manter o mais recente)

```sql
-- CUIDADO: Faça backup antes!

-- Deletar duplicatas mantendo o registro mais recente
DELETE FROM vehicle_cache a
USING vehicle_cache b
WHERE a.contrato_hash = b.contrato_hash
  AND a.id < b.id;

-- Verificar se ainda existem duplicatas
SELECT contrato_hash, COUNT(*)
FROM vehicle_cache
WHERE contrato_hash IS NOT NULL
GROUP BY contrato_hash
HAVING COUNT(*) > 1;
-- Deve retornar 0 registros
```

### 6.3 Adicionar as constraints únicas

Agora que não há mais duplicatas, adicione as constraints:

```sql
-- Adicionar constraints únicas
ALTER TABLE vehicle_cache 
ADD CONSTRAINT unique_contrato_hash UNIQUE (contrato_hash);

ALTER TABLE vehicle_cache 
ADD CONSTRAINT unique_placa_hash UNIQUE (placa_hash);

ALTER TABLE vehicle_cache 
ADD CONSTRAINT unique_contrato_placa_hash UNIQUE (contrato_placa_hash);

-- Verificar se foram criadas
\d vehicle_cache;
```

## Passo 7: Atualizar o Código com Constraints

### 7.1 Atualizar a entidade com uniqueConstraints

```java
@Table(name = "vehicle_cache", 
       indexes = {
        // ... índices existentes ...
       },
       uniqueConstraints = {
        @UniqueConstraint(name = "unique_contrato_hash", columnNames = {"contrato_hash"}),
        @UniqueConstraint(name = "unique_placa_hash", columnNames = {"placa_hash"}),
        @UniqueConstraint(name = "unique_contrato_placa_hash", columnNames = {"contrato_placa_hash"})
       })
```

### 7.2 Deploy final

```bash
mvn clean package
java -jar target/garantias-*.jar
```

## Passo 8: Testes de Validação

### 8.1 Testar inserção de duplicata

No DBeaver, tente inserir uma duplicata manualmente:

```sql
-- Pegar um hash existente
SELECT contrato_hash FROM vehicle_cache LIMIT 1;

-- Tentar inserir duplicata (deve dar erro)
INSERT INTO vehicle_cache (contrato, placa, contrato_hash, ...)
VALUES ('xxx', 'yyy', 'hash_que_ja_existe', ...);

-- Erro esperado:
-- ERROR: duplicate key value violates unique constraint "unique_contrato_hash"
```

### 8.2 Testar com múltiplas instâncias

1. Execute a aplicação em duas máquinas/terminais diferentes
2. Observe os logs quando o job executar
3. Uma instância processará normalmente, a outra mostrará:
   ```
   Registro duplicado ignorado: contrato=***, placa=***
   ```

## Passo 9: Monitoramento Contínuo

### 9.1 Queries úteis para monitoramento

```sql
-- Dashboard de saúde
SELECT 
    COUNT(*) as total_registros,
    COUNT(contrato_hash) as registros_com_hash,
    COUNT(*) - COUNT(contrato_hash) as registros_sem_hash
FROM vehicle_cache;

-- Verificar integridade
SELECT 
    CASE 
        WHEN COUNT(*) = COUNT(DISTINCT contrato_hash) 
        THEN 'OK - Sem duplicatas' 
        ELSE 'ERRO - Duplicatas encontradas' 
    END as status_integridade
FROM vehicle_cache
WHERE contrato_hash IS NOT NULL;
```

### 9.2 Job automático de limpeza

O sistema executa automaticamente às 1:30 AM:
- Remove duplicatas
- Logs em `logs/application.log`

## Troubleshooting

### Problema: Erro ao popular hashes
```
Solução: Verificar se as funções de criptografia do PostgreSQL estão instaladas
SELECT criptografar('teste');
SELECT descriptografar(criptografar('teste'));
```

### Problema: Constraint violation ao adicionar
```
Solução: Ainda existem duplicatas. Execute novamente a query de limpeza
```

### Problema: Performance lenta
```
Solução: Verificar se os índices foram criados
\di vehicle_cache*
```

## Checklist Final

- [ ] Backup realizado
- [ ] Colunas de hash criadas
- [ ] Código atualizado e deployado
- [ ] Hashes populados em registros existentes
- [ ] Duplicatas removidas
- [ ] Constraints únicas adicionadas
- [ ] Testes de duplicata executados
- [ ] Monitoramento configurado

## Conclusão

Após seguir todos estes passos, seu sistema estará protegido contra duplicatas, independentemente de quantas instâncias estiverem rodando ou em quantas máquinas diferentes!