package com.unesp.omnileito.notificacoes;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface AuditoriaRepository extends MongoRepository<AuditoriaDocumento, String> {
}
