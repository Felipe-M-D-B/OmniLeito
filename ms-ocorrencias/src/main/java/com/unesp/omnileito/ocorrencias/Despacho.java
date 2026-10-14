package com.unesp.omnileito.ocorrencias;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "despacho")
public class Despacho {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "ocorrencia_id", nullable = false, length = 36)
	private String ocorrenciaId;

	@Column(nullable = false)
	private int tentativa;

	@Column(name = "atualizado_em", nullable = false)
	private LocalDateTime atualizadoEm;

	protected Despacho() {
	}

	public Despacho(String ocorrenciaId, LocalDateTime agora) {
		this.ocorrenciaId = ocorrenciaId;
		this.tentativa = 1;
		this.atualizadoEm = agora;
	}

	public String getOcorrenciaId() { return ocorrenciaId; }
	public int getTentativa() { return tentativa; }
	public LocalDateTime getAtualizadoEm() { return atualizadoEm; }

	public void setTentativa(int tentativa) { this.tentativa = tentativa; }
	public void setAtualizadoEm(LocalDateTime atualizadoEm) { this.atualizadoEm = atualizadoEm; }
}
