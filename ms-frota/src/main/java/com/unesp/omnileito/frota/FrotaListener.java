package com.unesp.omnileito.frota;

import com.unesp.omnileito.comum.Filas;
import com.unesp.omnileito.comum.mensagem.AlocarAmbulancia;
import com.unesp.omnileito.comum.mensagem.AtualizarAmbulancia;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class FrotaListener {

	private final FrotaService frota;

	public FrotaListener(FrotaService frota) {
		this.frota = frota;
	}

	@RabbitListener(queues = Filas.ALOCAR_AMBULANCIA)
	public void alocar(AlocarAmbulancia pedido) {
		frota.alocar(pedido);
	}

	@RabbitListener(queues = Filas.ATUALIZAR_AMBULANCIA)
	public void atualizar(AtualizarAmbulancia pedido) {
		frota.atualizar(pedido);
	}
}
