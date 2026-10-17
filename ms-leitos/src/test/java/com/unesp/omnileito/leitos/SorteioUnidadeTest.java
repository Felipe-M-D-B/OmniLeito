package com.unesp.omnileito.leitos;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SorteioUnidadeTest {

	@Test
	void vazioSorteiaUmDosTres() {
		SorteioUnidade sorteio = new SorteioUnidade("");
		for (int i = 0; i < 30; i++) {
			assertTrue(EnumSet.allOf(ResultadoUnidade.class).contains(sorteio.sortear()));
		}
		assertEquals("", sorteio.modo());
	}

	@Test
	void valorFixoDevolveEsseValor() {
		SorteioUnidade sorteio = new SorteioUnidade(" ACEITE ");
		assertEquals(ResultadoUnidade.ACEITE, sorteio.sortear());
		assertEquals("ACEITE", sorteio.modo());
	}

	@Test
	void trocaEmExecucaoValeParaOProximoSorteio() {
		SorteioUnidade sorteio = new SorteioUnidade("");
		assertEquals("RECUSA", sorteio.definir("recusa"));
		assertEquals(ResultadoUnidade.RECUSA, sorteio.sortear());
		assertEquals("", sorteio.definir("  "));
		assertEquals("", sorteio.modo());
	}
}
