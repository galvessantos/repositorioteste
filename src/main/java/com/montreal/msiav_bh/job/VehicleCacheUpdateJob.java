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
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Component
@RequiredArgsConstructor
public class VehicleCacheUpdateJob {

    private final ApiQueryService apiQueryService;
    private final VehicleInquiryMapper vehicleInquiryMapper;
    private final VehicleCacheService vehicleCacheService;

    private final ReentrantLock jobLock = new ReentrantLock();

    @Value("${vehicle.cache.update.enabled:true}")
    private boolean cacheUpdateEnabled;

    @Value("${vehicle.cache.update.days-to-fetch:30}")
    private int daysToFetch;

    @Scheduled(fixedDelayString = "${vehicle.cache.update.interval:600000}")
    public void updateVehicleCache() {
        if (!jobLock.tryLock()) {
            log.info("Job de atualização do cache já está sendo executado - pulando esta execução");
            return;
        }

        try {
            if (!cacheUpdateEnabled) {
                log.debug("Atualização automática do cache está desabilitada");
                return;
            }

            LocalDateTime startTime = LocalDateTime.now();
            log.info("==== INICIANDO JOB DE ATUALIZAÇÃO DO CACHE ====");
            log.info("Horário: {}", startTime.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")));
            log.info("Timezone: {}", java.time.ZoneId.systemDefault());

            try {
                // Tentar buscar dados de diferentes períodos até encontrar dados disponíveis
                List<ConsultaNotificationResponseDTO.NotificationData> notifications = null;
                LocalDate successfulStartDate = null;
                LocalDate successfulEndDate = null;
                
                // Lista de períodos para tentar (do mais recente para o mais antigo)
                LocalDate[][] periodsToTry = {
                    {LocalDate.of(2025, 6, 1), LocalDate.of(2025, 6, 30)}, // Junho 2025
                    {LocalDate.of(2025, 5, 1), LocalDate.of(2025, 5, 31)}, // Maio 2025
                    {LocalDate.of(2025, 4, 1), LocalDate.of(2025, 4, 30)}, // Abril 2025
                    {LocalDate.of(2025, 3, 1), LocalDate.of(2025, 3, 31)}, // Março 2025
                };
                
                for (LocalDate[] period : periodsToTry) {
                    LocalDate startDate = period[0];
                    LocalDate endDate = period[1];
                    
                    log.info("Tentando período: {} a {}", startDate, endDate);
                    
                    try {
                        notifications = apiQueryService.searchByPeriod(startDate, endDate);
                        if (notifications != null && !notifications.isEmpty()) {
                            log.info("Dados encontrados para o período: {} a {}", startDate, endDate);
                            successfulStartDate = startDate;
                            successfulEndDate = endDate;
                            break;
                        }
                    } catch (Exception e) {
                        log.warn("Erro ao buscar dados para período {} a {}: {}", startDate, endDate, e.getMessage());
                    }
                }
                
                if (notifications == null || notifications.isEmpty()) {
                    log.warn("Nenhum dado encontrado em nenhum dos períodos tentados");
                    return;
                }

                if (notifications != null && !notifications.isEmpty()) {
                    log.info("API retornou {} notificações", notifications.size());

                    List<VehicleDTO> vehicles = vehicleInquiryMapper.mapToVeiculoDTO(notifications);
                    log.info("Convertidos para {} veículos únicos", vehicles.size());

                    // Usar as datas do período que foi encontrado com sucesso
                    CacheUpdateContext context = CacheUpdateContext.scheduledRefresh(successfulStartDate, successfulEndDate);

                    vehicleCacheService.updateCacheThreadSafe(vehicles, context);

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
        } finally {
            jobLock.unlock();
        }
    }

    @Scheduled(cron = "${vehicle.cache.cleanup.cron:0 0 2 * * ?}")
    public void cleanupOldCache() {
        if (!jobLock.tryLock()) {
            log.info("Job de atualização em andamento - pulando limpeza do cache");
            return;
        }

        try {
            log.info("==== INICIANDO LIMPEZA DO CACHE ====");
            log.info("Horário: {}", LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")));

            try {
                vehicleCacheService.cleanOldCache();
                log.info("Limpeza do cache concluída com sucesso");
            } catch (Exception e) {
                log.error("Falha na limpeza do cache", e);
            }
        } finally {
            jobLock.unlock();
        }
    }

    @Scheduled(cron = "${vehicle.cache.duplicate.cleanup.cron:0 30 1 * * ?}")
    public void cleanupDuplicates() {
        if (!jobLock.tryLock()) {
            log.info("Job de atualização em andamento - pulando limpeza de duplicatas");
            return;
        }

        try {
            log.info("==== INICIANDO LIMPEZA DE DUPLICATAS ====");
            log.info("Horário: {}", LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")));

            try {
                vehicleCacheService.cleanDuplicates();
                log.info("Limpeza de duplicatas concluída com sucesso");
            } catch (Exception e) {
                log.error("Falha na limpeza de duplicatas", e);
            }
        } finally {
            jobLock.unlock();
        }
    }
}