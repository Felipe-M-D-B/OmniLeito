package com.unesp.omnileito.comum;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class JwtFiltro extends OncePerRequestFilter {

	private final JwtServico jwtServico;

	public JwtFiltro(JwtServico jwtServico) {
		this.jwtServico = jwtServico;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String uri = request.getRequestURI();
		return uri.startsWith("/api/auth/")
				|| uri.startsWith("/swagger-ui")
				|| uri.startsWith("/v3/api-docs")
				|| uri.startsWith("/api-docs")
				|| uri.startsWith("/actuator");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String autorizacao = request.getHeader("Authorization");
		String token = autorizacao != null && autorizacao.startsWith("Bearer ") ? autorizacao.substring(7) : null;
		if (jwtServico.validar(token) == null) {
			response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
			response.setContentType(MediaType.APPLICATION_JSON_VALUE);
			response.setCharacterEncoding(StandardCharsets.UTF_8.name());
			response.getWriter().write("{\"codigo\":\"TOKEN_INVALIDO\",\"mensagem\":\"Token ausente ou inválido.\"}");
			return;
		}
		chain.doFilter(request, response);
	}
}
