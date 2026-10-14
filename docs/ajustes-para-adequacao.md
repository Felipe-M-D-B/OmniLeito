# Ajustes para adequação às documentações — OmniLeito

- Data: 13/10/2026
- Base: `docs/documento-de-requisitos.md` 0.3, `docs/contratos-entre-servicos.md` 0.2, `docs/modelo-de-dados.md` 0.2 e `docs/maquina-de-estados.md` 0.2
- Código analisado: ramo `develop`, commit `0a6f773`

Este arquivo descreve o que muda em cada arquivo e por quê. A implementação não foi feita. O banco abaixo é o desenho que a migração passa a criar, com a carga inicial já na forma do modelo de dados.

A adequação segue a ordem já fechada. O Felipe separa os serviços e corrige a Saga. O Igor entra com gateway, identidade e Resilience4j. O cache, o job e o MS-Notificacoes entram em cima do banco já nascido com as colunas de reserva. O `application.properties` do monolito permanece na porta 3306. Os serviços novos usam MySQL na porta 3310.

## 1. Banco

O arquivo `src/main/resources/db/migration/V1__init_schema.sql` cria um banco só, sem paciente, prioridade, local, confirmação nem histórico. A carga grava `L-102` como `GERAL` e livre. O requisito fecha três especialidades, `UTI`, `EMERGENCIA` e `ENFERMARIA`, cada uma com um leito livre e um ocupado. O modelo pede um schema por serviço, e a primeira migração de leitos já nasce com `reservado_em`, `confirmado`, `confirmado_em` e `reserva_recusada`, para não haver uma segunda migração em cima da mesma tabela.

Na implementação autorizada, o `V1` atual sai do módulo único e cada serviço recebe a própria primeira migração, com o SQL desta seção. A instância da porta 3310 já aplicou o `V1` antigo. Recriar só os schemas `omnileito_*` dessa instância.

Não há chave estrangeira entre schemas. `ambulancia_id` e `leito_id` na ocorrência são referência.

### omnileito_identidade

Dono: Igor.

```sql
CREATE TABLE regulador (
    id BIGINT NOT NULL AUTO_INCREMENT,
    usuario VARCHAR(80) NOT NULL,
    senha_hash VARCHAR(100) NOT NULL,
    criado_em DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_regulador_usuario (usuario)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Senha de demonstração: regulador. A coluna recebe o hash BCrypt, nunca o texto puro.
-- O valor do hash é gerado na implementação e entra neste INSERT.
INSERT INTO regulador (usuario, senha_hash, criado_em)
VALUES ('regulador', '<hash-bcrypt-de-regulador>', CURRENT_TIMESTAMP(3));
```

Justificativa: RF01. O monolito não tem usuário nem token.

### omnileito_ocorrencias

Dono: Felipe.

```sql
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
```

Sem carga de ocorrência. O chamado nasce na abertura, em `ABERTA`.

Justificativa: RF02 e RN10. O tipo de leito fica na abertura e o despacho não o recebe de novo. `despacho.tentativa` distingue a primeira busca sem leito, que devolve a ambulância, da busca seguinte. `leito_excluido` guarda a preferência da próxima reserva e permite repetir o mesmo leito quando ele for o único livre.

### omnileito_frota

Dono da tabela: Felipe. Cache, depois: Sérgio Leandro, sem migração extra.

```sql
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
```

Justificativa: a placa continua a da carga atual, agora na coluna `identificacao`. `ocorrencia_id` impede duas ambulâncias no mesmo chamado. O único sobre coluna anulável aceita várias linhas `NULL`, então as duas podem nascer sem ocorrência.

### omnileito_leitos

Dono da primeira migração: Felipe, já com as colunas do job. Job e cache, depois: Sérgio Leandro, sem coluna nova.

```sql
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
    (1, 'L-UTI-01', 'UTI',         'LIVRE',   NULL, NULL, FALSE, NULL),
    (1, 'L-UTI-02', 'UTI',         'OCUPADO', NULL, NULL, FALSE, NULL),
    (2, 'L-EMG-01', 'EMERGENCIA',  'LIVRE',   NULL, NULL, FALSE, NULL),
    (2, 'L-EMG-02', 'EMERGENCIA',  'OCUPADO', NULL, NULL, FALSE, NULL),
    (1, 'L-ENF-01', 'ENFERMARIA',  'LIVRE',   NULL, NULL, FALSE, NULL),
    (1, 'L-ENF-02', 'ENFERMARIA',  'OCUPADO', NULL, NULL, FALSE, NULL);
```

`reserva_recusada` nasce vazia. Cada recusa ou expiração insere uma linha, inclusive quando o mesmo leito se repete.

Justificativa: some `GERAL` e o código `L-102`. A reserva passa a filtrar `tipo_leito`. `reservado_em` e `confirmado` separam silêncio de aceite, e o job só expira `RESERVADO` sem confirmação. O histórico não bloqueia o leito: a busca pede primeiro um id de fora da lista e, se não houver outro `LIVRE` da especialidade, reserva um id que já esteja nela.

### omnileito_notificacoes

Dono: Sérgio Leandro. MongoDB, sem coleção de SMS. Coleções `notificacoes` e `auditoria`, com índice único em (`ocorrenciaId`, `tipo`, `ocorridaEm`) e, na auditoria, também `origem`. Entra quando o módulo existir, não numa alteração do `V1` atual.

## 2. Arquivos que já existem

### `src/main/resources/db/migration/V1__init_schema.sql`

Deixa de ser a migração do módulo único. O conteúdo da seção 1 toma o lugar dele, cada bloco no serviço dono.

Justificativa: o `V1` atual não cabe nos quatro schemas e a carga `GERAL` fere RN10.

### `domain/Ocorrencia.java`

Campos passam a ser os da tabela `ocorrencia`: `paciente`, `local`, `prioridade`, `tipoLeito`, `ambulanciaId`, `leitoId`, `unidadeNome`, `criadaEm`, `atualizadaEm`. Some `tipoLeitoNecessario`.

Justificativa: a abertura grava o tipo uma vez. O nome do paciente fica em `paciente`, não numa coluna solta da ocorrência.

### `domain/Ambulancia.java`

`placa` vira `identificacao`. Entra `ocorrenciaId`, nulo quando `DISPONIVEL` ou `INDISPONIVEL`.

Justificativa: o modelo usa `identificacao`. A coluna da ocorrência é o que impede alocar a mesma viatura duas vezes.

### `domain/Leito.java`

Entram `unidade`, `tipoLeito` no lugar de `tipoEspecialidade`, `ocorrenciaId`, `reservadoEm`, `confirmado` e `confirmadoEm`.

Justificativa: sem essas colunas não há prazo, aceite nem “qual leito está com qual chamado”.

### `domain/enums/StatusOcorrencia.java`

Inclui `EM_TRANSPORTE` e `CONCLUIDA`. Mantém `ABERTA`, `EM_DESPACHO`, `DESPACHADA`, `CANCELADA` e `FALHA_DESPACHO`.

Justificativa: a lista fechada de status. `EM_TRANSPORTE` e `CONCLUIDA` existem no modelo e este fluxo não os alcança. `CANCELADA` deixa de significar “falha não recuperável”: falha de despacho é `FALHA_DESPACHO`.

### `domain/enums/StatusAmbulancia.java`

`EM_DESLOCAMENTO` sai. Entram `DESPACHADA` e `AGUARDANDO_LEITO`. Ficam `DISPONIVEL`, `ALOCADA`, `EM_ATENDIMENTO` e `INDISPONIVEL`.

Justificativa: a máquina de estados. Aceite ou silêncio leva a `DESPACHADA`. Recusa ou prazo leva a `AGUARDANDO_LEITO`. A volta a `DISPONIVEL` fica só na primeira tentativa sem leito. `EM_ATENDIMENTO` permanece no modelo e a Saga desta entrega não o grava.

### `domain/enums/StatusLeito.java`

Ficam `LIVRE`, `RESERVADO` e `OCUPADO`. `EXPIRADO` pode permanecer no enum só como passagem dentro da transação: o commit grava `LIVRE`. `INDISPONIVEL` sai do leito.

Justificativa: o estado commitado do leito não tem `EXPIRADO` nem `INDISPONIVEL`. O motivo da recusa ou do prazo fica em `reserva_recusada`.

### `domain/enums/StatusSaga.java`

Sai. Nenhuma classe o usa, e os comentários descrevem a compensação antiga, em que a ambulância sempre volta a ficar disponível.

Justificativa: o estado da Saga é o status da ocorrência mais o status da ambulância e o do leito, como no quadro da máquina de estados.

### `dto/DespachoRequestDTO.java`

Sai deste fluxo. A abertura passa a ter o corpo com `nomePaciente`, `local`, `prioridade` e `tipoLeito`. O despacho não tem corpo.

Justificativa: RF02 e RF06. O exemplo `GERAL` no campo `tipoLeitoNecessario` deixa de existir.

### `dto/AlocacaoAmbulanciaEvent.java` e `dto/ReservaLeitoEvent.java`

Viraram mensagens separadas, no formato do contrato, publicadas só depois do commit:

| Mensagem | Quem publica | Quem consome |
| --- | --- | --- |
| `AlocarAmbulancia` | MS-Ocorrencias | MS-Frota |
| `AmbulanciaAlocada` | MS-Frota | MS-Ocorrencias |
| `ReservarLeito` | MS-Ocorrencias | MS-Leitos |
| `LeitoReservado` | MS-Leitos | MS-Ocorrencias |
| `LeitoExpirado` | MS-Leitos | MS-Ocorrencias |
| `AtualizarAmbulancia` | MS-Ocorrencias | MS-Frota |
| `RetentarBuscaLeito` | MS-Leitos | MS-Ocorrencias |
| `RegistrarNotificacao` | MS-Ocorrencias e MS-Leitos | MS-Notificacoes |

`ReservarLeito` leva `tipoLeito`, `leitosRecusados` e `tentativa`. `leitosRecusados` ordena a escolha e não proíbe o leito.

Justificativa: hoje o ouvinte chama o orquestrador no mesmo processo e a mensagem de alocação sai dentro da transação. Uma repetição pode alocar outra ambulância antes do commit. O contrato separa pedido e resposta.

### `configuration/RabbitMQConfig.java`

O exchange passa a ser `omnileito.saga` para a Saga e `omnileito.notificacao` para o alerta. As filas duráveis são as da tabela acima. Saem `saga.liberar-ambulancia` e `saga.cancelar-leito`: a liberação da ambulância é `AtualizarAmbulancia` com `DISPONIVEL`, e o cancelamento do leito é a própria recusa ou o job, dentro do MS-Leitos.

Justificativa: `saga.cancelar-leito` está declarada e não tem consumidor. A compensação nova não é “liberar a ambulância em toda falha de leito”.

### `controller/DespachoController.java`

`POST /api/despachos` sai. No MS-Ocorrencias entram:

- `POST /api/ocorrencias`, resposta `201` com id, status `ABERTA` e `tipoLeito`
- `GET /api/ocorrencias/{ocorrenciaId}`
- `POST /api/ocorrencias/{ocorrenciaId}/despachos`, corpo vazio, resposta `202`

Um segundo despacho da mesma chave responde `200` com o estado atual e não aloca de novo. `CANCELADA` e `CONCLUIDA` respondem `409`. `FALHA_DESPACHO` não recomeça sozinho.

Justificativa: RF03 e RF07. O controlador atual cria a ocorrência já em `EM_DESPACHO` e aceita o tipo no corpo.

### `service/SagaOrchestratorService.java`

`iniciarDespacho` deixa de criar ocorrência. Ele só aceita uma ocorrência `ABERTA`, grava `EM_DESPACHO`, cria `despacho` com `tentativa` 1 e, depois do commit, publica `AlocarAmbulancia`.

O resultado da ambulância e o do leito passam a seguir o quadro conjunto:

| Resultado | Ocorrência | Ambulância | Leito |
| --- | --- | --- | --- |
| Sem ambulância | `FALHA_DESPACHO` | nenhuma muda | nenhum |
| Sem leito na tentativa 1 | `FALHA_DESPACHO` | `DISPONIVEL` | nenhum |
| Aceite | `DESPACHADA` | `DESPACHADA` | `RESERVADO` confirmado |
| Silêncio | `DESPACHADA` | `DESPACHADA` | `RESERVADO` sem confirmação |
| Recusa ou prazo | `EM_DESPACHO` | `AGUARDANDO_LEITO` | aquele leito volta a `LIVRE` |
| Nova busca sem nenhum `LIVRE` da especialidade | `EM_DESPACHO` | `AGUARDANDO_LEITO` | nenhum |
| Novo aceite, noutro leito ou no mesmo se for o único livre | `DESPACHADA` | `DESPACHADA` | `RESERVADO` confirmado |

Justificativa: `processarResultadoLeito` hoje, em qualquer falha, publica a liberação da ambulância e grava `FALHA_DESPACHO`. Isso só vale na primeira tentativa sem leito. Recusa e prazo mantêm a equipe no chamado.

### `listener/SagaEventListener.java`

Cada fila fica no serviço dono. O MS-Frota, ao consumir `AlocarAmbulancia`, faz `UPDATE` condicional de `DISPONIVEL` para `ALOCADA`. Se a ocorrência já tiver ambulância, devolve essa mesma linha. O MS-Leitos, ao consumir `ReservarLeito`, atualiza um leito `LIVRE` do `tipo_leito` pedido, primeiro fora de `leitosRecusados`, depois qualquer `LIVRE` da especialidade. Em seguida sorteia aceite, recusa ou silêncio.

Some `findFirstByStatus`. Some a chamada direta ao orquestrador.

Justificativa: a busca atual ignora a especialidade e não é exclusiva. Duas mensagens da mesma ocorrência podem pegar duas ambulâncias ou dois leitos. O teste local já mostrou leito `GERAL` preso a um chamado de UTI.

### `repository/AmbulanciaRepository.java` e `repository/LeitoRepository.java`

A alocação e a reserva viram atualização condicional, com a condição no `WHERE`. A consulta de leito filtra `tipoLeito` e `LIVRE`.

Justificativa: `findFirstByStatus` não garante uma linha só nem a especialidade gravada na abertura.

### `OmniLeitoApplication.java`

Continua o arranque de cada processo depois da separação. `@EnableScheduling` entra só no MS-Leitos, junto com o job. Não entra no módulo único antes disso.

### `pom.xml`

As dependências de MongoDB, Redis e Resilience4j permanecem disponíveis e passam a ser usadas no módulo dono: Redis em Frota e Leitos, Resilience4j no gateway na chamada `GET /api/leitos/disponiveis`, MongoDB no MS-Notificacoes. Nenhuma delas passa a ser ligada dentro do processo único atual.

### `README.md`

O fluxo descrito deixa de ser `POST /api/despachos` com tipo no corpo, MySQL na porta 3306 e especialidade `GERAL`. Passa a apontar para a abertura, o despacho sem corpo e a carga da seção 1.

### `src/test/java/com/unesp/omnileito/OmniLeitoApplicationTests.java`

O teste de contexto permanece. Os testes novos cobrem alocação exclusiva, despacho repetido, primeira busca sem leito, recusa, prazo e a repetição do mesmo leito quando ele é o único livre da especialidade.

Justificativa: RNF05. Hoje só existe `contextLoads`.

## 3. Arquivos que ainda não existem

Entram na implementação autorizada, cada um no módulo dono. Não são um ajuste do monolito.

| Arquivo novo | Dono | Justificativa |
| --- | --- | --- |
| Abertura e consulta de ocorrência | Felipe | RF02 e RF03. O despacho deixa de criar o chamado |
| Login e emissão do JWT | Igor | RF01. A regulação entra com `Authorization: Bearer` |
| Gateway e rotas da seção 1 do contrato | Igor | RF04. Uma porta só para a regulação |
| Resilience4j em `GET /api/leitos/disponiveis` | Igor | RF05. Circuito aberto responde `503` com `SERVICO_LEITOS_INDISPONIVEL` e não impede abertura nem despacho |
| Sorteio da unidade no MS-Leitos | Felipe | RF13. Aceite, recusa ou silêncio a cada reserva, sem endpoint público do hospital |
| Consulta de disponíveis e cache Redis | Sérgio Leandro | RF17 a RF19. A chave nasce na leitura e é apagada em toda escrita de ocupação |
| Job de expiração, `LeitoExpirado` e `RetentarBuscaLeito` | Sérgio Leandro | RF20 a RF23. O prazo padrão é 2 minutos, propriedade `omnileito.reserva.expiracao-minutos` |
| MS-Notificacoes | Sérgio Leandro | RF24. Grava `DESPACHO`, `EXPIRACAO` e `FALTA_LEITO` |

## 4. O que esta adequação não muda

- Porta e senha do `application.properties` do monolito. O arquivo continua apontando para 3306. Os serviços novos usam a porta 3310.
- A regra local do `.cursor`, que permanece fora do commit.
