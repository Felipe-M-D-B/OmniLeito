package com.unesp.omnileito.gateway;

import com.unesp.omnileito.comum.ErroApi;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FallbackLeitosController {

	@RequestMapping("/fallback/leitos")
	public ResponseEntity<ErroApi> leitosIndisponivel() {
		return ResponseEntity.status(503).body(new ErroApi("SERVICO_LEITOS_INDISPONIVEL",
				"A consulta de leitos está indisponível."));
	}
}
