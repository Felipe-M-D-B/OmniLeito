package com.unesp.omnileito.comum;

public interface Publicador {

	void depoisDoCommit(Runnable acao);

	void saga(String fila, Object mensagem);

	void notificacao(Object mensagem);
}
