package com.unesp.omnileito.leitos;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "reserva_recusada")
public class ReservaRecusada {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "ocorrencia_id", nullable = false, length = 36)
	private String ocorrenciaId;

	@Column(name = "leito_id", nullable = false)
	private Long leitoId;

	@Column(nullable = false, length = 30)
	private String motivo;

	@Column(name = "ocorrido_em", nullable = false)
	private LocalDateTime ocorridoEm;

	protected ReservaRecusada() {
	}

	public ReservaRecusada(String ocorrenciaId, Long leitoId, String motivo, LocalDateTime ocorridoEm) {
		this.ocorrenciaId = ocorrenciaId;
		this.leitoId = leitoId;
		this.motivo = motivo;
		this.ocorridoEm = ocorridoEm;
	}

	public Long getLeitoId() { return leitoId; }
}
