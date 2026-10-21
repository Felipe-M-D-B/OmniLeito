CREATE TABLE ambulancia (
    id BIGINT NOT NULL AUTO_INCREMENT,
    identificacao VARCHAR(20) NOT NULL,
    status VARCHAR(30) NOT NULL,
    ocorrencia_id CHAR(36) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ambulancia_identificacao (identificacao),
    UNIQUE KEY uk_ambulancia_ocorrencia (ocorrencia_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO ambulancia (id, identificacao, status, ocorrencia_id) VALUES
    (1, 'ABC-1234', 'DISPONIVEL', NULL),
    (2, 'XYZ-5678', 'DISPONIVEL', NULL);
