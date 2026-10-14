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

	private final LeitoRepository leitos;
	private final ReservaRecusadaRepository recusas;
	private final CacheLeitos cache;
	private final SorteioUnidade sorteio;
	private final Publicador publicador;
	private final long expiracaoMinutos;
	private final JsonMapper json = JsonMapper.builder().build();
	private final ConcurrentHashMap<String, Long> ambulanciaPorOcorrencia = new ConcurrentHashMap<>();

	public LeitoService(LeitoRepository leitos, ReservaRecusadaRepository recusas, CacheLeitos cache,
			SorteioUnidade sorteio, Publicador publicador,
			@Value("${omnileito.reserva.expiracao-minutos:2}") long expiracaoMinutos) {
		this.leitos = leitos;
		this.recusas = recusas;
		this.cache = cache;
		this.sorteio = sorteio;
		this.publicador = publicador;
		this.expiracaoMinutos = expiracaoMinutos;
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
