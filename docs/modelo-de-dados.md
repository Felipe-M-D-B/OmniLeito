# Modelo de dados de cada serviço — OmniLeito

Documento local. Não vai para o Git.

- Versão: 0.2 — 13/10/2026. O histórico de recusa e expiração ordena a próxima reserva e não impede repetir o mesmo leito
- Base: `docs/documento-de-requisitos.md` versão 0.3 e `docs/contratos-entre-servicos.md` versão 0.2

Cada serviço tem o próprio banco. Não há chave estrangeira entre eles. O id de ambulância e o id de leito gravados na ocorrência são referência, não vínculo do MySQL.

Convenções:

- MySQL 8, InnoDB, `utf8mb4`. Coluna em snake_case. O JSON da API permanece em camelCase, como no contrato.
- Na máquina local, os schemas ficam no MySQL da porta 3310: `omnileito_identidade`, `omnileito_ocorrencias`, `omnileito_frota`, `omnileito_leitos`. O MongoDB usa o database `omnileito_notificacoes`. Nada disso usa o banco do Ameppre nem o do Coneximob.
- Senha de regulador só entra como hash. A carga guarda o hash de `regulador`, nunca o texto puro em coluna de aplicação.
- `EXPIRADO` não permanece gravado. Dentro da transação o leito passa por esse status e o commit deixa `LIVRE`. O histórico guarda o motivo.
- O cache não é fonte da verdade. MySQL é. Redis só guarda a lista de disponíveis e é apagado em toda escrita de ocupação.

## 1. MS-Identidade

Schema `omnileito_identidade`.

### regulador

| Coluna | Tipo | Nulo | Regra |
| --- | --- | --- | --- |
| id | BIGINT, auto incremento | não | chave primária |
| usuario | VARCHAR(80) | não | único |
| senha_hash | VARCHAR(100) | não | hash da senha |
| criado_em | DATETIME(3) | não | instante da carga ou do cadastro |

Carga: um registro, usuário `regulador`, senha de demonstração `regulador`.

## 2. MS-Ocorrencias

Schema `omnileito_ocorrencias`.

### paciente

| Coluna | Tipo | Nulo | Regra |
| --- | --- | --- | --- |
| id | BIGINT, auto incremento | não | chave primária |
| nome | VARCHAR(160) | não | nome informado na abertura |

Não há prontuário, documento nem data de nascimento neste recorte.

### ocorrencia

| Coluna | Tipo | Nulo | Regra |
| --- | --- | --- | --- |
| id | CHAR(36) | não | chave primária. UUID gerado na abertura |
| paciente_id | BIGINT | não | chave estrangeira para `paciente` neste mesmo banco |
| local | VARCHAR(255) | não | texto livre |
| prioridade | VARCHAR(20) | não | `VERMELHA`, `AMARELA` ou `VERDE` |
| tipo_leito | VARCHAR(20) | não | `UTI`, `EMERGENCIA` ou `ENFERMARIA`. Não muda depois da abertura |
| status | VARCHAR(30) | não | `ABERTA`, `EM_DESPACHO`, `DESPACHADA`, `EM_TRANSPORTE`, `CONCLUIDA`, `CANCELADA`, `FALHA_DESPACHO` |
| ambulancia_id | BIGINT | sim | referência ao MS-Frota. Nulo enquanto `ABERTA` |
| leito_id | BIGINT | sim | referência ao MS-Leitos. Nulo sem reserva ativa |
| unidade_nome | VARCHAR(160) | sim | nome da unidade do leito atual, copiado da mensagem `LeitoReservado` |
| criada_em | DATETIME(3) | não | |
| atualizada_em | DATETIME(3) | não | muda a cada transição de status |

Índice em `status` para o consumidor de `RetentarBuscaLeito` achar ocorrências `EM_DESPACHO`.

### despacho

Uma ocorrência tem zero ou um despacho. A linha nasce no `POST /despachos`, quando o status vai para `EM_DESPACHO`.

| Coluna | Tipo | Nulo | Regra |
| --- | --- | --- | --- |
| id | BIGINT, auto incremento | não | chave primária |
| ocorrencia_id | CHAR(36) | não | único. Chave estrangeira para `ocorrencia` |
| tentativa | INT | não | começa em 1 no primeiro `ReservarLeito`. Sobe a cada recusa ou expiração |
| atualizado_em | DATETIME(3) | não | |

A lista de leitos já recusados ou expirados não decide sozinha a reserva. Ela mora no MS-Leitos. A mensagem `ReservarLeito` leva a lista porque o MS-Ocorrencias a recebe em `LeitoReservado` e `LeitoExpirado` e a reenvia. Para não perdê-la se o processo cair, o MS-Ocorrencias também guarda a lista na tabela abaixo. A reserva usa essa lista só para preferir outro leito.

### leito_excluido

| Coluna | Tipo | Nulo | Regra |
| --- | --- | --- | --- |
| id | BIGINT, auto incremento | não | chave primária |
| ocorrencia_id | CHAR(36) | não | chave estrangeira para `ocorrencia` |
| leito_id | BIGINT | não | id no MS-Leitos |
| motivo | VARCHAR(30) | não | `RECUSA_UNIDADE` ou `EXPIRACAO` |
| ocorrido_em | DATETIME(3) | não | |

Único em (`ocorrencia_id`, `leito_id`): um leito entra uma vez nessa lista, mesmo que seja recusado ou expirado de novo. Numa repetição, atualizam-se `motivo` e `ocorrido_em`. Na hora de publicar `ReservarLeito`, `leitosRecusados` é o conjunto desses `leito_id`.

## 3. MS-Frota

Schema `omnileito_frota`.

### ambulancia

| Coluna | Tipo | Nulo | Regra |
| --- | --- | --- | --- |
| id | BIGINT, auto incremento | não | chave primária |
| identificacao | VARCHAR(20) | não | único. Placa ou código de chamada |
| status | VARCHAR(30) | não | `DISPONIVEL`, `ALOCADA`, `DESPACHADA`, `EM_ATENDIMENTO`, `AGUARDANDO_LEITO`, `INDISPONIVEL` |
| ocorrencia_id | CHAR(36) | sim | ocorrência dona. Nulo quando `DISPONIVEL` ou `INDISPONIVEL` |

Único em `ocorrencia_id` quando não é nulo: uma ocorrência não fica com duas ambulâncias.

Alocar é uma atualização condicional: só passa de `DISPONIVEL` para `ALOCADA` se o status ainda for `DISPONIVEL`. Se a ocorrência já tiver ambulância, a operação devolve essa mesma linha.

### Cache

Chave Redis `frota:disponiveis`. Valor: JSON no formato de `GET /api/ambulancias/disponiveis`. Sem TTL obrigatório. A escrita de status apaga a chave. Consulta com chave ausente lê o MySQL, filtra `DISPONIVEL` e grava de novo.

Carga:

| id | identificacao | status |
| --- | --- | --- |
| 1 | ABC-1234 | DISPONIVEL |
| 2 | XYZ-5678 | DISPONIVEL |

## 4. MS-Leitos

Schema `omnileito_leitos`.

### unidade_saude

| Coluna | Tipo | Nulo | Regra |
| --- | --- | --- | --- |
| id | BIGINT, auto incremento | não | chave primária |
| nome | VARCHAR(160) | não | único |

### leito

| Coluna | Tipo | Nulo | Regra |
| --- | --- | --- | --- |
| id | BIGINT, auto incremento | não | chave primária |
| unidade_id | BIGINT | não | chave estrangeira para `unidade_saude` neste banco |
| codigo | VARCHAR(40) | não | único |
| tipo_leito | VARCHAR(20) | não | `UTI`, `EMERGENCIA` ou `ENFERMARIA` |
| status | VARCHAR(20) | não | `LIVRE`, `RESERVADO` ou `OCUPADO` no estado commitado |
| ocorrencia_id | CHAR(36) | sim | dona da reserva. Nulo quando `LIVRE` ou `OCUPADO` |
| reservado_em | DATETIME(3) | sim | início da reserva. Nulo fora de `RESERVADO` |
| confirmado | BOOLEAN | não | padrão false. True só depois do aceite da unidade |
| confirmado_em | DATETIME(3) | sim | horário do aceite |

Único em `ocorrencia_id` quando não é nulo: uma ocorrência não fica com dois leitos reservados.

Reservar é uma atualização condicional sobre um leito `LIVRE` do `tipo_leito` pedido. O `UPDATE` leva `status = 'LIVRE'` na cláusula. A primeira tentativa restringe o id aos que estão fora da lista recusada. Se nenhuma linha mudar, uma segunda tentativa repete o `UPDATE` sem essa restrição. `SEM_LEITO_COMPATIVEL` só ocorre quando essa segunda tentativa também não altera linha.

No aceite: `confirmado` true e `confirmado_em` preenchido, status continua `RESERVADO`.

Na recusa ou no prazo: grava o histórico, depois `status` `LIVRE`, e zera `ocorrencia_id`, `reservado_em`, `confirmado` e `confirmado_em`.

O job seleciona `status = 'RESERVADO' AND confirmado = false AND reservado_em` anterior ao prazo.

### reserva_recusada

| Coluna | Tipo | Nulo | Regra |
| --- | --- | --- | --- |
| id | BIGINT, auto incremento | não | chave primária |
| ocorrencia_id | CHAR(36) | não | |
| leito_id | BIGINT | não | chave estrangeira para `leito` |
| motivo | VARCHAR(30) | não | `RECUSA_UNIDADE` ou `EXPIRACAO` |
| ocorrido_em | DATETIME(3) | não | |

Cada recusa ou expiração gera uma linha, inclusive quando o mesmo leito se repete para a mesma ocorrência. Índice em (`ocorrencia_id`, `leito_id`), sem unicidade. O MS-Leitos consulta os ids distintos desta tabela, além da lista que veio na mensagem, para preferir outro leito. Se não houver outro `LIVRE` da especialidade, reserva um id que já esteja no histórico.

### Cache

Chave Redis `leitos:disponiveis:{tipoLeito}`, com `tipoLeito` em maiúsculas. Valor: JSON de `GET /api/leitos/disponiveis` daquela especialidade. Apagar a chave da especialidade afetada em toda reserva, confirmação, recusa, expiração ou volta a `LIVRE`.

### Carga

| Unidade | Código | Tipo | Status |
| --- | --- | --- | --- |
| Hospital Central | L-UTI-01 | UTI | LIVRE |
| Hospital Central | L-UTI-02 | UTI | OCUPADO |
| UPA Norte | L-EMG-01 | EMERGENCIA | LIVRE |
| UPA Norte | L-EMG-02 | EMERGENCIA | OCUPADO |
| Hospital Central | L-ENF-01 | ENFERMARIA | LIVRE |
| Hospital Central | L-ENF-02 | ENFERMARIA | OCUPADO |

Há vaga e falta de vaga nas três especialidades. Os leitos `OCUPADO` nascem com `confirmado` false e sem ocorrência.

## 5. MS-Notificacoes

Database MongoDB `omnileito_notificacoes`.

### Coleção notificacoes

| Campo | Tipo | Regra |
| --- | --- | --- |
| _id | string | identificador do documento |
| ocorrenciaId | string | id da ocorrência |
| tipo | string | `DESPACHO`, `EXPIRACAO` ou `FALTA_LEITO` |
| mensagem | string | texto do alerta |
| ocorridaEm | datetime | o `ocorridaEm` da mensagem |

Índice único em (`ocorrenciaId`, `tipo`, `ocorridaEm`). A consulta `GET /api/notificacoes` filtra por `ocorrenciaId` e ordena por `ocorridaEm` decrescente.

### Coleção auditoria

A trilha do despacho usa a mesma mensagem `RegistrarNotificacao`. O consumidor grava os dois documentos no mesmo processamento.

| Campo | Tipo | Regra |
| --- | --- | --- |
| _id | string | identificador do documento |
| ocorrenciaId | string | |
| tipo | string | o mesmo `tipo` da notificação |
| mensagem | string | |
| ocorridaEm | datetime | |
| origem | string | `MS-Ocorrencias` ou `MS-Leitos`, conforme quem publicou |

Índice único igual ao da notificação, mais `origem`. Não há coleção de SMS.

## 6. O que cada banco responde sozinho

| Pergunta | Onde se lê |
| --- | --- |
| Quem é o regulador e se a senha confere | MS-Identidade |
| Nome, local, prioridade, tipo de leito e status do chamado | MS-Ocorrencias |
| Qual ambulância está com o chamado e em que status | MS-Frota, pela coluna `ocorrencia_id` |
| Qual leito está reservado, desde quando, e se a unidade confirmou | MS-Leitos |
| Quais leitos esta ocorrência já recusou ou deixou expirar, para tentar por último | MS-Leitos.`reserva_recusada` e a cópia MS-Ocorrencias.`leito_excluido` |
| Quais alertas já foram registrados | MS-Notificacoes |

A cópia em `leito_excluido` existe para o MS-Ocorrencias montar a mensagem mesmo depois de reiniciar. A reserva em si continua decidida só no MS-Leitos.

## 7. Quem cria o schema

| Schema ou database | Módulo que cria a migração |
| --- | --- |
| `omnileito_identidade` | Igor |
| `omnileito_ocorrencias` | Felipe |
| `omnileito_frota` | Felipe cria a tabela. Sérgio Leandro usa a mesma tabela no cache, sem migração extra |
| `omnileito_leitos` | Felipe cria `unidade_saude` e `leito`. Sérgio Leandro acrescenta `reservado_em`, `confirmado`, `confirmado_em` e `reserva_recusada` se ainda não estiverem na primeira migração |
| `omnileito_notificacoes` | Sérgio Leandro |

Para não haver duas migrações brigando pelo mesmo leito, a primeira versão do MS-Leitos já nasce com as colunas de reserva e com `reserva_recusada`. Felipe cria esse desenho. Sérgio Leandro não adiciona coluna depois. Ele implementa o job e o cache em cima deste modelo.
