package com.unesp.omnileito.leitos;

import com.unesp.omnileito.comum.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class SorteioUnidade {

	private final AtomicReference<String> resultadoFixo;

	public SorteioUnidade(@Value("${omnileito.reserva.resultado:}") String resultadoInicial) {
		this.resultadoFixo = new AtomicReference<>(normalizar(resultadoInicial));
	}

	public ResultadoUnidade sortear() {
		String fixo = resultadoFixo.get();
		if (!fixo.isBlank()) {
			return ResultadoUnidade.valueOf(fixo);
		}
		ResultadoUnidade[] valores = ResultadoUnidade.values();
		return valores[ThreadLocalRandom.current().nextInt(valores.length)];
	}

	public String definir(String resultado) {
		String normalizado = normalizar(resultado);
		if (!normalizado.isBlank()) {
			try {
				ResultadoUnidade.valueOf(normalizado);
			} catch (IllegalArgumentException erro) {
				throw new ApiException(HttpStatus.BAD_REQUEST, "CAMPO_INVALIDO", "Campo ausente ou valor fora da lista fechada.");
			}
		}
		resultadoFixo.set(normalizado);
		return modo();
	}

	public String modo() {
		return resultadoFixo.get();
	}

	private static String normalizar(String resultado) {
		return resultado == null ? "" : resultado.trim().toUpperCase();
	}
}
