package com.unesp.omnileito.identidade;

import com.unesp.omnileito.comum.ApiException;
import com.unesp.omnileito.comum.JwtServico;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class LoginController {

	private final ReguladorRepository reguladores;
	private final SenhaInicial senhaInicial;
	private final JwtServico jwtServico;
	private final long validadeSegundos;

	public LoginController(ReguladorRepository reguladores, SenhaInicial senhaInicial, JwtServico jwtServico,
			@Value("${omnileito.jwt.validade-segundos}") long validadeSegundos) {
		this.reguladores = reguladores;
		this.senhaInicial = senhaInicial;
		this.jwtServico = jwtServico;
		this.validadeSegundos = validadeSegundos;
	}

	@PostMapping("/login")
	public TokenResponse login(@Valid @RequestBody LoginRequest pedido) {
		Regulador regulador = reguladores.findByUsuario(pedido.usuario())
				.filter(encontrado -> senhaInicial.encoder().matches(pedido.senha(), encontrado.getSenhaHash()))
				.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "CREDENCIAL_INVALIDA", "Usuário ou senha inválidos."));
		return new TokenResponse(jwtServico.emitir(regulador.getUsuario(), validadeSegundos), "Bearer");
	}

	public record LoginRequest(@NotBlank String usuario, @NotBlank String senha) {
	}

	public record TokenResponse(String token, String tipo) {
	}
}
