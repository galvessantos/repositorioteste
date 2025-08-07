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
import java.util.ArrayList;
import java.util.List;

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

    @DeleteMapping("/cache/invalidate")
    @Operation(summary = "Invalidar todo o cache (usar com cuidado)")
    public ResponseEntity<Map<String, Object>> invalidateCache(
            @RequestHeader(value = "X-Confirm-Action", required = false) String confirmAction) {
        
        // Proteção simples para evitar uso acidental
        if (!"CONFIRM_INVALIDATE".equals(confirmAction)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", "Ação não confirmada. Adicione o header 'X-Confirm-Action: CONFIRM_INVALIDATE'",
                    "warning", "Esta operação remove TODOS os dados do cache"
            ));
        }
        
        try {
            log.warn("INVALIDAÇÃO DE CACHE SOLICITADA - Removendo todos os dados do cache");
            long recordsBeforeInvalidation = vehicleCacheService.getCacheStatus().getTotalRecords();
            
            vehicleCacheService.invalidateCache();
            
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Cache invalidado com sucesso",
                    "recordsRemoved", recordsBeforeInvalidation,
                    "warning", "Próxima consulta forçará atualização via API"
            ));
        } catch (Exception e) {
            log.error("Falha ao invalidar cache", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error",
                    "message", "Falha ao invalidar o cache",
                    "error", e.getMessage()
            ));
        }
    }

    @GetMapping("/debug/busca-alternativa")
    @Operation(summary = "Debug: testar busca alternativa por placa")
    public ResponseEntity<Map<String, Object>> debugBuscaAlternativa(
            @RequestParam(required = false) String placa) {
        try {
            if (placa == null || placa.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "erro", "Parâmetro 'placa' é obrigatório",
                    "exemplo", "/debug/busca-alternativa?placa=PSS0O37"
                ));
            }
            
            Map<String, Object> debug = new HashMap<>();
            debug.put("placaTestada", placa);
            
            // Primeiro, busca normal
            log.info("=== TESTE DE BUSCA NORMAL ===");
            PageDTO<VehicleDTO> resultadoNormal = vehicleApiService.getVehiclesWithFallback(
                null, null, null, null, null, null, null, null, null,
                placa, null, null, 0, 25, "id", "asc"
            );
            
            debug.put("buscaNormal", Map.of(
                "encontrados", resultadoNormal.getTotalElements(),
                "resultados", resultadoNormal.getContent().size()
            ));
            
            // Agora, busca alternativa direta
            log.info("=== TESTE DE BUSCA ALTERNATIVA ===");
            org.springframework.data.domain.Pageable pageable = 
                org.springframework.data.domain.PageRequest.of(0, 25);
            PageDTO<VehicleDTO> resultadoAlternativo = vehicleCacheService.searchByPlacaAlternative(placa, pageable);
            
            debug.put("buscaAlternativa", Map.of(
                "encontrados", resultadoAlternativo.getTotalElements(),
                "resultados", resultadoAlternativo.getContent().size()
            ));
            
            // Mostrar detalhes dos resultados encontrados
            if (!resultadoAlternativo.getContent().isEmpty()) {
                List<Map<String, Object>> detalhes = new ArrayList<>();
                for (VehicleDTO veiculo : resultadoAlternativo.getContent()) {
                    detalhes.add(Map.of(
                        "placaEncontrada", veiculo.placa(),
                        "credor", veiculo.credor(),
                        "contrato", veiculo.contrato(),
                        "modelo", veiculo.modelo()
                    ));
                }
                debug.put("detalhesEncontrados", detalhes);
            }
            
            return ResponseEntity.ok(debug);
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "erro", e.getMessage(),
                "stack", e.getClass().getSimpleName()
            ));
        }
    }

    @GetMapping("/debug/decrypt-placas")
    @Operation(summary = "Debug: descriptografar placas do banco")
    public ResponseEntity<Map<String, Object>> debugDecryptPlacas() {
        try {
            Map<String, Object> debug = new HashMap<>();
            
            // Buscar algumas placas do cache para testar descriptografia
            PageDTO<VehicleDTO> todosVeiculos = vehicleApiService.getVehiclesWithFallback(
                null, null, null, null, null, null, null, null, null,
                null, null, null, 0, 5, "id", "asc"
            );
            
            debug.put("totalVeiculosNaBase", todosVeiculos.getTotalElements());
            
            // Lista para armazenar resultados de descriptografia
            List<Map<String, Object>> placasTestadas = new ArrayList<>();
            
            for (VehicleDTO veiculo : todosVeiculos.getContent()) {
                Map<String, Object> placaTeste = new HashMap<>();
                placaTeste.put("placaDescriptografada", veiculo.placa());
                placaTeste.put("credor", veiculo.credor());
                placaTeste.put("modelo", veiculo.modelo());
                
                // Testar se conseguimos criptografar novamente e se bate
                if (veiculo.placa() != null && !"N/A".equals(veiculo.placa())) {
                    placaTeste.put("placaOriginal", veiculo.placa());
                    placaTeste.put("tamanhoPlaca", veiculo.placa().length());
                }
                
                placasTestadas.add(placaTeste);
            }
            
            debug.put("placasNoCache", placasTestadas);
            
            // Teste específico: tentar criptografar PSS0O37 e ver se bate com alguma do banco
            debug.put("testePSS0O37", Map.of(
                "placa", "PSS0O37",
                "explicacao", "Vamos comparar se PSS0O37 criptografado bate com alguma placa do banco"
            ));
            
            return ResponseEntity.ok(debug);
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "erro", e.getMessage(),
                "stack", e.getClass().getSimpleName()
            ));
        }
    }

    @GetMapping("/debug/crypto/{placa}")
    @Operation(summary = "Debug de criptografia de placa (temporário)")
    public ResponseEntity<Map<String, Object>> debugCrypto(@PathVariable String placa) {
        try {
            Map<String, Object> debug = new HashMap<>();
            debug.put("placaOriginal", placa);
            debug.put("placaNormalizada", placa.toUpperCase().trim());
            
            // Buscar informações do cache
            var cacheStatus = vehicleCacheService.getCacheStatus();
            debug.put("totalRegistrosCache", cacheStatus.getTotalRecords());
            debug.put("cacheValido", cacheStatus.isValid());
            
            // Tentar buscar a placa (isso ativará os logs de debug)
            PageDTO<VehicleDTO> resultado = vehicleApiService.getVehiclesWithFallback(
                null, null, null, null, null, null, null, null, null,
                placa, null, null, 0, 1, "id", "asc"
            );
            
            debug.put("resultadoBusca", Map.of(
                "encontrados", resultado.getTotalElements(),
                "pagina", resultado.getPage()
            ));
            
            return ResponseEntity.ok(debug);
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "erro", e.getMessage(),
                "placa", placa
            ));
        }
    }


}