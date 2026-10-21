package com.unesp.omnileito.comum;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public class PublicadorRabbit implements Publicador {

	private final RabbitTemplate rabbitTemplate;

	public PublicadorRabbit(RabbitTemplate rabbitTemplate) {
		this.rabbitTemplate = rabbitTemplate;
	}

	@Override
	public void depoisDoCommit(Runnable acao) {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					acao.run();
				}
			});
			return;
		}
		acao.run();
	}

	@Override
	public void saga(String fila, Object mensagem) {
		rabbitTemplate.convertAndSend(Filas.EXCHANGE_SAGA, fila, mensagem);
	}

	@Override
	public void notificacao(Object mensagem) {
		rabbitTemplate.convertAndSend(Filas.EXCHANGE_NOTIFICACAO, Filas.REGISTRAR_NOTIFICACAO, mensagem);
	}
}
