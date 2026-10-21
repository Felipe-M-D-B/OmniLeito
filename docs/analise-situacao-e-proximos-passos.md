# OmniLeito — situação e próximo passo

- Disciplina: Arquitetura de Software: Teoria e Prática 2026
- Grupo 3: Felipe Melchior de Britto; Igor Benedito Costa Gonçalves; Sérgio Leandro Alves da Silva
- Repositório: https://github.com/Felipe-M-D-B/OmniLeito
- Leitura desta revisão: 16/10/2026, à noite, depois da conferência das filas e do Mongo
- `develop` local e `origin/develop`: `95f2eeb` (`Ajustes_Testes`)
- Referência de requisito: `docs/documento-de-requisitos.md` 0.3. Contratos, modelo e máquina de estados seguem na 0.2

O compose está no ar nas portas 3310, 5672, 15672, 6379 e 27018. Os seis processos sobem contra esses containers. A correção `spring.mongodb.uri` já entrou no `95f2eeb`. O `docs/roteiro-de-testes.md` 0.2, nesta cópia de trabalho, ganhou o resumo dos seis microsserviços na seção 1 e a leitura do Mongo no item 13. Esse complemento do roteiro ainda não está no `origin`.

Situação usada abaixo:

- **No ar** — visto pelo gateway, pelo MySQL, pelo Redis, pelo painel do RabbitMQ ou pelo `mongosh`, nesta passagem.
- **No código** — o caminho existe e o teste de unidade cobre. A passagem no ar ainda não foi registrada.
- **Falta** — a demonstração do requisito ainda depende disto.

`EM_TRANSPORTE`, `CONCLUIDA`, `CANCELADA`, `EM_ATENDIMENTO` e `INDISPONIVEL` existem no modelo. Este fluxo não os alcança. `OCUPADO` só aparece na carga.

---

## 1. Divisão

| Membro | Pessoa | Aspecto técnico | Funcionalidade | Requisitos |
| --- | --- | --- | --- | --- |
| 1 | Igor Benedito Costa Gonçalves | API Gateway, roteamento e Resilience4j | Autenticar o regulador e abrir a ocorrência | RF01 a RF05 |
| 2 | Felipe Melchior de Britto | Mensageria e Saga orquestrada | Despachar ambulância e reservar leito, com compensação | RF06 a RF16 |
| 3 | Sérgio Leandro Alves da Silva | Cache de disponibilidade e agendamento | Consultar e atualizar ocupação; expirar reserva não confirmada | RF17 a RF24 |

Um repositório, módulos Maven e um processo Spring Boot por serviço. O `omnileito-comum` é biblioteca: JWT, erros, filas e o publicador. Não sobe sozinho.

| Processo | Porta | Persistência | Dono do módulo |
| --- | --- | --- | --- |
| API Gateway | 8085 | nenhuma. Não publica Swagger | Igor |
| MS-Identidade | 8092 | MySQL `omnileito_identidade` | Igor |
| MS-Ocorrencias | 8093 | MySQL `omnileito_ocorrencias` | Felipe |
| MS-Frota | 8094 | MySQL `omnileito_frota` e Redis | Felipe cria o processo. Sérgio Leandro acrescenta consulta, cache e invalidação |
| MS-Leitos | 8095 | MySQL `omnileito_leitos` e Redis | Felipe cria reserva e cancelamento. Igor protege a consulta. Sérgio Leandro acrescenta cache, confirmação e o job |
| MS-Notificacoes | 8096 | MongoDB `omnileito_notificacoes` | Sérgio Leandro. Felipe publica o evento de despacho |

O regulador entra só pela porta 8085. No meio da saga a mensagem sai depois do commit. O Swagger de cada serviço fica em `http://localhost:<porta>/swagger-ui.html`.

---

## 2. Igor — identidade, abertura e circuito

### Já tem

| Requisito | O que está pronto |
| --- | --- |
| RF01 | Login `regulador` / `regulador` no ar. O MS-Identidade confere o hash BCrypt e devolve o JWT |
| RF02 e RF03 | Abertura no ar: `201`, ocorrência `ABERTA`, com nome, local, prioridade e tipo de leito. A abertura não publica mensagem |
| RF04 | As chamadas do roteiro entram pelo gateway. Cada serviço publica o próprio Swagger |
| RF05 no código | Timeout, retry e circuit breaker só em `GET /api/leitos/disponiveis`. Abertura e despacho não passam por esse circuito |

### Falta

| Requisito | O que a demonstração ainda pede |
| --- | --- |
| RF05 no ar | Duas consultas de UTI com o gateway na 8085 e o MS-Leitos parado na 8095: HTTP `503`, `codigo` `SERVICO_LEITOS_INDISPONIVEL`. No mesmo momento, login e abertura seguem em `200` e `201`. A tentativa desta leitura respondeu `200` e `[]` porque a porta 8095 ainda escutava. Lista vazia é leito sem vaga, não circuito aberto |

---

## 3. Felipe — despacho e saga

### Já tem

| Requisito | O que está pronto |
| --- | --- |
| RF06, RF08, RF10, RF12 | Despacho de UTI no ar. A ocorrência foi a `DESPACHADA`, a ambulância `ABC-1234` a `DESPACHADA` e o leito `L-UTI-01` a `RESERVADO`, Hospital Central. O tipo pedido foi o gravado na abertura |
| RF13 | O hospital simulado sorteia `ACEITE`, `RECUSA` ou `SILENCIO`, lido só na subida do MS-Leitos. O silêncio foi o resultado visto no ar |
| RF16 | Oito filas `classic`, duráveis e `running`. Depois do despacho, Ready, Unacked e Total ficaram em `0`. A mensagem entra na fila depois do commit |
| Compose | `compose.yaml` no `develop`. MySQL do host na 3310, Mongo do host na 27018 |
| Teste de unidade | `OcorrenciaSagaServiceTest` e `FrotaServiceTest`: segundo despacho sem nova alocação, primeira busca sem leito devolve a ambulância, recusa mantém a ambulância e pede outra reserva, falha de despacho não recomeça, ocorrência cancelada responde conflito, segunda alocação da mesma ocorrência devolve a mesma ambulância |

Trocar `ACEITE`, `RECUSA` ou `SILENCIO` exige encerrar o processo da porta 8095 antes de subir outro. Ctrl+Z só suspende e a porta continua ocupada. O encerramento do roteiro é Ctrl+C.

### Falta no ar

O código e o roteiro já descrevem estes caminhos. A carga desta passagem foi a do silêncio, então eles não foram repetidos com o resultado fixo.

| Requisito | O que a demonstração ainda pede | Onde no roteiro |
| --- | --- | --- |
| RF07 | Segundo `POST /despachos` da mesma ocorrência em `200`, com a mesma ambulância e o mesmo leito. A ambulância 2 continua `DISPONIVEL` | Seção 7.3 |
| RF09 | Frota esgotada: ocorrência `FALHA_DESPACHO`, sem ambulância e sem leito. O leito da outra especialidade permanece `LIVRE` | Seção 9 |
| RF11 | Primeira busca sem leito `LIVRE` da especialidade: ambulância de volta a `DISPONIVEL`, ocorrência `FALHA_DESPACHO`, nenhum leito de outra especialidade reservado | Seção 8 |
| RF14 | `ACEITE`: leito `RESERVADO` com `confirmado` verdadeiro. A reserva confirmada não entra no job | Seção 7 |
| RF15 | `RECUSA`: ambulância `AGUARDANDO_LEITO`, ocorrência `EM_DESPACHO`, linha `RECUSA_UNIDADE` e, sem outro leito `LIVRE`, o mesmo leito pedido de novo. Uma volta basta. O ciclo para com Ctrl+C na porta 8095 | Seção 10 |

---

## 4. Sérgio Leandro — cache, prazo e notificação

### Já tem

| Requisito | O que está pronto |
| --- | --- |
| RF17 e RF18 | Consulta pelo gateway no ar. O cache nasce na primeira leitura. O Redis mostrou `frota:disponiveis` e `leitos:disponiveis:UTI`, `EMERGENCIA` e `ENFERMARIA`. Sem valor no Redis, a leitura cai no MySQL e preenche a chave |
| RF20 e RF21 | Com `SILENCIO`, `L-UTI-01` ficou `RESERVADO` e `confirmado` em `0`. O job, a cada 15 segundos, leu o MySQL. Às 00:43 e de novo às 00:45 nasceu `EXPIRACAO` em `reserva_recusada`. O mesmo leito foi reservado outra vez. A ambulância não voltou para `DISPONIVEL` |
| RF22 no código | `LeitoServiceTest` cobre os dois ramos: havendo outro `LIVRE`, a reserva pede esse outro; sendo ele o único, pede o mesmo de novo. No ar, a carga tem um `LIVRE` por especialidade, então o silêncio repetiu `L-UTI-01` |
| RF24 no ar | Coleção Mongo `notificacoes`, ocorrência `b73931fe-168a-46fa-952c-7d671d584a69`: 7 documentos `DESPACHO` e 6 `EXPIRACAO`, de 00:41 a 00:54 no horário de Brasília. A fila `notificacao.registrar` zerou porque o MS-Notificacoes gravou |
| Conexão | `spring.mongodb.uri` aponta para `mongodb://localhost:27018/omnileito_notificacoes`. Está no commit `95f2eeb`. O `mongosh` de dentro do container mostra a porta 27017. Essa é a porta interna |

O DBeaver Community não abre MongoDB. A leitura do roteiro é o `mongosh` do container `omnileito-mongo`, database `omnileito_notificacoes`, coleções `notificacoes` e `auditoria`.

### Falta

| Requisito | O que a demonstração ainda pede |
| --- | --- |
| RF19 no ar | Depois de reservar, reler a chave. Na seção 7, a lista de UTI fica vazia e `leitos:disponiveis:UTI` acompanha essa lista. Nesta passagem as chaves foram vistas na criação, não depois da reserva |
| Critério 6 | Com `ACEITE`, esperar mais de 2 minutos. `confirmado` permanece verdadeiro e não nasce `EXPIRACAO` para essa reserva |
| RF23 | Sem nenhum leito `LIVRE` da especialidade numa busca que já tem ambulância: a ambulância fica `AGUARDANDO_LEITO`, a ocorrência fica `EM_DESPACHO` e o MS-Notificacoes grava `FALTA_LEITO`. O teste `buscaSeguinteSemLeitoNaoDevolveAmbulancia` cobre isso. O roteiro 0.2 não tem esse passo no ar, porque a carga sempre deixa um `LIVRE` e a saga reserva esse de novo |
| RF22 no ar, ramo do outro leito | A carga de demonstração não tem dois leitos `LIVRE` da mesma especialidade. O ramo "pede o outro" permanece no teste de unidade |

---

## 5. Fila dos próximos passos

A ordem usa uma carga por vez. O circuito não gasta leito. O bloco `ACEITE` gasta a UTI e as duas ambulâncias. A recusa pede carga nova. O silêncio já foi fechado e não volta para a fila.

1. **Sérgio Leandro — publicar o complemento do roteiro.** A seção 1 agora resume os seis processos. O item 13 explica a tabela de filas, o Mongo na porta 27018 e a leitura pelo `mongosh`. Sem esse texto no `origin`, o grupo segue o roteiro anterior a esta conferência.
2. **Igor — circuito, seção 12.** Gateway no ar. Encerrar só a porta 8095 com Ctrl+C. Duas consultas de UTI devem responder `503` e `SERVICO_LEITOS_INDISPONIVEL`. Uma abertura, no mesmo momento, responde `201`. Subir o MS-Leitos de novo. Este passo não depende da carga limpa.
3. **Os três — voltar à carga inicial, seção 2.** O silêncio deixou `L-UTI-01` reservado e a ambulância 1 despachada. Parar os seis processos, apagar os quatro schemas da porta 3310, o database `omnileito_notificacoes` e as chaves do Redis. Subir de novo. O Flyway regrava duas ambulâncias `DISPONIVEL` e seis leitos.
4. **Felipe e Sérgio Leandro — `ACEITE`, seções 7, 8 e 9, nesta ordem.** Subir a porta 8095 com `ACEITE`.
   1. Despachar uma UTI até `DESPACHADA`, leito confirmado e notificação `DESPACHO`.
   2. Esperar pouco mais de 2 minutos. `confirmado` continua verdadeiro e não aparece `EXPIRACAO` dessa reserva.
   3. Reler `leitos:disponiveis:UTI`. A chave acompanha a lista vazia.
   4. Segundo despacho da mesma ocorrência em `200`.
   5. Outra UTI: `FALHA_DESPACHO` e a ambulância 2 de volta a `DISPONIVEL`.
   6. Enfermaria com a ambulância que voltou, depois uma emergência sem ambulância: `FALHA_DESPACHO` e `L-EMG-01` ainda `LIVRE`.
5. **Felipe — `RECUSA`, seção 10.** Nova carga. Uma volta com ambulância `AGUARDANDO_LEITO`, ocorrência `EM_DESPACHO` e linha `RECUSA_UNIDADE`. Confirmar e encerrar a porta 8095 com Ctrl+C, para o ciclo não continuar.
6. **Sérgio Leandro — `FALTA_LEITO`.** Só depois dos itens acima. O roteiro ainda não conduz esse caso. O preparo é uma busca seguinte em que a especialidade fique sem nenhum leito `LIVRE`. O esperado é ambulância `AGUARDANDO_LEITO`, ocorrência `EM_DESPACHO` e documento `FALTA_LEITO` na coleção `notificacoes`.
7. **Os três — filas no fim de cada cenário.** Painel em `http://localhost:15672`: oito filas com Ready, Unacked e Total em `0`. Quando o cenário publica alerta, o documento correspondente está em `omnileito_notificacoes.notificacoes`.

O silêncio da seção 11 permanece como evidência já colhida: `confirmado` em `0`, `EXPIRACAO` no MySQL e o par `DESPACHO` / `EXPIRACAO` no Mongo.

---

## 6. O que fica fora desta fila

- Transformar `EM_TRANSPORTE`, `CONCLUIDA`, `CANCELADA`, `EM_ATENDIMENTO` ou `INDISPONIVEL` em fluxo automático.
- SMS, mapa, protocolo clínico, relatório ou faturamento.
- Juntar o Swagger dos serviços no gateway.
- Um repositório Git por serviço.
- Montar dois leitos `LIVRE` da mesma especialidade só para repetir, no ar, o ramo que o `LeitoServiceTest` já cobre.

Teste automatizado do job, do circuito e do fallback do Redis pode vir depois. Não bloqueia a demonstração.
