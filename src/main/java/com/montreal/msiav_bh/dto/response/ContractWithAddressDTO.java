package com.montreal.msiav_bh.dto.response;

import com.montreal.msiav_bh.dto.QueryResultDTO;
import com.montreal.msiav_bh.dto.response.QueryDetailResponseDTO;
import com.montreal.msiav_bh.entity.ProbableAddressDebug;
import com.montreal.msiav_bh.entity.QueryResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ContractWithAddressDTO {
    private QueryDetailResponseDTO dadosApi;
    private List<ProbableAddressDebug> probableAddress;
    private QueryResult queryResult;
}
