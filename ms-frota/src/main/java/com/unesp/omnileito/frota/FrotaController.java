package com.unesp.omnileito.frota;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/ambulancias")
public class FrotaController {

	private final FrotaService frota;

	public FrotaController(FrotaService frota) {
		this.frota = frota;
	}

	@GetMapping("/disponiveis")
	public List<FrotaService.AmbulanciaResponse> disponiveis() {
		return frota.disponiveis();
	}
}
