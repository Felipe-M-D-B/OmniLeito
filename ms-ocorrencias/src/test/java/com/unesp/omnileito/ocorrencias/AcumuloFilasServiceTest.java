package com.unesp.omnileito.ocorrencias;

import com.unesp.omnileito.comum.ApiException;
import com.unesp.omnileito.comum.Filas;
import com.unesp.omnileito.comum.Publicador;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class AcumuloFilasServiceTest {

	@Test
	void publicaDuasVezesEmCadaFila() {
		Publicador publicador = mock(Publicador.class);
		AcumuloFilasService servico = new AcumuloFilasService(publicador);

		servico.acumular(2);

		verify(publicador, times(2)).saga(eq(Filas.ALOCAR_AMBULANCIA), any());
		verify(publicador, times(2)).saga(eq(Filas.AMBULANCIA_ALOCADA), any());
		verify(publicador, times(2)).saga(eq(Filas.RESERVAR_LEITO), any());
		verify(publicador, times(2)).saga(eq(Filas.LEITO_RESERVADO), any());
		verify(publicador, times(2)).saga(eq(Filas.LEITO_EXPIRADO), any());
		verify(publicador, times(2)).saga(eq(Filas.ATUALIZAR_AMBULANCIA), any());
		verify(publicador, times(2)).saga(eq(Filas.RETENTAR_BUSCA_LEITO), any());
		verify(publicador, times(2)).notificacao(any());
	}

	@Test
	void quantidadeForaDaFaixaNaoPublica() {
		Publicador publicador = mock(Publicador.class);
		AcumuloFilasService servico = new AcumuloFilasService(publicador);

		assertEquals("CAMPO_INVALIDO", assertThrows(ApiException.class, () -> servico.acumular(0)).getCodigo());
		assertEquals("CAMPO_INVALIDO", assertThrows(ApiException.class, () -> servico.acumular(201)).getCodigo());
		verify(publicador, never()).saga(any(), any());
	}
}
