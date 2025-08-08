package com.montreal.msiav_bh.service;

import com.montreal.msiav_bh.config.ApiQueryConfig;
import com.montreal.msiav_bh.dto.request.ConsultaAuthRequestDTO;
import com.montreal.msiav_bh.dto.request.ConsultaSearchRequestDTO;
import com.montreal.msiav_bh.dto.response.ConsultaAuthResponseDTO;
import com.montreal.msiav_bh.dto.response.ConsultaNotificationResponseDTO;
import com.montreal.msiav_bh.dto.response.QueryDetailResponseDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class ApiQueryService {

    private static final Logger logger = LoggerFactory.getLogger(ApiQueryService.class);

    @Autowired
    private ApiQueryConfig config;
    @Autowired
    private RestTemplate restTemplate;

    private String currentToken;
    private LocalDateTime tokenExpiration;
    private final ReentrantLock tokenLock = new ReentrantLock();

    private volatile int authRetryCount = 0;
    private volatile LocalDateTime lastAuthAttempt = null;
    private static final int MAX_AUTH_RETRIES = 3;
    private static final int AUTH_RETRY_DELAY_MINUTES = 5;

    public String authenticate() {
        return authenticateWithRetry(false);
    }

    private String authenticateWithRetry(boolean forceRefresh) {
        tokenLock.lock();
        try {
            if (!forceRefresh && isTokenValid()) {
                return currentToken;
            }

            if (shouldSkipAuthDueToRecentFailure()) {
                throw new RuntimeException("Muitas tentativas de autenticação falharam recentemente. Aguarde " +
                        AUTH_RETRY_DELAY_MINUTES + " minutos.");
            }

            return performAuthentication();

        } finally {
            tokenLock.unlock();
        }
    }

    private boolean isTokenValid() {
        if (currentToken == null || tokenExpiration == null) {
            return false;
        }

        LocalDateTime safeExpiration = tokenExpiration.minusMinutes(5);
        boolean isValid = LocalDateTime.now().isBefore(safeExpiration);

        if (isValid) {
            long minutesUntilExpiration = java.time.Duration.between(
                    LocalDateTime.now(), tokenExpiration
            ).toMinutes();
            logger.debug("Token válido por mais {} minutos", minutesUntilExpiration);
        } else {
            logger.info("Token expirado ou próximo da expiração");
        }

        return isValid;
    }

    private boolean shouldSkipAuthDueToRecentFailure() {
        if (lastAuthAttempt == null) {
            return false;
        }

        long minutesSinceLastAttempt = java.time.Duration.between(
                lastAuthAttempt, LocalDateTime.now()
        ).toMinutes();

        if (authRetryCount >= MAX_AUTH_RETRIES && minutesSinceLastAttempt < AUTH_RETRY_DELAY_MINUTES) {
            logger.warn("Muitas tentativas de auth falharam. Último: {} min atrás, tentativas: {}",
                    minutesSinceLastAttempt, authRetryCount);
            return true;
        }

        if (minutesSinceLastAttempt >= AUTH_RETRY_DELAY_MINUTES) {
            authRetryCount = 0;
        }

        return false;
    }

    private String performAuthentication() {
        lastAuthAttempt = LocalDateTime.now();

        try {
            String url = config.getBaseUrl() + "/api/sanctum/token";
            ConsultaAuthRequestDTO request = new ConsultaAuthRequestDTO(
                    config.getUsername(),
                    config.getPassword()
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            logger.info("Tentando autenticação na API Montreal - tentativa {}", authRetryCount + 1);

            ResponseEntity<ConsultaAuthResponseDTO> response = restTemplate.postForEntity(
                    url,
                    new HttpEntity<>(request, headers),
                    ConsultaAuthResponseDTO.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                currentToken = response.getBody().data().token();

                int tokenValiditySeconds = (int) Math.max(config.getTokenRefreshInterval() / 1000 - 300, 3600);
                tokenExpiration = LocalDateTime.now().plusSeconds(tokenValiditySeconds);

                authRetryCount = 0;

                logger.info("Autenticação bem-sucedida. Token válido até: {}", tokenExpiration);
                return currentToken;
            }

            throw new RuntimeException("Autenticação falhou: " + response.getStatusCode());

        } catch (Exception e) {
            authRetryCount++;
            logger.error("Falha na autenticação (tentativa {}): {}", authRetryCount, e.getMessage());

            currentToken = null;
            tokenExpiration = null;

            throw new RuntimeException("Erro na autenticação: " + e.getMessage(), e);
        }
    }

    public List<ConsultaNotificationResponseDTO.NotificationData> searchByPeriod(LocalDate startDate, LocalDate endDate) {
        return executeWithTokenRetry(() -> doSearchByPeriod(startDate, endDate));
    }

    private <T> T executeWithTokenRetry(java.util.function.Supplier<T> operation) {
        try {
            return operation.get();
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                logger.warn("Token inválido detectado durante operação - tentando refresh");
                try {
                    authenticateWithRetry(true);
                    return operation.get();
                } catch (Exception retryException) {
                    logger.error("Falha no retry após refresh do token: {}", retryException.getMessage());
                    throw retryException;
                }
            }
            throw e;
        }
    }

    private List<ConsultaNotificationResponseDTO.NotificationData> doSearchByPeriod(LocalDate startDate, LocalDate endDate) {
        String token = authenticate();
        String url = config.getBaseUrl() + "/api/recepcaoContrato/periodo/"
                + startDate.toString() + "/" + endDate.toString();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        logger.debug("Fazendo requisição para URL: {}", url);

        try {
            ResponseEntity<ConsultaNotificationResponseDTO> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    ConsultaNotificationResponseDTO.class
            );

            if (response.getStatusCode() == HttpStatus.OK &&
                    response.getBody() != null &&
                    response.getBody().success()) {
                List<ConsultaNotificationResponseDTO.NotificationData> data = response.getBody().data();
                logger.info("API retornou {} registros para o período {} a {}",
                        data != null ? data.size() : 0, startDate, endDate);
                return data;
            }

            logger.warn("Resposta inesperada da API: Status={}, Body={}",
                    response.getStatusCode(), response.getBody());

        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                logger.info("Período não encontrado na API: {} a {} - Erro: {}",
                        startDate, endDate, e.getResponseBodyAsString());
            } else if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                throw e;
            } else {
                logger.error("Erro na requisição: {} - URL: {} - Resposta: {}",
                        e.getStatusCode(), url, e.getResponseBodyAsString());
            }
        } catch (Exception e) {
            logger.error("Erro inesperado ao consultar período {} a {}: ", startDate, endDate, e);
        }

        return List.of();
    }

    public QueryDetailResponseDTO searchContract(String contractNumber) {
        return executeWithTokenRetry(() -> doSearchContract(contractNumber));
    }

    private QueryDetailResponseDTO doSearchContract(String contractNumber) {
        String url = config.getBaseUrl() + "/api/recepcaoContrato/receber";
        String token = authenticate();

        Map<String, String> body = new HashMap<>();
        body.put("nu_contrato", contractNumber);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(body, headers);

        ResponseEntity<QueryDetailResponseDTO> response =
                restTemplate.postForEntity(url, requestEntity, QueryDetailResponseDTO.class);

        return response.getBody();
    }

    public List<ConsultaNotificationResponseDTO.NotificationData> searchCancelledByPeriod(LocalDate startDate, LocalDate endDate) {
        return executeWithTokenRetry(() -> doSearchCancelledByPeriod(startDate, endDate));
    }

    private List<ConsultaNotificationResponseDTO.NotificationData> doSearchCancelledByPeriod(LocalDate startDate, LocalDate endDate) {
        String token = authenticate();
        String url = config.getBaseUrl() + "/api/recepcaoContrato/cancelados/periodo/" + startDate + "/" + endDate;

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<ConsultaNotificationResponseDTO> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, ConsultaNotificationResponseDTO.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null && response.getBody().success()) {
                return response.getBody().data();
            }
            return List.of();
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                throw e;
            }
            logger.error("Error searching cancelled", e);
            throw new RuntimeException("Search cancelled failed: " + e.getMessage());
        }
    }

    public String testConnection() {
        try {
            String testUrl = config.getBaseUrl();
            ResponseEntity<String> pingResponse = restTemplate.getForEntity(testUrl, String.class);

            if (!pingResponse.getStatusCode().is2xxSuccessful()) {
                return "Falha ao acessar URL base: " + pingResponse.getStatusCode();
            }

            try {
                String token = authenticateWithRetry(true);
                return "Conexão OK! Token obtido com sucesso. Válido até: " + tokenExpiration;
            } catch (Exception authEx) {
                return "Falha na autenticação: " + authEx.getMessage();
            }

        } catch (Exception e) {
            return "Falha na conexão:\nURL: " + config.getBaseUrl()
                    + "\nErro: " + e.getClass().getSimpleName() + ": " + e.getMessage();
        }
    }

    public String getTokenStatus() {
        if (currentToken == null) {
            return "Nenhum token ativo";
        }

        if (tokenExpiration == null) {
            return "Token ativo, mas sem data de expiração";
        }

        long minutesUntilExpiration = java.time.Duration.between(
                LocalDateTime.now(), tokenExpiration
        ).toMinutes();

        if (minutesUntilExpiration < 0) {
            return "Token expirado há " + Math.abs(minutesUntilExpiration) + " minutos";
        } else {
            return "Token válido por mais " + minutesUntilExpiration + " minutos";
        }
    }
}