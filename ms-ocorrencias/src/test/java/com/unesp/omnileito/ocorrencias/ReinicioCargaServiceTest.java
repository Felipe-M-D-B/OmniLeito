package com.unesp.omnileito.ocorrencias;

import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

class ReinicioCargaServiceTest {

	@Test
	void esvaziaFilasAntesDeApagarOcorrencias() {
		EsvaziadorFilas filas = mock(EsvaziadorFilas.class);
		LeitoExcluidoRepository excluidos = mock(LeitoExcluidoRepository.class);
		DespachoRepository despachos = mock(DespachoRepository.class);
		OcorrenciaRepository ocorrencias = mock(OcorrenciaRepository.class);
		PacienteRepository pacientes = mock(PacienteRepository.class);
		PausaFilas pausa = mock(PausaFilas.class);
		ReinicioCargaService servico = new ReinicioCargaService(filas, excluidos, despachos, ocorrencias, pacientes, pausa);

		servico.reiniciar();

		var ordem = inOrder(filas, excluidos, despachos, ocorrencias, pacientes, pausa);
		ordem.verify(filas).esvaziar();
		ordem.verify(excluidos).deleteAllInBatch();
		ordem.verify(despachos).deleteAllInBatch();
		ordem.verify(ocorrencias).deleteAllInBatch();
		ordem.verify(pacientes).deleteAllInBatch();
		ordem.verify(pausa).retomar();
	}
}
