package com.unesp.omnileito.notificacoes;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.OffsetDateTime;

@Document("notificacoes")
@CompoundIndex(name = "uk_notificacao", def = "{'ocorrenciaId': 1, 'tipo': 1, 'ocorridaEm': 1}", unique = true)
public class NotificacaoDocumento {

	@Id
	private String id;
	private String ocorrenciaId;
	private String tipo;
	private String mensagem;
	private OffsetDateTime ocorridaEm;

	public NotificacaoDocumento(String id, String ocorrenciaId, String tipo, String mensagem, OffsetDateTime ocorridaEm) {
		this.id = id;
		this.ocorrenciaId = ocorrenciaId;
		this.tipo = tipo;
		this.mensagem = mensagem;
		this.ocorridaEm = ocorridaEm;
	}

	public String getId() { return id; }
	public String getOcorrenciaId() { return ocorrenciaId; }
	public String getTipo() { return tipo; }
	public String getMensagem() { return mensagem; }
	public OffsetDateTime getOcorridaEm() { return ocorridaEm; }
}
