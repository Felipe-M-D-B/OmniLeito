package com.unesp.omnileito.ocorrencias;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReinicioCargaService {

	private final EsvaziadorFilas filas;
	private final LeitoExcluidoRepository excluidos;
	private final DespachoRepository despachos;
	private final OcorrenciaRepository ocorrencias;
	private final PacienteRepository pacientes;
	private final PausaFilas pausa;

	public ReinicioCargaService(EsvaziadorFilas filas, LeitoExcluidoRepository excluidos, DespachoRepository despachos,
			OcorrenciaRepository ocorrencias, PacienteRepository pacientes, PausaFilas pausa) {
		this.filas = filas;
		this.excluidos = excluidos;
		this.despachos = despachos;
		this.ocorrencias = ocorrencias;
		this.pacientes = pacientes;
		this.pausa = pausa;
	}

	@Transactional
	public void reiniciar() {
		filas.esvaziar();
		excluidos.deleteAllInBatch();
		despachos.deleteAllInBatch();
		ocorrencias.deleteAllInBatch();
		pacientes.deleteAllInBatch();
		pausa.retomar();
	}
}
