-- DIAGNÓSTICO E LIMPEZA DE DUPLICATAS NO CACHE DE VEÍCULOS
-- Execute este script no PostgreSQL para diagnosticar e limpar duplicatas

-- ========================================
-- PARTE 1: DIAGNÓSTICO
-- ========================================

-- 1. Verificar total de registros
SELECT 'Total de registros' as diagnostico, COUNT(*) as quantidade FROM vehicle_cache;

-- 2. Verificar registros por data de sync (para ver duplicatas temporais)
SELECT 
    DATE(api_sync_date) as data_sync,
    COUNT(*) as quantidade,
    MIN(api_sync_date) as primeiro_sync,
    MAX(api_sync_date) as ultimo_sync
FROM vehicle_cache 
GROUP BY DATE(api_sync_date) 
ORDER BY data_sync DESC;

-- 3. Verificar duplicatas por contrato descriptografado
SELECT 
    'Duplicatas por contrato' as diagnostico,
    COUNT(DISTINCT contrato) as contratos_unicos,
    COUNT(*) as total_registros,
    COUNT(*) - COUNT(DISTINCT contrato) as duplicatas_contratos
FROM vehicle_cache;

-- 4. Verificar duplicatas por placa descriptografada 
SELECT 
    'Duplicatas por placa' as diagnostico,
    COUNT(DISTINCT placa) as placas_unicas,
    COUNT(*) as total_registros,
    COUNT(*) - COUNT(DISTINCT placa) as duplicatas_placas
FROM vehicle_cache;

-- 5. Encontrar contratos específicos que têm duplicatas
WITH contrato_counts AS (
    SELECT 
        contrato,
        COUNT(*) as count_duplicates,
        MIN(id) as id_mais_antigo,
        MAX(id) as id_mais_recente,
        MIN(api_sync_date) as sync_mais_antigo,
        MAX(api_sync_date) as sync_mais_recente
    FROM vehicle_cache 
    GROUP BY contrato 
    HAVING COUNT(*) > 1
)
SELECT 
    'Contratos duplicados' as tipo,
    contrato,
    count_duplicates as total_duplicatas,
    id_mais_antigo,
    id_mais_recente,
    sync_mais_antigo,
    sync_mais_recente
FROM contrato_counts
ORDER BY count_duplicates DESC, sync_mais_recente DESC
LIMIT 10;

-- ========================================
-- PARTE 2: LIMPEZA (DESCOMENTAR PARA EXECUTAR)
-- ========================================

-- ATENÇÃO: Descomente as linhas abaixo APENAS depois de analisar o diagnóstico

-- Backup da tabela (opcional - descomente se quiser manter backup)
-- CREATE TABLE vehicle_cache_backup AS SELECT * FROM vehicle_cache;

-- Estratégia 1: Manter apenas o registro mais recente por contrato
/*
WITH duplicates_to_delete AS (
    SELECT id
    FROM (
        SELECT 
            id,
            contrato,
            ROW_NUMBER() OVER (
                PARTITION BY contrato 
                ORDER BY api_sync_date DESC, id DESC
            ) as rn
        FROM vehicle_cache
    ) ranked
    WHERE rn > 1
)
DELETE FROM vehicle_cache 
WHERE id IN (SELECT id FROM duplicates_to_delete);
*/

-- Estratégia 2: Manter apenas o registro mais recente por placa 
/*
WITH duplicates_to_delete AS (
    SELECT id
    FROM (
        SELECT 
            id,
            placa,
            ROW_NUMBER() OVER (
                PARTITION BY placa 
                ORDER BY api_sync_date DESC, id DESC
            ) as rn
        FROM vehicle_cache
    ) ranked
    WHERE rn > 1
)
DELETE FROM vehicle_cache 
WHERE id IN (SELECT id FROM duplicates_to_delete);
*/

-- Estratégia 3: Remover registros antigos (mais de 14 dias)
/*
DELETE FROM vehicle_cache 
WHERE api_sync_date < NOW() - INTERVAL '14 days';
*/

-- ========================================
-- PARTE 3: VERIFICAÇÃO PÓS-LIMPEZA
-- ========================================

-- Verificar resultado após limpeza (descomente após executar limpeza)
/*
SELECT 'Após limpeza - Total registros' as status, COUNT(*) as quantidade FROM vehicle_cache;
SELECT 'Após limpeza - Contratos únicos' as status, COUNT(DISTINCT contrato) as quantidade FROM vehicle_cache;
SELECT 'Após limpeza - Placas únicas' as status, COUNT(DISTINCT placa) as quantidade FROM vehicle_cache;
*/