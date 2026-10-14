package com.unesp.omnileito.leitos;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "leito")
public class Leito {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(optional = false, fetch = FetchType.EAGER)
	@JoinColumn(name = "unidade_id")
	private UnidadeSaude unidade;

	@Column(nullable = false, unique = true, length = 40)
	private String codigo;

	@Column(name = "tipo_leito", nullable = false, length = 20)
	private String tipoLeito;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private StatusLeito status;

	@Column(name = "ocorrencia_id", length = 36)
	private String ocorrenciaId;

	@Column(name = "reservado_em")
	private LocalDateTime reservadoEm;

	@Column(nullable = false)
	private boolean confirmado;

	@Column(name = "confirmado_em")
	private LocalDateTime confirmadoEm;

	protected Leito() {
	}

	public Leito(Long id, UnidadeSaude unidade, String codigo, String tipoLeito, StatusLeito status) {
		this.id = id;
		this.unidade = unidade;
		this.codigo = codigo;
		this.tipoLeito = tipoLeito;
		this.status = status;
	}

	public Long getId() { return id; }
	public UnidadeSaude getUnidade() { return unidade; }
	public String getCodigo() { return codigo; }
	public String getTipoLeito() { return tipoLeito; }
	public StatusLeito getStatus() { return status; }
	public String getOcorrenciaId() { return ocorrenciaId; }
	public LocalDateTime getReservadoEm() { return reservadoEm; }
	public boolean isConfirmado() { return confirmado; }

	public void setStatus(StatusLeito status) { this.status = status; }
	public void setOcorrenciaId(String ocorrenciaId) { this.ocorrenciaId = ocorrenciaId; }
	public void setReservadoEm(LocalDateTime reservadoEm) { this.reservadoEm = reservadoEm; }
	public void setConfirmado(boolean confirmado) { this.confirmado = confirmado; }
	public void setConfirmadoEm(LocalDateTime confirmadoEm) { this.confirmadoEm = confirmadoEm; }
}
