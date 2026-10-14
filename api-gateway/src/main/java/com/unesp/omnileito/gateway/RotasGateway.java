package com.unesp.omnileito.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

@Configuration
public class RotasGateway {

	@Bean
	public RouteLocator rotas(RouteLocatorBuilder builder,
			@Value("${omnileito.destino.identidade}") String identidade,
			@Value("${omnileito.destino.ocorrencias}") String ocorrencias,
			@Value("${omnileito.destino.frota}") String frota,
			@Value("${omnileito.destino.leitos}") String leitos,
			@Value("${omnileito.destino.notificacoes}") String notificacoes) {
		return builder.routes()
				.route("auth", rota -> rota.path("/api/auth/**").uri(identidade))
				.route("ocorrencias", rota -> rota.path("/api/ocorrencias/**").uri(ocorrencias))
				.route("ambulancias", rota -> rota.path("/api/ambulancias/**").uri(frota))
				.route("leitos", rota -> rota.path("/api/leitos/**")
						.filters(filtro -> filtro
								.circuitBreaker(config -> config.setName("leitos").setFallbackUri("forward:/fallback/leitos"))
								.retry(config -> config.setRetries(2).setMethods(HttpMethod.GET)))
						.uri(leitos))
				.route("notificacoes", rota -> rota.path("/api/notificacoes/**").uri(notificacoes))
				.build();
	}
}
