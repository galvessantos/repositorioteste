package com.montreal.msiav_bh.service;

import com.montreal.msiav_bh.dto.response.QueryDetailResponseDTO;
import com.montreal.msiav_bh.entity.Contract;
import com.montreal.msiav_bh.mapper.ContractMapper;
import com.montreal.msiav_bh.repository.ContractRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class ContractPersistenceService {

    private final ContractRepository contractRepository;
    private final ContractMapper contractMapper;

    public ContractPersistenceService(ContractRepository contractRepository,
                                      ContractMapper contractMapper) {
        this.contractRepository = contractRepository;
        this.contractMapper = contractMapper;
    }

    public Contract saveContract(QueryDetailResponseDTO response) {
        if (response == null || !response.success() || response.data() == null) {
            throw new IllegalArgumentException("Resposta inválida da API");
        }

        // Verificar se o contrato já existe
        Optional<Contract> existingContract = Optional.empty();
        if (response.data().contrato() != null && response.data().contrato().numero() != null) {
            existingContract = contractRepository.findByNumero(response.data().contrato().numero());
        }

        Contract contract;
        if (existingContract.isPresent()) {
            // Atualizar contrato existente
            contract = existingContract.get();
            // Para simplicidade, vamos deletar e recriar
            contractRepository.delete(contract);
            contract = contractMapper.toEntity(response.data());
        } else {
            // Criar novo contrato
            contract = contractMapper.toEntity(response.data());
        }

        return contractRepository.save(contract);
    }

    @Transactional
    public Optional<Contract> findContractByPlaca(String placa) {
        List<Contract> contracts = contractRepository.findByVeiculosLicensePlate(placa);

        if (contracts.isEmpty()) {
            return Optional.empty();
        }

        Contract contract = contracts.get(0);

        // Forçar carregamento das coleções lazy
        contract.getDevedores().size();
        contract.getVeiculos().size();
        contract.getGarantidores().size();
        contract.getOrgaos().size();
        contract.getNotificacoes().size();

        if (contract.getCredor() != null) {
            contract.getCredor().getId();
        }
        if (contract.getServentia() != null) {
            contract.getServentia().getId();
        }
        if (contract.getDetran() != null) {
            contract.getDetran().getId();
        }

        return Optional.of(contract);
    }


    public List<Contract> findAllContractsByPlaca(String placa) {
        return contractRepository.findByVeiculosLicensePlate(placa);
    }
}