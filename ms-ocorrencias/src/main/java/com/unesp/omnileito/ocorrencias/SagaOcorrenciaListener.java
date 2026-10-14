package com.unesp.omnileito.ocorrencias;

import com.unesp.omnileito.comum.Filas;
import com.unesp.omnileito.comum.mensagem.AmbulanciaAlocada;
import com.unesp.omnileito.comum.mensagem.LeitoExpirado;
import com.unesp.omnileito.comum.mensagem.LeitoReservado;
import com.unesp.omnileito.comum.mensagem.RetentarBuscaLeito;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class SagaOcorrenciaListener {

	private final OcorrenciaSagaService saga;

	public SagaOcorrenciaListener(OcorrenciaSagaService saga) {
		this.saga = saga;
	}

	@RabbitListener(queues = Filas.AMBULANCIA_ALOCADA)
	public void ambulancia(AmbulanciaAlocada evento) {
		saga.receberAmbulancia(evento);
	}

	@RabbitListener(queues = Filas.LEITO_RESERVADO)
	public void leito(LeitoReservado evento) {
		saga.receberLeito(evento);
	}

	@RabbitListener(queues = Filas.LEITO_EXPIRADO)
	public void expiracao(LeitoExpirado evento) {
		saga.receberExpiracao(evento);
	}

	@RabbitListener(queues = Filas.RETENTAR_BUSCA_LEITO)
	public void retentar(RetentarBuscaLeito evento) {
		saga.retentar(evento);
	}
}
