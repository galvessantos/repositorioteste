package com.montreal.msiav_bh.job;

import com.montreal.msiav_bh.context.CacheUpdateContext;
import com.montreal.msiav_bh.dto.VehicleDTO;
import com.montreal.msiav_bh.dto.response.ConsultaNotificationResponseDTO;
import com.montreal.msiav_bh.mapper.VehicleInquiryMapper;
import com.montreal.msiav_bh.service.ApiQueryService;
import com.montreal.msiav_bh.service.VehicleCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class VehicleCacheUpdateJob {

    private final ApiQueryService apiQueryService;
    private final VehicleInquiryMapper vehicleInquiryMapper;
    private final VehicleCacheService vehicleCacheService;

    @Value("${vehicle.cache.update.enabled:true}")
    private boolean cacheUpdateEnabled;

    @Value("${vehicle.cache.update.days-to-fetch:30}")
    private int daysToFetch;

    @Scheduled(fixedDelayString = "${vehicle.cache.update.interval:600000}")
    public void updateVehicleCache() {
        if (!cacheUpdateEnabled) {
            log.debug("Atualização automática do cache está desabilitada");
            return;
        }

        LocalDateTime startTime = LocalDateTime.now();
        log.info("==== INICIANDO JOB DE ATUALIZAÇÃO DO CACHE ====");
        log.info("Horário: {}", startTime.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")));

        try {
            LocalDate endDate = LocalDate.now();
            LocalDate startDate = endDate.minusDays(daysToFetch);

            log.info("Período de busca: {} a {}", startDate, endDate);
            log.info("Consultando API externa...");

            List<ConsultaNotificationResponseDTO.NotificationData> notifications =
                    apiQueryService.searchByPeriod(startDate, endDate);

            if (notifications != null && !notifications.isEmpty()) {
                log.info("API retornou {} notificações", notifications.size());

                List<VehicleDTO> vehicles = vehicleInquiryMapper.mapToVeiculoDTO(notifications);
                log.info("Convertidos para {} veículos únicos", vehicles.size());

                CacheUpdateContext context = CacheUpdateContext.filteredSearch(startDate, endDate);
                vehicleCacheService.updateCache(vehicles, context);

                long duration = java.time.Duration.between(startTime, LocalDateTime.now()).toSeconds();
                log.info("==== JOB CONCLUÍDO COM SUCESSO ====");
                log.info("Tempo de execução: {} segundos", duration);
                log.info("Total de veículos atualizados: {}", vehicles.size());
            } else {
                log.warn("API retornou lista vazia - nenhuma atualização realizada");
            }

        } catch (Exception e) {
            log.error("==== FALHA NO JOB DE ATUALIZAÇÃO ====", e);

            if (e.getMessage() != null && e.getMessage().contains("criptografia")) {
                log.error("ERRO CRÍTICO DE CRIPTOGRAFIA - Job abortado para evitar dados inconsistentes!");
                log.error("Verifique a conectividade com PostgreSQL e as funções de criptografia");
                return;
            }

            log.error("Os dados continuarão sendo servidos do PostgreSQL (podem estar desatualizados)");
        }
    }

    @Scheduled(cron = "${vehicle.cache.cleanup.cron:0 0 2 * * ?}")
    public void cleanupOldCache() {
        log.info("==== INICIANDO LIMPEZA DO CACHE ====");
        log.info("Horário: {}", LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")));

        try {
            vehicleCacheService.cleanOldCache();
            log.info("Limpeza do cache concluída com sucesso");
        } catch (Exception e) {
            log.error("Falha na limpeza do cache", e);
        }
    }
}