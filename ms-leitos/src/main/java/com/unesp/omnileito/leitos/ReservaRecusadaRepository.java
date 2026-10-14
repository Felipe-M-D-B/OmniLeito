package com.unesp.omnileito.leitos;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReservaRecusadaRepository extends JpaRepository<ReservaRecusada, Long> {

	List<ReservaRecusada> findByOcorrenciaId(String ocorrenciaId);
}
