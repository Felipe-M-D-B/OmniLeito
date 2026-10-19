package com.unesp.omnileito.frota;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/ambulancias")
public class FrotaController {

	private final FrotaService frota;
	private final PausaFilas pausa;

	public FrotaController(FrotaService frota, PausaFilas pausa) {
		this.frota = frota;
		this.pausa = pausa;
	}

	@GetMapping("/disponiveis")
	public List<FrotaService.AmbulanciaResponse> disponiveis() {
		return frota.disponiveis();
	}

	@PostMapping("/demonstracao/reinicio")
	public ResponseEntity<Void> reiniciarCarga() {
		frota.reiniciarCarga();
		pausa.retomar();
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/demonstracao/filas/pausa")
	public ResponseEntity<Void> pausarFilas() {
		pausa.pausar();
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/demonstracao/filas/retomada")
	public ResponseEntity<Void> retomarFilas() {
		pausa.retomar();
		return ResponseEntity.noContent().build();
	}
}
