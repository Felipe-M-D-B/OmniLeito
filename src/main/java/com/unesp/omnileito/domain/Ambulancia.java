package com.unesp.omnileito.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.unesp.omnileito.domain.enums.StatusAmbulancia;

@Entity
@Table(name = "ambulancias")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Ambulancia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String placa;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private StatusAmbulancia status;
}