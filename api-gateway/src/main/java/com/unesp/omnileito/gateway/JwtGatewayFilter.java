package com.unesp.omnileito.gateway;

import com.unesp.omnileito.comum.JwtServico;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

@Component
public class JwtGatewayFilter implements GlobalFilter, Ordered {

	private final JwtServico jwtServico;

	public JwtGatewayFilter(JwtServico jwtServico) {
		this.jwtServico = jwtServico;
	}

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
		String caminho = exchange.getRequest().getURI().getPath();
		if (caminho.startsWith("/api/auth/") || caminho.startsWith("/fallback/")) {
			return chain.filter(exchange);
		}
		if (!caminho.startsWith("/api/")) {
			return chain.filter(exchange);
		}
		String autorizacao = exchange.getRequest().getHeaders().getFirst("Authorization");
		String token = autorizacao != null && autorizacao.startsWith("Bearer ") ? autorizacao.substring(7) : null;
		if (jwtServico.validar(token) == null) {
			byte[] corpo = "{\"codigo\":\"TOKEN_INVALIDO\",\"mensagem\":\"Token ausente ou inválido.\"}"
					.getBytes(StandardCharsets.UTF_8);
			exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
			exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
			DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(corpo);
			return exchange.getResponse().writeWith(Mono.just(buffer));
		}
		return chain.filter(exchange);
	}

	@Override
	public int getOrder() {
		return -1;
	}
}
