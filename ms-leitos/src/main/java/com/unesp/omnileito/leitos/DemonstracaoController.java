package com.unesp.omnileito.leitos;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/leitos/demonstracao")
public class DemonstracaoController {

	private final LeitoService leitos;

	public DemonstracaoController(LeitoService leitos) {
		this.leitos = leitos;
	}

	@GetMapping("/resultado")
	public ResultadoResponse resultadoAtual() {
		return new ResultadoResponse(leitos.resultadoAtual());
	}

	@PutMapping("/resultado")
	public ResultadoResponse definirResultado(@RequestBody ResultadoRequest pedido) {
		return new ResultadoResponse(leitos.definirResultado(pedido.resultado()));
	}

	@PostMapping("/leito-extra")
	public ResponseEntity<LeitoService.LeitoResponse> criarExtra(@RequestBody LeitoExtraRequest pedido) {
		return ResponseEntity.status(201).body(leitos.criarExtra(pedido.tipoLeito(), pedido.codigo(), pedido.unidadeNome()));
	}

	@DeleteMapping("/leito-extra/{codigo}")
	public ResponseEntity<Void> removerExtra(@PathVariable String codigo) {
		leitos.removerExtra(codigo);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/proxima-busca-sem-vaga")
	public ResponseEntity<Void> armarProximaBuscaSemVaga(@RequestBody TipoRequest pedido) {
		leitos.armarProximaBuscaSemVaga(pedido.tipoLeito());
		return ResponseEntity.noContent().build();
	}

	public record ResultadoRequest(String resultado) {
	}

	public record ResultadoResponse(String resultado) {
	}

	public record LeitoExtraRequest(String tipoLeito, String codigo, String unidadeNome) {
	}

	public record TipoRequest(String tipoLeito) {
	}
}
