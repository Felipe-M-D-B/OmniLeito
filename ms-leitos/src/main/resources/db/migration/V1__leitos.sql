CREATE TABLE unidade_saude (
    id BIGINT NOT NULL AUTO_INCREMENT,
    nome VARCHAR(160) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_unidade_nome (nome)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE leito (
    id BIGINT NOT NULL AUTO_INCREMENT,
    unidade_id BIGINT NOT NULL,
    codigo VARCHAR(40) NOT NULL,
    tipo_leito VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    ocorrencia_id CHAR(36) NULL,
    reservado_em DATETIME(3) NULL,
    confirmado BOOLEAN NOT NULL DEFAULT FALSE,
    confirmado_em DATETIME(3) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_leito_codigo (codigo),
    UNIQUE KEY uk_leito_ocorrencia (ocorrencia_id),
    CONSTRAINT fk_leito_unidade FOREIGN KEY (unidade_id) REFERENCES unidade_saude (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE reserva_recusada (
    id BIGINT NOT NULL AUTO_INCREMENT,
    ocorrencia_id CHAR(36) NOT NULL,
    leito_id BIGINT NOT NULL,
    motivo VARCHAR(30) NOT NULL,
    ocorrido_em DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_reserva_recusada_ocorrencia_leito (ocorrencia_id, leito_id),
    CONSTRAINT fk_reserva_recusada_leito FOREIGN KEY (leito_id) REFERENCES leito (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO unidade_saude (id, nome) VALUES
    (1, 'Hospital Central'),
    (2, 'UPA Norte');

INSERT INTO leito (unidade_id, codigo, tipo_leito, status, ocorrencia_id, reservado_em, confirmado, confirmado_em) VALUES
    (1, 'L-UTI-01', 'UTI', 'LIVRE', NULL, NULL, FALSE, NULL),
    (1, 'L-UTI-02', 'UTI', 'OCUPADO', NULL, NULL, FALSE, NULL),
    (2, 'L-EMG-01', 'EMERGENCIA', 'LIVRE', NULL, NULL, FALSE, NULL),
    (2, 'L-EMG-02', 'EMERGENCIA', 'OCUPADO', NULL, NULL, FALSE, NULL),
    (1, 'L-ENF-01', 'ENFERMARIA', 'LIVRE', NULL, NULL, FALSE, NULL),
    (1, 'L-ENF-02', 'ENFERMARIA', 'OCUPADO', NULL, NULL, FALSE, NULL);
