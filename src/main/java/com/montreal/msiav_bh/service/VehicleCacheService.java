package com.montreal.msiav_bh.service;

import com.montreal.msiav_bh.context.CacheUpdateContext;
import com.montreal.msiav_bh.dto.VehicleDTO;
import com.montreal.msiav_bh.entity.VehicleCache;
import com.montreal.msiav_bh.mapper.VehicleCacheMapper;
import com.montreal.msiav_bh.repository.VehicleCacheRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class VehicleCacheService {

    private final VehicleCacheRepository vehicleCacheRepository;
    private final VehicleCacheMapper vehicleCacheMapper;
    private final VehicleCacheCryptoService cryptoService;

    private final ReentrantLock cacheLock = new ReentrantLock();

    @Value("${vehicle.cache.expiry.minutes:10}")
    private int cacheExpiryMinutes;

    @Value("${vehicle.cache.retention.days:7}")
    private int cacheRetentionDays;

    public boolean isCacheValid() {
        Optional<LocalDateTime> lastSyncOpt = vehicleCacheRepository.findLastSyncDate();

        if (lastSyncOpt.isEmpty()) {
            log.warn("Cache vazio - nenhuma sincronização encontrada");
            return false;
        }

        LocalDateTime lastSync = lastSyncOpt.get();
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(cacheExpiryMinutes);
        boolean isValid = lastSync.isAfter(cutoff);

        if (isValid) {
            log.info("Cache válido - última sincronização: {} (há {} minutos)",
                    lastSync, Duration.between(lastSync, LocalDateTime.now()).toMinutes());
        } else {
            log.warn("Cache desatualizado - última sincronização: {} (há {} minutos)",
                    lastSync, Duration.between(lastSync, LocalDateTime.now()).toMinutes());
        }

        return isValid;
    }

    public Page<VehicleDTO> getFromCache(LocalDate dataInicio, LocalDate dataFim,
                                         String credor, String contrato,
                                         String protocolo, String cpf,
                                         String uf, String cidade,
                                         String modelo, String placa,
                                         String etapaAtual, String statusApreensao,
                                         Pageable pageable) {

        log.info("Buscando dados do PostgreSQL (Cache)");
        log.debug("Filtros originais: dataInicio={}, dataFim={}, credor={}, contrato={}, protocolo={}, cpf={}, " +
                        "uf={}, cidade={}, modelo={}, placa={}, etapaAtual={}, statusApreensao={}",
                dataInicio, dataFim, credor, contrato, protocolo, cpf,
                uf, cidade, modelo, placa, etapaAtual, statusApreensao);

        String contratoEncrypted = cryptoService.encryptContrato(contrato);
        String placaEncrypted = cryptoService.encryptPlaca(placa);

        log.debug("Buscando com campos criptografados: contrato={}, placa={}",
                contratoEncrypted != null ? "***ENCRYPTED***" : null,
                placaEncrypted != null ? "***ENCRYPTED***" : null);

        Page<VehicleCache> cachedVehicles = vehicleCacheRepository.findWithFiltersFixed(
                dataInicio, dataFim, credor, contratoEncrypted, protocolo, cpf,
                uf, cidade, modelo, placaEncrypted, etapaAtual, statusApreensao, pageable
        );

        log.info("Dados recuperados do PostgreSQL: {} registros de {} total",
                cachedVehicles.getContent().size(), cachedVehicles.getTotalElements());

        return cachedVehicles.map(this::decryptAndMapToDTO);
    }

    private VehicleDTO decryptAndMapToDTO(VehicleCache entity) {
        try {
            String placaDescriptografada = cryptoService.decryptPlaca(entity.getPlaca());
            String contratoDescriptografado = cryptoService.decryptContrato(entity.getContrato());

            log.debug("Descriptografando dados - Placa: {} chars, Contrato: {} chars",
                    placaDescriptografada != null ? placaDescriptografada.length() : 0,
                    contratoDescriptografado != null ? contratoDescriptografado.length() : 0);

            return new VehicleDTO(
                    entity.getExternalId(),
                    entity.getCredor(),
                    entity.getDataPedido(),
                    contratoDescriptografado,
                    placaDescriptografada,
                    entity.getModelo(),
                    entity.getUf(),
                    entity.getCidade(),
                    entity.getCpfDevedor(),
                    entity.getProtocolo(),
                    entity.getEtapaAtual(),
                    entity.getStatusApreensao(),
                    entity.getUltimaMovimentacao()
            );
        } catch (Exception e) {
            log.error("Erro ao descriptografar dados do veículo ID {}: {}", entity.getId(), e.getMessage());
            return vehicleCacheMapper.toDTO(entity);
        }
    }

    @Transactional
    public void updateCacheThreadSafe(List<VehicleDTO> vehicles, CacheUpdateContext context) {
        cacheLock.lock();
        try {
            log.info("Atualizando cache de forma thread-safe com {} veículos. Contexto: {}",
                    vehicles.size(), context);

            cleanDuplicates();

            updateCache(vehicles, context);

        } finally {
            cacheLock.unlock();
        }
    }

    @Transactional
    public void updateCache(List<VehicleDTO> vehicles, CacheUpdateContext context) {
        log.info("Atualizando cache do PostgreSQL com {} veículos. Contexto: {}",
                vehicles.size(), context);

        try {
            LocalDateTime syncDate = LocalDateTime.now();

            if (context.isFullRefresh()) {
                handleFullRefresh(vehicles, syncDate, context);
            } else {
                handleIncrementalUpdate(vehicles, syncDate, context);
            }

            cleanOldCache();
            log.info("Cache do PostgreSQL atualizado com sucesso (dados sensíveis criptografados)");
        } catch (Exception e) {
            log.error("Erro ao atualizar cache do PostgreSQL", e);
            throw new RuntimeException("Falha ao atualizar cache", e);
        }
    }

    private void handleFullRefresh(List<VehicleDTO> vehicles, LocalDateTime syncDate, CacheUpdateContext context) {
        log.debug("Executando atualização completa do cache");

        if (vehicles.isEmpty() && !context.isHasFilters()) {
            long currentCount = vehicleCacheRepository.count();
            if (currentCount > 100) {
                log.warn("API retornou vazio mas cache tem {} registros - preservando dados atuais", currentCount);
                return;
            } else {
                log.warn("API retornou vazio e cache pequeno - limpando cache");
                vehicleCacheRepository.deleteAll();
                return;
            }
        }

        if (vehicles.isEmpty()) {
            log.info("API retornou vazio com filtros - preservando cache existente");
            return;
        }

        Set<String> activePlacasEncrypted = vehicles.stream()
                .map(VehicleDTO::placa)
                .filter(Objects::nonNull)
                .filter(placa -> !"N/A".equals(placa) && !placa.trim().isEmpty())
                .map(cryptoService::encryptPlaca)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (!activePlacasEncrypted.isEmpty()) {
            int removedCount = vehicleCacheRepository.countByPlacaNotIn(activePlacasEncrypted);

            long totalCache = vehicleCacheRepository.count();
            if (removedCount > totalCache * 0.8) {
                log.warn("Tentativa de remover {} de {} registros ({}%) - preservando dados",
                        removedCount, totalCache, (removedCount * 100 / totalCache));
            } else {
                vehicleCacheRepository.deleteByPlacaNotIn(activePlacasEncrypted);
                log.info("Removidos {} veículos não mais presentes na API", removedCount);
            }
        }

        updateOrInsertVehicles(vehicles, syncDate);
    }

    private void handleIncrementalUpdate(List<VehicleDTO> vehicles, LocalDateTime syncDate, CacheUpdateContext context) {
        log.debug("Executando atualização incremental do cache");
        updateOrInsertVehicles(vehicles, syncDate);
        log.info("Atualização incremental completada para {} veículos", vehicles.size());
    }

    private void updateOrInsertVehicles(List<VehicleDTO> vehicles, LocalDateTime syncDate) {
        int updated = 0;
        int inserted = 0;
        int duplicateSkipped = 0;
        int noChangesFound = 0;

        log.info("=== PROCESSANDO {} VEÍCULOS DA API ===", vehicles.size());

        for (VehicleDTO dto : vehicles) {
            try {
                String placaDescriptografada = cryptoService.decryptPlaca(dto.placa());
                Optional<VehicleCache> existing = findExistingVehicle(dto);

                if (existing.isPresent()) {
                    VehicleCache existingEntity = existing.get();

                    if (hasDataChanges(existingEntity, dto)) {
                        VehicleCache updatedEntity = updateExistingVehicle(existingEntity, dto, syncDate);
                        vehicleCacheRepository.save(updatedEntity);
                        updated++;
                        log.debug("✓ Veículo ATUALIZADO (dados mudaram): placa={}", placaDescriptografada);
                    } else {
                        existingEntity.setApiSyncDate(syncDate);
                        vehicleCacheRepository.save(existingEntity);
                        noChangesFound++;
                        log.trace("⚡ Veículo SEM MUDANÇAS (só sync date): placa={}", placaDescriptografada);
                    }
                } else {
                    VehicleCache newEntity = vehicleCacheMapper.toEntity(dto, syncDate);
                    vehicleCacheRepository.save(newEntity);
                    inserted++;
                    log.debug("➕ NOVO veículo inserido: placa={}", placaDescriptografada);
                }
            } catch (Exception e) {
                String placaDescriptografada = cryptoService.decryptPlaca(dto.placa());
                if (e.getMessage() != null &&
                        (e.getMessage().contains("constraint") ||
                                e.getMessage().contains("duplicate") ||
                                e.getMessage().contains("unique"))) {
                    log.debug("Registro duplicado ignorado (constraint violation): placa={}, erro={}",
                            placaDescriptografada, e.getMessage().substring(0, Math.min(100, e.getMessage().length())));
                    duplicateSkipped++;
                } else if (e.getMessage() != null &&
                        e.getMessage().contains("value too long for type character varying")) {
                    log.error("ERRO DE TAMANHO DE CAMPO: Algum campo excede o limite do banco de dados");
                    log.error("Placa afetada: {}", placaDescriptografada);
                    log.error("Este erro indica que os campos criptografados são muito longos");
                    log.error("SOLUÇÃO: Execute a migração do banco: ALTER TABLE vehicle_cache ALTER COLUMN contrato TYPE TEXT, ALTER TABLE vehicle_cache ALTER COLUMN placa TYPE TEXT;");
                    throw new RuntimeException("Campo muito longo - necessária migração do banco de dados", e);
                } else {
                    log.error("Erro inesperado ao processar veículo placa={}: {}", placaDescriptografada, e.getMessage());
                    throw e;
                }
            }
        }

        log.info("=== RESULTADO DA SINCRONIZAÇÃO ===");
        log.info("{} atualizados (com mudanças)", updated);
        log.info("{} sem mudanças (só sync date)", noChangesFound);
        log.info("{} novos inseridos", inserted);
        log.info("{} duplicados ignorados", duplicateSkipped);
        log.info("Total processado: {}", vehicles.size());

        if (inserted == 0 && updated == 0 && noChangesFound > 0) {
            log.info("SINCRONIZAÇÃO PERFEITA: Dados já estavam em sincronia com a API!");
        }
    }

    private Optional<VehicleCache> findExistingVehicle(VehicleDTO dto) {
        log.debug("Procurando veículo existente para contrato:{}, placa:{}, protocolo:{}",
                dto.contrato(), dto.placa(), dto.protocolo());

        String dtoPlacaDecrypted = cryptoService.decryptPlaca(dto.placa());
        String dtoContratoDecrypted = cryptoService.decryptContrato(dto.contrato());

        if (dtoPlacaDecrypted != null && !"N/A".equals(dtoPlacaDecrypted) && !dtoPlacaDecrypted.trim().isEmpty()) {
            log.debug("Buscando por placa descriptografada: {}", dtoPlacaDecrypted);
            Optional<VehicleCache> byPlaca = findByDecryptedPlaca(dtoPlacaDecrypted);
            if (byPlaca.isPresent()) {
                log.debug("Veículo encontrado por placa descriptografada");
                return byPlaca;
            }
        }

        if (dtoContratoDecrypted != null && !"N/A".equals(dtoContratoDecrypted) && !dtoContratoDecrypted.trim().isEmpty()) {
            log.debug("Buscando por contrato descriptografado: {}", dtoContratoDecrypted);
            Optional<VehicleCache> byContrato = findByDecryptedContrato(dtoContratoDecrypted);
            if (byContrato.isPresent()) {
                log.debug("Veículo encontrado por contrato descriptografado");
                return byContrato;
            }
        }

        if (dto.protocolo() != null && !"N/A".equals(dto.protocolo()) && !dto.protocolo().trim().isEmpty()) {
            Optional<VehicleCache> byProtocolo = vehicleCacheRepository.findByProtocolo(dto.protocolo());
            if (byProtocolo.isPresent()) {
                log.debug("Veículo encontrado por protocolo: {}", dto.protocolo());
                return byProtocolo;
            }
        }

        log.debug("Nenhum veículo existente encontrado - será inserido como novo");
        return Optional.empty();
    }

    private Optional<VehicleCache> findByDecryptedPlaca(String placaPlainText) {
        try {
            String placaNormalizada = placaPlainText.toUpperCase().trim();

            List<VehicleCache> allVehicles = vehicleCacheRepository.findByPlacaIsNotNull();

            int totalVehicles = allVehicles.size();
            log.debug("Processando {} veículos para busca por placa '{}'", totalVehicles, placaNormalizada);

            for (int i = 0; i < allVehicles.size(); i++) {
                VehicleCache vehicle = allVehicles.get(i);
                try {
                    String decryptedPlaca = cryptoService.decryptPlaca(vehicle.getPlaca());
                    if (decryptedPlaca != null) {
                        String decryptedPlacaNormalizada = decryptedPlaca.toUpperCase().trim();
                        if (placaNormalizada.equals(decryptedPlacaNormalizada)) {
                            log.debug("MATCH! Placa '{}' encontrada no registro {}/{} (ID: {})",
                                    placaNormalizada, i+1, totalVehicles, vehicle.getId());
                            return Optional.of(vehicle);
                        }
                    }
                } catch (Exception e) {
                    log.trace("Erro ao descriptografar placa do veículo ID {}: {}", vehicle.getId(), e.getMessage());
                }

                if ((i + 1) % 25 == 0) {
                    log.trace("🔍 Progresso busca por placa '{}': {}/{} processados", placaNormalizada, i + 1, totalVehicles);
                }
            }

            log.debug("Placa '{}' não encontrada após processar {} registros", placaNormalizada, totalVehicles);
            return Optional.empty();
        } catch (Exception e) {
            log.error("Erro na busca por placa descriptografada '{}': {}", placaPlainText, e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<VehicleCache> findByDecryptedContrato(String contratoPlainText) {
        try {
            String contratoNormalizado = contratoPlainText.trim();

            List<VehicleCache> allVehicles = vehicleCacheRepository.findByContratoIsNotNull();

            int totalVehicles = allVehicles.size();
            log.debug("🔍 Processando {} veículos para busca por contrato '{}'", totalVehicles, contratoNormalizado);

            for (int i = 0; i < allVehicles.size(); i++) {
                VehicleCache vehicle = allVehicles.get(i);
                try {
                    String decryptedContrato = cryptoService.decryptContrato(vehicle.getContrato());
                    if (contratoNormalizado.equals(decryptedContrato)) {
                        log.debug("MATCH! Contrato '{}' encontrado no registro {}/{} (ID: {})",
                                contratoNormalizado, i+1, totalVehicles, vehicle.getId());
                        return Optional.of(vehicle);
                    }
                } catch (Exception e) {
                    log.trace("Erro ao descriptografar contrato do veículo ID {}: {}", vehicle.getId(), e.getMessage());
                }

                if ((i + 1) % 50 == 0) {
                    log.trace("🔍 Progresso busca por contrato '{}': {}/{} processados", contratoNormalizado, i + 1, totalVehicles);
                }
            }

            log.debug("Contrato '{}' não encontrado após processar {} registros", contratoNormalizado, totalVehicles);
            return Optional.empty();
        } catch (Exception e) {
            log.error("Erro na busca por contrato descriptografado '{}': {}", contratoPlainText, e.getMessage());
            return Optional.empty();
        }
    }

    private VehicleCache updateExistingVehicle(VehicleCache existing, VehicleDTO dto, LocalDateTime syncDate) {
        existing.setCredor(dto.credor());
        existing.setDataPedido(dto.dataPedido());

        existing.setContrato(dto.contrato());
        existing.setPlaca(dto.placa());

        existing.setModelo(dto.modelo());
        existing.setUf(dto.uf());
        existing.setCidade(dto.cidade());
        existing.setCpfDevedor(dto.cpfDevedor());
        existing.setProtocolo(dto.protocolo());
        existing.setEtapaAtual(dto.etapaAtual());
        existing.setStatusApreensao(dto.statusApreensao());
        existing.setUltimaMovimentacao(dto.ultimaMovimentacao());
        existing.setApiSyncDate(syncDate);

        return existing;
    }

    private boolean hasDataChanges(VehicleCache existing, VehicleDTO dto) {
        try {
            String existingContrato = cryptoService.decryptContrato(existing.getContrato());
            String existingPlaca = cryptoService.decryptPlaca(existing.getPlaca());

            String dtoContratoDecrypted = cryptoService.decryptContrato(dto.contrato());
            String dtoPlacaDecrypted = cryptoService.decryptPlaca(dto.placa());

            boolean contratoChanged = !Objects.equals(existingContrato, dtoContratoDecrypted);
            boolean placaChanged = !Objects.equals(existingPlaca, dtoPlacaDecrypted);
            boolean credorChanged = !Objects.equals(existing.getCredor(), dto.credor());
            boolean dataPedidoChanged = !Objects.equals(existing.getDataPedido(), dto.dataPedido());
            boolean modeloChanged = !Objects.equals(existing.getModelo(), dto.modelo());
            boolean ufChanged = !Objects.equals(existing.getUf(), dto.uf());
            boolean cidadeChanged = !Objects.equals(existing.getCidade(), dto.cidade());
            boolean cpfDevedorChanged = !Objects.equals(existing.getCpfDevedor(), dto.cpfDevedor());
            boolean protocoloChanged = !Objects.equals(existing.getProtocolo(), dto.protocolo());
            boolean etapaAtualChanged = !Objects.equals(existing.getEtapaAtual(), dto.etapaAtual());
            boolean statusApreensaoChanged = !Objects.equals(existing.getStatusApreensao(), dto.statusApreensao());
            boolean ultimaMovimentacaoChanged = !Objects.equals(existing.getUltimaMovimentacao(), dto.ultimaMovimentacao());

            boolean hasChanges = contratoChanged || placaChanged || credorChanged || dataPedidoChanged ||
                    modeloChanged || ufChanged || cidadeChanged || cpfDevedorChanged ||
                    protocoloChanged || etapaAtualChanged || statusApreensaoChanged || ultimaMovimentacaoChanged;

            if (hasChanges) {
                log.debug("Mudanças detectadas na placa {}: contrato={}, placa={}, credor={}, dataPedido={}, " +
                                "modelo={}, uf={}, cidade={}, cpf={}, etapa={}, status={}, ultimaMov={}",
                        dtoPlacaDecrypted, contratoChanged, placaChanged, credorChanged, dataPedidoChanged,
                        modeloChanged, ufChanged, cidadeChanged, cpfDevedorChanged,
                        etapaAtualChanged, statusApreensaoChanged, ultimaMovimentacaoChanged);
            }

            return hasChanges;

        } catch (Exception e) {
            log.warn("Erro ao comparar dados do veículo placa={}: {} - assumindo que há mudanças",
                    cryptoService.decryptPlaca(dto.placa()), e.getMessage());
            return true;
        }
    }

    @Transactional
    public void cleanOldCache() {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(cacheRetentionDays);
        vehicleCacheRepository.deleteOldCacheEntries(cutoffDate);
        log.info("Cache limpo - removidas entradas antigas anteriores a {}", cutoffDate);
    }

    @Transactional
    public void cleanDuplicates() {
        log.info("Iniciando limpeza de duplicatas no cache");
        try {
            long beforeCount = vehicleCacheRepository.count();


            List<VehicleCache> allVehicles = vehicleCacheRepository.findAll();
            Map<String, List<VehicleCache>> groupedByContrato = new HashMap<>();
            Map<String, List<VehicleCache>> groupedByPlaca = new HashMap<>();

            for (VehicleCache vehicle : allVehicles) {
                try {
                    String contratoDescriptografado = cryptoService.decryptContrato(vehicle.getContrato());
                    if (contratoDescriptografado != null && !"N/A".equals(contratoDescriptografado)
                            && !contratoDescriptografado.trim().isEmpty()) {
                        groupedByContrato.computeIfAbsent(contratoDescriptografado, k -> new ArrayList<>()).add(vehicle);
                    }

                    String placaDescriptografada = cryptoService.decryptPlaca(vehicle.getPlaca());
                    if (placaDescriptografada != null && !"N/A".equals(placaDescriptografada)
                            && !placaDescriptografada.trim().isEmpty()) {
                        groupedByPlaca.computeIfAbsent(placaDescriptografada, k -> new ArrayList<>()).add(vehicle);
                    }
                } catch (Exception e) {
                    log.trace("Erro ao descriptografar dados do veículo ID {}: {}", vehicle.getId(), e.getMessage());
                }
            }

            int deletedCount = 0;

            for (Map.Entry<String, List<VehicleCache>> entry : groupedByContrato.entrySet()) {
                List<VehicleCache> duplicates = entry.getValue();
                if (duplicates.size() > 1) {
                    deletedCount += processDuplicates(duplicates, "contrato");
                }
            }

            for (Map.Entry<String, List<VehicleCache>> entry : groupedByPlaca.entrySet()) {
                List<VehicleCache> duplicates = entry.getValue();
                if (duplicates.size() > 1) {
                    List<VehicleCache> stillExisting = duplicates.stream()
                            .filter(v -> vehicleCacheRepository.existsById(v.getId()))
                            .collect(Collectors.toList());

                    if (stillExisting.size() > 1) {
                        deletedCount += processDuplicates(stillExisting, "placa");
                    }
                }
            }

            long afterCount = vehicleCacheRepository.count();
            log.info("Limpeza de duplicatas concluída: {} registros removidos (antes: {}, depois: {})",
                    deletedCount, beforeCount, afterCount);
        } catch (Exception e) {
            log.error("Erro durante limpeza de duplicatas", e);
        }
    }

    private int processDuplicates(List<VehicleCache> duplicates, String campo) {
        duplicates.sort((a, b) -> {
            if (a.getApiSyncDate() != null && b.getApiSyncDate() != null) {
                return b.getApiSyncDate().compareTo(a.getApiSyncDate());
            }
            return b.getId().compareTo(a.getId());
        });

        List<VehicleCache> toDelete = duplicates.subList(1, duplicates.size());
        vehicleCacheRepository.deleteAll(toDelete);
        log.debug("Removidas {} duplicatas para {}: ***", toDelete.size(), campo);

        return toDelete.size();
    }

    @Transactional
    public void invalidateCache() {
        log.info("Invalidando todo o cache de veículos");
        vehicleCacheRepository.deleteAll();
        log.info("Cache invalidado com sucesso - {} registros removidos", vehicleCacheRepository.count());
    }

    public CacheStatus getCacheStatus() {
        Optional<LocalDateTime> lastSyncOpt = vehicleCacheRepository.findLastSyncDate();
        long totalRecords = vehicleCacheRepository.count();

        if (lastSyncOpt.isEmpty()) {
            return CacheStatus.builder()
                    .valid(false)
                    .totalRecords(totalRecords)
                    .message("Cache vazio")
                    .build();
        }

        LocalDateTime lastSync = lastSyncOpt.get();
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(cacheExpiryMinutes);
        boolean isValid = lastSync.isAfter(cutoff);
        long minutesSinceSync = Duration.between(lastSync, LocalDateTime.now()).toMinutes();

        return CacheStatus.builder()
                .valid(isValid)
                .lastSyncDate(lastSync)
                .totalRecords(totalRecords)
                .minutesSinceLastSync(minutesSinceSync)
                .message(isValid ? "Cache válido (dados sensíveis protegidos)" :
                        String.format("Cache desatualizado (última sync há %d minutos)", minutesSinceSync))
                .build();
    }

    @lombok.Data
    @lombok.Builder
    public static class CacheStatus {
        private boolean valid;
        private LocalDateTime lastSyncDate;
        private long totalRecords;
        private long minutesSinceLastSync;
        private String message;
    }
}