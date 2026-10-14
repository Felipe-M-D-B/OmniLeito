package com.unesp.omnileito.comum.mensagem;

public record AmbulanciaAlocada(
		String ocorrenciaId,
		boolean sucesso,
		Long ambulanciaId,
		String identificacao,
		String motivo
) {
}
