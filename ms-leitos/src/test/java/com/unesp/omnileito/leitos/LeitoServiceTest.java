package com.unesp.omnileito.leitos;

import com.unesp.omnileito.comum.Publicador;
import com.unesp.omnileito.comum.mensagem.LeitoReservado;
import com.unesp.omnileito.comum.mensagem.ReservarLeito;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LeitoServiceTest {

	private LeitoRepository leitos;
	private ReservaRecusadaRepository recusas;
	private SorteioUnidade sorteio;
	private Gravador publicador;
	private LeitoService servico;
	private final UnidadeSaude hospital = new UnidadeSaude(1L, "Hospital Central");

	@BeforeEach
	void preparar() {
		leitos = mock(LeitoRepository.class);
		recusas = mock(ReservaRecusadaRepository.class);
		sorteio = mock(SorteioUnidade.class);
		publicador = new Gravador();
		CacheLeitos cache = mock(CacheLeitos.class);
		servico = new LeitoService(leitos, recusas, cache, sorteio, publicador, 2);
		when(leitos.findByOcorrenciaId("oc-1")).thenReturn(Optional.empty());
		when(recusas.findByOcorrenciaId("oc-1")).thenReturn(List.of());
	}

	@Test
	void prefereOutroLeitoLivreQuandoHaRecusa() {
		Leito primeiro = leito(10L, "L-UTI-01");
		Leito segundo = leito(11L, "L-UTI-02");
		when(leitos.findByStatusAndTipoLeitoAndIdNotInOrderById(StatusLeito.LIVRE, "UTI", List.of(10L))).thenReturn(List.of(segundo));
		when(leitos.reservarSeLivre(eq(11L), eq("oc-1"), any())).thenReturn(1);
		when(leitos.findById(11L)).thenReturn(Optional.of(segundo));
		when(sorteio.sortear()).thenReturn(ResultadoUnidade.ACEITE);

		servico.reservar(new ReservarLeito("oc-1", 1L, "UTI", List.of(10L), 2));

		verify(leitos, never()).reservarSeLivre(eq(10L), any(), any());
		LeitoReservado evento = (LeitoReservado) publicador.saga.get(0);
		assertEquals(11L, evento.leitoId());
		assertEquals("ACEITE", evento.resultadoUnidade());
		assertEquals(true, segundo.isConfirmado());
	}

	@Test
	void repeteOMesmoLeitoQuandoEleEOunicoLivre() {
		Leito unico = leito(10L, "L-UTI-01");
		when(leitos.findByStatusAndTipoLeitoAndIdNotInOrderById(StatusLeito.LIVRE, "UTI", List.of(10L))).thenReturn(List.of());
		when(leitos.findByStatusAndTipoLeitoOrderById(StatusLeito.LIVRE, "UTI")).thenReturn(List.of(unico));
		when(leitos.reservarSeLivre(eq(10L), eq("oc-1"), any())).thenReturn(1);
		when(leitos.findById(10L)).thenReturn(Optional.of(unico));
		when(sorteio.sortear()).thenReturn(ResultadoUnidade.SILENCIO);

		servico.reservar(new ReservarLeito("oc-1", 1L, "UTI", List.of(10L), 2));

		LeitoReservado evento = (LeitoReservado) publicador.saga.get(0);
		assertEquals(10L, evento.leitoId());
		assertEquals("SILENCIO", evento.resultadoUnidade());
		assertEquals(false, unico.isConfirmado());
	}

	@Test
	void semNenhumLivreInformaFalta() {
		when(leitos.findByStatusAndTipoLeitoAndIdNotInOrderById(any(), any(), any())).thenReturn(List.of());
		when(leitos.findByStatusAndTipoLeitoOrderById(StatusLeito.LIVRE, "UTI")).thenReturn(List.of());

		servico.reservar(new ReservarLeito("oc-1", 1L, "UTI", List.of(10L), 2));

		LeitoReservado evento = (LeitoReservado) publicador.saga.get(0);
		assertEquals(false, evento.sucesso());
		assertEquals("SEM_LEITO_COMPATIVEL", evento.motivo());
	}

	private Leito leito(Long id, String codigo) {
		return new Leito(id, hospital, codigo, "UTI", StatusLeito.LIVRE);
	}

	private static final class Gravador implements Publicador {
		private final List<Object> saga = new ArrayList<>();

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
		}
	}
}
