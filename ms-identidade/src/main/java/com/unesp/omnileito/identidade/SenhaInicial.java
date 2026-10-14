package com.unesp.omnileito.identidade;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class SenhaInicial implements ApplicationRunner {

	private final ReguladorRepository reguladores;
	private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

	public SenhaInicial(ReguladorRepository reguladores) {
		this.reguladores = reguladores;
	}

	@Override
	public void run(ApplicationArguments args) {
		reguladores.findByUsuario("regulador").ifPresent(regulador -> {
			if (!regulador.getSenhaHash().startsWith("$2")) {
				regulador.setSenhaHash(encoder.encode("regulador"));
				reguladores.save(regulador);
			}
		});
	}

	public BCryptPasswordEncoder encoder() {
		return encoder;
	}
}
