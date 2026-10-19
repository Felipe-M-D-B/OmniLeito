package com.unesp.omnileito.notificacoes;

import com.unesp.omnileito.comum.Filas;
import com.unesp.omnileito.comum.mensagem.RegistrarNotificacao;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class NotificacaoListener {

	private final NotificacaoService notificacoes;

	public NotificacaoListener(NotificacaoService notificacoes) {
		this.notificacoes = notificacoes;
	}

	@RabbitListener(id = Filas.REGISTRAR_NOTIFICACAO, queues = Filas.REGISTRAR_NOTIFICACAO)
	public void registrar(RegistrarNotificacao pedido) {
		String origem = "EXPIRACAO".equals(pedido.tipo()) ? "MS-Leitos" : "MS-Ocorrencias";
		notificacoes.registrar(pedido, origem);
	}
}
