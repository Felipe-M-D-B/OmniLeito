package com.unesp.omnileito.ocorrencias;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OcorrenciaRepository extends JpaRepository<Ocorrencia, String> {

	List<Ocorrencia> findByStatusAndTipoLeitoAndLeitoIdIsNullAndAmbulanciaIdIsNotNull(StatusOcorrencia status, String tipoLeito);
}
