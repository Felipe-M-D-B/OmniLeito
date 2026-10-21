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

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class AcumuloFilasService {

	static final String OCORRENCIA = "inspecao-fila";
	private static final OffsetDateTime QUANDO = OffsetDateTime.parse("2026-10-18T03:00:00Z");

	private final Publicador publicador;

	public AcumuloFilasService(Publicador publicador) {
		this.publicador = publicador;
	}

	public void acumular(int quantidade) {
		if (quantidade < 1 || quantidade > 200) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "CAMPO_INVALIDO", "Campo ausente ou valor fora da lista fechada.");
		}
		for (int indice = 0; indice < quantidade; indice++) {
			publicador.saga(Filas.ALOCAR_AMBULANCIA, new AlocarAmbulancia(OCORRENCIA, "VERDE"));
			publicador.saga(Filas.AMBULANCIA_ALOCADA, new AmbulanciaAlocada(OCORRENCIA, false, null, null, "INSPECAO"));
			publicador.saga(Filas.RESERVAR_LEITO, new ReservarLeito(OCORRENCIA, null, "ENFERMARIA", List.of(), 1));
			publicador.saga(Filas.LEITO_RESERVADO, new LeitoReservado(OCORRENCIA, null, 1, false, null, null, "ENFERMARIA",
					null, null, null, "INSPECAO"));
			publicador.saga(Filas.LEITO_EXPIRADO, new LeitoExpirado(OCORRENCIA, null, null, "ENFERMARIA"));
			publicador.saga(Filas.ATUALIZAR_AMBULANCIA, new AtualizarAmbulancia(OCORRENCIA, null, "DISPONIVEL"));
			publicador.saga(Filas.RETENTAR_BUSCA_LEITO, new RetentarBuscaLeito("ENFERMARIA"));
			publicador.notificacao(new RegistrarNotificacao(OCORRENCIA, "DESPACHO", "Inspeção da fila.", QUANDO));
		}
	}
}
