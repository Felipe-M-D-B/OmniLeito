package com.unesp.omnileito.ocorrencias;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ocorrencias")
public class OcorrenciaController {

	private final OcorrenciaSagaService saga;

	public OcorrenciaController(OcorrenciaSagaService saga) {
		this.saga = saga;
	}

	@PostMapping
	public ResponseEntity<OcorrenciaResponse> abrir(@Valid @RequestBody AberturaRequest pedido) {
		return ResponseEntity.status(201).body(saga.abrir(pedido));
	}

	@GetMapping("/{ocorrenciaId}")
	public OcorrenciaResponse consultar(@PathVariable String ocorrenciaId) {
		return saga.consultar(ocorrenciaId);
	}

	@PostMapping("/{ocorrenciaId}/despachos")
	public ResponseEntity<OcorrenciaResponse> despachar(@PathVariable String ocorrenciaId) {
		OcorrenciaSagaService.DespachoHttp resultado = saga.despachar(ocorrenciaId);
		return ResponseEntity.status(resultado.status()).body(resultado.corpo());
	}
}
