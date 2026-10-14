package com.unesp.omnileito.frota;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;
import com.unesp.omnileito.comum.Filas;
import com.unesp.omnileito.comum.Publicador;
import com.unesp.omnileito.comum.mensagem.AlocarAmbulancia;
import com.unesp.omnileito.comum.mensagem.AmbulanciaAlocada;
import com.unesp.omnileito.comum.mensagem.AtualizarAmbulancia;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
public class FrotaService {

	private static final Set<String> STATUS_PEDIDO = Set.of("DISPONIVEL", "DESPACHADA", "AGUARDANDO_LEITO");

	private final AmbulanciaRepository ambulancias;
	private final CacheOcupacao cache;
	private final Publicador publicador;
	private final JsonMapper json = JsonMapper.builder().build();

	public FrotaService(AmbulanciaRepository ambulancias, CacheOcupacao cache, Publicador publicador) {
		this.ambulancias = ambulancias;
		this.cache = cache;
		this.publicador = publicador;
	}

	public List<AmbulanciaResponse> disponiveis() {
		return cache.ler(CacheOcupacao.FROTA).map(this::lerCache).orElseGet(() -> {
			List<AmbulanciaResponse> lista = ambulancias.findByStatusOrderById(StatusAmbulancia.DISPONIVEL).stream()
					.map(AmbulanciaResponse::de).toList();
			try {
				cache.gravar(CacheOcupacao.FROTA, json.writeValueAsString(lista));
			} catch (Exception ignorado) {
				// Sem cache preenchido, a próxima consulta lê o MySQL de novo.
			}
			return lista;
		});
	}

	@Transactional
	public void alocar(AlocarAmbulancia pedido) {
		Ambulancia jaAlocada = ambulancias.findByOcorrenciaId(pedido.ocorrenciaId()).orElse(null);
		if (jaAlocada != null) {
			publicar(new AmbulanciaAlocada(pedido.ocorrenciaId(), true, jaAlocada.getId(), jaAlocada.getIdentificacao(), null));
			return;
		}
		for (Ambulancia candidata : ambulancias.findByStatusOrderById(StatusAmbulancia.DISPONIVEL)) {
			if (ambulancias.alocarSeDisponivel(candidata.getId(), pedido.ocorrenciaId()) == 1) {
				cache.apagar(CacheOcupacao.FROTA);
				Ambulancia alocada = ambulancias.findById(candidata.getId()).orElseThrow();
				publicar(new AmbulanciaAlocada(pedido.ocorrenciaId(), true, alocada.getId(), alocada.getIdentificacao(), null));
				return;
			}
		}
		publicar(new AmbulanciaAlocada(pedido.ocorrenciaId(), false, null, null, "SEM_AMBULANCIA"));
	}

	@Transactional
	public void atualizar(AtualizarAmbulancia pedido) {
		if (pedido.ambulanciaId() == null || !STATUS_PEDIDO.contains(pedido.status())) {
			return;
		}
		Ambulancia ambulancia = ambulancias.findById(pedido.ambulanciaId()).orElse(null);
		if (ambulancia == null) {
			return;
		}
		if (ambulancia.getOcorrenciaId() != null && !ambulancia.getOcorrenciaId().equals(pedido.ocorrenciaId())
				&& ambulancia.getStatus() != StatusAmbulancia.DISPONIVEL) {
			return;
		}
		StatusAmbulancia novo = StatusAmbulancia.valueOf(pedido.status());
		if (ambulancia.getStatus() == novo && pedido.ocorrenciaId().equals(ambulancia.getOcorrenciaId())) {
			return;
		}
		ambulancia.setStatus(novo);
		ambulancia.setOcorrenciaId(novo == StatusAmbulancia.DISPONIVEL ? null : pedido.ocorrenciaId());
		cache.apagar(CacheOcupacao.FROTA);
	}

	private void publicar(AmbulanciaAlocada evento) {
		publicador.depoisDoCommit(() -> publicador.saga(Filas.AMBULANCIA_ALOCADA, evento));
	}

	private List<AmbulanciaResponse> lerCache(String valor) {
		try {
			return json.readValue(valor, new TypeReference<>() {
			});
		} catch (Exception erro) {
			cache.apagar(CacheOcupacao.FROTA);
			return ambulancias.findByStatusOrderById(StatusAmbulancia.DISPONIVEL).stream()
					.map(AmbulanciaResponse::de).toList();
		}
	}

	public record AmbulanciaResponse(Long id, String identificacao, String status) {
		static AmbulanciaResponse de(Ambulancia ambulancia) {
			return new AmbulanciaResponse(ambulancia.getId(), ambulancia.getIdentificacao(), ambulancia.getStatus().name());
		}
	}
}
