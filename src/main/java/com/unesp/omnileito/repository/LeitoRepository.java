package com.unesp.omnileito.repository;

import com.unesp.omnileito.domain.Leito;
import com.unesp.omnileito.domain.enums.StatusLeito;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LeitoRepository extends JpaRepository<Leito, Long> {
    // Busca o primeiro leito livre para a Saga
    Optional<Leito> findFirstByStatus(StatusLeito status);
}