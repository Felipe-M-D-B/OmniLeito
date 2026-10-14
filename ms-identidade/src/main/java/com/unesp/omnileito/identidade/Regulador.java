package com.unesp.omnileito.identidade;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "regulador")
public class Regulador {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true, length = 80)
	private String usuario;

	@Column(name = "senha_hash", nullable = false, length = 100)
	private String senhaHash;

	@Column(name = "criado_em", nullable = false)
	private LocalDateTime criadoEm;

	public String getUsuario() {
		return usuario;
	}

	public String getSenhaHash() {
		return senhaHash;
	}

	public void setSenhaHash(String senhaHash) {
		this.senhaHash = senhaHash;
	}
}
