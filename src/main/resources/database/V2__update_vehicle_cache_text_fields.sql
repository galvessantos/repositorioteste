-- Migração para ajustar campos de texto criptografados na tabela vehicle_cache
-- Os campos contrato e placa precisam suportar valores criptografados longos

-- Alterar campo contrato para TEXT
ALTER TABLE vehicle_cache ALTER COLUMN contrato TYPE TEXT;

-- Alterar campo placa para TEXT
ALTER TABLE vehicle_cache ALTER COLUMN placa TYPE TEXT;

-- Adicionar comentários para documentar o propósito
COMMENT ON COLUMN vehicle_cache.contrato IS 'Campo criptografado - pode exceder 255 caracteres';
COMMENT ON COLUMN vehicle_cache.placa IS 'Campo criptografado - pode exceder 255 caracteres';