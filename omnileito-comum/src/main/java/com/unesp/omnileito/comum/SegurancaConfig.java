package com.unesp.omnileito.comum;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SegurancaConfig {

	@Bean
	public JwtServico jwtServico(@Value("${omnileito.jwt.segredo}") String segredo) {
		return new JwtServico(segredo);
	}

	@Bean
	public FilterRegistrationBean<JwtFiltro> jwtFiltro(JwtServico jwtServico) {
		FilterRegistrationBean<JwtFiltro> registro = new FilterRegistrationBean<>(new JwtFiltro(jwtServico));
		registro.addUrlPatterns("/api/*");
		registro.setOrder(1);
		return registro;
	}
}
