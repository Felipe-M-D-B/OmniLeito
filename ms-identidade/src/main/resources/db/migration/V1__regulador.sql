CREATE TABLE regulador (
    id BIGINT NOT NULL AUTO_INCREMENT,
    usuario VARCHAR(80) NOT NULL,
    senha_hash VARCHAR(100) NOT NULL,
    criado_em DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_regulador_usuario (usuario)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- A coluna não guarda a senha em texto. O arranque troca este marcador pelo hash BCrypt de "regulador".
INSERT INTO regulador (usuario, senha_hash, criado_em)
VALUES ('regulador', 'TROCAR_NO_ARRANQUE', CURRENT_TIMESTAMP(3));
