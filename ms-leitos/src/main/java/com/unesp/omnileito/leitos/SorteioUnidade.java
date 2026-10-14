package com.unesp.omnileito.leitos;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

@Component
public class SorteioUnidade {

	private final String resultadoFixo;

	public SorteioUnidade(@Value("${omnileito.reserva.resultado:}") String resultadoFixo) {
		this.resultadoFixo = resultadoFixo;
	}

	public ResultadoUnidade sortear() {
		if (resultadoFixo != null && !resultadoFixo.isBlank()) {
			return ResultadoUnidade.valueOf(resultadoFixo.trim());
		}
		ResultadoUnidade[] valores = ResultadoUnidade.values();
		return valores[ThreadLocalRandom.current().nextInt(valores.length)];
	}
}
