package com.unesp.omnileito.comum;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SegurancaConfig {

	@Bean
	public JwtServico jwtServico(@Value("${omnileito.jwt.segredo}") String segredo) {
		return new JwtServico(segredo);
	}

	@Bean
	public OpenAPI documentoComBearer() {
		String esquema = "bearer";
		return new OpenAPI()
				.components(new Components().addSecuritySchemes(esquema, new SecurityScheme()
						.type(SecurityScheme.Type.HTTP)
						.scheme("bearer")
						.bearerFormat("JWT")))
				.addSecurityItem(new SecurityRequirement().addList(esquema));
	}

	@Bean
	public OpenApiCustomizer loginSemBearer() {
		return openApi -> {
			if (openApi.getPaths() == null) {
				return;
			}
			openApi.getPaths().forEach((caminho, item) -> {
				if (caminho.startsWith("/api/auth/")) {
					item.readOperations().forEach(operacao -> operacao.setSecurity(List.of()));
				}
			});
		};
	}

	@Bean
	public FilterRegistrationBean<JwtFiltro> jwtFiltro(JwtServico jwtServico) {
		FilterRegistrationBean<JwtFiltro> registro = new FilterRegistrationBean<>(new JwtFiltro(jwtServico));
		registro.addUrlPatterns("/api/*");
		registro.setOrder(1);
		return registro;
	}
}
