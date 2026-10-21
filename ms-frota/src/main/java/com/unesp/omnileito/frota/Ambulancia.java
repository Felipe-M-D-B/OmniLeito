package com.unesp.omnileito.frota;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "ambulancia")
public class Ambulancia {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true, length = 20)
	private String identificacao;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private StatusAmbulancia status;

	@Column(name = "ocorrencia_id", length = 36)
	private String ocorrenciaId;

	public Long getId() { return id; }
	public String getIdentificacao() { return identificacao; }
	public StatusAmbulancia getStatus() { return status; }
	public String getOcorrenciaId() { return ocorrenciaId; }

	public void setStatus(StatusAmbulancia status) { this.status = status; }
	public void setOcorrenciaId(String ocorrenciaId) { this.ocorrenciaId = ocorrenciaId; }
}
