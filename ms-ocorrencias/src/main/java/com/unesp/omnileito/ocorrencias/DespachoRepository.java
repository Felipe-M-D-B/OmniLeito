package com.unesp.omnileito.ocorrencias;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DespachoRepository extends JpaRepository<Despacho, Long> {

	Optional<Despacho> findByOcorrenciaId(String ocorrenciaId);
}
