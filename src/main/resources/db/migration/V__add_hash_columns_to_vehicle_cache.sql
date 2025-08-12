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

-- Adicionar constraints únicas nos hashes
ALTER TABLE vehicle_cache ADD CONSTRAINT unique_contrato_hash UNIQUE (contrato_hash);
ALTER TABLE vehicle_cache ADD CONSTRAINT unique_placa_hash UNIQUE (placa_hash);
ALTER TABLE vehicle_cache ADD CONSTRAINT unique_contrato_placa_hash UNIQUE (contrato_placa_hash);

-- Comentários para documentação
COMMENT ON COLUMN vehicle_cache.contrato_hash IS 'Hash SHA-256 do contrato descriptografado';
COMMENT ON COLUMN vehicle_cache.placa_hash IS 'Hash SHA-256 da placa descriptografada';
COMMENT ON COLUMN vehicle_cache.contrato_placa_hash IS 'Hash SHA-256 da combinação contrato|placa';

-- Script para popular hashes em registros existentes (opcional - será feito via aplicação)
-- UPDATE vehicle_cache SET 
--   contrato_hash = encode(sha256(descriptografar(decode(contrato, 'hex'))::bytea), 'hex'),
--   placa_hash = encode(sha256(descriptografar(decode(placa, 'hex'))::bytea), 'hex')
-- WHERE contrato_hash IS NULL OR placa_hash IS NULL;