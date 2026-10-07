package com.unesp.omnileito.configuration;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE_SAGA = "saga.exchange";

    // Filas
    public static final String QUEUE_ALOCAR_AMBULANCIA = "saga.alocar-ambulancia.queue";
    public static final String QUEUE_RESERVAR_LEITO = "saga.reservar-leito.queue";
    public static final String QUEUE_LIBERAR_AMBULANCIA = "saga.liberar-ambulancia.queue";
    public static final String QUEUE_CANCELAR_LEITO = "saga.cancelar-leito.queue";

    // Routing Keys
    public static final String ROUTING_ALOCAR_AMBULANCIA = "routing.alocar-ambulancia";
    public static final String ROUTING_RESERVAR_LEITO = "routing.reservar-leito";
    public static final String ROUTING_LIBERAR_AMBULANCIA = "routing.liberar-ambulancia";
    public static final String ROUTING_CANCELAR_LEITO = "routing.cancelar-leito";

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

    // Bindings (Vinculam as Routing Keys às Filas)
    @Bean
    public Binding bindingAlocarAmbulancia() {
        return BindingBuilder.bind(queueAlocarAmbulancia()).to(sagaExchange()).with(ROUTING_ALOCAR_AMBULANCIA);
    }

    @Bean
    public Binding bindingReservarLeito() {
        return BindingBuilder.bind(queueReservarLeito()).to(sagaExchange()).with(ROUTING_RESERVAR_LEITO);
    }

    @Bean
    public Binding bindingLiberarAmbulancia() {
        return BindingBuilder.bind(queueLiberarAmbulancia()).to(sagaExchange()).with(ROUTING_LIBERAR_AMBULANCIA);
    }

    @Bean
    public Binding bindingCancelarLeito() {
        return BindingBuilder.bind(queueCancelarLeito()).to(sagaExchange()).with(ROUTING_CANCELAR_LEITO);
    }

    @Bean
    @SuppressWarnings("deprecation")
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}