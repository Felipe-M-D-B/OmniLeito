package com.unesp.omnileito.repository;

import com.unesp.omnileito.domain.Ambulancia;
import com.unesp.omnileito.domain.enums.StatusAmbulancia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AmbulanciaRepository extends JpaRepository<Ambulancia, Long> {
    // Busca a primeira ambulância livre para a Saga
    Optional<Ambulancia> findFirstByStatus(StatusAmbulancia status);
}