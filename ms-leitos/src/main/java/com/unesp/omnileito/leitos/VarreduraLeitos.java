package com.unesp.omnileito.leitos;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
class VarreduraLeitos {

	private final AtomicBoolean pausada = new AtomicBoolean();

	void pausar() {
		pausada.set(true);
	}

	void retomar() {
		pausada.set(false);
	}

	boolean pausada() {
		return pausada.get();
	}
}
