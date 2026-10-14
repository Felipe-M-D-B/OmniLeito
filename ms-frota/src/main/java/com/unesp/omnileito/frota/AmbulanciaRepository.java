package com.unesp.omnileito.frota;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AmbulanciaRepository extends JpaRepository<Ambulancia, Long> {

	Optional<Ambulancia> findByOcorrenciaId(String ocorrenciaId);

	List<Ambulancia> findByStatusOrderById(StatusAmbulancia status);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("update Ambulancia a set a.status = com.unesp.omnileito.frota.StatusAmbulancia.ALOCADA, a.ocorrenciaId = :ocorrenciaId where a.id = :id and a.status = com.unesp.omnileito.frota.StatusAmbulancia.DISPONIVEL")
	int alocarSeDisponivel(@Param("id") Long id, @Param("ocorrenciaId") String ocorrenciaId);
}
