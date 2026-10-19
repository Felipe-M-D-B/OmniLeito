package com.unesp.omnileito.ocorrencias;

import com.unesp.omnileito.comum.Filas;
import com.unesp.omnileito.comum.PausaConsumidores;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PausaFilas {

	static final List<String> IDS = List.of(
			Filas.AMBULANCIA_ALOCADA,
			Filas.LEITO_RESERVADO,
			Filas.LEITO_EXPIRADO,
			Filas.RETENTAR_BUSCA_LEITO);

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
