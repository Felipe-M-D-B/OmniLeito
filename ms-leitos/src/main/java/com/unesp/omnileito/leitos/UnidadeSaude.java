package com.unesp.omnileito.leitos;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "unidade_saude")
public class UnidadeSaude {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true, length = 160)
	private String nome;

	protected UnidadeSaude() {
	}

	public UnidadeSaude(Long id, String nome) {
		this.id = id;
		this.nome = nome;
	}

	public Long getId() { return id; }
	public String getNome() { return nome; }
}
