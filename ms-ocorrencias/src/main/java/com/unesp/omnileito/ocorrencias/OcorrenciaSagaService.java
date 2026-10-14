package com.unesp.omnileito.ocorrencias;

import com.unesp.omnileito.comum.ApiException;
import com.unesp.omnileito.comum.Filas;
import com.unesp.omnileito.comum.Publicador;
import com.unesp.omnileito.comum.mensagem.AlocarAmbulancia;
import com.unesp.omnileito.comum.mensagem.AmbulanciaAlocada;
import com.unesp.omnileito.comum.mensagem.AtualizarAmbulancia;
import com.unesp.omnileito.comum.mensagem.LeitoExpirado;
import com.unesp.omnileito.comum.mensagem.LeitoReservado;
import com.unesp.omnileito.comum.mensagem.RegistrarNotificacao;
import com.unesp.omnileito.comum.mensagem.ReservarLeito;
import com.unesp.omnileito.comum.mensagem.RetentarBuscaLeito;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OcorrenciaSagaService {

	private static final ZoneId ZONA = ZoneId.of("America/Sao_Paulo");
	private static final Set<String> PRIORIDADES = Set.of("VERMELHA", "AMARELA", "VERDE");
	private static final Set<String> TIPOS = Set.of("UTI", "EMERGENCIA", "ENFERMARIA");

	private final OcorrenciaRepository ocorrencias;
	private final PacienteRepository pacientes;
	private final DespachoRepository despachos;
	private final LeitoExcluidoRepository excluidos;
	private final Publicador publicador;
	private final ConcurrentHashMap<String, String> identificacoes = new ConcurrentHashMap<>();

	public OcorrenciaSagaService(OcorrenciaRepository ocorrencias, PacienteRepository pacientes,
			DespachoRepository despachos, LeitoExcluidoRepository excluidos, Publicador publicador) {
		this.ocorrencias = ocorrencias;
		this.pacientes = pacientes;
		this.despachos = despachos;
		this.excluidos = excluidos;
		this.publicador = publicador;
	}

	@Transactional
	public OcorrenciaResponse abrir(AberturaRequest pedido) {
		if (!PRIORIDADES.contains(pedido.prioridade()) || !TIPOS.contains(pedido.tipoLeito())) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "CAMPO_INVALIDO", "Campo ausente ou valor fora da lista fechada.");
		}
		Paciente paciente = pacientes.save(new Paciente(pedido.paciente().nome().trim()));
		Ocorrencia ocorrencia = new Ocorrencia(UUID.randomUUID().toString(), paciente, pedido.local().trim(),
				pedido.prioridade(), pedido.tipoLeito(), LocalDateTime.now());
		return resposta(ocorrencias.save(ocorrencia));
	}

	@Transactional(readOnly = true)
	public OcorrenciaResponse consultar(String id) {
		return resposta(buscar(id));
	}

	@Transactional
	public DespachoHttp despachar(String id) {
		Ocorrencia ocorrencia = buscar(id);
		if (ocorrencia.getStatus() == StatusOcorrencia.CANCELADA || ocorrencia.getStatus() == StatusOcorrencia.CONCLUIDA) {
			throw new ApiException(HttpStatus.CONFLICT, "OCORRENCIA_NAO_ABERTA", "Ocorrência não está aberta para despacho.");
		}
		if (ocorrencia.getStatus() != StatusOcorrencia.ABERTA) {
			return new DespachoHttp(200, resposta(ocorrencia));
		}
		LocalDateTime agora = LocalDateTime.now();
		ocorrencia.setStatus(StatusOcorrencia.EM_DESPACHO);
		ocorrencia.setAtualizadaEm(agora);
		despachos.save(new Despacho(ocorrencia.getId(), agora));
		publicador.depoisDoCommit(() -> publicador.saga(Filas.ALOCAR_AMBULANCIA,
				new AlocarAmbulancia(ocorrencia.getId(), ocorrencia.getPrioridade())));
		return new DespachoHttp(202, resposta(ocorrencia));
	}

	@Transactional
	public void receberAmbulancia(AmbulanciaAlocada evento) {
		Ocorrencia ocorrencia = ocorrencias.findById(evento.ocorrenciaId()).orElse(null);
		if (ocorrencia == null || ocorrencia.getStatus() != StatusOcorrencia.EM_DESPACHO) {
			return;
		}
		if (!evento.sucesso()) {
			ocorrencia.setStatus(StatusOcorrencia.FALHA_DESPACHO);
			ocorrencia.setAtualizadaEm(LocalDateTime.now());
			return;
		}
		if (evento.identificacao() != null) {
			identificacoes.put(ocorrencia.getId(), evento.identificacao());
		}
		if (ocorrencia.getAmbulanciaId() == null) {
			ocorrencia.setAmbulanciaId(evento.ambulanciaId());
			ocorrencia.setAtualizadaEm(LocalDateTime.now());
		}
		if (ocorrencia.getLeitoId() == null) {
			publicarReserva(ocorrencia, evento.ambulanciaId());
		}
	}

	@Transactional
	public void receberLeito(LeitoReservado evento) {
		Ocorrencia ocorrencia = ocorrencias.findById(evento.ocorrenciaId()).orElse(null);
		if (ocorrencia == null) {
			return;
		}
		if (evento.sucesso()) {
			confirmarDestino(ocorrencia, evento);
			return;
		}
		if ("RECUSA_UNIDADE".equals(evento.motivo())) {
			recusarOuExpirar(ocorrencia, evento.leitoId(), evento.ambulanciaId(), "RECUSA_UNIDADE", evento.tentativa());
			return;
		}
		if ("SEM_LEITO_COMPATIVEL".equals(evento.motivo())) {
			semLeito(ocorrencia, evento);
		}
	}

	@Transactional
	public void receberExpiracao(LeitoExpirado evento) {
		Ocorrencia ocorrencia = ocorrencias.findById(evento.ocorrenciaId()).orElse(null);
		if (ocorrencia == null) {
			return;
		}
		if (ocorrencia.getLeitoId() != null && !ocorrencia.getLeitoId().equals(evento.leitoId())
				&& ocorrencia.getStatus() == StatusOcorrencia.DESPACHADA) {
			return;
		}
		Long ambulanciaId = evento.ambulanciaId() != null ? evento.ambulanciaId() : ocorrencia.getAmbulanciaId();
		int tentativa = despachos.findByOcorrenciaId(ocorrencia.getId()).map(Despacho::getTentativa).orElse(1);
		recusarOuExpirar(ocorrencia, evento.leitoId(), ambulanciaId, "EXPIRACAO", tentativa);
	}

	@Transactional
	public void retentar(RetentarBuscaLeito evento) {
		List<Ocorrencia> esperando = ocorrencias.findByStatusAndTipoLeitoAndLeitoIdIsNullAndAmbulanciaIdIsNotNull(
				StatusOcorrencia.EM_DESPACHO, evento.tipoLeito());
		for (Ocorrencia ocorrencia : esperando) {
			int tentativa = despachos.findByOcorrenciaId(ocorrencia.getId()).map(Despacho::getTentativa).orElse(1);
			if (tentativa > 1) {
				publicarReserva(ocorrencia, ocorrencia.getAmbulanciaId());
			}
		}
	}

	private void confirmarDestino(Ocorrencia ocorrencia, LeitoReservado evento) {
		if (ocorrencia.getStatus() == StatusOcorrencia.DESPACHADA && evento.leitoId().equals(ocorrencia.getLeitoId())) {
			publicarDestino(ocorrencia, evento.codigo(), evento.unidadeNome());
			return;
		}
		LocalDateTime agora = LocalDateTime.now();
		ocorrencia.setStatus(StatusOcorrencia.DESPACHADA);
		ocorrencia.setAmbulanciaId(evento.ambulanciaId());
		ocorrencia.setLeitoId(evento.leitoId());
		ocorrencia.setUnidadeNome(evento.unidadeNome());
		ocorrencia.setAtualizadaEm(agora);
		publicarDestino(ocorrencia, evento.codigo(), evento.unidadeNome());
	}

	private void publicarDestino(Ocorrencia ocorrencia, String codigo, String unidadeNome) {
		String placa = identificacoes.getOrDefault(ocorrencia.getId(), "id " + ocorrencia.getAmbulanciaId());
		String texto = "Ocorrência despachada. Ambulância " + placa + ", leito " + codigo + ", " + unidadeNome + ".";
		OffsetDateTime quando = ocorrencia.getAtualizadaEm().atZone(ZONA).toOffsetDateTime();
		publicador.depoisDoCommit(() -> {
			publicador.saga(Filas.ATUALIZAR_AMBULANCIA,
					new AtualizarAmbulancia(ocorrencia.getId(), ocorrencia.getAmbulanciaId(), "DESPACHADA"));
			publicador.notificacao(new RegistrarNotificacao(ocorrencia.getId(), "DESPACHO", texto, quando));
		});
	}

	private void recusarOuExpirar(Ocorrencia ocorrencia, Long leitoId, Long ambulanciaId, String motivo, int tentativaAtual) {
		LocalDateTime agora = LocalDateTime.now();
		boolean jaTratado = excluidos.findByOcorrenciaIdAndLeitoId(ocorrencia.getId(), leitoId).isPresent()
				&& ocorrencia.getLeitoId() == null
				&& ocorrencia.getStatus() == StatusOcorrencia.EM_DESPACHO;
		if (!jaTratado) {
			excluidos.findByOcorrenciaIdAndLeitoId(ocorrencia.getId(), leitoId)
					.ifPresentOrElse(existente -> existente.atualizar(motivo, agora),
							() -> excluidos.save(new LeitoExcluido(ocorrencia.getId(), leitoId, motivo, agora)));
			ocorrencia.setStatus(StatusOcorrencia.EM_DESPACHO);
			ocorrencia.setLeitoId(null);
			ocorrencia.setUnidadeNome(null);
			if (ambulanciaId != null) {
				ocorrencia.setAmbulanciaId(ambulanciaId);
			}
			ocorrencia.setAtualizadaEm(agora);
			despachos.findByOcorrenciaId(ocorrencia.getId()).ifPresent(despacho -> {
				despacho.setTentativa(tentativaAtual + 1);
				despacho.setAtualizadoEm(agora);
			});
		}
		Long ambulancia = ocorrencia.getAmbulanciaId();
		publicador.depoisDoCommit(() -> {
			publicador.saga(Filas.ATUALIZAR_AMBULANCIA,
					new AtualizarAmbulancia(ocorrencia.getId(), ambulancia, "AGUARDANDO_LEITO"));
			publicador.saga(Filas.RESERVAR_LEITO, pedidoReserva(ocorrencia, ambulancia));
		});
	}

	private void semLeito(Ocorrencia ocorrencia, LeitoReservado evento) {
		if (evento.tentativa() <= 1) {
			if (ocorrencia.getStatus() != StatusOcorrencia.FALHA_DESPACHO) {
				ocorrencia.setStatus(StatusOcorrencia.FALHA_DESPACHO);
				ocorrencia.setAmbulanciaId(null);
				ocorrencia.setLeitoId(null);
				ocorrencia.setAtualizadaEm(LocalDateTime.now());
			}
			publicador.depoisDoCommit(() -> publicador.saga(Filas.ATUALIZAR_AMBULANCIA,
					new AtualizarAmbulancia(ocorrencia.getId(), evento.ambulanciaId(), "DISPONIVEL")));
			return;
		}
		LocalDateTime marco = ocorrencia.getAtualizadaEm();
		if (ocorrencia.getStatus() != StatusOcorrencia.EM_DESPACHO) {
			ocorrencia.setStatus(StatusOcorrencia.EM_DESPACHO);
			ocorrencia.setLeitoId(null);
			marco = LocalDateTime.now();
			ocorrencia.setAtualizadaEm(marco);
		}
		OffsetDateTime quando = marco.atZone(ZONA).toOffsetDateTime();
		String texto = "Não há leito livre da especialidade " + ocorrencia.getTipoLeito() + ".";
		publicador.depoisDoCommit(() -> publicador.notificacao(
				new RegistrarNotificacao(ocorrencia.getId(), "FALTA_LEITO", texto, quando)));
	}

	private void publicarReserva(Ocorrencia ocorrencia, Long ambulanciaId) {
		publicador.depoisDoCommit(() -> publicador.saga(Filas.RESERVAR_LEITO, pedidoReserva(ocorrencia, ambulanciaId)));
	}

	private ReservarLeito pedidoReserva(Ocorrencia ocorrencia, Long ambulanciaId) {
		int tentativa = despachos.findByOcorrenciaId(ocorrencia.getId()).map(Despacho::getTentativa).orElse(1);
		List<Long> recusados = excluidos.findByOcorrenciaId(ocorrencia.getId()).stream().map(LeitoExcluido::getLeitoId).toList();
		return new ReservarLeito(ocorrencia.getId(), ambulanciaId, ocorrencia.getTipoLeito(), recusados, tentativa);
	}

	private Ocorrencia buscar(String id) {
		return ocorrencias.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "OCORRENCIA_NAO_ENCONTRADA",
				"Ocorrência não encontrada."));
	}

	private OcorrenciaResponse resposta(Ocorrencia ocorrencia) {
		return new OcorrenciaResponse(ocorrencia.getId(), ocorrencia.getStatus().name(),
				new PacienteResponse(ocorrencia.getPaciente().getNome()), ocorrencia.getLocal(), ocorrencia.getPrioridade(),
				ocorrencia.getTipoLeito(), ocorrencia.getAmbulanciaId(), ocorrencia.getLeitoId(), ocorrencia.getUnidadeNome(),
				ocorrencia.getCriadaEm().atZone(ZONA).toOffsetDateTime());
	}

	public record DespachoHttp(int status, OcorrenciaResponse corpo) {
	}
}
