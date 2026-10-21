package com.unesp.omnileito.ocorrencias;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AberturaRequest(
		@Valid @NotNull PacienteRequest paciente,
		@NotBlank String local,
		@NotBlank String prioridade,
		@NotBlank String tipoLeito
) {
	public record PacienteRequest(@NotBlank String nome) {
	}
}
