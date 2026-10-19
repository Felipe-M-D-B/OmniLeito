package com.unesp.omnileito.notificacoes;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/notificacoes")
public class NotificacaoController {

	private final NotificacaoService notificacoes;
	private final PausaFilas pausa;

	public NotificacaoController(NotificacaoService notificacoes, PausaFilas pausa) {
		this.notificacoes = notificacoes;
		this.pausa = pausa;
	}

	@GetMapping
	public List<NotificacaoService.NotificacaoResponse> listar(@RequestParam String ocorrenciaId) {
		return notificacoes.listar(ocorrenciaId);
	}

	@PostMapping("/demonstracao/reinicio")
	public ResponseEntity<Void> reiniciarCarga() {
		notificacoes.reiniciarCarga();
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
