-- Script para corrigir campos de tamanho inadequado na tabela vehicle_cache
-- IMPORTANTE: Execute este script antes de rodar o job de atualização do cache

-- 1. Verificar os tipos atuais dos campos
SELECT 
    column_name, 
    data_type, 
    character_maximum_length 
FROM information_schema.columns 
WHERE table_name = 'vehicle_cache' 
AND column_name IN ('contrato', 'placa');

-- 2. Alterar campos para TEXT (suporta valores grandes)
BEGIN;

    -- Verificar se existem dados na tabela
    SELECT COUNT(*) as total_records FROM vehicle_cache;
    
    -- Alterar tipo do campo contrato
    ALTER TABLE vehicle_cache ALTER COLUMN contrato TYPE TEXT;
    
    -- Alterar tipo do campo placa
    ALTER TABLE vehicle_cache ALTER COLUMN placa TYPE TEXT;
    
    -- Verificar se as alterações foram aplicadas
    SELECT 
        column_name, 
        data_type, 
        character_maximum_length 
    FROM information_schema.columns 
    WHERE table_name = 'vehicle_cache' 
    AND column_name IN ('contrato', 'placa');

COMMIT;

-- 3. Adicionar comentários para documentação
COMMENT ON COLUMN vehicle_cache.contrato IS 'Campo criptografado - pode exceder 255 caracteres';
COMMENT ON COLUMN vehicle_cache.placa IS 'Campo criptografado - pode exceder 255 caracteres';

-- 4. Verificação final
\echo 'Campos alterados com sucesso!'
\echo 'Agora você pode executar o job de atualização do cache.'