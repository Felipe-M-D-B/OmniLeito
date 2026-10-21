package com.unesp.omnileito.comum.mensagem;

import java.util.List;

public record ReservarLeito(
		String ocorrenciaId,
		Long ambulanciaId,
		String tipoLeito,
		List<Long> leitosRecusados,
		int tentativa
) {
}
