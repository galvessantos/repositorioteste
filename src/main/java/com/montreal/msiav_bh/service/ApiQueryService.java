package com.montreal.msiav_bh.service;

import com.montreal.core.utils.CryptoUtil;
import com.montreal.core.utils.PostgresCryptoUtil;
import com.montreal.msiav_bh.config.ApiQueryConfig;
import com.montreal.msiav_bh.dto.request.ConsultaAuthRequestDTO;
import com.montreal.msiav_bh.dto.response.ConsultaAuthResponseDTO;
import com.montreal.msiav_bh.dto.response.ConsultaNotificationResponseDTO;
import com.montreal.msiav_bh.dto.response.ContractWithAddressDTO;
import com.montreal.msiav_bh.dto.response.QueryDetailResponseDTO;
import com.montreal.msiav_bh.entity.*;
import com.montreal.msiav_bh.repository.ProbableAddressDebugRepository;
import com.montreal.msiav_bh.repository.QueryResultRepository;
import com.montreal.msiav_bh.repository.VehicleCacheRepository;
import com.montreal.msiav_bh.repository.VehicleDebugRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Service
public class ApiQueryService {

    private static final Logger logger = LoggerFactory.getLogger(ApiQueryService.class);

    @Autowired
    private ApiQueryConfig config;
    @Autowired
    private RestTemplate restTemplate;
    @Autowired
    private ContractPersistenceService persistenceService;
    @Autowired
    private ProbableAddressDebugRepository probableAddressDebugRepository;
    @Autowired
    private VehicleDebugRepository vehicleDebugRepository;
    @Autowired
    private QueryResultRepository queryResultRepository;
    @Autowired
    private VehicleCacheRepository vehicleCacheRepository;
    @Autowired
    private VehicleCacheCryptoService vehicleCacheCryptoService;

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

    @Transactional
    public ContractWithAddressDTO searchContract(String placa) {
        log.info("Iniciando busca de contrato para placa: {}", placa);

        QueryDetailResponseDTO response = doSearchContract(placa);
        log.debug("API externa executada com sucesso");


        List<ProbableAddressDebug> probableAddresses = probableAddressDebugRepository
                .findByLicensePlate(placa);
        log.debug("Encontrados {} endereços prováveis", probableAddresses.size());


        QueryResult queryResult = buildQueryResult(placa);

        ContractWithAddressDTO contractWithAddressDTO = ContractWithAddressDTO.builder()
                .dadosApi(response)
                .probableAddress(probableAddresses)
                .queryResult(queryResult)
                .build();

        log.info("Busca finalizada com sucesso - QueryResult: {}", queryResult != null ? "preenchido" : "null");
        return contractWithAddressDTO;
    }





    private QueryDetailResponseDTO doSearchContract(String placa) {
        String url = config.getBaseUrl() + "/api/recepcaoContrato/receber";
        String token = authenticate();

        Map<String, String> body = new HashMap<>();
        body.put("placa", placa);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(performAuthentication());
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(body, headers);

        ResponseEntity<QueryDetailResponseDTO> response =
                restTemplate.postForEntity(url, requestEntity, QueryDetailResponseDTO.class);

        return response.getBody();
}


    private QueryResult buildQueryResult(String placa) {
        log.debug("Buscando dados do vehicle_cache para placa: {}", placa);

        if (placa == null || placa.trim().isEmpty()) {
            log.warn("Placa é nula ou vazia");
            return null;
        }

        String placaNormalizada = placa.trim().toUpperCase();

        try {

            Optional<VehicleCache> directResult = vehicleCacheRepository.findByPlaca(placaNormalizada);
            if (directResult.isPresent()) {
                return buildQueryResultFromCache(directResult.get());
            }


            String placaCriptografada = vehicleCacheCryptoService.encryptPlaca(placaNormalizada);
            Optional<VehicleCache> encryptedResult = vehicleCacheRepository.findByPlaca(placaCriptografada);
            if (encryptedResult.isPresent()) {
                return buildQueryResultFromCache(encryptedResult.get());
            }


            log.warn("Nenhum registro encontrado para placa '{}'. Verificando últimos registros...", placaNormalizada);
            List<VehicleCache> recentVehicles = vehicleCacheRepository.findAll(
                    PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "id"))
            ).getContent();

            for (VehicleCache vehicle : recentVehicles) {
                try {
                    String placaDescriptografada = vehicleCacheCryptoService.decryptPlaca(vehicle.getPlaca());
                    if (placaNormalizada.equals(placaDescriptografada)) {
                        log.info("Registro encontrado por comparação manual - ID: {}", vehicle.getId());
                        return buildQueryResultFromCache(vehicle);
                    }
                } catch (Exception e) {
                    log.debug("Erro ao descriptografar placa do registro ID {}: {}", vehicle.getId(), e.getMessage());
                }
            }

            log.warn("Nenhum registro correspondente encontrado no vehicle_cache para a placa: {}", placaNormalizada);
            return null;

        } catch (Exception e) {
            log.error("Erro ao buscar dados do vehicle_cache para a placa: {}", placa, e);
            return null;
        }
    }

    private QueryResult buildQueryResultFromCache(VehicleCache vehicleCache) {
        QueryResult queryResult = new QueryResult();


        queryResult.setEtapaAtual(vehicleCache.getEtapaAtual());
        queryResult.setStatusApreensao(vehicleCache.getStatusApreensao());
        queryResult.setDataUltimaMovimentacao(vehicleCache.getUltimaMovimentacao());
        queryResult.setDataHoraApreensao(LocalDateTime.now());


        Address address = new Address();
        address.setCity(vehicleCache.getCidade());
        address.setState(vehicleCache.getUf());
        queryResult.setAddress(address);


        VehicleDebug vehicleDebug = createOrUpdateVehicleDebug(vehicleCache);
        queryResult.setVehicle(vehicleDebug);

        log.debug("QueryResult criado com sucesso - Etapa: {}, Status: {}, Veículo ID: {}",
                queryResult.getEtapaAtual(),
                queryResult.getStatusApreensao(),
                vehicleDebug.getId());

        return queryResult;
    }

    private VehicleDebug createOrUpdateVehicleDebug(VehicleCache vehicleCache) {

        Optional<VehicleDebug> existingVehicle = vehicleDebugRepository.findByLicensePlate(vehicleCache.getPlaca());

        VehicleDebug vehicleDebug;

        if (existingVehicle.isPresent()) {
            vehicleDebug = existingVehicle.get();
            log.debug("Utilizando VehicleDebug existente - ID: {}", vehicleDebug.getId());
        } else {
            vehicleDebug = new VehicleDebug();
            log.debug("Criando novo VehicleDebug");
        }


        try {
            String placaDescriptografada = vehicleCacheCryptoService.decryptPlaca(vehicleCache.getPlaca());
            vehicleDebug.setLicensePlate(placaDescriptografada);
        } catch (Exception e) {
            log.error("Erro ao descriptografar placa para VehicleDebug: {}", e.getMessage());
            vehicleDebug.setLicensePlate(vehicleCache.getPlaca());
        }

//        vehicleDebug.setModel(vehicleCache.getModelo());
//        vehicleDebug.setContractNumber(vehicleCache.getContrato());
//        vehicleDebug.setProtocol(vehicleCache.getProtocolo());
//        // Outros campos conforme necessário

        return vehicleDebugRepository.save(vehicleDebug);
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

    @Transactional
    public void addProbableAddressByPlate(String licensePlate, Address address) {
        address.setId(null); // garante novo endereço

        ProbableAddressDebug probableAddress = ProbableAddressDebug.builder()
                .licensePlate(licensePlate)
                .address(address)
                .build();

        probableAddressDebugRepository.save(probableAddress);
    }


    @Transactional
    public void updateProbableAddress(Long probableAddressId, Address newAddressData) {
        ProbableAddressDebug probableAddress = probableAddressDebugRepository.findById(probableAddressId)
                .orElseThrow(() -> new EntityNotFoundException("Endereço provável não encontrado com id: " + probableAddressId));

        Address existingAddress = probableAddress.getAddress();

        existingAddress.setPostalCode(newAddressData.getPostalCode());
        existingAddress.setStreet(newAddressData.getStreet());
        existingAddress.setNumber(newAddressData.getNumber());
        existingAddress.setNeighborhood(newAddressData.getNeighborhood());
        existingAddress.setComplement(newAddressData.getComplement());
        existingAddress.setState(newAddressData.getState());
        existingAddress.setCity(newAddressData.getCity());
        existingAddress.setNote(newAddressData.getNote());

        probableAddressDebugRepository.save(probableAddress);
    }


    @Transactional
    public void deleteProbableAddress(Long probableAddressId) {
        ProbableAddressDebug probableAddress = probableAddressDebugRepository.findById(probableAddressId)
                .orElseThrow(() -> new EntityNotFoundException("Endereço provável não encontrado com id: " + probableAddressId));

        probableAddressDebugRepository.delete(probableAddress);
    }


}