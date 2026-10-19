package com.unesp.omnileito.ocorrencias;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ocorrencias/demonstracao")
public class FilasDemonstracaoController {

	private final PausaFilas pausa;
	private final EsvaziadorFilas esvaziador;
	private final AcumuloFilasService acumulo;
	private final RajadaService rajada;

	public FilasDemonstracaoController(PausaFilas pausa, EsvaziadorFilas esvaziador, AcumuloFilasService acumulo,
			RajadaService rajada) {
		this.pausa = pausa;
		this.esvaziador = esvaziador;
		this.acumulo = acumulo;
		this.rajada = rajada;
	}

	@PostMapping("/filas/pausa")
	public ResponseEntity<Void> pausar() {
		pausa.pausar();
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/filas/retomada")
	public ResponseEntity<Void> retomar() {
		pausa.retomar();
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/filas/esvaziar")
	public ResponseEntity<Void> esvaziar() {
		esvaziador.esvaziar();
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/filas/acumulo")
	public ResponseEntity<Void> acumular(@RequestBody QuantidadeRequest pedido) {
		acumulo.acumular(pedido.quantidade() == null ? 0 : pedido.quantidade());
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/rajada")
	public RajadaResponse rajada(@RequestBody QuantidadeRequest pedido) {
		int quantidade = rajada.disparar(pedido.quantidade() == null ? 0 : pedido.quantidade());
		return new RajadaResponse(quantidade);
	}

	public record QuantidadeRequest(Integer quantidade) {
	}

	public record RajadaResponse(int quantidade) {
	}
}
