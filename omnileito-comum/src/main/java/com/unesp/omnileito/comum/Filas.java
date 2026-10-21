package com.unesp.omnileito.comum;

public final class Filas {

	public static final String EXCHANGE_SAGA = "omnileito.saga";
	public static final String EXCHANGE_NOTIFICACAO = "omnileito.notificacao";

	public static final String ALOCAR_AMBULANCIA = "saga.alocar-ambulancia";
	public static final String AMBULANCIA_ALOCADA = "saga.ambulancia-alocada";
	public static final String RESERVAR_LEITO = "saga.reservar-leito";
	public static final String LEITO_RESERVADO = "saga.leito-reservado";
	public static final String LEITO_EXPIRADO = "saga.leito-expirado";
	public static final String ATUALIZAR_AMBULANCIA = "saga.atualizar-ambulancia";
	public static final String RETENTAR_BUSCA_LEITO = "saga.retentar-busca-leito";
	public static final String REGISTRAR_NOTIFICACAO = "notificacao.registrar";

	private Filas() {
	}
}
