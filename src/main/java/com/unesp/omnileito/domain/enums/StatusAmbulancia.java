package com.unesp.omnileito.domain.enums;

public enum StatusAmbulancia {
    DISPONIVEL,        // Pronta para receber chamados
    ALOCADA,           // Reservada temporariamente durante a execução da Saga
    EM_DESLOCAMENTO,   // Despacho confirmado, a caminho da ocorrência
    EM_ATENDIMENTO,    // Na ocorrência ou hospital
    INDISPONIVEL       // Em manutenção ou fora de serviço
}
