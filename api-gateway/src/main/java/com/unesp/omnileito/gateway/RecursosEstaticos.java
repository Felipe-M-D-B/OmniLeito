package com.unesp.omnileito.gateway;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;

@Configuration
public class RecursosEstaticos {

	@Bean
	public RouterFunction<ServerResponse> interfaceRegulacao() {
		return RouterFunctions.route(GET("/"), pedido -> pagina("static/index.html", MediaType.TEXT_HTML))
				.and(RouterFunctions.route(GET("/regulacao.css"), pedido -> pagina("static/regulacao.css", MediaType.valueOf("text/css"))))
				.and(RouterFunctions.route(GET("/regulacao.js"), pedido -> pagina("static/regulacao.js", MediaType.valueOf("text/javascript"))));
	}

	private static Mono<ServerResponse> pagina(String caminho, MediaType tipo) {
		return ServerResponse.ok().contentType(tipo).body(BodyInserters.fromResource(new ClassPathResource(caminho)));
	}
}
