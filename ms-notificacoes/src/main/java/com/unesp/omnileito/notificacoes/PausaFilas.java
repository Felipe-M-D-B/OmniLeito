package com.unesp.omnileito.notificacoes;

import com.unesp.omnileito.comum.Filas;
import com.unesp.omnileito.comum.PausaConsumidores;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PausaFilas {

	private static final List<String> IDS = List.of(Filas.REGISTRAR_NOTIFICACAO);

	private final RabbitListenerEndpointRegistry registry;

	public PausaFilas(RabbitListenerEndpointRegistry registry) {
		this.registry = registry;
	}

	public void pausar() {
		PausaConsumidores.pausar(registry, IDS);
	}

	public void retomar() {
		PausaConsumidores.retomar(registry, IDS);
	}
}
