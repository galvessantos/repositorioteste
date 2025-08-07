package com.montreal.msiav_bh.service;

import com.montreal.msiav_bh.context.CacheUpdateContext;
import com.montreal.msiav_bh.dto.PageDTO;
import com.montreal.msiav_bh.dto.VehicleDTO;
import com.montreal.msiav_bh.dto.response.ConsultaNotificationResponseDTO;
import com.montreal.msiav_bh.dto.response.QueryDetailResponseDTO;
import com.montreal.msiav_bh.mapper.VehicleInquiryMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class VehicleApiService {

    private final ApiQueryService apiQueryService;
    private final VehicleInquiryMapper vehicleInquiryMapper;
    private final VehicleCacheService vehicleCacheService;

    private static final String CIRCUIT_BREAKER_NAME = "vehicle-api";

    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "fallbackToDatabase")
    @Retry(name = CIRCUIT_BREAKER_NAME)
    public PageDTO<VehicleDTO> getVehiclesWithFallback(
            LocalDate dataInicio, LocalDate dataFim, String credor, String contrato,
            String protocolo, String cpf, String uf, String cidade, String modelo,
            String placa, String etapaAtual, String statusApreensao,
            int page, int size, String sortBy, String sortDir) {

        log.info("==== INICIANDO BUSCA DE VEÍCULOS (DATABASE-FIRST) ====");

        try {
            PageDTO<VehicleDTO> databaseResult = getFromDatabase(
                    dataInicio, dataFim, credor, contrato, protocolo, cpf,
                    uf, cidade, modelo, placa, etapaAtual, statusApreensao,
                    page, size, sortBy, sortDir
            );

            VehicleCacheService.CacheStatus cacheStatus = vehicleCacheService.getCacheStatus();

            if (cacheStatus.getTotalRecords() == 0 ||
                    (databaseResult.content().isEmpty() && cacheStatus.getMinutesSinceLastSync() > 60)) {

                log.warn("Cache vazio ou muito desatualizado - buscando da API");
                return fetchFromApiWithUpdate(dataInicio, dataFim, credor, contrato,
                        protocolo, cpf, uf, cidade, modelo, placa, etapaAtual,
                        statusApreensao, page, size, sortBy, sortDir);
            }

            if (!cacheStatus.isValid() && cacheStatus.getTotalRecords() > 0) {
                log.info("Cache desatualizado - iniciando atualização em background");
                CompletableFuture.runAsync(() -> updateCacheInBackground(dataInicio, dataFim));
            }

            log.info("Retornando {} registros do PostgreSQL", databaseResult.totalElements());
            return databaseResult;

        } catch (Exception e) {
            log.error("Erro ao buscar do banco - tentando API como fallback: {}", e.getMessage());
            return fetchFromApiDirect(dataInicio, dataFim, credor, contrato,
                    protocolo, cpf, uf, cidade, modelo, placa, etapaAtual,
                    statusApreensao, page, size, sortBy, sortDir);
        }
    }

    private PageDTO<VehicleDTO> getFromDatabase(
            LocalDate dataInicio, LocalDate dataFim, String credor, String contrato,
            String protocolo, String cpf, String uf, String cidade, String modelo,
            String placa, String etapaAtual, String statusApreensao,
            int page, int size, String sortBy, String sortDir) {

        log.debug("Consultando PostgreSQL com filtros aplicados");

        Sort.Direction direction = "desc".equalsIgnoreCase(sortDir) ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));

        Page<VehicleDTO> result = vehicleCacheService.getFromCache(
                dataInicio, dataFim, credor, contrato, protocolo, cpf,
                uf, cidade, modelo, placa, etapaAtual, statusApreensao, pageable
        );

        return PageDTO.of(
                result.getContent(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements()
        );
    }

    private PageDTO<VehicleDTO> fetchFromApiWithUpdate(
            LocalDate dataInicio, LocalDate dataFim, String credor, String contrato,
            String protocolo, String cpf, String uf, String cidade, String modelo,
            String placa, String etapaAtual, String statusApreensao,
            int page, int size, String sortBy, String sortDir) {

        log.info("Buscando dados da API externa e atualizando cache");

        try {
            LocalDate searchStart = dataInicio != null ? dataInicio : LocalDate.now().minusDays(30);
            LocalDate searchEnd = dataFim != null ? dataFim : LocalDate.now();

            CompletableFuture<List<VehicleDTO>> future = CompletableFuture.supplyAsync(() -> {
                List<ConsultaNotificationResponseDTO.NotificationData> notifications =
                        apiQueryService.searchByPeriod(searchStart, searchEnd);
                return vehicleInquiryMapper.mapToVeiculoDTOForResponse(notifications);
            });

            List<VehicleDTO> vehicles = future.get(30, TimeUnit.SECONDS);

            if (!vehicles.isEmpty()) {
                CacheUpdateContext context = CacheUpdateContext.filteredSearch(
                        searchStart, searchEnd, credor, contrato, protocolo, cpf
                );
                vehicleCacheService.updateCache(vehicles, context);

                return getFromDatabase(dataInicio, dataFim, credor, contrato, protocolo,
                        cpf, uf, cidade, modelo, placa, etapaAtual, statusApreensao,
                        page, size, sortBy, sortDir);
            }

            log.warn("API retornou lista vazia");
            return PageDTO.of(List.of(), page, size, 0);

        } catch (Exception e) {
            log.error("Erro ao buscar da API: {}", e.getMessage());
            throw new RuntimeException("Falha ao buscar dados da API", e);
        }
    }

    private PageDTO<VehicleDTO> fetchFromApiDirect(
            LocalDate dataInicio, LocalDate dataFim, String credor, String contrato,
            String protocolo, String cpf, String uf, String cidade, String modelo,
            String placa, String etapaAtual, String statusApreensao,
            int page, int size, String sortBy, String sortDir) {

        log.info("Buscando diretamente da API (modo emergência)");

        try {
            LocalDate searchStart = dataInicio != null ? dataInicio : LocalDate.now().minusDays(30);
            LocalDate searchEnd = dataFim != null ? dataFim : LocalDate.now();

            List<ConsultaNotificationResponseDTO.NotificationData> notifications =
                    apiQueryService.searchByPeriod(searchStart, searchEnd);

            List<VehicleDTO> vehicles = vehicleInquiryMapper.mapToVeiculoDTOForResponse(notifications);

            vehicles = applyLocalFilters(vehicles, credor, contrato, protocolo,
                    cpf, uf, cidade, modelo, placa, etapaAtual, statusApreensao);

            vehicles = sortVehicles(vehicles, sortBy, sortDir);

            return paginateVehicles(vehicles, page, size);

        } catch (Exception e) {
            log.error("Falha crítica ao buscar da API: {}", e.getMessage());
            return PageDTO.of(List.of(), page, size, 0);
        }
    }

    private void updateCacheInBackground(LocalDate dataInicio, LocalDate dataFim) {
        try {
            log.debug("Iniciando atualização de cache em background");

            LocalDate searchStart = dataInicio != null ? dataInicio : LocalDate.now().minusDays(30);
            LocalDate searchEnd = dataFim != null ? dataFim : LocalDate.now();

            List<ConsultaNotificationResponseDTO.NotificationData> notifications =
                    apiQueryService.searchByPeriod(searchStart, searchEnd);

            List<VehicleDTO> vehicles = vehicleInquiryMapper.mapToVeiculoDTO(notifications);

            if (!vehicles.isEmpty()) {
                CacheUpdateContext context = CacheUpdateContext.scheduledRefresh(searchStart, searchEnd);
                vehicleCacheService.updateCache(vehicles, context);
                log.info("Cache atualizado em background com {} registros", vehicles.size());
            }

        } catch (Exception e) {
            log.error("Erro na atualização em background do cache: {}", e.getMessage());
        }
    }

    public PageDTO<VehicleDTO> fallbackToDatabase(
            LocalDate dataInicio, LocalDate dataFim, String credor, String contrato,
            String protocolo, String cpf, String uf, String cidade, String modelo,
            String placa, String etapaAtual, String statusApreensao,
            int page, int size, String sortBy, String sortDir, Throwable throwable) {

        log.warn("Circuit breaker ativado - usando apenas dados do PostgreSQL. Erro: {}",
                throwable.getMessage());

        try {
            return getFromDatabase(dataInicio, dataFim, credor, contrato, protocolo,
                    cpf, uf, cidade, modelo, placa, etapaAtual, statusApreensao,
                    page, size, sortBy, sortDir);
        } catch (Exception e) {
            log.error("Falha ao buscar do banco no fallback: {}", e.getMessage());
            return PageDTO.of(List.of(), page, size, 0);
        }
    }

    private List<VehicleDTO> applyLocalFilters(List<VehicleDTO> vehicles,
                                               String credor, String contrato, String protocolo, String cpf,
                                               String uf, String cidade, String modelo, String placa,
                                               String etapaAtual, String statusApreensao) {

        return vehicles.stream()
                .filter(v -> credor == null || (v.credor() != null && v.credor().contains(credor)))
                .filter(v -> contrato == null || (v.contrato() != null && v.contrato().equals(contrato)))
                .filter(v -> protocolo == null || (v.protocolo() != null && v.protocolo().equals(protocolo)))
                .filter(v -> cpf == null || (v.cpfDevedor() != null && v.cpfDevedor().equals(cpf)))
                .filter(v -> uf == null || (v.uf() != null && v.uf().equals(uf)))
                .filter(v -> cidade == null || (v.cidade() != null && v.cidade().contains(cidade)))
                .filter(v -> modelo == null || (v.modelo() != null && v.modelo().contains(modelo)))
                .filter(v -> placa == null || (v.placa() != null && v.placa().equals(placa)))
                .filter(v -> etapaAtual == null || (v.etapaAtual() != null && v.etapaAtual().equals(etapaAtual)))
                .filter(v -> statusApreensao == null || (v.statusApreensao() != null && v.statusApreensao().equals(statusApreensao)))
                .toList();
    }

    private List<VehicleDTO> sortVehicles(List<VehicleDTO> vehicles, String sortBy, String sortDir) {
        var comparator = switch (sortBy.toLowerCase()) {
            case "credor" -> java.util.Comparator.comparing(VehicleDTO::credor,
                    java.util.Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
            case "datapedido" -> java.util.Comparator.comparing(VehicleDTO::dataPedido,
                    java.util.Comparator.nullsLast(LocalDate::compareTo));
            case "placa" -> java.util.Comparator.comparing(VehicleDTO::placa,
                    java.util.Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
            default -> java.util.Comparator.comparing(VehicleDTO::protocolo,
                    java.util.Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
        };

        return vehicles.stream()
                .sorted("desc".equalsIgnoreCase(sortDir) ? comparator.reversed() : comparator)
                .toList();
    }

    private PageDTO<VehicleDTO> paginateVehicles(List<VehicleDTO> vehicles, int page, int size) {
        int total = vehicles.size();
        int start = page * size;
        int end = Math.min(start + size, total);

        return start >= total
                ? PageDTO.of(List.of(), page, size, total)
                : PageDTO.of(vehicles.subList(start, end), page, size, total);
    }

    public void forceRefreshFromApi() {
        log.info("Forçando atualização manual do cache via API");

        try {
            LocalDate endDate = LocalDate.now();
            LocalDate startDate = endDate.minusDays(30);

            List<ConsultaNotificationResponseDTO.NotificationData> notifications =
                    apiQueryService.searchByPeriod(startDate, endDate);

            List<VehicleDTO> vehicles = vehicleInquiryMapper.mapToVeiculoDTO(notifications);

            if (!vehicles.isEmpty()) {
                CacheUpdateContext context = CacheUpdateContext.fullRefresh();
                vehicleCacheService.updateCache(vehicles, context);
                log.info("Cache forçadamente atualizado - {} registros", vehicles.size());
            }

        } catch (Exception e) {
            log.error("Erro ao forçar atualização: {}", e.getMessage());
            throw new RuntimeException("Falha ao forçar atualização do cache", e);
        }
    }

    public QueryDetailResponseDTO searchContract(String contrato) {
        log.info("Buscando detalhes do contrato: {}", contrato);
        return apiQueryService.searchContract(contrato);
    }
}