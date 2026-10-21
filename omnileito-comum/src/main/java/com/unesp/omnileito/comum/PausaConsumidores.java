package com.unesp.omnileito.comum;

import org.springframework.amqp.core.MessageListenerContainer;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.http.HttpStatus;

import java.util.List;

public final class PausaConsumidores {

	private PausaConsumidores() {
	}

	public static void pausar(RabbitListenerEndpointRegistry registry, List<String> ids) {
		for (String id : ids) {
			MessageListenerContainer container = exigir(registry, id);
			if (container.isRunning()) {
				container.stop();
			}
		}
	}

	public static void retomar(RabbitListenerEndpointRegistry registry, List<String> ids) {
		for (String id : ids) {
			MessageListenerContainer container = exigir(registry, id);
			if (!container.isRunning()) {
				container.start();
			}
		}
	}

	private static MessageListenerContainer exigir(RabbitListenerEndpointRegistry registry, String id) {
		MessageListenerContainer container = registry.getListenerContainer(id);
		if (container == null) {
			throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "FILA_SEM_LISTENER", "Listener não encontrado.");
		}
		return container;
	}
}
