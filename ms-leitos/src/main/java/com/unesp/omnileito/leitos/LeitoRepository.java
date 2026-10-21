package com.unesp.omnileito.leitos;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface LeitoRepository extends JpaRepository<Leito, Long> {

	Optional<Leito> findByOcorrenciaId(String ocorrenciaId);

	Optional<Leito> findByCodigo(String codigo);

	List<Leito> findByStatusAndTipoLeitoOrderById(StatusLeito status, String tipoLeito);

	List<Leito> findByStatusAndTipoLeitoAndIdNotInOrderById(StatusLeito status, String tipoLeito, List<Long> ids);

	List<Leito> findByStatusAndConfirmadoFalseAndReservadoEmBefore(StatusLeito status, LocalDateTime limite);

	@Query("select distinct l.tipoLeito from Leito l where l.status = com.unesp.omnileito.leitos.StatusLeito.LIVRE")
	List<String> tiposComVaga();

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("update Leito l set l.status = com.unesp.omnileito.leitos.StatusLeito.RESERVADO, l.ocorrenciaId = :ocorrenciaId, l.reservadoEm = :agora, l.confirmado = false, l.confirmadoEm = null where l.id = :id and l.status = com.unesp.omnileito.leitos.StatusLeito.LIVRE")
	int reservarSeLivre(@Param("id") Long id, @Param("ocorrenciaId") String ocorrenciaId, @Param("agora") LocalDateTime agora);
}
