package com.montreal.msiav_bh.repository;

import com.montreal.msiav_bh.entity.DebtorDebug;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DebtorDebugRepository extends JpaRepository<DebtorDebug, Long> {
    Optional<DebtorDebug> findByCpfCnpj(String cpfCnpj);

}
