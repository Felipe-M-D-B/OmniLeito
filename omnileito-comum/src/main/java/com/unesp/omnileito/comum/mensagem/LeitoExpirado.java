package com.unesp.omnileito.comum.mensagem;

public record LeitoExpirado(String ocorrenciaId, Long ambulanciaId, Long leitoId, String tipoLeito) {
}
