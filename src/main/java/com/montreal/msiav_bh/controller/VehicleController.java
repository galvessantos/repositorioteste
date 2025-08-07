package com.montreal.msiav_bh.controller;

import com.montreal.msiav_bh.dto.PageDTO;
import com.montreal.msiav_bh.dto.VehicleDTO;
import com.montreal.msiav_bh.dto.response.QueryDetailResponseDTO;
import com.montreal.msiav_bh.service.VehicleApiService;
import com.montreal.msiav_bh.service.VehicleCacheService;
import com.montreal.msiav_bh.utils.exceptions.ValidationMessages;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/vehicle")
@Tag(name = "Veiculos", description = "Veiculos - Database-First Strategy")
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "Acesso não autorizado"),
        @ApiResponse(responseCode = "404", description = "Recurso não encontrado")
})
public class VehicleController {

    private final VehicleApiService vehicleApiService;
    private final VehicleCacheService vehicleCacheService;

    @GetMapping
    @Operation(summary = "Buscar veículos (Database-First)")
    public ResponseEntity<?> buscarVeiculos(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate dataInicio,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate dataFim,
            @RequestParam(required = false) String credor,
            @RequestParam(required = false) String contrato,
            @RequestParam(required = false) String protocolo,
            @RequestParam(required = false) @Pattern(regexp = "\\d{11,14}", message = ValidationMessages.CPF_INVALIDO) String cpf,
            @RequestParam(required = false) String uf,
            @RequestParam(required = false) String cidade,
            @RequestParam(required = false) String modelo,
            @RequestParam(required = false) String placa,
            @RequestParam(required = false) String etapaAtual,
            @RequestParam(required = false) String statusApreensao,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "protocolo") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir,
            HttpServletResponse httpResponse
    ) {
        log.info("Requisição recebida - Estratégia: DATABASE-FIRST");

        if (dataInicio == null && dataFim == null) {
            dataInicio = LocalDate.of(2025, 3, 20);
            dataFim = LocalDate.of(2025, 7, 20);
            log.info("Usando período padrão: {} a {}", dataInicio, dataFim);
        }

        if (dataInicio != null && dataFim != null && dataFim.isBefore(dataInicio)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", ValidationMessages.DATA_FIM_ANTERIOR_INICIO,
                    "source", "validation"
            ));
        }
        if (dataInicio != null && dataFim == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", ValidationMessages.DATA_FIM_OBRIGATORIA,
                    "source", "validation"
            ));
        }
        if (dataInicio == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", ValidationMessages.DATA_INICIO_OBRIGATORIA,
                    "source", "validation"
            ));
        }

        try {
            PageDTO<VehicleDTO> resultado = vehicleApiService.getVehiclesWithFallback(
                    dataInicio, dataFim, credor, contrato, protocolo, cpf, uf, cidade,
                    modelo, placa, etapaAtual, statusApreensao, page, size, sortBy, sortDir
            );

            httpResponse.setHeader("X-Data-Strategy", "Database-First");
            httpResponse.setHeader("X-Data-Source", "PostgreSQL");

            VehicleCacheService.CacheStatus cacheStatus = vehicleCacheService.getCacheStatus();
            httpResponse.setHeader("X-Cache-Status", cacheStatus.isValid() ? "Valid" : "Outdated");
            httpResponse.setHeader("X-Cache-Age-Minutes", String.valueOf(cacheStatus.getMinutesSinceLastSync()));

            Map<String, Object> response = new HashMap<>();
            response.put("data", resultado);
            response.put("metadata", Map.of(
                    "source", "PostgreSQL",
                    "strategy", "Database-First",
                    "cacheValid", cacheStatus.isValid(),
                    "cacheAgeMinutes", cacheStatus.getMinutesSinceLastSync(),
                    "totalRecordsInCache", cacheStatus.getTotalRecords()
            ));

            log.info("Resposta enviada com {} registros", resultado.content().size());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Erro ao processar requisição: {}", e.getMessage(), e);

            httpResponse.setHeader("X-Data-Strategy", "Database-First");
            httpResponse.setHeader("X-Data-Source", "Error");

            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Erro ao processar requisição",
                    "message", e.getMessage(),
                    "source", "internal"
            ));
        }
    }

    @GetMapping("/contract")
    @Operation(summary = "Buscar detalhes de contrato específico")
    public ResponseEntity<?> getDados(@RequestParam String contrato) {
        log.info("Buscando contrato: {}", contrato);
        try {
            QueryDetailResponseDTO resposta = vehicleApiService.searchContract(contrato);
            return ResponseEntity.ok(resposta);
        } catch (Exception e) {
            log.error("Erro ao buscar contrato: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Erro ao buscar contrato",
                    "message", e.getMessage()
            ));
        }
    }

    @GetMapping("/health")
    @Operation(summary = "Verificar saúde da API")
    public ResponseEntity<Map<String, String>> healthCheck() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "strategy", "Database-First",
                "message", "API operacional com estratégia Database-First"
        ));
    }

    @PostMapping("/cache/refresh")
    @Operation(summary = "Forçar atualização do cache via API")
    public ResponseEntity<Map<String, String>> forceRefreshCache() {
        try {
            log.info("Solicitação manual de atualização do cache");
            vehicleApiService.forceRefreshFromApi();
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Cache atualizado com sucesso via API",
                    "source", "API → PostgreSQL"
            ));
        } catch (Exception e) {
            log.error("Falha ao atualizar cache: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error",
                    "message", "Falha ao atualizar o cache",
                    "error", e.getMessage()
            ));
        }
    }

    @GetMapping("/cache/status")
    @Operation(summary = "Obter status detalhado do cache")
    public ResponseEntity<Map<String, Object>> getCacheStatus() {
        VehicleCacheService.CacheStatus status = vehicleCacheService.getCacheStatus();

        Map<String, Object> response = new HashMap<>();
        response.put("valid", status.isValid());
        response.put("totalRecords", status.getTotalRecords());
        response.put("lastSyncDate", status.getLastSyncDate());
        response.put("minutesSinceLastSync", status.getMinutesSinceLastSync());
        response.put("message", status.getMessage());
        response.put("strategy", "Database-First");
        response.put("dataSource", status.isValid() ? "PostgreSQL (Updated)" : "PostgreSQL (Outdated)");

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/cache/invalidate")
    @Operation(summary = "Invalidar todo o cache")
    public ResponseEntity<Map<String, String>> invalidateCache() {
        try {
            log.info("Invalidando cache manualmente");
            vehicleCacheService.invalidateCache();
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Cache invalidado. Próxima consulta forçará atualização via API",
                    "action", "Cache cleared"
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error",
                    "message", "Falha ao invalidar o cache",
                    "error", e.getMessage()
            ));
        }
    }

    @PostMapping("/cache/clean-duplicates")
    @Operation(summary = "Limpar registros duplicados do cache")
    public ResponseEntity<Map<String, Object>> cleanDuplicates() {
        try {
            log.info("Iniciando limpeza manual de duplicatas");
            VehicleCacheService.CacheStatus beforeStatus = vehicleCacheService.getCacheStatus();
            vehicleCacheService.cleanDuplicates();
            VehicleCacheService.CacheStatus afterStatus = vehicleCacheService.getCacheStatus();

            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Limpeza de duplicatas concluída",
                    "before", Map.of("totalRecords", beforeStatus.getTotalRecords()),
                    "after", Map.of("totalRecords", afterStatus.getTotalRecords()),
                    "removed", beforeStatus.getTotalRecords() - afterStatus.getTotalRecords()
            ));
        } catch (Exception e) {
            log.error("Falha na limpeza de duplicatas: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error",
                    "message", "Falha na limpeza de duplicatas",
                    "error", e.getMessage()
            ));
        }
    }

    @GetMapping("/cache/debug/{contrato}")
    @Operation(summary = "Debug de duplicatas para contrato específico")
    public ResponseEntity<Map<String, Object>> debugContract(@PathVariable String contrato) {
        try {
            log.info("Debug do contrato: {}", contrato);
            Map<String, Object> debugInfo = vehicleCacheService.debugContract(contrato);
            return ResponseEntity.ok(debugInfo);
        } catch (Exception e) {
            log.error("Erro no debug do contrato: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error",
                    "message", "Erro no debug",
                    "error", e.getMessage()
            ));
        }
    }


}