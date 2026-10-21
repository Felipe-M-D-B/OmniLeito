package com.unesp.omnileito.comum;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TopologiaRabbit {

	@Bean
	public Declarables topologiaOmniLeito() {
		DirectExchange saga = new DirectExchange(Filas.EXCHANGE_SAGA, true, false);
		DirectExchange notificacao = new DirectExchange(Filas.EXCHANGE_NOTIFICACAO, true, false);
		Queue alocar = duravel(Filas.ALOCAR_AMBULANCIA);
		Queue ambulanciaAlocada = duravel(Filas.AMBULANCIA_ALOCADA);
		Queue reservar = duravel(Filas.RESERVAR_LEITO);
		Queue leitoReservado = duravel(Filas.LEITO_RESERVADO);
		Queue leitoExpirado = duravel(Filas.LEITO_EXPIRADO);
		Queue atualizar = duravel(Filas.ATUALIZAR_AMBULANCIA);
		Queue retentar = duravel(Filas.RETENTAR_BUSCA_LEITO);
		Queue registrar = duravel(Filas.REGISTRAR_NOTIFICACAO);
		return new Declarables(
				saga,
				notificacao,
				alocar,
				ambulanciaAlocada,
				reservar,
				leitoReservado,
				leitoExpirado,
				atualizar,
				retentar,
				registrar,
				ligar(alocar, saga),
				ligar(ambulanciaAlocada, saga),
				ligar(reservar, saga),
				ligar(leitoReservado, saga),
				ligar(leitoExpirado, saga),
				ligar(atualizar, saga),
				ligar(retentar, saga),
				ligar(registrar, notificacao)
		);
	}

	private static Queue duravel(String nome) {
		return QueueBuilder.durable(nome).build();
	}

	private static Binding ligar(Queue fila, DirectExchange exchange) {
		return BindingBuilder.bind(fila).to(exchange).with(fila.getName());
	}
}
