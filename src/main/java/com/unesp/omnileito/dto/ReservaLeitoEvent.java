package com.unesp.omnileito.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservaLeitoEvent {
    private String ocorrenciaId;
    private Long ambulanciaId;
    private Long leitoId;
    private boolean sucesso;
    private String mensagemErro;
}
