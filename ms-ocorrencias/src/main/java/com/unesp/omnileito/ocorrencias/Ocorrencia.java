package com.unesp.omnileito.ocorrencias;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "ocorrencia")
public class Ocorrencia {

	@Id
	@Column(length = 36)
	private String id;

	@ManyToOne(optional = false, fetch = FetchType.EAGER, cascade = CascadeType.PERSIST)
	@JoinColumn(name = "paciente_id")
	private Paciente paciente;

	@Column(nullable = false)
	private String local;

	@Column(nullable = false, length = 20)
	private String prioridade;

	@Column(name = "tipo_leito", nullable = false, length = 20)
	private String tipoLeito;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private StatusOcorrencia status;

	@Column(name = "ambulancia_id")
	private Long ambulanciaId;

	@Column(name = "leito_id")
	private Long leitoId;

	@Column(name = "unidade_nome", length = 160)
	private String unidadeNome;

	@Column(name = "criada_em", nullable = false)
	private LocalDateTime criadaEm;

	@Column(name = "atualizada_em", nullable = false)
	private LocalDateTime atualizadaEm;

	protected Ocorrencia() {
	}

	public Ocorrencia(String id, Paciente paciente, String local, String prioridade, String tipoLeito, LocalDateTime agora) {
		this.id = id;
		this.paciente = paciente;
		this.local = local;
		this.prioridade = prioridade;
		this.tipoLeito = tipoLeito;
		this.status = StatusOcorrencia.ABERTA;
		this.criadaEm = agora;
		this.atualizadaEm = agora;
	}

	public String getId() { return id; }
	public Paciente getPaciente() { return paciente; }
	public String getLocal() { return local; }
	public String getPrioridade() { return prioridade; }
	public String getTipoLeito() { return tipoLeito; }
	public StatusOcorrencia getStatus() { return status; }
	public Long getAmbulanciaId() { return ambulanciaId; }
	public Long getLeitoId() { return leitoId; }
	public String getUnidadeNome() { return unidadeNome; }
	public LocalDateTime getCriadaEm() { return criadaEm; }
	public LocalDateTime getAtualizadaEm() { return atualizadaEm; }

	public void setStatus(StatusOcorrencia status) { this.status = status; }
	public void setAmbulanciaId(Long ambulanciaId) { this.ambulanciaId = ambulanciaId; }
	public void setLeitoId(Long leitoId) { this.leitoId = leitoId; }
	public void setUnidadeNome(String unidadeNome) { this.unidadeNome = unidadeNome; }
	public void setAtualizadaEm(LocalDateTime atualizadaEm) { this.atualizadaEm = atualizadaEm; }
}
