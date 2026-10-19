package com.unesp.omnileito.leitos;

import com.unesp.omnileito.comum.Filas;
import com.unesp.omnileito.comum.PausaConsumidores;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PausaFilas {

	private static final List<String> IDS = List.of(Filas.RESERVAR_LEITO);

	private final RabbitListenerEndpointRegistry registry;
	private final VarreduraLeitos varredura;

	public PausaFilas(RabbitListenerEndpointRegistry registry, VarreduraLeitos varredura) {
		this.registry = registry;
		this.varredura = varredura;
	}

	public void pausar() {
		varredura.pausar();
		PausaConsumidores.pausar(registry, IDS);
	}

	public void retomar() {
		PausaConsumidores.retomar(registry, IDS);
		varredura.retomar();
	}
}
