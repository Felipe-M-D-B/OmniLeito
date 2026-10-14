package com.unesp.omnileito.comum.mensagem;

import java.time.OffsetDateTime;

public record RegistrarNotificacao(String ocorrenciaId, String tipo, String mensagem, OffsetDateTime ocorridaEm) {
}
