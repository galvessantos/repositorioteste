package com.montreal.msiav_bh.controller;

import com.montreal.msiav_bh.service.VehicleCacheService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin/vehicle-cache")
@RequiredArgsConstructor
@Tag(name = "Vehicle Cache Admin", description = "Endpoints administrativos para gerenciar o cache de veículos")
@PreAuthorize("hasRole('ADMIN')")
public class VehicleCacheAdminController {

    private final VehicleCacheService vehicleCacheService;

    @PostMapping("/create-unique-indexes")
    @Operation(summary = "Criar índices únicos para dados existentes",
            description = "Cria índices únicos baseados em hash para todos os veículos existentes no cache")
    @ApiResponse(responseCode = "200", description = "Índices criados com sucesso")
    public ResponseEntity<Map<String, String>> createUniqueIndexes() {
        log.info("Requisição para criar índices únicos recebida");
        
        try {
            vehicleCacheService.createUniqueIndexesForExistingData();
            
            Map<String, String> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "Índices únicos criados com sucesso. Verifique os logs para detalhes.");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Erro ao criar índices únicos", e);
            
            Map<String, String> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Erro ao criar índices únicos: " + e.getMessage());
            
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @DeleteMapping("/remove-duplicates")
    @Operation(summary = "Remover veículos duplicados",
            description = "Remove veículos duplicados do cache, mantendo apenas o registro mais recente")
    @ApiResponse(responseCode = "200", description = "Duplicatas removidas com sucesso")
    public ResponseEntity<Map<String, String>> removeDuplicates() {
        log.info("Requisição para remover duplicatas recebida");
        
        try {
            vehicleCacheService.removeDuplicateVehicles();
            
            Map<String, String> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "Duplicatas removidas com sucesso. Verifique os logs para detalhes.");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Erro ao remover duplicatas", e);
            
            Map<String, String> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Erro ao remover duplicatas: " + e.getMessage());
            
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @PostMapping("/cleanup-and-reindex")
    @Operation(summary = "Limpar duplicatas e recriar índices",
            description = "Remove duplicatas e depois cria índices únicos para todos os registros restantes")
    @ApiResponse(responseCode = "200", description = "Limpeza e reindexação concluídas")
    public ResponseEntity<Map<String, String>> cleanupAndReindex() {
        log.info("Requisição para limpeza e reindexação recebida");
        
        try {
            // Primeiro remove duplicatas
            vehicleCacheService.removeDuplicateVehicles();
            
            // Depois cria índices únicos
            vehicleCacheService.createUniqueIndexesForExistingData();
            
            Map<String, String> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "Limpeza e reindexação concluídas com sucesso. Verifique os logs para detalhes.");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Erro durante limpeza e reindexação", e);
            
            Map<String, String> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Erro durante limpeza e reindexação: " + e.getMessage());
            
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @GetMapping("/status")
    @Operation(summary = "Obter status do cache",
            description = "Retorna informações sobre o status atual do cache de veículos")
    @ApiResponse(responseCode = "200", description = "Status retornado com sucesso")
    public ResponseEntity<VehicleCacheService.CacheStatus> getCacheStatus() {
        VehicleCacheService.CacheStatus status = vehicleCacheService.getCacheStatus();
        return ResponseEntity.ok(status);
    }
}