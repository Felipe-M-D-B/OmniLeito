package com.unesp.omnileito.ocorrencias;

import com.unesp.omnileito.comum.ApiException;
import com.unesp.omnileito.comum.Publicador;
import com.unesp.omnileito.comum.mensagem.AtualizarAmbulancia;
import com.unesp.omnileito.comum.mensagem.LeitoReservado;
import com.unesp.omnileito.comum.mensagem.RegistrarNotificacao;
import com.unesp.omnileito.comum.mensagem.ReservarLeito;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OcorrenciaSagaServiceTest {

	private OcorrenciaRepository ocorrencias;
	private PacienteRepository pacientes;
	private DespachoRepository despachos;
	private LeitoExcluidoRepository excluidos;
	private Gravador publicador;
	private OcorrenciaSagaService saga;

	@BeforeEach
	void preparar() {
		ocorrencias = mock(OcorrenciaRepository.class);
		pacientes = mock(PacienteRepository.class);
		despachos = mock(DespachoRepository.class);
		excluidos = mock(LeitoExcluidoRepository.class);
		publicador = new Gravador();
		saga = new OcorrenciaSagaService(ocorrencias, pacientes, despachos, excluidos, publicador);
		when(pacientes.save(any())).thenAnswer(chamada -> chamada.getArgument(0));
	}

	@Test
	void aberturaNaoPublicaMensagem() {
		when(ocorrencias.save(any())).thenAnswer(chamada -> chamada.getArgument(0));
		OcorrenciaResponse resposta = saga.abrir(new AberturaRequest(new AberturaRequest.PacienteRequest("Maria Souza"),
				"Rua das Flores, 120", "VERMELHA", "UTI"));
		assertEquals("ABERTA", resposta.status());
		assertEquals("UTI", resposta.tipoLeito());
		assertTrue(publicador.saga.isEmpty());
	}

	@Test
	void segundoDespachoNaoAlocaDeNovo() {
		Ocorrencia ocorrencia = ocorrencia(StatusOcorrencia.EM_DESPACHO);
		when(ocorrencias.findById("oc-1")).thenReturn(Optional.of(ocorrencia));
		OcorrenciaSagaService.DespachoHttp resposta = saga.despachar("oc-1");
		assertEquals(200, resposta.status());
		assertTrue(publicador.saga.isEmpty());
		verify(despachos, never()).save(any());
	}

	@Test
	void falhaDeDespachoNaoRecomeca() {
		Ocorrencia ocorrencia = ocorrencia(StatusOcorrencia.FALHA_DESPACHO);
		when(ocorrencias.findById("oc-1")).thenReturn(Optional.of(ocorrencia));
		assertEquals(200, saga.despachar("oc-1").status());
		assertTrue(publicador.saga.isEmpty());
	}

	@Test
	void canceladaRespondeConflito() {
		when(ocorrencias.findById("oc-1")).thenReturn(Optional.of(ocorrencia(StatusOcorrencia.CANCELADA)));
		ApiException erro = assertThrows(ApiException.class, () -> saga.despachar("oc-1"));
		assertEquals("OCORRENCIA_NAO_ABERTA", erro.getCodigo());
	}

	@Test
	void primeiraBuscaSemLeitoDevolveAmbulancia() {
		Ocorrencia ocorrencia = ocorrencia(StatusOcorrencia.EM_DESPACHO);
		ocorrencia.setAmbulanciaId(1L);
		when(ocorrencias.findById("oc-1")).thenReturn(Optional.of(ocorrencia));
		saga.receberLeito(new LeitoReservado("oc-1", 1L, 1, false, null, null, "UTI", null, null, null, "SEM_LEITO_COMPATIVEL"));
		assertEquals(StatusOcorrencia.FALHA_DESPACHO, ocorrencia.getStatus());
		AtualizarAmbulancia atualizacao = (AtualizarAmbulancia) publicador.saga.get(0);
		assertEquals("DISPONIVEL", atualizacao.status());
		assertEquals(1L, atualizacao.ambulanciaId());
	}

	@Test
	void recusaMantemAmbulanciaEPedeOutraReserva() {
		Ocorrencia ocorrencia = ocorrencia(StatusOcorrencia.DESPACHADA);
		ocorrencia.setAmbulanciaId(1L);
		ocorrencia.setLeitoId(10L);
		Despacho despacho = new Despacho("oc-1", LocalDateTime.now());
		when(ocorrencias.findById("oc-1")).thenReturn(Optional.of(ocorrencia));
		when(despachos.findByOcorrenciaId("oc-1")).thenReturn(Optional.of(despacho));
		when(excluidos.findByOcorrenciaIdAndLeitoId("oc-1", 10L)).thenReturn(Optional.empty());
		when(excluidos.findByOcorrenciaId("oc-1")).thenReturn(List.of(new LeitoExcluido("oc-1", 10L, "RECUSA_UNIDADE", LocalDateTime.now())));
		saga.receberLeito(new LeitoReservado("oc-1", 1L, 1, false, 10L, "L-UTI-01", "UTI", 1L, "Hospital Central", null, "RECUSA_UNIDADE"));
		assertEquals(StatusOcorrencia.EM_DESPACHO, ocorrencia.getStatus());
		assertEquals(1L, ocorrencia.getAmbulanciaId());
		assertEquals(2, despacho.getTentativa());
		assertTrue(publicador.saga.stream().anyMatch(mensagem -> mensagem instanceof AtualizarAmbulancia atualizacao
				&& "AGUARDANDO_LEITO".equals(atualizacao.status())));
		assertTrue(publicador.saga.stream().anyMatch(mensagem -> mensagem instanceof ReservarLeito reserva && reserva.tentativa() == 2));
	}

	@Test
	void buscaSeguinteSemLeitoNaoDevolveAmbulancia() {
		Ocorrencia ocorrencia = ocorrencia(StatusOcorrencia.EM_DESPACHO);
		ocorrencia.setAmbulanciaId(1L);
		when(ocorrencias.findById("oc-1")).thenReturn(Optional.of(ocorrencia));
		saga.receberLeito(new LeitoReservado("oc-1", 1L, 2, false, null, null, "UTI", null, null, null, "SEM_LEITO_COMPATIVEL"));
		assertEquals(StatusOcorrencia.EM_DESPACHO, ocorrencia.getStatus());
		assertEquals(1L, ocorrencia.getAmbulanciaId());
		assertTrue(publicador.saga.stream().noneMatch(mensagem -> mensagem instanceof AtualizarAmbulancia));
		assertTrue(publicador.notas.stream().anyMatch(mensagem -> mensagem instanceof RegistrarNotificacao nota
				&& "FALTA_LEITO".equals(nota.tipo())));
	}

	private static Ocorrencia ocorrencia(StatusOcorrencia status) {
		Ocorrencia ocorrencia = new Ocorrencia("oc-1", new Paciente("Maria Souza"), "Rua das Flores, 120", "VERMELHA", "UTI",
				LocalDateTime.now());
		ocorrencia.setStatus(status);
		return ocorrencia;
	}

	private static final class Gravador implements Publicador {
		private final List<Object> saga = new ArrayList<>();
		private final List<Object> notas = new ArrayList<>();

		@Override
		public void depoisDoCommit(Runnable acao) {
			acao.run();
		}

		@Override
		public void saga(String fila, Object mensagem) {
			saga.add(mensagem);
		}

		@Override
		public void notificacao(Object mensagem) {
			notas.add(mensagem);
		}
	}
}
