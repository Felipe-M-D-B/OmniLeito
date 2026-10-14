package com.unesp.omnileito.ocorrencias;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LeitoExcluidoRepository extends JpaRepository<LeitoExcluido, Long> {

	List<LeitoExcluido> findByOcorrenciaId(String ocorrenciaId);

	Optional<LeitoExcluido> findByOcorrenciaIdAndLeitoId(String ocorrenciaId, Long leitoId);
}
