package com.unesp.omnileito.notificacoes;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.OffsetDateTime;

@Document("auditoria")
@CompoundIndex(name = "uk_auditoria", def = "{'ocorrenciaId': 1, 'tipo': 1, 'ocorridaEm': 1, 'origem': 1}", unique = true)
public class AuditoriaDocumento {

	@Id
	private String id;
	private String ocorrenciaId;
	private String tipo;
	private String mensagem;
	private OffsetDateTime ocorridaEm;
	private String origem;

	public AuditoriaDocumento(String id, String ocorrenciaId, String tipo, String mensagem, OffsetDateTime ocorridaEm, String origem) {
		this.id = id;
		this.ocorrenciaId = ocorrenciaId;
		this.tipo = tipo;
		this.mensagem = mensagem;
		this.ocorridaEm = ocorridaEm;
		this.origem = origem;
	}

	public String getId() { return id; }
	public String getOcorrenciaId() { return ocorrenciaId; }
	public String getTipo() { return tipo; }
	public String getMensagem() { return mensagem; }
	public OffsetDateTime getOcorridaEm() { return ocorridaEm; }
	public String getOrigem() { return origem; }
}
