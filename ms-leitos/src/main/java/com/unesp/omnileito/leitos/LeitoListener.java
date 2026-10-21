package com.unesp.omnileito.leitos;

import com.unesp.omnileito.comum.Filas;
import com.unesp.omnileito.comum.mensagem.ReservarLeito;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class LeitoListener {

	private final LeitoService leitos;

	public LeitoListener(LeitoService leitos) {
		this.leitos = leitos;
	}

	@RabbitListener(id = Filas.RESERVAR_LEITO, queues = Filas.RESERVAR_LEITO)
	public void reservar(ReservarLeito pedido) {
		leitos.reservar(pedido);
	}
}
