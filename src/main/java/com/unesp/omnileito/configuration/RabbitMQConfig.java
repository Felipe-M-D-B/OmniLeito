package com.unesp.omnileito.configuration;

import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;

public class RabbitMQConfig {

    public static final String EXCHANGE_SAGA = "saga.exchange";

    // Queues de comando/execução
    public static final String QUEUE_ALOCAR_AMBULANCIA = "saga.alocar-ambulancia.queue";
    public static final String QUEUE_RESERVAR_LEITO = "saga.reservar-leito.queue";

    // Queues de compensação (Rollback)
    public static final String QUEUE_LIBERAR_AMBULANCIA = "saga.liberar-ambulancia.queue";
    public static final String QUEUE_CANCELAR_LEITO = "saga.cancelar-leito.queue";

    @Bean
    public DirectExchange sagaExchange() {
        return new DirectExchange(EXCHANGE_SAGA);
    }

    @Bean
    public Queue queueAlocarAmbulancia() {
        return new Queue(QUEUE_ALOCAR_AMBULANCIA, true);
    }

    @Bean
    public Queue queueReservarLeito() {
        return new Queue(QUEUE_RESERVAR_LEITO, true);
    }

    @Bean
    public Queue queueLiberarAmbulancia() {
        return new Queue(QUEUE_LIBERAR_AMBULANCIA, true);
    }

    @Bean
    public Queue queueCancelarLeito() {
        return new Queue(QUEUE_CANCELAR_LEITO, true);
    }

    // Configura o conversor de mensagens para JSON automaticamente
    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
