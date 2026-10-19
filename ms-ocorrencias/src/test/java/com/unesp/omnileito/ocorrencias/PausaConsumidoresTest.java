package com.unesp.omnileito.ocorrencias;

import com.unesp.omnileito.comum.ApiException;
import com.unesp.omnileito.comum.PausaConsumidores;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessageListenerContainer;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PausaConsumidoresTest {

	@Test
	void pausaParaOContainerERetomadaLigaDeNovo() {
		RabbitListenerEndpointRegistry registry = mock(RabbitListenerEndpointRegistry.class);
		MessageListenerContainer container = mock(MessageListenerContainer.class);
		when(registry.getListenerContainer("saga.alocar-ambulancia")).thenReturn(container);
		when(container.isRunning()).thenReturn(true, false, false);
		List<String> ids = List.of("saga.alocar-ambulancia");

		PausaConsumidores.pausar(registry, ids);
		PausaConsumidores.pausar(registry, ids);
		PausaConsumidores.retomar(registry, ids);

		verify(container, times(1)).stop();
		verify(container).start();
	}

	@Test
	void listenerAusenteRecusaAPausa() {
		RabbitListenerEndpointRegistry registry = mock(RabbitListenerEndpointRegistry.class);
		when(registry.getListenerContainer("ausente")).thenReturn(null);

		ApiException erro = assertThrows(ApiException.class,
				() -> PausaConsumidores.pausar(registry, List.of("ausente")));

		assertEquals("FILA_SEM_LISTENER", erro.getCodigo());
		verify(registry, never()).getListenerContainer("outra");
	}
}
