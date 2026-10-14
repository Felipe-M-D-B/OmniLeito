package com.unesp.omnileito.notificacoes;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface NotificacaoRepository extends MongoRepository<NotificacaoDocumento, String> {

	Optional<NotificacaoDocumento> findByOcorrenciaIdAndTipoAndOcorridaEm(String ocorrenciaId, String tipo, OffsetDateTime ocorridaEm);

	List<NotificacaoDocumento> findByOcorrenciaIdOrderByOcorridaEmDesc(String ocorrenciaId);
}
