package com.unesp.omnileito.leitos;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;
import com.unesp.omnileito.comum.ApiException;
import com.unesp.omnileito.comum.Filas;
import com.unesp.omnileito.comum.Publicador;
import com.unesp.omnileito.comum.mensagem.LeitoExpirado;
import com.unesp.omnileito.comum.mensagem.LeitoReservado;
import com.unesp.omnileito.comum.mensagem.RegistrarNotificacao;
import com.unesp.omnileito.comum.mensagem.ReservarLeito;
import com.unesp.omnileito.comum.mensagem.RetentarBuscaLeito;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LeitoService {

	private static final ZoneId ZONA = ZoneId.of("America/Sao_Paulo");
	private static final Set<String> TIPOS = Set.of("UTI", "EMERGENCIA", "ENFERMARIA");
	private static final Set<String> CODIGOS_DA_CARGA = Set.of(
			"L-UTI-01", "L-UTI-02", "L-EMG-01", "L-EMG-02", "L-ENF-01", "L-ENF-02");

	private final LeitoRepository leitos;
	private final ReservaRecusadaRepository recusas;
	private final UnidadeSaudeRepository unidades;
	private final CacheLeitos cache;
	private final SorteioUnidade sorteio;
	private final Publicador publicador;
	private final VarreduraLeitos varredura;
	private final long expiracaoMinutos;
	private final JsonMapper json = JsonMapper.builder().build();
	private final ConcurrentHashMap<String, Long> ambulanciaPorOcorrencia = new ConcurrentHashMap<>();
	private final Set<String> proximaBuscaSemVaga = ConcurrentHashMap.newKeySet();

	public LeitoService(LeitoRepository leitos, ReservaRecusadaRepository recusas, UnidadeSaudeRepository unidades,
			CacheLeitos cache, SorteioUnidade sorteio, Publicador publicador, VarreduraLeitos varredura,
			@Value("${omnileito.reserva.expiracao-minutos:2}") long expiracaoMinutos) {
		this.leitos = leitos;
		this.recusas = recusas;
		this.unidades = unidades;
		this.cache = cache;
		this.sorteio = sorteio;
		this.publicador = publicador;
		this.varredura = varredura;
		this.expiracaoMinutos = expiracaoMinutos;
	}

	public String definirResultado(String resultado) {
		return sorteio.definir(resultado);
	}

	public String resultadoAtual() {
		return sorteio.modo();
	}

	@Transactional
	public LeitoResponse criarExtra(String tipoLeito, String codigo, String unidadeNome) {
		exigirTipo(tipoLeito);
		String codigoLimpo = codigo == null ? "" : codigo.trim();
		if (codigoLimpo.isBlank() || codigoLimpo.length() > 40 || unidadeNome == null || unidadeNome.isBlank()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "CAMPO_INVALIDO", "Campo ausente ou valor fora da lista fechada.");
		}
		if (CODIGOS_DA_CARGA.contains(codigoLimpo)) {
			throw new ApiException(HttpStatus.CONFLICT, "LEITO_DA_CARGA", "O leito da carga não é criado pela demonstração.");
		}
		if (leitos.findByCodigo(codigoLimpo).isPresent()) {
			throw new ApiException(HttpStatus.CONFLICT, "LEITO_JA_EXISTE", "Já existe leito com esse código.");
		}
		UnidadeSaude unidade = unidades.findByNome(unidadeNome.trim())
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "UNIDADE_NAO_ENCONTRADA", "Unidade não encontrada."));
		Leito salvo = leitos.save(new Leito(unidade, codigoLimpo, tipoLeito, StatusLeito.LIVRE));
		cache.apagar(tipoLeito);
		return LeitoResponse.de(salvo);
	}

	@Transactional
	public void removerExtra(String codigo) {
		if (codigo == null || CODIGOS_DA_CARGA.contains(codigo)) {
			throw new ApiException(HttpStatus.CONFLICT, "LEITO_DA_CARGA", "O leito da carga não é removido pela demonstração.");
		}
		Leito leito = leitos.findByCodigo(codigo)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "LEITO_NAO_ENCONTRADO", "Leito não encontrado."));
		if (leito.getStatus() != StatusLeito.LIVRE) {
			throw new ApiException(HttpStatus.CONFLICT, "LEITO_NAO_LIVRE", "Só um leito livre da demonstração pode ser removido.");
		}
		leitos.delete(leito);
		cache.apagar(leito.getTipoLeito());
	}

	public void armarProximaBuscaSemVaga(String tipoLeito) {
		exigirTipo(tipoLeito);
		proximaBuscaSemVaga.add(tipoLeito);
	}

	@Transactional
	public void reiniciarCarga() {
		recusas.deleteAllInBatch();
		for (Leito leito : leitos.findAll()) {
			if (!CODIGOS_DA_CARGA.contains(leito.getCodigo())) {
				leitos.delete(leito);
				continue;
			}
			leito.setStatus(leito.getCodigo().endsWith("-01") ? StatusLeito.LIVRE : StatusLeito.OCUPADO);
			leito.setOcorrenciaId(null);
			leito.setReservadoEm(null);
			leito.setConfirmado(false);
			leito.setConfirmadoEm(null);
		}
		proximaBuscaSemVaga.clear();
		ambulanciaPorOcorrencia.clear();
		sorteio.definir("");
		TIPOS.forEach(cache::apagar);
	}

	private void exigirTipo(String tipoLeito) {
		if (tipoLeito == null || !TIPOS.contains(tipoLeito)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "CAMPO_INVALIDO", "Campo ausente ou valor fora da lista fechada.");
		}
	}

	public List<LeitoResponse> disponiveis(String tipoLeito) {
		if (tipoLeito == null || !TIPOS.contains(tipoLeito)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "CAMPO_INVALIDO", "Campo ausente ou valor fora da lista fechada.");
		}
		String chave = CacheLeitos.chave(tipoLeito);
		return cache.ler(chave).flatMap(this::lerJson).orElseGet(() -> {
			List<LeitoResponse> lista = leitos.findByStatusAndTipoLeitoOrderById(StatusLeito.LIVRE, tipoLeito).stream()
					.map(LeitoResponse::de).toList();
			try {
				cache.gravar(chave, json.writeValueAsString(lista));
			} catch (Exception ignorado) {
				// Sem cache preenchido, a próxima consulta lê o MySQL de novo.
			}
			return lista;
		});
	}

	@Transactional
	public void reservar(ReservarLeito pedido) {
		if (pedido.ambulanciaId() != null) {
			ambulanciaPorOcorrencia.put(pedido.ocorrenciaId(), pedido.ambulanciaId());
		}
		Leito ativo = leitos.findByOcorrenciaId(pedido.ocorrenciaId()).orElse(null);
		if (ativo != null && ativo.getStatus() == StatusLeito.RESERVADO) {
			publicar(eventoDaReservaAtual(pedido, ativo));
			return;
		}
		if (pedido.tentativa() > 1 && proximaBuscaSemVaga.remove(pedido.tipoLeito())) {
			cache.apagar(pedido.tipoLeito());
			publicar(new LeitoReservado(pedido.ocorrenciaId(), pedido.ambulanciaId(), pedido.tentativa(), false, null,
					null, pedido.tipoLeito(), null, null, null, "SEM_LEITO_COMPATIVEL"));
			return;
		}
		Set<Long> porUltimo = new LinkedHashSet<>();
		if (pedido.leitosRecusados() != null) {
			porUltimo.addAll(pedido.leitosRecusados());
		}
		recusas.findByOcorrenciaId(pedido.ocorrenciaId()).forEach(recusa -> porUltimo.add(recusa.getLeitoId()));
		Leito escolhido = escolher(pedido.tipoLeito(), pedido.ocorrenciaId(), new ArrayList<>(porUltimo));
		if (escolhido == null) {
			publicar(new LeitoReservado(pedido.ocorrenciaId(), pedido.ambulanciaId(), pedido.tentativa(), false, null,
					null, pedido.tipoLeito(), null, null, null, "SEM_LEITO_COMPATIVEL"));
			return;
		}
		ResultadoUnidade resultado = sorteio.sortear();
		LocalDateTime agora = LocalDateTime.now();
		if (resultado == ResultadoUnidade.ACEITE) {
			escolhido.setConfirmado(true);
			escolhido.setConfirmadoEm(agora);
			cache.apagar(escolhido.getTipoLeito());
			publicar(evento(pedido, escolhido, true, "ACEITE", null));
			return;
		}
		if (resultado == ResultadoUnidade.SILENCIO) {
			cache.apagar(escolhido.getTipoLeito());
			publicar(evento(pedido, escolhido, true, "SILENCIO", null));
			return;
		}
		recusar(escolhido, pedido.ocorrenciaId(), "RECUSA_UNIDADE", agora);
		publicar(evento(pedido, escolhido, false, null, "RECUSA_UNIDADE"));
	}

	@Scheduled(fixedDelayString = "${omnileito.reserva.varredura-ms:15000}")
	@Transactional
	public void expirarReservas() {
		if (varredura.pausada()) {
			return;
		}
		LocalDateTime limite = LocalDateTime.now().minusMinutes(expiracaoMinutos);
		List<Leito> vencidas = leitos.findByStatusAndConfirmadoFalseAndReservadoEmBefore(StatusLeito.RESERVADO, limite);
		for (Leito leito : vencidas) {
			String ocorrenciaId = leito.getOcorrenciaId();
			Long ambulanciaId = ambulanciaPorOcorrencia.get(ocorrenciaId);
			String tipo = leito.getTipoLeito();
			String codigo = leito.getCodigo();
			Long leitoId = leito.getId();
			recusar(leito, ocorrenciaId, "EXPIRACAO", LocalDateTime.now());
			OffsetDateTime quando = LocalDateTime.now().atZone(ZONA).toOffsetDateTime();
			String texto = "Reserva do leito " + codigo + " expirou.";
			publicador.depoisDoCommit(() -> {
				publicador.saga(Filas.LEITO_EXPIRADO, new LeitoExpirado(ocorrenciaId, ambulanciaId, leitoId, tipo));
				publicador.notificacao(new RegistrarNotificacao(ocorrenciaId, "EXPIRACAO", texto, quando));
			});
		}
		for (String tipo : leitos.tiposComVaga()) {
			publicador.depoisDoCommit(() -> publicador.saga(Filas.RETENTAR_BUSCA_LEITO, new RetentarBuscaLeito(tipo)));
		}
	}

	private Leito escolher(String tipo, String ocorrenciaId, List<Long> porUltimo) {
		Leito preferido = marcarPrimeiro(tipo, ocorrenciaId, porUltimo);
		if (preferido != null) {
			return preferido;
		}
		return marcarPrimeiro(tipo, ocorrenciaId, List.of());
	}

	private Leito marcarPrimeiro(String tipo, String ocorrenciaId, List<Long> excluidos) {
		List<Leito> candidatos = excluidos.isEmpty()
				? leitos.findByStatusAndTipoLeitoOrderById(StatusLeito.LIVRE, tipo)
				: leitos.findByStatusAndTipoLeitoAndIdNotInOrderById(StatusLeito.LIVRE, tipo, excluidos);
		LocalDateTime agora = LocalDateTime.now();
		for (Leito candidato : candidatos) {
			if (leitos.reservarSeLivre(candidato.getId(), ocorrenciaId, agora) == 1) {
				return leitos.findById(candidato.getId()).orElseThrow();
			}
		}
		return null;
	}

	private void recusar(Leito leito, String ocorrenciaId, String motivo, LocalDateTime agora) {
		recusas.save(new ReservaRecusada(ocorrenciaId, leito.getId(), motivo, agora));
		leito.setStatus(StatusLeito.EXPIRADO);
		leito.setStatus(StatusLeito.LIVRE);
		leito.setOcorrenciaId(null);
		leito.setReservadoEm(null);
		leito.setConfirmado(false);
		leito.setConfirmadoEm(null);
		cache.apagar(leito.getTipoLeito());
	}

	private LeitoReservado eventoDaReservaAtual(ReservarLeito pedido, Leito leito) {
		String resultado = leito.isConfirmado() ? "ACEITE" : "SILENCIO";
		return evento(pedido, leito, true, resultado, null);
	}

	private LeitoReservado evento(ReservarLeito pedido, Leito leito, boolean sucesso, String resultado, String motivo) {
		return new LeitoReservado(pedido.ocorrenciaId(), pedido.ambulanciaId(), pedido.tentativa(), sucesso, leito.getId(),
				leito.getCodigo(), leito.getTipoLeito(), leito.getUnidade().getId(), leito.getUnidade().getNome(), resultado, motivo);
	}

	private void publicar(LeitoReservado evento) {
		publicador.depoisDoCommit(() -> publicador.saga(Filas.LEITO_RESERVADO, evento));
	}

	private java.util.Optional<List<LeitoResponse>> lerJson(String valor) {
		try {
			return java.util.Optional.of(json.readValue(valor, new TypeReference<>() {
			}));
		} catch (Exception erro) {
			return java.util.Optional.empty();
		}
	}

	public record LeitoResponse(Long id, String codigo, String tipoLeito, String status, UnidadeResponse unidade) {
		static LeitoResponse de(Leito leito) {
			return new LeitoResponse(leito.getId(), leito.getCodigo(), leito.getTipoLeito(), leito.getStatus().name(),
					new UnidadeResponse(leito.getUnidade().getId(), leito.getUnidade().getNome()));
		}
	}

	public record UnidadeResponse(Long id, String nome) {
	}
}
