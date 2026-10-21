CREATE TABLE paciente (
    id BIGINT NOT NULL AUTO_INCREMENT,
    nome VARCHAR(160) NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE ocorrencia (
    id CHAR(36) NOT NULL,
    paciente_id BIGINT NOT NULL,
    local VARCHAR(255) NOT NULL,
    prioridade VARCHAR(20) NOT NULL,
    tipo_leito VARCHAR(20) NOT NULL,
    status VARCHAR(30) NOT NULL,
    ambulancia_id BIGINT NULL,
    leito_id BIGINT NULL,
    unidade_nome VARCHAR(160) NULL,
    criada_em DATETIME(3) NOT NULL,
    atualizada_em DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_ocorrencia_paciente FOREIGN KEY (paciente_id) REFERENCES paciente (id),
    KEY idx_ocorrencia_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE despacho (
    id BIGINT NOT NULL AUTO_INCREMENT,
    ocorrencia_id CHAR(36) NOT NULL,
    tentativa INT NOT NULL,
    atualizado_em DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_despacho_ocorrencia (ocorrencia_id),
    CONSTRAINT fk_despacho_ocorrencia FOREIGN KEY (ocorrencia_id) REFERENCES ocorrencia (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE leito_excluido (
    id BIGINT NOT NULL AUTO_INCREMENT,
    ocorrencia_id CHAR(36) NOT NULL,
    leito_id BIGINT NOT NULL,
    motivo VARCHAR(30) NOT NULL,
    ocorrido_em DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_leito_excluido (ocorrencia_id, leito_id),
    CONSTRAINT fk_leito_excluido_ocorrencia FOREIGN KEY (ocorrencia_id) REFERENCES ocorrencia (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
