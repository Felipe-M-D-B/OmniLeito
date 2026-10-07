package com.unesp.omnileito.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlocacaoAmbulanciaEvent {
    private String ocorrenciaId;
    private Long ambulanciaId;
    private boolean sucesso;
    private String mensagemErro;
    private String tipoLeitoNecessario;
}
