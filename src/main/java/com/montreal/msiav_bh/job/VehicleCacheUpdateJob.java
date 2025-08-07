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
import java.time.temporal.ChronoUnit;


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

    @Value("${vehicle.cache.update.fallback-days:60}")
    private int fallbackDaysToFetch;

    @Value("${vehicle.cache.update.max-historical-days:180}")
    private int maxHistoricalDays;

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

            try {
                List<ConsultaNotificationResponseDTO.NotificationData> notifications =
                        tryFetchWithDateRange(daysToFetch, "período principal");

                if ((notifications == null || notifications.isEmpty()) && fallbackDaysToFetch > daysToFetch) {
                    log.info("Tentando período de fallback de {} dias", fallbackDaysToFetch);
                    notifications = tryFetchWithDateRange(fallbackDaysToFetch, "período de fallback");
                }

                if (notifications == null || notifications.isEmpty()) {
                    notifications = tryHistoricalPeriods();
                }

                if (!notifications.isEmpty()) {
                    log.info("API retornou {} notificações", notifications.size());

                    List<VehicleDTO> vehicles = vehicleInquiryMapper.mapToVeiculoDTO(notifications);
                    log.info("Convertidos para {} veículos únicos", vehicles.size());

                    LocalDate endDate = LocalDate.now();
                    LocalDate startDate = endDate.minusDays(daysToFetch);
                    CacheUpdateContext context = CacheUpdateContext.scheduledRefresh(startDate, endDate);

                    log.info("🔄 Iniciando sincronização com PostgreSQL...");
                    vehicleCacheService.updateCacheThreadSafe(vehicles, context);

                    long duration = java.time.Duration.between(startTime, LocalDateTime.now()).toSeconds();
                    log.info("==== JOB CONCLUÍDO COM SUCESSO ====");
                    log.info("⏱️ Tempo de execução: {} segundos", duration);
                    log.info("📊 Total de veículos processados: {}", vehicles.size());
                    log.info("✅ Próxima execução em 10 minutos");
                } else {
                    log.warn("==== NENHUM DADO ENCONTRADO EM TODOS OS PERÍODOS TENTADOS ====");
                    log.warn("⚠️ Verifique se há dados disponíveis na API externa ou se as datas estão corretas");
                    log.warn("🔧 Cache atual será mantido até próxima sincronização bem-sucedida");
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

    private List<ConsultaNotificationResponseDTO.NotificationData> tryFetchWithDateRange(int days, String periodName) {
        try {
            LocalDate endDate = LocalDate.now();
            LocalDate startDate = endDate.minusDays(days);

            log.info("Tentando {} - Período de busca: {} a {}", periodName, startDate, endDate);
            log.info("Consultando API externa...");

            return apiQueryService.searchByPeriod(startDate, endDate);
        } catch (Exception e) {
            log.warn("Erro ao buscar dados para {}: {}", periodName, e.getMessage());
            return List.of();
        }
    }

    private List<ConsultaNotificationResponseDTO.NotificationData> tryHistoricalPeriods() {
        log.info("Tentando períodos históricos...");

        LocalDate currentDate = LocalDate.now();

        for (int monthsBack = 1; monthsBack <= 6; monthsBack++) {
            try {
                LocalDate endDate = currentDate.minusMonths(monthsBack);
                LocalDate startDate = endDate.minusDays(30);

                if (ChronoUnit.DAYS.between(startDate, currentDate) > maxHistoricalDays)
                {
                    break;
                }

                log.info("Tentando período histórico: {} a {} ({} meses atrás)",
                        startDate, endDate, monthsBack);

                List<ConsultaNotificationResponseDTO.NotificationData> notifications =
                        apiQueryService.searchByPeriod(startDate, endDate);

                if (notifications != null && !notifications.isEmpty()) {
                    log.info("Encontrados dados no período histórico de {} meses atrás", monthsBack);
                    return notifications;
                }
            } catch (Exception e) {
                log.debug("Erro ao buscar período histórico de {} meses: {}", monthsBack, e.getMessage());
            }
        }

        log.warn("Nenhum dado encontrado em períodos históricos");
        return List.of();
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