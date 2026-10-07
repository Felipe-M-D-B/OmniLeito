package com.unesp.omnileito.domain.enums;

public enum StatusOcorrencia {
    ABERTA,            // Criada e aguardando regulação / despacho
    EM_DESPACHO,       // Saga iniciada (tentando alocar ambulância e reservar leito)
    DESPACHADA,        // Saga concluída com sucesso (ambulância e leito confirmados)
    CANCELADA,         // Ocorrência cancelada ou falha não recuperável
    FALHA_DESPACHO     // Erro no processamento da Saga (necessita intervenção do regulador)
}
