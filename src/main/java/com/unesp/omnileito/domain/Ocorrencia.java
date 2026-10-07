package com.unesp.omnileito.domain;

import com.unesp.omnileito.domain.enums.StatusOcorrencia;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "ocorrencias")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Ocorrencia {

    @Id
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusOcorrencia status;

    @Column(name = "tipo_leito_necessario")
    private String tipoLeitoNecessario;

    @Column(name = "ambulancia_alocada_id")
    private Long ambulanciaAlocadaId;

    @Column(name = "leito_reservado_id")
    private Long leitoReservadoId;
}