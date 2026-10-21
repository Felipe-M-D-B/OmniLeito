package com.unesp.omnileito.ocorrencias;

import java.time.OffsetDateTime;

public record OcorrenciaResponse(
		String id,
		String status,
		PacienteResponse paciente,
		String local,
		String prioridade,
		String tipoLeito,
		Long ambulanciaId,
		Long leitoId,
		String unidadeNome,
		OffsetDateTime criadaEm
) {
}
