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
import org.springframework.data.domain.PageRequest;
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

    public PageDTO<VehicleDTO> getFromCache(LocalDate dataInicio, LocalDate dataFim,
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

        // DEBUG: Log específico para busca por placa
        if (placa != null && !placa.trim().isEmpty()) {
            log.info("🔍 DEBUG BUSCA POR PLACA:");
            log.info("  - Placa original: '{}'", placa);
            log.info("  - Placa normalizada: '{}'", placa.toUpperCase().trim());
            log.info("  - Placa criptografada: '{}'", placaEncrypted != null ? placaEncrypted.substring(0, Math.min(20, placaEncrypted.length())) + "..." : "NULL");
            log.info("  - Tamanho da placa criptografada: {}", placaEncrypted != null ? placaEncrypted.length() : 0);
            
            // Verificar se existem placas no cache que começam de forma similar
            try {
                long totalRecords = vehicleCacheRepository.count();
                log.info("  - Total de registros no cache: {}", totalRecords);
                
                if (totalRecords > 0) {
                    // Buscar algumas placas de exemplo para comparação
                    Page<VehicleCache> sampleRecords = vehicleCacheRepository.findAll(PageRequest.of(0, 3));
                    log.info("  - Amostras de placas no cache:");
                    for (VehicleCache record : sampleRecords.getContent()) {
                        String placaExemplo = record.getPlaca();
                        if (placaExemplo != null) {
                            log.info("    * Placa no banco: '{}' (tamanho: {})", 
                                placaExemplo.substring(0, Math.min(20, placaExemplo.length())) + "...", 
                                placaExemplo.length());
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Erro ao buscar registros de exemplo: {}", e.getMessage());
            }
        }

        Page<VehicleCache> cachedVehicles = vehicleCacheRepository.findWithFiltersFixed(
                dataInicio, dataFim, credor, contratoEncrypted, protocolo, cpf,
                uf, cidade, modelo, placaEncrypted, etapaAtual, statusApreensao, pageable
        );

        log.info("Dados recuperados do PostgreSQL: {} registros de {} total",
                cachedVehicles.getContent().size(), cachedVehicles.getTotalElements());

        // Se busca por placa não retornou resultados, tentar busca alternativa
        if (placa != null && !placa.trim().isEmpty() && cachedVehicles.getTotalElements() == 0) {
            log.warn("Busca normal por placa '{}' não retornou resultados. Tentando busca alternativa...", placa);
            return searchByPlacaAlternative(placa, pageable);
        }

        List<VehicleDTO> vehiclesCacheDecrypted = cachedVehicles.getContent().stream()
                .map(vehicleCacheMapper::toDto)
                .collect(Collectors.toList());

        log.info("Dados descriptografados: {} veículos prontos para retorno",
                vehiclesCacheDecrypted.size());

        return PageDTO.<VehicleDTO>builder()
                .content(vehiclesCacheDecrypted)
                .page(cachedVehicles.getNumber())
                .size(cachedVehicles.getSize())
                .totalElements(cachedVehicles.getTotalElements())
                .totalPages(cachedVehicles.getTotalPages())
                .first(cachedVehicles.isFirst())
                .last(cachedVehicles.isLast())
                .empty(cachedVehicles.isEmpty())
                .build();
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

    /**
     * Método alternativo para busca por placa que testa várias variações
     * Usado quando a busca normal falha
     */
    public PageDTO<VehicleDTO> searchByPlacaAlternative(String placa, Pageable pageable) {
        if (placa == null || placa.trim().isEmpty()) {
            return PageDTO.<VehicleDTO>builder()
                    .content(List.of())
                    .page(0)
                    .size(0)
                    .totalElements(0L)
                    .totalPages(0)
                    .first(true)
                    .last(true)
                    .empty(true)
                    .build();
        }

        log.info("🔍 BUSCA ALTERNATIVA POR PLACA: '{}'", placa);
        
        // Buscar todos os veículos e filtrar em memória (menos eficiente, mas mais confiável)
        Page<VehicleCache> allCachedVehicles = vehicleCacheRepository.findAll(pageable);
        
        log.info("Total de veículos no cache para filtrar: {}", allCachedVehicles.getTotalElements());
        
        List<VehicleCache> matchingVehicles = new ArrayList<>();
        String placaNormalizada = placa.toUpperCase().trim();
        
        // Variações possíveis da placa para teste
        List<String> placaVariacoes = List.of(
            placaNormalizada,
            placaNormalizada.replace("O", "0"), // O -> 0
            placaNormalizada.replace("0", "O"), // 0 -> O
            placaNormalizada.replace("I", "1"), // I -> 1
            placaNormalizada.replace("1", "I")  // 1 -> I
        );
        
        for (VehicleCache vehicle : allCachedVehicles.getContent()) {
            try {
                String placaDescriptografada = cryptoService.decryptPlaca(vehicle.getPlaca());
                if (placaDescriptografada != null && !"N/A".equals(placaDescriptografada)) {
                    String placaDescNormalizada = placaDescriptografada.toUpperCase().trim();
                    
                    // Testar todas as variações
                    for (String variacao : placaVariacoes) {
                        if (variacao.equals(placaDescNormalizada)) {
                            log.info("✅ MATCH ENCONTRADO: '{}' == '{}' (variação: '{}')", 
                                    placa, placaDescriptografada, variacao);
                            matchingVehicles.add(vehicle);
                            break; // Sair do loop de variações
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Erro ao descriptografar placa do veículo ID {}: {}", vehicle.getId(), e.getMessage());
            }
        }
        
        log.info("Busca alternativa encontrou {} veículos", matchingVehicles.size());
        
        // Converter para DTO
        List<VehicleDTO> vehiclesDto = matchingVehicles.stream()
                .map(vehicleCacheMapper::toDto)
                .collect(Collectors.toList());
        
        return PageDTO.<VehicleDTO>builder()
                .content(vehiclesDto)
                .page(pageable.getPageNumber())
                .size(pageable.getPageSize())
                .totalElements((long) vehiclesDto.size())
                .totalPages(vehiclesDto.isEmpty() ? 0 : 1)
                .first(true)
                .last(true)
                .empty(vehiclesDto.isEmpty())
                .build();
    }

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
                } else if (e.getMessage() != null && 
                           e.getMessage().contains("value too long for type character varying")) {
                    log.error("ERRO DE TAMANHO DE CAMPO: Algum campo excede o limite do banco de dados");
                    log.error("Protocolo afetado: {}", dto.protocolo());
                    log.error("Este erro indica que os campos criptografados são muito longos");
                    log.error("SOLUÇÃO: Execute a migração do banco: ALTER TABLE vehicle_cache ALTER COLUMN contrato TYPE TEXT, ALTER TABLE vehicle_cache ALTER COLUMN placa TYPE TEXT;");
                    throw new RuntimeException("Campo muito longo - necessária migração do banco de dados", e);
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
        log.debug("Procurando veículo existente para contrato:{}, placa:{}, protocolo:{}",
                dto.contrato(), dto.placa(), dto.protocolo());

        if (dto.contrato() != null && !"N/A".equals(dto.contrato()) && !dto.contrato().trim().isEmpty()) {
            try {
                String contratoEncrypted = cryptoService.encryptContrato(dto.contrato());
                Optional<VehicleCache> byContrato = vehicleCacheRepository.findByContrato(contratoEncrypted);
                if (byContrato.isPresent()) {
                    log.debug("Veículo encontrado por contrato criptografado");
                    return byContrato;
                }
            } catch (Exception e) {
                log.debug("Erro ao buscar por contrato: {}", e.getMessage());
            }
        }

        if (dto.placa() != null && !"N/A".equals(dto.placa()) && !dto.placa().trim().isEmpty()) {
            try {
                String placaEncrypted = cryptoService.encryptPlaca(dto.placa());
                Optional<VehicleCache> byPlaca = vehicleCacheRepository.findByPlaca(placaEncrypted);
                if (byPlaca.isPresent()) {
                    log.debug("Veículo encontrado por placa criptografada");
                    return byPlaca;
                }
            } catch (Exception e) {
                log.debug("Erro ao buscar por placa: {}", e.getMessage());
            }
        }

        if (dto.protocolo() != null && !"N/A".equals(dto.protocolo()) && !dto.protocolo().trim().isEmpty()) {
            Optional<VehicleCache> byProtocolo = vehicleCacheRepository.findByProtocolo(dto.protocolo());
            if (byProtocolo.isPresent()) {
                log.debug("Veículo encontrado por protocolo");
                return byProtocolo;
            }
        }

        log.debug("Nenhum veículo existente encontrado");
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