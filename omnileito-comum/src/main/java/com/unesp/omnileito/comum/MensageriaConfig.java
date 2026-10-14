package com.unesp.omnileito.comum;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import(TopologiaRabbit.class)
public class MensageriaConfig {

	@Bean
	public MessageConverter messageConverter() {
		return new JacksonJsonMessageConverter();
	}

	@Bean
	public Publicador publicador(RabbitTemplate rabbitTemplate) {
		return new PublicadorRabbit(rabbitTemplate);
	}
}
