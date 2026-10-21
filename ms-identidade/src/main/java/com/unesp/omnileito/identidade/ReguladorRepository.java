package com.unesp.omnileito.identidade;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReguladorRepository extends JpaRepository<Regulador, Long> {

	Optional<Regulador> findByUsuario(String usuario);
}
