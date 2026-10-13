# OmniLeito — situação por membro e pontos a definir

Documento local. Não vai para o Git.

- Disciplina: Arquitetura de Software: Teoria e Prática 2026
- Grupo 3: Felipe Melchior de Britto; Igor Benedito Costa Gonçalves; Sérgio Leandro Alves da Silva
- Repositório: https://github.com/Felipe-M-D-B/OmniLeito
- Branch lida: `develop` (`c279cda`, alinhada com `origin/develop`)
- Primeira leitura: 13/10/2026, de manhã
- Teste local da Saga: 13/10/2026, MySQL na porta 3310
- Esta revisão: 13/10/2026. A seção 8.1 está decidida: microsserviços separados, como na proposta entregue ao professor

Nenhuma implementação nova entra neste arquivo. O código de negócio continua o que está em `develop`. A implementação espera validação dos pontos que a seção 8 ainda deixa abertos.

A proposta está em `docs/proposta-workshop-praticas.md` e em `docs/Proposta_Grupo_03.docx.pdf`. O recorte de outubro é as três funcionalidades ponta a ponta. GIS, protocolo clínico, relatórios e SMS real ficam mockados. A interface é Swagger.

---

## 1. Divisão

| Membro | Pessoa | Aspecto técnico | Funcionalidade |
| --- | --- | --- | --- |
| 1 | Igor Benedito Costa Gonçalves | API Gateway, roteamento e Resilience4j (timeout, retry, circuit breaker) | Autenticar o regulador e abrir a ocorrência |
| 2 | Felipe Melchior de Britto | Mensageria e Saga orquestrada (ocorrência–ambulância–leito) | Despachar ambulância e reservar leito, com compensação |
| 3 | Sérgio Leandro Alves da Silva | Cache de disponibilidade e agendamento | Consultar e atualizar ocupação; expirar reserva não confirmada |

O `develop` hoje é um único aplicativo Spring Boot, pacote `com.unesp.omnileito`. Orquestrador, frota e leitos dividem o mesmo processo e o mesmo banco. A proposta fechada na seção 8.1 separa os processos.

### Arquitetura alvo

Sistema superior: rede municipal de urgência (central de regulação, bases, unidades hospitalares, canais de alerta).

Sistema de interesse: Plataforma de Despacho de Ocorrências e Reserva de Leitos.

Atores: Regulador, Equipe da ambulância, Unidade hospitalar (aceite e recusa simulados), Canal de alerta (SMS mockado), API Gateway.

| Processo | Papel | Persistência | Quem mexe |
| --- | --- | --- | --- |
| API Gateway | Única porta da central. A regulação e o hospital não enxergam endereço interno | nenhuma | Igor |
| MS-Identidade | Autentica o regulador e devolve o token | MySQL, dado local do regulador | Igor |
| MS-Ocorrencias | Abre ocorrência `ABERTA`. Orquestra a Saga. Atualiza status. Compensa o chamado | MySQL: Paciente, Ocorrencia, Despacho | Igor no `abrir`. Felipe na Saga |
| MS-Frota | Aloca e libera ambulância. Lista as livres | MySQL: Ambulancia. Redis: ambulâncias livres | Felipe nos comandos da Saga. Sérgio Leandro no cache e na consulta |
| MS-Leitos | Reserva e cancela leito compatível. Simula a unidade. Lista leitos por especialidade. Expira reserva não confirmada | MySQL: Leito, UnidadeSaude. Redis: leitos por especialidade | Felipe na reserva e na compensação. Igor protege a chamada a este serviço com Resilience4j. Sérgio Leandro no cache, na confirmação lida pelo job e no agendamento |
| MS-Notificacoes | Histórico de notificação e trilha de auditoria do despacho | MongoDB | Sérgio Leandro grava o alerta de expiração. Felipe publica o evento de ocorrência despachada |
| RabbitMQ | Filas de despacho, recusa de leito e alerta. Entrega at-least-once | broker | Felipe define os contratos. Os três publicam ou consomem o que for da sua parte |

Fluxo fechado com a proposta:

1. Registrar ocorrência `ABERTA` e classificar a prioridade.
2. Alocar ambulância `DISPONIVEL`.
3. Reservar leito compatível na unidade de destino.
4. Em sucesso, marcar ocorrência `DESPACHADA`, leito `RESERVADO` e publicar notificação à equipe e ao hospital.
5. Em falha ou timeout, compensar: liberar ambulância, cancelar reserva de leito, reenfileirar o chamado ou cancelá-lo.
6. Reserva não confirmada pela unidade depois de X minutos expira por job, reutilizando essas compensações.

A chave da ocorrência torna alocação e reserva idempotentes, para a entrega at-least-once não prender o mesmo recurso duas vezes.

Estudo de caso: abertura autenticada, duas ambulâncias, leitos com e sem vaga, hospital aceitando e recusando, inspeção do estado final em ocorrência, frota, leito e notificação.

Stack por preocupação, como na proposta: Java 21 e Spring Boot (Maven); Spring Web e springdoc; MySQL em cada serviço para o dado transacional daquele serviço; MongoDB só em MS-Notificacoes; RabbitMQ; Redis em MS-Frota e MS-Leitos; Spring Cloud Gateway; Resilience4j na chamada ao MS-Leitos; Spring Scheduler no job de expiração; JUnit 5 para alocação exclusiva, idempotência e compensação.

O Git do grupo já é um repositório só. A leitura prática é manter esse Git e subir um processo Spring Boot por serviço (módulos Maven). Processos separados cumprem a proposta. Repositórios Git separados seriam outro acordo, com o Felipe e o Igor.

MS-Frota e MS-Leitos têm dois donos de código. Felipe entra com os comandos da Saga (alocar, reservar, liberar, cancelar). Sérgio Leandro entra com consulta, cache, invalidação e o job. Igor entra na chamada ao MS-Leitos, pelo gateway, com timeout, retry e circuit breaker. O esqueleto desses dois processos precisa de um combinado antes dos dois implementarem em paralelo.

---

## 2. Sequência lógica do projeto

Ordem do fluxo da regulação, do login até a reserva expirar. Cada passo nomeia o processo alvo. A ordem de construção está na seção 8.9.

Situação:

- **Feito** — está em `develop` e o teste local chegou nesse resultado, já na forma de processos separados.
- **Parcial** — há código no monolito, e o passo ainda não é um microsserviço nem fecha o que a proposta pede. O teste da seção 4 é a evidência do monolito.
- **Não feito** — não há fluxo.

| # | Passo | Serviço | Responsável | Situação |
| --- | --- | --- | --- | --- |
| 1 | Base executável com MySQL, Flyway, duas ambulâncias e dois leitos | Hoje um processo e um banco. Alvo: banco local em cada serviço | Felipe, como base do grupo | Parcial. O monolito sobe. Os bancos separados não existem |
| 2 | Filas da Saga no RabbitMQ | RabbitMQ, compartilhado | Felipe | Parcial. Exchange e quatro filas existem. Os consumidores estão no mesmo processo do orquestrador. A fila de cancelar leito só está declarada |
| 3 | Autenticar o regulador e devolver o token | MS-Identidade | Igor | Não feito |
| 4 | Abrir ocorrência `ABERTA` (paciente, local, prioridade) | Gateway encaminha. MS-Ocorrencias grava | Igor | Não feito. No monolito o despacho cria a ocorrência já em `EM_DESPACHO` |
| 5 | Encaminhar a abertura com timeout, retry e circuit breaker | API Gateway, Resilience4j na chamada ao MS-Leitos | Igor | Não feito. Spring Cloud Gateway nem está no `pom.xml` |
| 6 | Consultar ambulâncias livres e leitos por especialidade | MS-Frota e MS-Leitos, Redis com fallback no MySQL | Sérgio Leandro | Não feito |
| 7 | Pedir despacho de uma ocorrência já `ABERTA` | MS-Ocorrencias | Felipe | Parcial. `POST /api/despachos` existe no monolito e não exige `ABERTA` |
| 8 | Alocar ambulância `DISPONIVEL`, de forma exclusiva e idempotente | MS-Frota, por mensagem | Felipe | Parcial. O listener aloca no mesmo processo, sem trava e sem idempotência |
| 9 | Reservar leito compatível com a especialidade | MS-Leitos, por mensagem | Felipe | Parcial. Reserva o primeiro `LIVRE` e ignora o tipo. No teste, pedido UTI ficou com leito GERAL |
| 10 | Simular aceite, recusa ou indisponibilidade da unidade | Chamada ao MS-Leitos protegida pelo Igor. Recusa compensada pelo Felipe. Confirmação lida pelo job do Sérgio Leandro | Igor, Felipe e Sérgio Leandro | Não feito |
| 11 | Em sucesso: ocorrência `DESPACHADA`, leito `RESERVADO`, notificação à equipe e ao hospital | MS-Ocorrencias, MS-Leitos e MS-Notificacoes | Felipe publica. MS-Notificacoes registra | Parcial. O monolito grava `DESPACHADA` no mesmo passo da reserva. O teste não terminou nesse status. Não há Mongo nem notificação |
| 12 | Em falha ou timeout: liberar ambulância, cancelar leito, reenfileirar ou cancelar o chamado | MS-Frota, MS-Leitos, MS-Ocorrencias | Felipe | Parcial. A ambulância volta a `DISPONIVEL`. O leito não é cancelado |
| 13 | Invalidar o cache depois de alocar, reservar, confirmar ou compensar | Redis do MS-Frota e do MS-Leitos | Sérgio Leandro | Não feito |
| 14 | Expirar reserva `RESERVADO` sem confirmação depois de X minutos, com as mesmas compensações | Job no MS-Leitos, mensagens para MS-Frota e MS-Ocorrencias | Sérgio Leandro | Não feito |
| 15 | Registrar o alerta dessa expiração | MS-Notificacoes (MongoDB) | Sérgio Leandro | Não feito |

Leitura rápida: nada desta tabela está **Feito** na forma de microsserviço. O que existe é semente no monolito, nos passos 1, 2, 7, 8, 9, 11 e 12, todos ligados ao Felipe. Igor ainda não tem gateway, identidade nem Resilience4j em uso. Sérgio Leandro ainda não tem cache, job nem MS-Notificacoes.

---

## 3. Base comum, já no repositório

O que está em `develop` serve de semente. Permanece no formato de um aplicativo só até a separação ser feita.

| Item | Estado no monolito | Para onde vai |
| --- | --- | --- |
| Java 21, Spring Boot parent `4.1.1`, Maven wrapper | no projeto | cada módulo |
| `POST /api/despachos` e Swagger em `/swagger-ui-entity.html` | no ar quando a aplicação sobe | Swagger de cada serviço; a regulação entra pelo gateway |
| MySQL + JPA + Flyway `V1__init_schema.sql` | um banco, três tabelas | um schema por serviço. O `V1` único não se copia inteiro para todos |
| RabbitMQ: exchange `saga.exchange`, quatro filas e quatro routing keys | declarado; consumidores locais | permanece o broker; cada serviço consome a própria fila |
| Redis, MongoDB, Resilience4j `2.2.0`, Actuator | só no `pom.xml` do monolito | Redis em Frota e Leitos; Mongo em Notificacoes; Resilience4j no caller do MS-Leitos |
| Spring Cloud Gateway | ausente | projeto do gateway, Igor |
| Teste automatizado | `OmniLeitoApplicationTests` só sobe o contexto | JUnit 5 de alocação exclusiva, idempotência e compensação, ainda por escrever |
| `application.properties` | porta `8084`, MySQL em `localhost:3306` | cada processo com a própria porta. Nesta máquina, nunca a 3306 |

Nesta máquina a porta 3306 é o banco do Ameppre. O teste do monolito usou MySQL exclusivo na porta 3310 e a aplicação na porta 8085. O passo a passo está em `docs/como-testar-localmente.md`.

Histórico que formou o `develop`: configuração inicial, reestruturação, enums, RabbitMQ, primeira alocação de ambulância, `StatusSaga`, README e merge do PR #1 (`felipe-parte2`). A `main` parou em `c42593c` (correção de nomenclatura) e está atrás do `develop`.

### O que o `V1` guarda, e o serviço dono

| Dado | No monolito | Serviço alvo |
| --- | --- | --- |
| `ocorrencias` (`id`, `status`, `tipo_leito_necessario`, `ambulancia_alocada_id`, `leito_reservado_id`) | tabela única | MS-Ocorrencias. Os ids de ambulância e leito passam a ser referência entre serviços, não chave do mesmo banco |
| `ambulancias` (`ABC-1234`, `XYZ-5678`, as duas `DISPONIVEL`) | tabela única | MS-Frota |
| `leitos` (`L-101` UTI, `L-102` GERAL, os dois `LIVRE`) | tabela única | MS-Leitos |
| Paciente, Despacho | ausentes | MS-Ocorrencias |
| Regulador | ausente | MS-Identidade |
| UnidadeSaude, instante da reserva, confirmação da unidade | ausentes | MS-Leitos |
| Notificacao, auditoria | ausentes | MS-Notificacoes, MongoDB |

Enums já gravados no código do monolito, e que os serviços herdam:

- `StatusOcorrencia`: `ABERTA`, `EM_DESPACHO`, `DESPACHADA`, `CANCELADA`, `FALHA_DESPACHO`
- `StatusAmbulancia`: `DISPONIVEL`, `ALOCADA`, `EM_DESLOCAMENTO`, `EM_ATENDIMENTO`, `INDISPONIVEL`
- `StatusLeito`: `LIVRE`, `RESERVADO`, `OCUPADO`, `EXPIRADO`, `INDISPONIVEL` — o comentário de `EXPIRADO` já cita o job do membro 3
- `StatusSaga`: `INICIADA`, `AMBULANCIA_ALOCADA`, `LEITO_RESERVADO`, `CONCLUIDA`, `EM_COMPENSACAO`, `COMPENSADA`, `FALHADA` — nenhum código usa

A proposta cita prioridade (VERMELHA, AMARELA, VERDE). No código a prioridade não existe. O tipo de leito é texto livre; a carga usa `UTI` e `GERAL`. O requisito fechado é UTI, EMERGENCIA e ENFERMARIA.

---

## 4. O que o teste local mostrou

Três `POST /api/despachos` no monolito, base limpa da porta 3310: `OC-1001` UTI, `OC-1002` GERAL, `OC-1003` UTI. Leitura no terminal e no DBeaver, conexão `omnileito` porta 3310:

| Ocorrência | Status gravado | Ambulância | Leito |
| --- | --- | --- | --- |
| OC-1001 | `EM_DESPACHO` | 2 | 2 |
| OC-1002 | `FALHA_DESPACHO` | vazio | vazio |
| OC-1003 | `EM_DESPACHO` | vazio | vazio |

| Recurso | Status gravado | Apontado por alguma ocorrência? |
| --- | --- | --- |
| Ambulância 1 `ABC-1234` | `ALOCADA` | não |
| Ambulância 2 `XYZ-5678` | `ALOCADA` | OC-1001 |
| Leito 1 `L-101` UTI | `RESERVADO` | não |
| Leito 2 `L-102` GERAL | `RESERVADO` | OC-1001, que pediu UTI |

O fluxo descrito no README (duas primeiras `DESPACHADA`, a terceira `FALHA_DESPACHO` sem segurar recurso) não foi o estado final. Há recurso ocupado sem ocorrência dona, ocorrência em `EM_DESPACHO` com os dois ids preenchidos, e leito reservado de tipo diferente do pedido.

Esse estado é do monolito, seção 6. Separar os processos sem corrigir alocação exclusiva, tipo do leito e idempotência só espalha o mesmo furo entre MS-Frota e MS-Leitos. O cache do membro 3 entra depois dessa correção, já dentro desses serviços.

---

## 5. Membro 1 — Igor — gateway, identidade e abertura

### Implementado

Nada da funcionalidade 1 está no código. Não há processo de gateway nem de identidade.

O que existe no monolito e não substitui a parte dele:

- dependência `resilience4j-spring-boot3` 2.2.0 no `pom.xml` único, sem anotação, sem configuração e sem chamada protegida
- Actuator no POM, sem uso pela regulação
- Swagger do aplicativo único
- enum `ABERTA`, sem fluxo que grave esse status

### O que a proposta pede

Funcionalidade 1, pelo gateway:

1. O regulador informa usuário e senha ao MS-Identidade.
2. O MS-Identidade devolve o token.
3. O regulador informa paciente, local e prioridade.
4. O gateway encaminha ao MS-Ocorrencias.
5. O MS-Ocorrencias grava a ocorrência `ABERTA` e devolve o identificador.

Resilience4j (timeout, retry, circuit breaker) fica na chamada ao MS-Leitos, que é onde a unidade simulada pode estar indisponível. A falha do hospital para no gateway e não derruba a central. A abertura da ocorrência, no MS-Ocorrencias, segue independente dessa chamada.

Hoje quem cria a ocorrência é o despacho do membro 2, já em `EM_DESPACHO`, dentro de `SagaOrchestratorService.iniciarDespacho`.

### Falta

1. Módulo do MS-Identidade: autenticar regulador (usuário, senha, token). Swagger, sem tela.
2. Contrato de abertura com o MS-Ocorrencias: nome do paciente, local em texto, prioridade (VERMELHA, AMARELA, VERDE) e tipo de leito (UTI, EMERGENCIA, ENFERMARIA); resposta com id, status `ABERTA` e tipo de leito. O despacho usa o tipo já gravado.
3. Rota no Spring Cloud Gateway para identidade, abertura, despacho e consultas de ocupação.
4. Resilience4j na rota ou no cliente que chama o MS-Leitos. Demonstração: unidade fora do ar não impede abrir a ocorrência e não segura a thread da central para sempre.
5. O `POST` de abertura não dispara a Saga. Quem despacha é o MS-Ocorrencias, na parte do Felipe, e só com ocorrência `ABERTA`.

---

## 6. Membro 2 — Felipe — Saga e mensageria

### Implementado no monolito

É a única parte com comportamento executável. Entrou pelo PR #1. Ainda não é a Saga entre processos.

Entrada:

- `DespachoController`: `POST /api/despachos` com `{ ocorrenciaId, tipoLeitoNecessario }`.
- A resposta HTTP é só o texto de que a Saga foi iniciada. O status final fica no banco, depois do consumo das filas.

Orquestrador (`SagaOrchestratorService`), no mesmo processo dos listeners:

1. Cria a ocorrência se o id não existir, grava `EM_DESPACHO` e publica em `routing.alocar-ambulancia`. A publicação ocorre dentro do método `@Transactional`.
2. Se a alocação volta com sucesso, publica `routing.reservar-leito`. Se volta com falha, grava `FALHA_DESPACHO`. O motivo `"FALHA_ALOCACAO_AMBULANCIA"` não é persistido.
3. Se a reserva volta com sucesso, grava `DESPACHADA` e preenche `ambulancia_alocada_id` e `leito_reservado_id` na mesma linha da ocorrência.
4. Se a reserva falha, publica `routing.liberar-ambulancia` com o id da ambulância e grava `FALHA_DESPACHO`.

Listeners (`SagaEventListener`), com acesso direto aos repositórios de ambulância e leito:

- `saga.alocar-ambulancia.queue`: pega a primeira ambulância `DISPONIVEL`, muda para `ALOCADA` e devolve o evento ao orquestrador em chamada de método.
- `saga.reservar-leito.queue`: pega o primeiro leito `LIVRE`, sem olhar `tipoLeitoNecessario`, muda para `RESERVADO` e devolve ao orquestrador em chamada de método.
- `saga.liberar-ambulancia.queue`: devolve essa ambulância para `DISPONIVEL`.

RabbitMQ (`RabbitMQConfig`): exchange direct `saga.exchange`; filas duráveis de alocar, reservar, liberar e cancelar; routing keys; conversor JSON Jackson.

`saga.cancelar-leito.queue` está declarada. Ninguém publica nela e ninguém a escuta. No alvo, quem escuta é o MS-Leitos.

Compensação que roda hoje: falha de ambulância só marca a ocorrência; falha de leito libera a ambulância. Não há cancelamento de leito, idempotência, trava nem uso de `StatusSaga`.

### Distância até os microsserviços

No alvo, o MS-Ocorrencias não abre repositório de ambulância nem de leito. Ele publica comando e espera evento. O MS-Frota consome alocar e liberar. O MS-Leitos consome reservar e cancelar. A devolução ao orquestrador também é mensagem, não chamada de método no mesmo processo. Os ids gravados na ocorrência são referências, e cada serviço confirma a escrita no próprio banco.

A semente (filas, enums, orquestrador, carga de duas ambulâncias e dois leitos) permanece útil. O acoplamento a remover é o listener e o orquestrador compartilhando `AmbulanciaRepository` e `LeitoRepository`.

### O que a proposta pede além do que roda

1. Despachar ocorrência que o Igor já gravou como `ABERTA`.
2. `MS-Frota.alocar` com ambulância `DISPONIVEL`, exclusivo e idempotente pela chave da ocorrência.
3. `MS-Leitos.reservar` com leito compatível com a especialidade.
4. Em sucesso, leito `RESERVADO`, ocorrência `DESPACHADA`, evento para o MS-Notificacoes.
5. Em recusa, timeout ou falha, compensar nos três serviços: liberar ambulância, cancelar leito, reenfileirar ou cancelar o chamado.
6. Entrega at-least-once sem dupla alocação.
7. Hospital simulado no caminho do MS-Leitos, com aceite e recusa visíveis na inspeção final.

`EM_DESLOCAMENTO`, `EM_ATENDIMENTO` e `OCUPADO` continuam fora do recorte, a menos que o grupo queira mostrá-los.

### Leitura do estado deixado pelo teste

O código de sucesso grava `DESPACHADA` e os dois ids no mesmo `save`. Uma linha `EM_DESPACHO` com os dois ids preenchidos significa que esse `save` chegou a ocorrer e um `iniciarDespacho` posterior gravou de novo `EM_DESPACHO` sem limpar os ids.

Os outros fatos batem com o código atual:

- `findFirstByStatus` não ordena e não filtra tipo: `OC-1001` pediu UTI e ficou com o leito `L-102` GERAL.
- Duas alocações e duas reservas commitam no listener antes de a ocorrência guardar o dono. Ambulância 1 e leito 1 ficaram ocupados sem ocorrência apontando para eles.
- `OC-1002` em `FALHA_DESPACHO` com ids vazios é o caminho de falha da alocação.
- `OC-1003` permaneceu `EM_DESPACHO` sem ids: o HTTP já tinha respondido e o resultado dessa mensagem não ficou gravado como falha nem como despacho.
- A publicação dentro da transação de `iniciarDespacho` permite o listener rodar antes do commit da ocorrência. Sem idempotência, uma segunda entrega pode alocar outro recurso.

Conclusão: a Saga do monolito executa passos e o estado final deste teste não é um despacho consistente. Levar esse código para três processos, do jeito que está, replica recurso órfão em bancos diferentes.

### Falta na parte 2

1. Extrair MS-Ocorrencias, MS-Frota e MS-Leitos para processos distintos, com MySQL local de cada um.
2. Despachar só ocorrência `ABERTA`.
3. Reservar leito da especialidade pedida.
4. Alocação exclusiva e idempotência pela chave da ocorrência, nos dois serviços de recurso.
5. Publicar a mensagem depois do commit local.
6. MS-Leitos consumir `saga.cancelar-leito.queue`.
7. Aceite e recusa simulados da unidade, com a chamada ao MS-Leitos protegida pelo Resilience4j do Igor.
8. Publicar o evento de ocorrência despachada para o MS-Notificacoes.
9. Teste dos três finais entre processos: sucesso, falta de ambulância, falta de leito com liberação da ambulância e cancelamento do leito quando a reserva tiver existido.

---

## 7. Membro 3 — Sérgio Leandro — cache, consulta e expiração

### Implementado

Nada da funcionalidade 3 roda. Não há Redis configurado, não há job e não há MS-Notificacoes.

Ganchos no monolito, ainda sem uso:

- `spring-boot-starter-data-redis` no POM único
- `spring-boot-starter-data-mongodb` no POM único
- `StatusLeito.EXPIRADO`
- fila `saga.cancelar-leito.queue`, sem produtor e sem consumidor
- pontos em que a ocupação muda: ambulância vai a `ALOCADA`, leito vai a `RESERVADO`, ambulância volta a `DISPONIVEL`

### O que a proposta pede, já nos serviços certos

Funcionalidade 3:

1. A regulação consulta ambulâncias livres no MS-Frota e leitos por especialidade no MS-Leitos. A entrada da regulação é o gateway.
2. A leitura passa pelo Redis e, em falta de valor válido, cai no MySQL daquele serviço.
3. Alocar, reservar, confirmar ou compensar invalida o cache do serviço que gravou.
4. O job do MS-Leitos encontra leito `RESERVADO` há mais de X minutos sem confirmação da unidade.
5. A expiração reutiliza a compensação da Saga: mensagem para liberar a ambulância no MS-Frota, cancelar o leito no MS-Leitos, atualizar a ocorrência no MS-Ocorrencias.
6. O MS-Notificacoes registra o alerta. O canal de SMS permanece mockado.

O Scheduler devolve leito e ambulância sem um orquestrador externo: o job dispara as mesmas mensagens de compensação, e o MS-Ocorrencias não precisa de um segundo coordenador.

O X não foi escolhido. Para a demonstração, o valor fica em propriedade do MS-Leitos e é curto o bastante para a banca ver o efeito.

### Falta na parte 3

1. Combinar com o Felipe o esqueleto do MS-Frota e do MS-Leitos antes de colocar cache dentro deles. Cache no monolito seria descartado na separação.
2. Schema do MS-Leitos com instante da reserva, ocorrência dona e indicador de confirmação da unidade. Sem o instante, o job não tem critério. Isso é migração do banco de leitos, não um `V2` no banco único do monolito.
3. Redis em container `omnileito-redis`, porta 6379 (livre nesta máquina). Um uso lógico para frota e outro para leitos, ou dois bancos lógicos no mesmo Redis, desde que a invalidação de um não apague o outro.
4. `listarDisponiveis` no MS-Frota e `listarDisponiveis(especialidade)` no MS-Leitos, cache-aside.
5. Invalidação depois dos comandos do Felipe, já nos processos de frota e leitos.
6. Endpoints no Swagger desses dois serviços, roteados pelo gateway: consultar ocupação e registrar a atualização de confirmação.
7. `@EnableScheduling` no MS-Leitos e o job de expiração.
8. MS-Notificacoes em MongoDB, gravando o alerta de expiração e, quando o Felipe publicar, o evento de despacho.
9. Testes: fallback do cache, invalidação após reserva, expiração não mexe em reserva confirmada, expiração devolve ambulância e leito por mensagem.

---

## 8. Pontos que precisam ser definidos antes de continuar

A seção 8.1 está decidida. O fluxo, a idempotência, o Resilience4j, o MS-Notificacoes, o prazo, as especialidades e o destino da ambulância na recusa ou no prazo estão em `docs/documento-de-requisitos.md`.

### 8.1 Microsserviços separados — decidido

A entrega segue a proposta ao professor. Processos: API Gateway, MS-Identidade, MS-Ocorrencias, MS-Frota, MS-Leitos, MS-Notificacoes, mais o RabbitMQ.

Cada serviço tem o próprio ciclo e o próprio banco transacional. Ocorrência, ambulância e leito não compartilham a mesma transação. O `develop` é a semente monolítica dessa separação, não o formato da apresentação.

Leitura prática do repositório: um Git, vários módulos Maven, um processo por módulo. O grupo só muda isso se preferir um repositório por serviço.

Dono de cada processo, para ninguém criar o mesmo módulo duas vezes:

| Processo | Dono do módulo | O que os outros fazem nele |
| --- | --- | --- |
| API Gateway | Igor | Felipe e Sérgio Leandro informam as rotas dos seus endpoints |
| MS-Identidade | Igor | — |
| MS-Ocorrencias | Felipe | Igor chama `abrir` pelo gateway |
| MS-Frota | Felipe cria o processo e os comandos `alocar` e `liberar` | Sérgio Leandro acrescenta consulta, Redis e invalidação |
| MS-Leitos | Felipe cria o processo e os comandos `reservar` e `cancelar` | Igor protege quem chama este serviço. Sérgio Leandro acrescenta consulta, Redis, confirmação e o job |
| MS-Notificacoes | Sérgio Leandro | Felipe publica o evento de despacho |

### 8.2 Quando a ocorrência nasce e quem pode despachar — alinhado à proposta

O Igor, via gateway, grava `ABERTA` no MS-Ocorrencias, com prioridade. O Felipe só despacha id que já esteja `ABERTA`. Um segundo despacho do mesmo id não aloca outro recurso: a operação é idempotente pela chave da ocorrência.

O `POST /api/despachos` do monolito, que cria a ocorrência, deixa de ser a porta de entrada quando o MS-Ocorrencias existir.

### 8.3 O que `RESERVADO` e `DESPACHADA` significam — alinhado à proposta

O passo 4 da proposta marca, no sucesso da Saga, ocorrência `DESPACHADA` e leito `RESERVADO`, e publica a notificação. A confirmação da unidade é outro fato: reserva não confirmada depois de X minutos expira e cai na mesma compensação. Recusa e timeout também compensam.

`DESPACHADA` com leito `RESERVADO` significa recursos presos e unidade ainda capaz de confirmar, recusar ou estourar o prazo. Reserva confirmada não entra no job. O status `EXPIRADO` pode ser o estado transitório do leito antes de voltar a `LIVRE`; o efeito visível na banca é leito livre e ambulância livre.

### 8.4 Aceite, recusa e timeout da unidade — alinhado à proposta

O hospital simulado fica no caminho do MS-Leitos. O Resilience4j do Igor envolve a chamada ao MS-Leitos, para a unidade indisponível não se propagar à central.

Contrato:

- aceite grava confirmação e o horário no MS-Leitos
- recusa cancela só aquele leito; a ambulância fica `AGUARDANDO_LEITO` e o sistema pede um leito da mesma especialidade, outro se houver, o mesmo se for o único `LIVRE`
- timeout, depois de esgotar retry e circuit breaker, deixa a reserva não confirmada; o job aplica o mesmo desfecho da recusa

### 8.5 Prazo X, reenfileirar ou cancelar — decidido no documento de requisitos

O desfecho fechado em 13/10/2026 está em `docs/documento-de-requisitos.md` versão 0.3. A reserva recusada ou expirada devolve o leito e mantém a ambulância na ocorrência, em `AGUARDANDO_LEITO`. A nova busca prefere outro leito `LIVRE` da mesma especialidade e, se não houver, reserva de novo o mesmo leito. O prazo fica em propriedade do MS-Leitos, com padrão de 2 minutos. A ocorrência não volta para `ABERTA` nem vai para `CANCELADA` nesse caminho.

### 8.6 Notificação — alinhado à proposta

MS-Notificacoes é processo próprio, com MongoDB: histórico de notificação e trilha de auditoria do despacho. SMS e push reais ficam mockados.

O Felipe publica o evento de sucesso da Saga. O Sérgio Leandro publica o alerta de expiração e é o dono do módulo. Relatório gerencial continua fora do recorte.

### 8.7 Especialidade do leito — decidido

A lista fechada é UTI, EMERGENCIA e ENFERMARIA. A reserva e o cache filtram por ela. A carga atual do monolito (`UTI` e `GERAL`) não atende este requisito e será trocada na implementação.

### 8.8 Branch, portas e o que não entra no Git

Parte 3 na branch `leandro-parte3`, a partir de `develop`, quando a implementação for autorizada. Se a separação em módulos começar pelo Felipe, a branch dele é a base e a do Sérgio Leandro nasce dessa branch, para o cache nascer já no MS-Frota e no MS-Leitos.

`docs/` e `.cursor/` continuam fora do Git. O `.gitignore` desta máquina já tem essas duas entradas e ainda não foi commitado.

Nesta máquina cada processo usa porta livre. Não usar 3306 (Ameppre), 3307 (Coneximob), 80, 443 nem 8080. O MySQL da porta 3310 pode abrigar um schema por serviço (`omnileito_identidade`, `omnileito_ocorrencias`, `omnileito_frota`, `omnileito_leitos`). Mongo e Redis em containers `omnileito-*`. Nada disso entra no `application.properties` como porta fixa do Ameppre.

### 8.9 Ordem de construção

A sequência do produto está na seção 2. A construção segue a fronteira dos processos:

1. A seção 8.1, o prazo, o destino da ambulância e a lista de especialidades estão decididos em `docs/documento-de-requisitos.md`.
2. Felipe abre os módulos MS-Ocorrencias, MS-Frota e MS-Leitos e tira os repositórios de frota e leito de dentro do orquestrador. Corrige tipo, idempotência, publicação após o commit e o consumidor de cancelar leito. O teste da seção 4, refeito entre processos, termina com duas ocorrências `DESPACHADA`, leitos do tipo pedido em `RESERVADO`, e a terceira em falha sem recurso órfão.
3. Igor sobe gateway e MS-Identidade, grava `ABERTA` no MS-Ocorrencias e aplica Resilience4j na chamada ao MS-Leitos, com a unidade simulada.
4. Sérgio Leandro coloca Redis e as consultas no MS-Frota e no MS-Leitos, o job de expiração no MS-Leitos e o MS-Notificacoes em Mongo.

O cache não começa no monolito. O esqueleto do MS-Frota e do MS-Leitos vem antes, no passo 2, para a parte 3 escrever dentro do processo certo.

---

## 9. Ambiente desta máquina

Preservar containers `coneximob-*` (portas 8080 e 3307) e `ameppre-*` (portas 80, 443 e 3306). Podem ser encerrados: `unesp-as-mysql` (3308) e `unesp-as-phpmyadmin` (8088).

Infra já usada no teste do monolito: `omnileito-mysql` na porta 3310, `omnileito-rabbitmq` nas portas 5672 e 15672, aplicação na porta 8085. DBeaver: host `127.0.0.1`, porta `3310`, database `omnileito`, usuário `root`, senha `root123`.

Quando a separação for autorizada, o mesmo MySQL da 3310 ganha um schema por serviço. Redis em `omnileito-redis` na porta 6379. Mongo em container `omnileito-mongo`, em porta livre. O gateway ocupa outra porta livre, diferente de 8080 e de 8085 enquanto o monolito ainda estiver no ar para comparação.

---

## 10. À espera de validação

A arquitetura de microsserviços está decidida. Nada será implementado até um ok explícito.

O requisito está em `docs/documento-de-requisitos.md`. A implementação continua esperando autorização. O ok útil diz se o primeiro código é a separação dos módulos pelo Felipe ou se a parte 3 espera essa branch para entrar no MS-Frota, no MS-Leitos e no MS-Notificacoes.
