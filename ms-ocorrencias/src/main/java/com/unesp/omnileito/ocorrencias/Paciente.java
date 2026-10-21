package com.unesp.omnileito.ocorrencias;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "paciente")
public class Paciente {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 160)
	private String nome;

	protected Paciente() {
	}

	public Paciente(String nome) {
		this.nome = nome;
	}

	public String getNome() {
		return nome;
	}
}
