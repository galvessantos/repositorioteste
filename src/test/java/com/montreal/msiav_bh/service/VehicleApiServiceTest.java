package com.montreal.msiav_bh.service;

import com.montreal.msiav_bh.dto.PageDTO;
import com.montreal.msiav_bh.dto.VehicleDTO;
import com.montreal.msiav_bh.mapper.VehicleInquiryMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VehicleApiServiceTest {

    @Mock
    private ApiQueryService apiQueryService;

    @Mock
    private VehicleCacheService vehicleCacheService;

    @Mock
    private VehicleInquiryMapper vehicleInquiryMapper;

    @InjectMocks
    private VehicleApiService vehicleApiService;

    @BeforeEach
    void setUp() {
    }

    @Test
    void shouldAlwaysQueryDatabaseFirst() {
        LocalDate dataInicio = LocalDate.now().minusDays(30);
        LocalDate dataFim = LocalDate.now();

        VehicleDTO mockVehicle = new VehicleDTO(
                1L, "Credor Test", dataInicio, "123456", "ABC-1234",
                "Model X", "SP", "São Paulo", "12345678901",
                "PROT-123", "Em andamento", "Ativo", dataFim
        );

        Page<VehicleDTO> mockPage = new PageImpl<>(List.of(mockVehicle));

        when(vehicleCacheService.isCacheValid()).thenReturn(true);
        when(vehicleCacheService.getFromCache(any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(mockPage);

        PageDTO<VehicleDTO> result = vehicleApiService.getVehiclesWithFallback(
                dataInicio, dataFim, null, null, null, null, null, null,
                null, null, null, null, 0, 10, "protocolo", "asc"
        );

        assertNotNull(result);
        assertEquals(1, result.content().size());

        verify(vehicleCacheService, times(1)).getFromCache(any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any());

        verify(apiQueryService, never()).searchByPeriod(any(), any());
    }

    @Test
    void shouldTryToUpdateCacheWhenInvalid() {
        LocalDate dataInicio = LocalDate.now().minusDays(30);
        LocalDate dataFim = LocalDate.now();

        Page<VehicleDTO> mockPage = new PageImpl<>(List.of());

        when(vehicleCacheService.isCacheValid()).thenReturn(false);
        when(vehicleCacheService.getFromCache(any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(mockPage);

        PageDTO<VehicleDTO> result = vehicleApiService.getVehiclesWithFallback(
                dataInicio, dataFim, null, null, null, null, null, null,
                null, null, null, null, 0, 10, "protocolo", "asc"
        );

        assertNotNull(result);

        verify(vehicleCacheService, times(1)).getFromCache(any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any());

        verify(vehicleCacheService, times(1)).isCacheValid();
    }
}