-- Tabela para armazenar índices únicos baseados em hash dos valores descriptografados
CREATE TABLE IF NOT EXISTS vehicle_cache_unique_index (
    id BIGSERIAL PRIMARY KEY,
    vehicle_cache_id BIGINT NOT NULL UNIQUE,
    contrato_hash VARCHAR(64),
    placa_hash VARCHAR(64),
    contrato_placa_hash VARCHAR(64),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Constraints de unicidade
    CONSTRAINT unique_contrato_hash UNIQUE (contrato_hash),
    CONSTRAINT unique_placa_hash UNIQUE (placa_hash),
    CONSTRAINT unique_contrato_placa_hash UNIQUE (contrato_placa_hash),
    
    -- Foreign key
    CONSTRAINT fk_vehicle_cache_id 
        FOREIGN KEY (vehicle_cache_id) 
        REFERENCES vehicle_cache(id) 
        ON DELETE CASCADE
);

-- Índices para melhorar performance
CREATE INDEX IF NOT EXISTS idx_vehicle_cache_id ON vehicle_cache_unique_index(vehicle_cache_id);
CREATE INDEX IF NOT EXISTS idx_contrato_hash ON vehicle_cache_unique_index(contrato_hash);
CREATE INDEX IF NOT EXISTS idx_placa_hash ON vehicle_cache_unique_index(placa_hash);

-- Comentários para documentação
COMMENT ON TABLE vehicle_cache_unique_index IS 'Tabela de índices únicos para prevenir duplicatas em dados criptografados';
COMMENT ON COLUMN vehicle_cache_unique_index.vehicle_cache_id IS 'ID do veículo na tabela vehicle_cache';
COMMENT ON COLUMN vehicle_cache_unique_index.contrato_hash IS 'Hash SHA-256 do contrato descriptografado';
COMMENT ON COLUMN vehicle_cache_unique_index.placa_hash IS 'Hash SHA-256 da placa descriptografada';
COMMENT ON COLUMN vehicle_cache_unique_index.contrato_placa_hash IS 'Hash SHA-256 da combinação contrato|placa';