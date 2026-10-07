package com.unesp.omnileito.domain;

import com.unesp.omnileito.domain.enums.StatusLeito;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "leitos")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Leito {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String codigo;

    @Column(name = "tipo_especialidade")
    private String tipoEspecialidade;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusLeito status;
}