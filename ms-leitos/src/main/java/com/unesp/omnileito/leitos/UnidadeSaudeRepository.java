package com.unesp.omnileito.leitos;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UnidadeSaudeRepository extends JpaRepository<UnidadeSaude, Long> {

	Optional<UnidadeSaude> findByNome(String nome);
}
