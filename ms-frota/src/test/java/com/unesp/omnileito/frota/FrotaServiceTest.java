package com.unesp.omnileito.frota;

import com.unesp.omnileito.comum.Publicador;
import com.unesp.omnileito.comum.mensagem.AlocarAmbulancia;
import com.unesp.omnileito.comum.mensagem.AmbulanciaAlocada;
import com.unesp.omnileito.comum.mensagem.AtualizarAmbulancia;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FrotaServiceTest {

	private AmbulanciaRepository ambulancias;
	private CacheOcupacao cache;
	private Gravador publicador;
	private FrotaService servico;

	@BeforeEach
	void preparar() {
		ambulancias = mock(AmbulanciaRepository.class);
		cache = mock(CacheOcupacao.class);
		publicador = new Gravador();
		servico = new FrotaService(ambulancias, cache, publicador);
	}

	@Test
	void segundaAlocacaoDaMesmaOcorrenciaDevolveAMesmaAmbulancia() {
		Ambulancia existente = new Ambulancia();
		existente.setStatus(StatusAmbulancia.ALOCADA);
		existente.setOcorrenciaId("oc-1");
		when(ambulancias.findByOcorrenciaId("oc-1")).thenReturn(Optional.of(existente));
		when(ambulancias.findById(1L)).thenReturn(Optional.of(existente));

		servico.alocar(new AlocarAmbulancia("oc-1", "VERMELHA"));

		AmbulanciaAlocada evento = (AmbulanciaAlocada) publicador.saga.get(0);
		assertEquals(true, evento.sucesso());
		assertEquals(existente.getId(), evento.ambulanciaId());
	}

	@Test
	void voltaParaDisponivelSoQuandoPedido() {
		Ambulancia ambulancia = new Ambulancia();
		ambulancia.setStatus(StatusAmbulancia.ALOCADA);
		ambulancia.setOcorrenciaId("oc-1");
		when(ambulancias.findById(1L)).thenReturn(Optional.of(ambulancia));

		servico.atualizar(new AtualizarAmbulancia("oc-1", 1L, "DISPONIVEL"));

		assertEquals(StatusAmbulancia.DISPONIVEL, ambulancia.getStatus());
		assertNull(ambulancia.getOcorrenciaId());
	}

	@Test
	void reinicioDevolveAmbulanciasDisponiveis() {
		Ambulancia ambulancia = new Ambulancia();
		ambulancia.setStatus(StatusAmbulancia.DESPACHADA);
		ambulancia.setOcorrenciaId("oc-1");
		when(ambulancias.findAll()).thenReturn(List.of(ambulancia));

		servico.reiniciarCarga();

		assertEquals(StatusAmbulancia.DISPONIVEL, ambulancia.getStatus());
		assertNull(ambulancia.getOcorrenciaId());
		verify(cache).apagar(CacheOcupacao.FROTA);
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
