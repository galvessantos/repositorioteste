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
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class VehicleCacheService {

    private final VehicleCacheRepository vehicleCacheRepository;
    private final VehicleCacheMapper vehicleCacheMapper;
    private final VehicleCacheCryptoService cryptoService;

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

        for (VehicleDTO dto : vehicles) {
            try {
                Optional<VehicleCache> existing = findExistingVehicle(dto);

                if (existing.isPresent()) {
                    VehicleCache updatedEntity = updateExistingVehicle(existing.get(), dto, syncDate);
                    vehicleCacheRepository.save(updatedEntity);
                    updated++;
                    log.trace("Veículo atualizado: protocolo={}", dto.protocolo());
                } else {
                    VehicleCache newEntity = vehicleCacheMapper.toEntity(dto, syncDate);
                    vehicleCacheRepository.save(newEntity);
                    inserted++;
                    log.trace("Novo veículo inserido: protocolo={}", dto.protocolo());
                }
            } catch (Exception e) {
                if (e.getMessage() != null &&
                        (e.getMessage().contains("constraint") ||
                                e.getMessage().contains("duplicate") ||
                                e.getMessage().contains("unique"))) {
                    log.debug("Registro duplicado ignorado (constraint violation): protocolo={}, erro={}",
                            dto.protocolo(), e.getMessage().substring(0, Math.min(100, e.getMessage().length())));
                    duplicateSkipped++;
                } else {
                    log.error("Erro inesperado ao processar veículo protocolo={}: {}", dto.protocolo(), e.getMessage());
                    throw e;
                }
            }
        }

        log.info("Cache atualizado: {} atualizados, {} inseridos, {} duplicados ignorados",
                updated, inserted, duplicateSkipped);
    }

    private Optional<VehicleCache> findExistingVehicle(VehicleDTO dto) {
        log.debug("Procurando veículo existente para contrato:{}, placa:{}", dto.contrato(), dto.placa());

        List<VehicleCache> allVehicles = vehicleCacheRepository.findAll();

        for (VehicleCache vehicle : allVehicles) {
            try {
                if (dto.contrato() != null && !"N/A".equals(dto.contrato()) && !dto.contrato().trim().isEmpty()) {
                    String contratoDecrypted = cryptoService.decryptContrato(vehicle.getContrato());
                    if (dto.contrato().equals(contratoDecrypted)) {
                        log.debug("Veículo encontrado por contrato: {} = {}", dto.contrato(), contratoDecrypted);
                        return Optional.of(vehicle);
                    }
                }

                if (dto.placa() != null && !"N/A".equals(dto.placa()) && !dto.placa().trim().isEmpty()) {
                    String placaDecrypted = cryptoService.decryptPlaca(vehicle.getPlaca());
                    if (dto.placa().equals(placaDecrypted)) {
                        log.debug("Veículo encontrado por placa: {} = {}", dto.placa(), placaDecrypted);
                        return Optional.of(vehicle);
                    }
                }
            } catch (Exception e) {
                log.trace("Erro ao descriptografar veículo ID {}: {}", vehicle.getId(), e.getMessage());
            }
        }

        log.debug("Nenhum veículo existente encontrado para contrato:{}, placa:{}",
                dto.contrato(), dto.placa());
        return Optional.empty();
    }

    private VehicleCache updateExistingVehicle(VehicleCache existing, VehicleDTO dto, LocalDateTime syncDate) {
        existing.setCredor(dto.credor());
        existing.setDataPedido(dto.dataPedido());

        existing.setContrato(cryptoService.encryptContrato(dto.contrato()));
        existing.setPlaca(cryptoService.encryptPlaca(dto.placa()));

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

    @Transactional
    public void cleanOldCache() {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(cacheRetentionDays);
        vehicleCacheRepository.deleteOldCacheEntries(cutoffDate);
        log.info("Cache limpo - removidas entradas antigas anteriores a {}", cutoffDate);

        cleanDuplicates();
    }

    @Transactional
    public void cleanDuplicates() {
        log.info("Iniciando limpeza de duplicatas no cache");
        try {
            long beforeCount = vehicleCacheRepository.count();

            List<String> duplicateContracts = vehicleCacheRepository.findDuplicateContracts();

            int deletedCount = 0;
            for (String contract : duplicateContracts) {
                List<VehicleCache> duplicates = vehicleCacheRepository.findByContratoOrderByIdDesc(contract);
                if (duplicates.size() > 1) {
                    List<VehicleCache> toDelete = duplicates.subList(1, duplicates.size());
                    vehicleCacheRepository.deleteAll(toDelete);
                    deletedCount += toDelete.size();
                    log.debug("Removidas {} duplicatas para contrato: ***", toDelete.size());
                }
            }

            List<String> duplicatePlates = vehicleCacheRepository.findDuplicatePlates();

            for (String plate : duplicatePlates) {
                List<VehicleCache> duplicates = vehicleCacheRepository.findByPlacaOrderByIdDesc(plate);
                if (duplicates.size() > 1) {
                    List<VehicleCache> toDelete = duplicates.subList(1, duplicates.size());
                    vehicleCacheRepository.deleteAll(toDelete);
                    deletedCount += toDelete.size();
                    log.debug("Removidas {} duplicatas para placa: ***", toDelete.size());
                }
            }

            long afterCount = vehicleCacheRepository.count();
            log.info("Limpeza de duplicatas concluída: {} registros removidos (antes: {}, depois: {})",
                    deletedCount, beforeCount, afterCount);
        } catch (Exception e) {
            log.error("Erro durante limpeza de duplicatas", e);
        }
    }

    @Transactional
    public void invalidateCache() {
        log.info("Invalidando todo o cache de veículos");
        vehicleCacheRepository.deleteAll();
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

    public Map<String, Object> debugContract(String contrato) {
        Map<String, Object> debugInfo = new HashMap<>();

        try {
            String contratoEncrypted = cryptoService.encryptContrato(contrato);

            List<VehicleCache> duplicates = vehicleCacheRepository.findByContratoOrderByIdDesc(contratoEncrypted);

            debugInfo.put("contrato_original", contrato);
            debugInfo.put("contrato_criptografado", contratoEncrypted);
            debugInfo.put("total_encontrados", duplicates.size());

            List<Map<String, Object>> registros = new ArrayList<>();
            for (VehicleCache entity : duplicates) {
                Map<String, Object> registro = new HashMap<>();
                registro.put("id", entity.getId());
                registro.put("external_id", entity.getExternalId());
                registro.put("contrato_criptografado", entity.getContrato());
                registro.put("placa_criptografada", entity.getPlaca());
                registro.put("credor", entity.getCredor());
                registro.put("api_sync_date", entity.getApiSyncDate());
                registro.put("created_at", entity.getCreatedAt());

                try {
                    String contratoDesc = cryptoService.decryptContrato(entity.getContrato());
                    String placaDesc = cryptoService.decryptPlaca(entity.getPlaca());
                    registro.put("contrato_descriptografado", contratoDesc);
                    registro.put("placa_descriptografada", placaDesc);
                } catch (Exception e) {
                    registro.put("erro_descriptografia", e.getMessage());
                }

                registros.add(registro);
            }

            debugInfo.put("registros", registros);

        } catch (Exception e) {
            debugInfo.put("erro", e.getMessage());
        }

        return debugInfo;
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