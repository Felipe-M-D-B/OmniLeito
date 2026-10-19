package com.unesp.omnileito.ocorrencias;

import com.unesp.omnileito.comum.Filas;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class EsvaziadorFilas {

	private static final List<String> FILAS = List.of(
			Filas.ALOCAR_AMBULANCIA,
			Filas.AMBULANCIA_ALOCADA,
			Filas.RESERVAR_LEITO,
			Filas.LEITO_RESERVADO,
			Filas.LEITO_EXPIRADO,
			Filas.ATUALIZAR_AMBULANCIA,
			Filas.RETENTAR_BUSCA_LEITO,
			Filas.REGISTRAR_NOTIFICACAO);

	private final RabbitAdmin admin;

	public EsvaziadorFilas(ConnectionFactory conexoes) {
		this.admin = new RabbitAdmin(conexoes);
	}

	public void esvaziar() {
		for (String fila : FILAS) {
			try {
				admin.purgeQueue(fila);
			} catch (RuntimeException ignorado) {
				// Fila ausente não impede a carga de voltar ao MySQL.
			}
		}
	}
}
