package com.unesp.omnileito.domain.enums;

public enum StatusLeito {
    LIVRE,             // Vaga disponível no hospital
    RESERVADO,         // Reservado temporariamente pela Saga (aguarda confirmação/aceite da unidade)
    OCUPADO,           // Paciente deu entrada no leito
    EXPIRADO,          // Reserva não confirmada no tempo limite (gerenciado pelo Job do Membro 3)
    INDISPONIVEL       // Interditado/Manutenção
}
