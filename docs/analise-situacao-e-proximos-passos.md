# OmniLeito — situação e próximo passo

- Disciplina: Arquitetura de Software: Teoria e Prática 2026
- Grupo 3: Felipe Melchior de Britto; Igor Benedito Costa Gonçalves; Sérgio Leandro Alves da Silva
- Repositório: https://github.com/Felipe-M-D-B/OmniLeito
- Leitura desta revisão: 16/10/2026, depois do roteiro no compose
- `develop` local e `origin/develop`: `e094bcc` (merge do PR #3, `Inclusão de docker-compose`)

O compose está no ar nas portas 3310, 5672, 15672, 6379 e 27018. Os seis processos foram subidos de novo contra esses containers. O roteiro em `docs/roteiro-de-testes.md` está na versão 0.2, ainda só nesta cópia de trabalho, junto com a correção da URI do Mongo.

Requisitos, contratos, modelo e máquina de estados continuam nas versões 0.3 e 0.2.

---

## 1. Divisão

| Membro | Pessoa | Aspecto técnico | Funcionalidade |
| --- | --- | --- | --- |
| 1 | Igor Benedito Costa Gonçalves | API Gateway, roteamento e Resilience4j | Autenticar o regulador e abrir a ocorrência |
| 2 | Felipe Melchior de Britto | Mensageria e Saga orquestrada | Despachar ambulância e reservar leito, com compensação |
| 3 | Sérgio Leandro Alves da Silva | Cache de disponibilidade e agendamento | Consultar e atualizar ocupação; expirar reserva não confirmada |

Um repositório, sete módulos Maven e um processo Spring Boot por módulo. O diretório `src/` do monolito não está no Git.

| Processo | Porta | Persistência |
| --- | --- | --- |
| API Gateway | 8085 | nenhuma. Não publica Swagger |
| MS-Identidade | 8092 | MySQL `omnileito_identidade` |
| MS-Ocorrencias | 8093 | MySQL `omnileito_ocorrencias` |
| MS-Frota | 8094 | MySQL `omnileito_frota` e Redis |
| MS-Leitos | 8095 | MySQL `omnileito_leitos` e Redis |
| MS-Notificacoes | 8096 | MongoDB `omnileito_notificacoes` |

O Swagger de cada serviço fica em `http://localhost:<porta>/swagger-ui.html`. O despacho é o `OcorrenciaController` na porta 8093.

---

## 2. O que o fluxo já faz

Situação nesta revisão:

- **Feito** — visto no ar, pelo gateway, nesta passagem com a carga do compose.
- **Feito no código** — o caminho existe e o teste de unidade passou. A passagem no ar ainda não foi registrada nesta leitura.
- **Aberto** — a demonstração ainda depende disto.

| # | Passo | Situação |
| --- | --- | --- |
| 1 | MySQL por serviço, Flyway e carga de duas ambulâncias e seis leitos | Feito. Schemas na porta 3310. A carga foi conferida: `ABC-1234` e `XYZ-5678` em `DISPONIVEL`, um `LIVRE` e um `OCUPADO` por especialidade |
| 2 | Filas da saga no RabbitMQ | Feito. Oito filas, Ready `0`, Unacked `0` e Total `0` depois dos despachos |
| 3 | Login do regulador | Feito. `regulador` / `regulador`, JWT no gateway |
| 4 | Abertura `ABERTA` | Feito. `201`, sem ambulância e sem leito. A abertura não publica mensagem |
| 5 | Gateway e Resilience4j em `GET /api/leitos/disponiveis` | Feito no código. A consulta com os dois processos no ar respondeu `200`. O `503` com o MS-Leitos parado ainda não foi obtido |
| 6 | Consulta com Redis | Feito. Nasceram `frota:disponiveis` e `leitos:disponiveis:UTI`, `EMERGENCIA` e `ENFERMARIA` |
| 7 | Despacho, corpo vazio | Feito. `202` e, em seguida, `DESPACHADA`. O segundo despacho em `200` está no teste de unidade e não foi repetido nesta carga |
| 8 | Alocação exclusiva | Feito no código. `./mvnw test` passou: 7 testes em ocorrências, 2 na frota e 3 em leitos |
| 9 | Reserva pela especialidade | Feito. UTI foi para `L-UTI-01`, Hospital Central |
| 10 | Aceite, recusa ou silêncio | Silêncio foi visto no ar. Aceite e recusa estão no código e no roteiro. A recusa com ambulância em `AGUARDANDO_LEITO` não foi registrada nesta leitura |
| 11 | Sucesso: `DESPACHADA`, leito `RESERVADO` | Feito. Ambulância 1 `DESPACHADA`, `L-UTI-01` `RESERVADO` |
| 12 | Sem ambulância, ou sem leito na primeira tentativa | Feito no código. Não foi repetido nesta carga limpa |
| 13 | Invalidar o cache | Feito no código. A consulta da seção 6 gravou as chaves. A invalidação depois da reserva não foi relida no Redis nesta passagem |
| 14 | Expirar reserva sem confirmação | Feito. Com `SILENCIO`, `confirmado` ficou `0`. Às 00:43 e de novo às 00:45 nasceu `EXPIRACAO` em `reserva_recusada`, e o mesmo leito foi reservado outra vez |
| 15 | Alertas `DESPACHO`, `EXPIRACAO` e `FALTA_LEITO` | `EXPIRACAO` foi visto na tabela de recusas. `DESPACHO` e `FALTA_LEITO` estão no código. `FALTA_LEITO` tem teste de unidade |

`EM_TRANSPORTE`, `CONCLUIDA`, `CANCELADA`, `EM_ATENDIMENTO` e `INDISPONIVEL` existem no modelo e este fluxo não os alcança.

---

## 3. O que cada membro já tem no código

### Igor

Login, JWT no gateway, rotas e o circuito só em `GET /api/leitos/disponiveis`. Abertura e despacho não passam por esse circuito. Cada serviço publica o próprio Swagger.

### Felipe

Saga, frota e leitos. O resultado do hospital sai de `omnileito.reserva.resultado`, lido só na subida do MS-Leitos. Sem o argumento, cada reserva sorteia `ACEITE`, `RECUSA` ou `SILENCIO`. Trocar o resultado exige encerrar o processo da porta 8095 antes de subir outro. Ctrl+Z deixa o processo suspenso e a porta ocupada.

O `compose.yaml` está no `develop`. O Mongo do host é a porta 27018. Dentro do container continua a 27017.

### Sérgio Leandro

Cache que nasce na primeira leitura e cai no MySQL se o Redis não responde. Job a cada 15 segundos, prazo de 2 minutos, só para `RESERVADO` sem confirmação. Com `SILENCIO` e um único leito `LIVRE`, o estado visível volta a `DESPACHADA` e `RESERVADO`. A prova da expiração é `confirmado` em `0` e a linha `EXPIRACAO`.

O MS-Notificacoes, no Spring Boot 4.1, conecta por `spring.mongodb.uri`. A chave `spring.data.mongodb.uri` que veio no PR #3 não é a que o cliente usa. Sem a chave nova, o processo cai em `localhost:27017` e não sobe. A correção está nesta cópia de trabalho e ainda não foi para o `origin`.

`./mvnw test` passou em 16/10. As linhas `WARNING` do Mockito não são falha.

---

## 4. Distância até a demonstração

Infraestrutura, login, abertura, consulta, cache, despacho até `DESPACHADA`, silêncio com expiração e filas vazias já foram vistos. Falta registrar, com o gateway no ar:

1. Recusa: ambulância `AGUARDANDO_LEITO` e o mesmo leito pedido de novo.
2. Primeira busca sem leito `LIVRE`, com ambulância ainda `DISPONIVEL`: `FALHA_DESPACHO` e a ambulância de volta a `DISPONIVEL`.
3. Segundo despacho da mesma ocorrência em `200`, sem alocar de novo.
4. `GET /api/leitos/disponiveis` com a porta 8095 fechada e a 8085 aberta: `503` e `SERVICO_LEITOS_INDISPONIVEL`. Login e abertura continuam. A tentativa desta leitura respondeu `200` porque o MS-Leitos ainda escutava.

A carga desta passagem já foi gasta pelo silêncio: `L-UTI-01` segue `RESERVADO` e o ciclo continua enquanto o MS-Leitos estiver com `SILENCIO`.

---

## 5. Próximo passo

Publicar a correção do Mongo e fechar os quatro caminhos que ainda não foram vistos no ar.

### 5.1 Levar ao grupo a URI que o Spring Boot 4 lê

Em `ms-notificacoes/src/main/resources/application.properties`, a conexão é:

```properties
spring.mongodb.uri=${OMNILEITO_MONGO_URI:mongodb://localhost:27018/omnileito_notificacoes}
```

O `docs/roteiro-de-testes.md` 0.2 acompanha essa porta, o `install` do `omnileito-comum`, o job que lê a tabela e o modo de ler o painel do RabbitMQ. Os dois arquivos estão modificados e fora do `origin`.

### 5.2 Fechar o roteiro com carga limpa

Pare os seis processos com Ctrl+C. Apague só os schemas `omnileito_*` da porta 3310, o banco `omnileito_notificacoes` e as chaves do Redis. Suba de novo. O Flyway regrava a carga.

Depois, um resultado fixo por cenário, sempre encerrando a porta 8095 antes de trocar:

1. `ACEITE`: despacho até `DESPACHADA`, segundo despacho em `200`, e outra UTI com a ambulância 2 ainda livre até `FALHA_DESPACHO`.
2. `RECUSA`: uma volta com `AGUARDANDO_LEITO` e nova linha em `reserva_recusada`. Encerrar o MS-Leitos para o ciclo não continuar.
3. Gateway no ar e MS-Leitos parado: duas consultas de UTI em `503`, e uma abertura em `201`.

---

## 6. O que não é o próximo passo

- Transformar `EM_TRANSPORTE`, `CONCLUIDA`, `CANCELADA`, `EM_ATENDIMENTO` ou `INDISPONIVEL` em fluxo automático.
- SMS, mapa, protocolo clínico, relatório ou faturamento.
- Juntar o Swagger dos cinco serviços no gateway.
- Um repositório Git por serviço.

Teste automatizado do job, do circuito e do fallback do Redis pode vir depois. Não bloqueia a demonstração.
