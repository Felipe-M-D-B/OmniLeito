package com.unesp.omnileito.leitos;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/leitos")
public class LeitoController {

	private final LeitoService leitos;

	public LeitoController(LeitoService leitos) {
		this.leitos = leitos;
	}

	@GetMapping("/disponiveis")
	public List<LeitoService.LeitoResponse> disponiveis(@RequestParam String tipoLeito) {
		return leitos.disponiveis(tipoLeito);
	}
}
