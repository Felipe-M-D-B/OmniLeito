package com.unesp.omnileito.ocorrencias;

import com.unesp.omnileito.comum.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RajadaServiceTest {

	private OcorrenciaSagaService saga;
	private RajadaService servico;

	@BeforeEach
	void preparar() {
		saga = mock(OcorrenciaSagaService.class);
		servico = new RajadaService(saga, 0);
	}

	@Test
	void abreEDespachaCadaOcorrencia() {
		when(saga.abrir(any())).thenReturn(resposta("oc-1"), resposta("oc-2"));

		assertEquals(2, servico.disparar(2));

		verify(saga, times(2)).abrir(any());
		verify(saga).despachar("oc-1");
		verify(saga).despachar("oc-2");
	}

	@Test
	void quantidadeForaDaFaixaNaoAbre() {
		assertEquals("CAMPO_INVALIDO", assertThrows(ApiException.class, () -> servico.disparar(0)).getCodigo());
		assertEquals("CAMPO_INVALIDO", assertThrows(ApiException.class, () -> servico.disparar(101)).getCodigo());
		verify(saga, never()).abrir(any());
	}

	private static OcorrenciaResponse resposta(String id) {
		return new OcorrenciaResponse(id, "ABERTA", new PacienteResponse("Carga Fila"), "Rua da Fila", "VERDE",
				"ENFERMARIA", null, null, null, OffsetDateTime.parse("2026-10-18T03:00:00Z"));
	}
}
