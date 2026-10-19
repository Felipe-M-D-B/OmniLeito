package com.unesp.omnileito.notificacoes;

import com.unesp.omnileito.comum.ApiException;
import com.unesp.omnileito.comum.mensagem.RegistrarNotificacao;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class NotificacaoService {

	private final NotificacaoRepository notificacoes;
	private final AuditoriaRepository auditorias;

	public NotificacaoService(NotificacaoRepository notificacoes, AuditoriaRepository auditorias) {
		this.notificacoes = notificacoes;
		this.auditorias = auditorias;
	}

	public void registrar(RegistrarNotificacao pedido, String origem) {
		if (pedido.ocorrenciaId() == null || pedido.tipo() == null || pedido.ocorridaEm() == null) {
			return;
		}
		if (notificacoes.findByOcorrenciaIdAndTipoAndOcorridaEm(pedido.ocorrenciaId(), pedido.tipo(), pedido.ocorridaEm()).isPresent()) {
			return;
		}
		try {
			notificacoes.save(new NotificacaoDocumento(UUID.randomUUID().toString(), pedido.ocorrenciaId(), pedido.tipo(),
					pedido.mensagem(), pedido.ocorridaEm()));
			auditorias.save(new AuditoriaDocumento(UUID.randomUUID().toString(), pedido.ocorrenciaId(), pedido.tipo(),
					pedido.mensagem(), pedido.ocorridaEm(), origem));
		} catch (DuplicateKeyException ignorado) {
			// Entrega repetida da mesma chave não cria outro alerta.
		}
	}

	public void reiniciarCarga() {
		notificacoes.deleteAll();
		auditorias.deleteAll();
	}

	public List<NotificacaoResponse> listar(String ocorrenciaId) {
		if (ocorrenciaId == null || ocorrenciaId.isBlank()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "CAMPO_INVALIDO", "Campo ausente ou valor fora da lista fechada.");
		}
		return notificacoes.findByOcorrenciaIdOrderByOcorridaEmDesc(ocorrenciaId).stream()
				.map(doc -> new NotificacaoResponse(doc.getId(), doc.getOcorrenciaId(), doc.getTipo(), doc.getMensagem(), doc.getOcorridaEm()))
				.toList();
	}

	public record NotificacaoResponse(String id, String ocorrenciaId, String tipo, String mensagem, java.time.OffsetDateTime ocorridaEm) {
	}
}
