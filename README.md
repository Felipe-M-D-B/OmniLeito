# OmniLeito

Plataforma de despacho de ocorrências e reserva de leitos para um consórcio municipal de urgência. O recorte é a saga entre regulador, frota e hospital: achar uma ambulância, reservar um leito da especialidade pedida e manter ocorrência, ambulância e leito coerentes quando a unidade aceita, recusa ou fica em silêncio.

Cada módulo é um processo Spring Boot. O regulador fala só com o gateway, na porta **8085**. Os serviços não se chamam por HTTP no meio da saga: depois do commit, publicam mensagem no RabbitMQ.

Disciplina Arquitetura de Software: Teoria e Prática 2026, grupo 3 (Felipe Melchior de Britto, Igor Benedito Costa Gonçalves, Sérgio Leandro Alves da Silva).

## O que entra e o que fica de fora

Entram autenticação do regulador, abertura da ocorrência, despacho com saga, consulta de ocupação com cache, expiração de reserva não confirmada, hospital simulado e o registro da notificação. A interface de demonstração é o Swagger.

Ficam de fora, tratados como mock: mapa, protocolo clínico, relatório gerencial, faturamento, SMS ou push de verdade e geolocalização em tempo real. `EM_TRANSPORTE`, `CONCLUIDA`, `CANCELADA`, `EM_ATENDIMENTO` e `INDISPONIVEL` existem no modelo e este fluxo não os alcança. `OCUPADO` só aparece na carga, como leito sem vaga.

## Módulos

| Módulo | Porta | Banco | Responsabilidade |
| --- | --- | --- | --- |
| `api-gateway` | 8085 | — | Única entrada do regulador. Valida o JWT e encaminha. Resilience4j só em `GET /api/leitos/disponiveis` |
| `ms-identidade` | 8092 | MySQL `omnileito_identidade` | Login e emissão do JWT |
| `ms-ocorrencias` | 8093 | MySQL `omnileito_ocorrencias` | Abertura, despacho e decisão do próximo passo da saga |
| `ms-frota` | 8094 | MySQL `omnileito_frota` e Redis | Ambulância disponível, alocação e troca de status |
| `ms-leitos` | 8095 | MySQL `omnileito_leitos` e Redis | Reserva, sorteio da unidade, prazo e nova busca |
| `ms-notificacoes` | 8096 | MongoDB `omnileito_notificacoes` | Alerta e trilha de auditoria |
| `omnileito-comum` | — | — | JWT, erros, filas, mensagens e o publicador. Não sobe sozinho |

Não há chave estrangeira entre schemas. O id da ocorrência é um UUID gerado pelo MS-Ocorrencias. O id da ambulância e o id do leito são numéricos e locais de cada serviço; a ocorrência só guarda a referência.

## Stack

- Java 21
- Spring Boot 4.1.1, Maven multimódulo
- Spring Cloud Gateway (WebFlux) e Spring Cloud BOM `2025.1.2`
- Spring Data JPA, Flyway e MySQL 8
- MongoDB só em notificações e auditoria
- RabbitMQ, filas duráveis, entrega ao menos uma vez
- Redis em frota e leitos
- Resilience4j 2.2.0 no gateway, só na consulta de leitos
- springdoc 2.8.13
- JWT HMAC-SHA256 próprio (`JwtServico`). Senha com BCrypt
- JUnit 5 e Mockito

O JSON da aplicação usa Jackson 3 (`tools.jackson`). O conversor das mensagens é `JacksonJsonMessageConverter`.

## Portas

| Porta | Uso |
| --- | --- |
| 3310 | MySQL do OmniLeito. Dentro do container o servidor escuta 3306; no host a porta publicada é 3310 |
| 5672 e 15672 | RabbitMQ. Painel em `http://localhost:15672`, `guest` / `guest` |
| 6379 | Redis |
| 27018 | MongoDB no host (o container usa 27017) |
| 8085 | Gateway |
| 8092 a 8096 | Os cinco serviços |

## Subir a infraestrutura

A infraestrutura de desenvolvimento é definida em [`compose.yaml`](./compose.yaml). Na pasta do projeto, suba todos os serviços com:

```bash
docker compose up -d
docker compose ps
```

O Compose cria volumes nomeados (`mysql-data`, `rabbitmq-data`, `redis-data` e `mongo-data`), portanto `docker compose down` para e remove os containers sem apagar os dados. Para remover também os dados persistidos, use `docker compose down -v`.

Usuário e senha de desenvolvimento do MySQL: `root` / `root123`. O RabbitMQ usa `guest` / `guest`. Cada serviço cria o próprio schema com `createDatabaseIfNotExist=true` e o Flyway aplica a migração na primeira subida. Essas credenciais são apenas para desenvolvimento local.

Para parar a infraestrutura sem remover os volumes:

```bash
docker compose down
```

Frota e leitos sobem com o Redis fechado: a consulta cai no MySQL. O MS-Notificacoes usa o Mongo publicado na porta `27018` do host; dentro do container, o MongoDB continua ouvindo na porta `27017`.

Variáveis, todas com padrão para esta máquina:

| Variável | Padrão |
| --- | --- |
| `OMNILEITO_MYSQL_PORT` | `3310` |
| `OMNILEITO_MYSQL_USER` | `root` |
| `OMNILEITO_MYSQL_PASSWORD` | `root123` |
| `OMNILEITO_RABBIT_HOST` / `OMNILEITO_RABBIT_PORT` | `localhost` / `5672` |
| `OMNILEITO_RABBIT_USER` / `OMNILEITO_RABBIT_PASSWORD` | `guest` / `guest` |
| `OMNILEITO_REDIS_HOST` / `OMNILEITO_REDIS_PORT` | `localhost` / `6379` |
| `OMNILEITO_MONGO_URI` | `mongodb://localhost:27018/omnileito_notificacoes` |
| `OMNILEITO_JWT_SEGREDO` | `omnileito-demo-segredo-32-bytes!!` |

O mesmo segredo precisa valer no gateway e nos serviços. O padrão já é o mesmo em todos.

## Subir os processos

Na pasta do projeto, uma vez, para publicar o `omnileito-comum` no repositório local:

```bash
./mvnw -DskipTests install
```

Cada comando abaixo ocupa o terminal até o processo ser encerrado. São seis processos ao mesmo tempo, então são seis terminais, todos abertos na pasta do projeto. Colar a lista inteira num único terminal só sobe o primeiro: o `spring-boot:run` não devolve o prompt.

O `-pl` limita o Maven àquele módulo. Ele não entra no reator dos outros, inclusive do `omnileito-comum`. Esse módulo é uma biblioteca: JWT, erros, filas e as mensagens da saga. Ele não tem `main` e não escuta porta. Os seis serviços dependem do jar `com.unesp:omnileito-comum:0.0.1-SNAPSHOT`, que o `install` gravou em `~/.m2`. Sem esse jar, o `-pl` para na resolução da dependência e o serviço não sobe. `./mvnw test` compila o comum dentro da build, mas não publica o jar. Teste verde não substitui o `install`.

Rode o `install` de novo depois de mudar qualquer classe em `omnileito-comum`. O processo que já está no ar continua com o jar antigo até ser reiniciado. Mudança só dentro de um serviço, por exemplo `ms-leitos`, pede reiniciar aquele processo. O `install` geral não é obrigatório nesse caso, porque o `spring-boot:run` recompila o módulo pedido no `-pl`.

Suba o gateway por último. As rotas passam a responder quando identidade, ocorrências, frota, leitos e notificações já escutam nas portas 8092 a 8096.

```bash
./mvnw -pl ms-identidade spring-boot:run
./mvnw -pl ms-ocorrencias spring-boot:run
./mvnw -pl ms-frota spring-boot:run
./mvnw -pl ms-leitos spring-boot:run
./mvnw -pl ms-notificacoes spring-boot:run
./mvnw -pl api-gateway spring-boot:run
```

O hospital da demonstração não tem endpoint. O MS-Leitos sorteia `ACEITE`, `RECUSA` ou `SILENCIO` a cada reserva. Para fixar o resultado, encerre só o processo da porta 8095 e suba de novo:

```bash
./mvnw -pl ms-leitos spring-boot:run -Dspring-boot.run.arguments=--omnileito.reserva.resultado=ACEITE
```

Troque `ACEITE` por `RECUSA` ou `SILENCIO`. Vazio, o padrão, volta a ser aleatório.

O prazo de uma reserva `RESERVADO` sem confirmação é `omnileito.reserva.expiracao-minutos` (padrão `2`). O job varre a cada `omnileito.reserva.varredura-ms` (padrão `15000`). Reserva confirmada não expira.

Cada serviço publica o próprio Swagger. O gateway, na porta 8085, não tem essa página. O endereço antigo `http://localhost:8085/swagger-ui/index.html#/despacho-controller/iniciarDespacho` era do monolito e responde 404.

| Serviço | Swagger |
| --- | --- |
| Identidade | http://localhost:8092/swagger-ui.html |
| Ocorrências | http://localhost:8093/swagger-ui.html |
| Frota | http://localhost:8094/swagger-ui.html |
| Leitos | http://localhost:8095/swagger-ui.html |
| Notificações | http://localhost:8096/swagger-ui.html |

O despacho está no Swagger de ocorrências, no `OcorrenciaController`: abrir a ocorrência, consultar pelo id e `POST /api/ocorrencias/{ocorrenciaId}/despachos`. A operação `iniciarDespacho` não existe mais.

Testes de unidade, sem infraestrutura:

```bash
./mvnw test
```

O passo a passo das chamadas, do cache, da recusa, do prazo e do circuito está em [docs/roteiro-de-testes.md](docs/roteiro-de-testes.md).

## Carga inicial

O Flyway grava isto na primeira subida de cada schema.

| Recurso | Estado |
| --- | --- |
| Regulador `regulador` / `regulador` | senha guardada em BCrypt. O SQL insere um sentinela e o arranque troca pelo hash |
| Ambulância 1 `ABC-1234` | `DISPONIVEL` |
| Ambulância 2 `XYZ-5678` | `DISPONIVEL` |
| `L-UTI-01`, Hospital Central | `UTI`, `LIVRE` |
| `L-UTI-02`, Hospital Central | `UTI`, `OCUPADO` |
| `L-EMG-01`, UPA Norte | `EMERGENCIA`, `LIVRE` |
| `L-EMG-02`, UPA Norte | `EMERGENCIA`, `OCUPADO` |
| `L-ENF-01`, Hospital Central | `ENFERMARIA`, `LIVRE` |
| `L-ENF-02`, Hospital Central | `ENFERMARIA`, `OCUPADO` |

Cada especialidade tem um único leito livre. Se a unidade recusa ou o prazo estoura e não há outro compatível, a busca seguinte pode reservar esse mesmo leito de novo.

Para voltar a essa carga, pare os seis processos Java e apague só os bancos do OmniLeito na porta 3310, o banco `omnileito_notificacoes` e as chaves do Redis deste projeto. O comando está na seção 2 do roteiro de testes.

## Autenticação

`POST /api/auth/login` não exige token.

```bash
curl -s -X POST http://localhost:8085/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"usuario":"regulador","senha":"regulador"}'
```

Resposta `200`:

```json
{ "token": "<jwt>", "tipo": "Bearer" }
```

As outras rotas `/api/**` exigem `Authorization: Bearer <jwt>`. Usuário ou senha inválidos: `401`, `codigo` `CREDENCIAL_INVALIDA`. Token ausente ou inválido: `401`, `codigo` `TOKEN_INVALIDO`.

Corpo de erro, em qualquer serviço:

```json
{ "codigo": "OCORRENCIA_NAO_ENCONTRADA", "mensagem": "Ocorrência não encontrada." }
```

| HTTP | Quando |
| --- | --- |
| 400 | Campo ausente ou valor fora da lista. `codigo` `CAMPO_INVALIDO` |
| 401 | Credencial ou token inválido |
| 404 | Ocorrência inexistente. `codigo` `OCORRENCIA_NAO_ENCONTRADA` |
| 409 | Despacho de ocorrência `CANCELADA` ou `CONCLUIDA`. `codigo` `OCORRENCIA_NAO_ABERTA` |
| 503 | Circuito de leitos aberto. `codigo` `SERVICO_LEITOS_INDISPONIVEL` |

## Rotas do gateway

O prefixo público é `/api`. O gateway encaminha pelo caminho.

| Método e caminho | Serviço | Auth |
| --- | --- | --- |
| `POST /api/auth/login` | identidade | não |
| `POST /api/ocorrencias` | ocorrências | sim |
| `GET /api/ocorrencias/{id}` | ocorrências | sim |
| `POST /api/ocorrencias/{id}/despachos` | ocorrências | sim |
| `GET /api/ambulancias/disponiveis` | frota | sim |
| `GET /api/leitos/disponiveis?tipoLeito=` | leitos | sim, com timeout, retry e circuit breaker |
| `GET /api/notificacoes?ocorrenciaId=` | notificações | sim |

### Abrir ocorrência

Não publica mensagem e não dispara a saga. O tipo de leito fica fixo aqui. O despacho não aceita outro.

`prioridade`: `VERMELHA`, `AMARELA`, `VERDE`. `tipoLeito`: `UTI`, `EMERGENCIA`, `ENFERMARIA`.

```bash
curl -s -X POST http://localhost:8085/api/ocorrencias \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"paciente":{"nome":"Maria Souza"},"local":"Rua das Flores, 120","prioridade":"VERMELHA","tipoLeito":"UTI"}'
```

Resposta `201`, status `ABERTA`, `ambulanciaId` e `leitoId` nulos. Guarde o `id`.

### Despachar

Corpo vazio. O `202` só avisa que a saga começou. O estado final se lê no `GET`.

```bash
curl -s -D - -X POST http://localhost:8085/api/ocorrencias/$ID/despachos \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{}'

curl -s http://localhost:8085/api/ocorrencias/$ID \
  -H "Authorization: Bearer $TOKEN"
```

| Situação da ocorrência | HTTP | Efeito |
| --- | --- | --- |
| `ABERTA` | 202 | Passa a `EM_DESPACHO` e, depois do commit, publica `AlocarAmbulancia` |
| Saga já iniciada (`EM_DESPACHO`, `DESPACHADA` ou `FALHA_DESPACHO`) | 200 | Devolve o estado atual. Não aloca outra ambulância nem outro leito |
| Inexistente | 404 | — |
| `CANCELADA` ou `CONCLUIDA` | 409 | — |

`FALHA_DESPACHO` não volta sozinho para `ABERTA`.

### Consultas

`GET /api/ambulancias/disponiveis` lista só `DISPONIVEL`.

`GET /api/leitos/disponiveis?tipoLeito=UTI` exige o tipo e lista só leitos `LIVRE` daquela especialidade. `OCUPADO` e leito de outra especialidade não entram. Com a carga intacta, UTI devolve só `L-UTI-01` do Hospital Central.

`GET /api/notificacoes?ocorrenciaId=` exige o id e devolve da mais recente para a mais antiga. Tipos: `DESPACHO`, `EXPIRACAO`, `FALTA_LEITO`. Não há SMS. O alerta é esse registro. O mesmo trio `ocorrenciaId`, `tipo` e `ocorridaEm` não gera segunda linha. O horário na resposta pode aparecer em UTC (`Z`); é o mesmo instante gravado.

## Saga

A chave de idempotência é o id da ocorrência. A mensagem só sai depois do commit de quem a originou. Consumir de novo a mesma ocorrência não cria segunda alocação nem segunda reserva ativa.

Exchanges direct: `omnileito.saga` e `omnileito.notificacao`. A routing key é o nome da fila.

| Mensagem | Fila | Publica | Consome |
| --- | --- | --- | --- |
| `AlocarAmbulancia` | `saga.alocar-ambulancia` | ocorrências | frota |
| `AmbulanciaAlocada` | `saga.ambulancia-alocada` | frota | ocorrências |
| `ReservarLeito` | `saga.reservar-leito` | ocorrências | leitos |
| `LeitoReservado` | `saga.leito-reservado` | leitos | ocorrências |
| `LeitoExpirado` | `saga.leito-expirado` | leitos | ocorrências |
| `AtualizarAmbulancia` | `saga.atualizar-ambulancia` | ocorrências | frota |
| `RetentarBuscaLeito` | `saga.retentar-busca-leito` | leitos | ocorrências |
| `RegistrarNotificacao` | `notificacao.registrar` | ocorrências e leitos | notificações |

`AtualizarAmbulancia` só aceita `DISPONIVEL`, `DESPACHADA` ou `AGUARDANDO_LEITO`.

### Aceite ou silêncio na primeira reserva

1. O despacho grava `EM_DESPACHO` e publica `AlocarAmbulancia`.
2. A frota escolhe uma ambulância `DISPONIVEL`, grava `ALOCADA` ligada à ocorrência e responde `AmbulanciaAlocada`. Se essa ocorrência já tiver ambulância, devolve a mesma.
3. Ocorrências publica `ReservarLeito` com `tentativa` 1 e o `tipoLeito` da abertura.
4. Leitos reserva um `LIVRE` dessa especialidade. No aceite, o leito fica `RESERVADO` e confirmado. No silêncio, fica `RESERVADO` sem confirmação, com `reservado_em`.
5. Ocorrências grava `DESPACHADA`, o id da ambulância, o id do leito e o nome da unidade.
6. Publica `AtualizarAmbulancia` com `DESPACHADA` e `RegistrarNotificacao` com `DESPACHO`.

`DESPACHADA` segura os dois recursos. A confirmação da unidade é outro passo: no silêncio ela ainda não aconteceu, e o job pode expirar essa reserva.

### Sem ambulância

`AmbulanciaAlocada` vem com `motivo` `SEM_AMBULANCIA`. A ocorrência fica `FALHA_DESPACHO`. Nenhum leito é reservado.

### Sem leito na primeira tentativa

Não existe leito `LIVRE` da especialidade, nem entre os já recusados. `LeitoReservado` vem com `SEM_LEITO_COMPATIVEL` e `tentativa` 1. A ambulância volta a `DISPONIVEL`, porque a equipe ainda não saiu, e a ocorrência fica `FALHA_DESPACHO`.

### Recusa ou prazo

O leito recusado ou expirado volta a `LIVRE`. `EXPIRADO` só existe dentro da transação; o commit grava `LIVRE`. O histórico fica em `reserva_recusada` (uma linha por evento) e em `leito_excluido` (uma linha por par ocorrência e leito, atualizada se o mesmo leito se repetir).

A ambulância fica `AGUARDANDO_LEITO`: a equipe já está no chamado, em atendimento ou a caminho, à espera do destino. A ocorrência volta a `EM_DESPACHO`. Ocorrências publica `AtualizarAmbulancia` e um novo `ReservarLeito`, com a tentativa somada.

A busca pede primeiro um leito `LIVRE` da mesma especialidade que não esteja na lista. Se não houver, pede de novo um leito dessa lista, inclusive o que acabou de voltar a `LIVRE`. Cada reserva nova passa outra vez pelo sorteio do hospital.

No prazo, o MS-Leitos também publica `RegistrarNotificacao` com `EXPIRACAO`. A origem dessa trilha é `MS-Leitos`. As outras são `MS-Ocorrencias`.

### Nova busca ainda sem vaga

`SEM_LEITO_COMPATIVEL` com tentativa maior que 1 deixa a ocorrência em `EM_DESPACHO` e a ambulância em `AGUARDANDO_LEITO`. Ocorrências publica `FALTA_LEITO`. A ambulância não volta para `DISPONIVEL`. Esse motivo só aparece quando não há nenhum `LIVRE` da especialidade.

No fim de cada varredura, se a especialidade tiver ao menos um `LIVRE`, o MS-Leitos publica `RetentarBuscaLeito`. Ocorrências republica `ReservarLeito` para cada ocorrência `EM_DESPACHO` daquele tipo que já tenha ambulância, leito vazio e tentativa maior que 1.

### Quadro

| Evento | Ocorrência | Ambulância | Leito |
| --- | --- | --- | --- |
| Abertura | `ABERTA` | nenhuma ligada | nenhum |
| Despacho aceito na API | `EM_DESPACHO` | ainda nenhuma | nenhum |
| Ambulância alocada | `EM_DESPACHO` | `ALOCADA` | nenhum |
| Sem ambulância livre | `FALHA_DESPACHO` | como estavam | nenhum |
| Reserva aceita | `DESPACHADA` | `DESPACHADA` | `RESERVADO` confirmado |
| Reserva em silêncio | `DESPACHADA` | `DESPACHADA` | `RESERVADO` sem confirmação |
| Sem leito na primeira tentativa | `FALHA_DESPACHO` | `DISPONIVEL` | nenhum |
| Recusa ou prazo | `EM_DESPACHO` | `AGUARDANDO_LEITO` | o leito volta a `LIVRE` |
| Nova busca sem nenhum `LIVRE` da especialidade | `EM_DESPACHO` | `AGUARDANDO_LEITO` | nenhum |
| O único `LIVRE` é o que acabou de voltar | conforme o novo sorteio | `DESPACHADA` no aceite ou no silêncio; `AGUARDANDO_LEITO` numa nova recusa | reservado de novo, ou `LIVRE` se a unidade recusar outra vez |

Com `resultado=RECUSA` e um único leito livre da especialidade, a saga pede esse leito de novo e o ciclo continua. Vale para uma demonstração curta: depois de ver a ambulância em `AGUARDANDO_LEITO`, encerre o MS-Leitos.

## Cache

| Chave | Conteúdo |
| --- | --- |
| `frota:disponiveis` | Ambulâncias `DISPONIVEL` |
| `leitos:disponiveis:{TIPO}` | Leitos `LIVRE` da especialidade. Ex.: `leitos:disponiveis:UTI` |

A leitura usa o cache. Se a chave não existe ou o Redis não responde, a consulta lê o MySQL e tenta preencher o cache. Alocar, voltar a `DISPONIVEL`, passar a `DESPACHADA` ou a `AGUARDANDO_LEITO` apaga `frota:disponiveis`. Reservar, confirmar, recusar, expirar ou devolver o leito a `LIVRE` apaga a chave daquela especialidade.

## Circuito da consulta de leitos

Só `GET /api/leitos/disponiveis` passa pelo Resilience4j. Abertura e despacho seguem com o circuito aberto.

| Parâmetro | Valor |
| --- | --- |
| Janela | 4 chamadas |
| Mínimo de chamadas | 2 |
| Taxa de falha para abrir | 50% |
| Tempo aberto | 10 s |
| Timeout | 2 s |
| Tentativas | 3 |

O fallback responde `503` com `codigo` `SERVICO_LEITOS_INDISPONIVEL`. Para ver isso, pare só o processo da porta 8095 e repita a consulta. Login e `POST /api/ocorrencias` continuam respondendo.

## Documentos

| Arquivo | Conteúdo |
| --- | --- |
| [docs/documento-de-requisitos.md](docs/documento-de-requisitos.md) | Requisitos, versão 0.3 |
| [docs/contratos-entre-servicos.md](docs/contratos-entre-servicos.md) | Rotas, mensagens e conversas da saga, versão 0.2 |
| [docs/modelo-de-dados.md](docs/modelo-de-dados.md) | Tabelas, índices e a regra do `UPDATE` da reserva, versão 0.2 |
| [docs/maquina-de-estados.md](docs/maquina-de-estados.md) | Estados de ocorrência, ambulância e leito, versão 0.2 |
| [docs/roteiro-de-testes.md](docs/roteiro-de-testes.md) | Como exercitar cada processo já implementado |
| [docs/ajustes-para-adequacao.md](docs/ajustes-para-adequacao.md) | Plano que originou esta implementação |
| [docs/proposta-workshop-praticas.md](docs/proposta-workshop-praticas.md) | Proposta do grupo |
| [docs/como-testar-localmente-monolito.md](docs/como-testar-localmente-monolito.md) | Roteiro do monolito antigo. O sistema atual é o deste README |

Os PDF ao lado dos markdown de requisitos, contratos, modelo e máquina de estados acompanham a mesma versão do texto.
