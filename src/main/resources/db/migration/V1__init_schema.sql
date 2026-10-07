-- Criação da tabela de Ocorrências
CREATE TABLE IF NOT EXISTS ocorrencias (
                                           id VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL,
    tipo_leito_necessario VARCHAR(100),
    ambulancia_alocada_id BIGINT,
    leito_reservado_id BIGINT,
    PRIMARY KEY (id)
    );

-- Criação da tabela de Ambulâncias
CREATE TABLE IF NOT EXISTS ambulancias (
                                           id BIGINT AUTO_INCREMENT NOT NULL,
                                           placa VARCHAR(20) NOT NULL,
    status VARCHAR(50) NOT NULL,
    PRIMARY KEY (id)
    );

-- Criação da tabela de Leitos
CREATE TABLE IF NOT EXISTS leitos (
                                      id BIGINT AUTO_INCREMENT NOT NULL,
                                      codigo VARCHAR(50) NOT NULL,
    tipo_especialidade VARCHAR(100) NOT NULL,
    status VARCHAR(50) NOT NULL,
    PRIMARY KEY (id)
    );

-- Inserção de dados iniciais de teste (Opcional)
INSERT INTO ambulancias (placa, status) VALUES ('ABC-1234', 'DISPONIVEL');
INSERT INTO ambulancias (placa, status) VALUES ('XYZ-5678', 'DISPONIVEL');

INSERT INTO leitos (codigo, tipo_especialidade, status) VALUES ('L-101', 'UTI', 'LIVRE');
INSERT INTO leitos (codigo, tipo_especialidade, status) VALUES ('L-102', 'GERAL', 'LIVRE');