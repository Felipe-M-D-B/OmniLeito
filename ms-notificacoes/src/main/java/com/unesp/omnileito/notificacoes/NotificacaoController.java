package com.unesp.omnileito.notificacoes;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/notificacoes")
public class NotificacaoController {

	private final NotificacaoService notificacoes;

	public NotificacaoController(NotificacaoService notificacoes) {
		this.notificacoes = notificacoes;
	}

	@GetMapping
	public List<NotificacaoService.NotificacaoResponse> listar(@RequestParam String ocorrenciaId) {
		return notificacoes.listar(ocorrenciaId);
	}
}
