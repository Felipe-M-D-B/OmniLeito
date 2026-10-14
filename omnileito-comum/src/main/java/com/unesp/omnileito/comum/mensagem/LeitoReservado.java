package com.unesp.omnileito.comum.mensagem;

public record LeitoReservado(
		String ocorrenciaId,
		Long ambulanciaId,
		int tentativa,
		boolean sucesso,
		Long leitoId,
		String codigo,
		String tipoLeito,
		Long unidadeId,
		String unidadeNome,
		String resultadoUnidade,
		String motivo
) {
}
