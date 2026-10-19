package com.unesp.omnileito.ocorrencias;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ocorrencias/demonstracao")
public class ReinicioCargaController {

	private final ReinicioCargaService reinicio;

	public ReinicioCargaController(ReinicioCargaService reinicio) {
		this.reinicio = reinicio;
	}

	@PostMapping("/reinicio")
	public ResponseEntity<Void> reiniciar() {
		reinicio.reiniciar();
		return ResponseEntity.noContent().build();
	}
}
