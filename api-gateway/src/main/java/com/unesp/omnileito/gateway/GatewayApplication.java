package com.unesp.omnileito.gateway;

import com.unesp.omnileito.comum.JwtServico;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class GatewayApplication {

	public static void main(String[] args) {
		SpringApplication.run(GatewayApplication.class, args);
	}

	@Bean
	public JwtServico jwtServico(@Value("${omnileito.jwt.segredo}") String segredo) {
		return new JwtServico(segredo);
	}
}
