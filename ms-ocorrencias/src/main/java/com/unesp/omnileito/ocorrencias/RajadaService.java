package com.unesp.omnileito.ocorrencias;

import com.unesp.omnileito.comum.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class RajadaService {

	private final OcorrenciaSagaService saga;
	private final long pausaMs;

	public RajadaService(OcorrenciaSagaService saga, @Value("${omnileito.rajada.pausa-ms:200}") long pausaMs) {
		this.saga = saga;
		this.pausaMs = pausaMs;
	}

	public int disparar(int quantidade) {
		if (quantidade < 1 || quantidade > 100) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "CAMPO_INVALIDO", "Campo ausente ou valor fora da lista fechada.");
		}
		for (int indice = 1; indice <= quantidade; indice++) {
			OcorrenciaResponse aberta = saga.abrir(new AberturaRequest(
					new AberturaRequest.PacienteRequest("Carga Fila " + indice),
					"Rua da Fila",
					"VERDE",
					"ENFERMARIA"));
			saga.despachar(aberta.id());
			if (indice < quantidade) {
				aguardar();
			}
		}
		return quantidade;
	}

	private void aguardar() {
		if (pausaMs <= 0) {
			return;
		}
		try {
			Thread.sleep(pausaMs);
		} catch (InterruptedException interrompido) {
			Thread.currentThread().interrupt();
			throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "RAJADA_INTERROMPIDA", "A rajada foi interrompida.");
		}
	}
}
