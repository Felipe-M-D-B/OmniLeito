# Documento de requisitos — OmniLeito

Documento local do grupo. Não vai para o Git.

- Disciplina: Arquitetura de Software: Teoria e Prática 2026
- Grupo 3: Felipe Melchior de Britto; Igor Benedito Costa Gonçalves; Sérgio Leandro Alves da Silva
- Sistema: Plataforma de Despacho de Ocorrências e Reserva de Leitos
- Versão: 0.3 — 13/10/2026. Se não houver outro leito compatível, a nova busca pode reservar de novo o mesmo leito já recusado ou expirado
- Fontes: `docs/proposta-workshop-praticas.md`, `docs/analise-situacao-e-proximos-passos.md` e as decisões fechadas nesta data

Este documento é a referência de requisito para a implementação. Onde ele diverge do texto antigo da proposta sobre o destino da ambulância após recusa ou prazo, vale o que está aqui.

---

## 1. Objetivo

Tornar observável e tratável a inconsistência entre ocorrência, frota e leito numa arquitetura de microsserviços. O recorte é um consórcio municipal de urgência. Não é um SAMU completo.

## 2. Escopo

Entram:

- Autenticação do regulador e abertura de ocorrência
- Despacho com Saga: ambulância e leito, com compensação
- Consulta de ocupação com cache e expiração de reserva não confirmada
- Hospital simulado (aceite, recusa ou silêncio)
- Inspeção do estado final em ocorrência, frota, leito e notificação
- Interface OpenAPI/Swagger

Ficam de fora, mockados:

- GIS e mapa
- Protocolo clínico
- Relatórios gerenciais e faturamento
- Envio real de SMS ou push
- Geolocalização em tempo real

## 3. Atores

| Ator | Papel |
| --- | --- |
| Regulador | Autentica, abre ocorrência, consulta ocupação e pede despacho |
| Equipe da ambulância | Recebe o aviso de despacho e o destino do leito. Não opera tela neste recorte |
| Unidade hospitalar | Sistema externo simulado. Aceita, recusa ou não responde à reserva |
| Canal de alerta | Sistema externo mockado. Recebe o registro da notificação, sem SMS real |

## 4. Arquitetura exigida

Processos separados, um banco transacional por serviço. Ocorrência, ambulância e leito não compartilham a mesma transação.

| Processo | Responsabilidade de requisito |
| --- | --- |
| API Gateway | Única porta da regulação e do hospital. Timeout, retry e circuit breaker na chamada ao MS-Leitos |
| MS-Identidade | Autentica o regulador e devolve o token |
| MS-Ocorrencias | Grava a ocorrência, orquestra a Saga, atualiza status, publica eventos |
| MS-Frota | Aloca e libera ambulância. Lista as livres. Cache Redis |
| MS-Leitos | Reserva e cancela leito por especialidade. Simula a unidade. Lista leitos. Job de expiração. Cache Redis |
| MS-Notificacoes | Histórico de notificação e trilha de auditoria. MongoDB |
| RabbitMQ | Despacho, recusa de leito e alerta. Entrega at-least-once |

Dono de módulo, para a implementação não duplicar processo:

| Processo | Dono do módulo | Participação dos outros |
| --- | --- | --- |
| API Gateway e MS-Identidade | Igor | Felipe e Sérgio Leandro informam as rotas |
| MS-Ocorrencias | Felipe | Igor chama a abertura pelo gateway |
| MS-Frota | Felipe cria o processo e os comandos da Saga | Sérgio Leandro acrescenta consulta, cache e invalidação |
| MS-Leitos | Felipe cria o processo, a reserva e o cancelamento | Igor protege a chamada. Sérgio Leandro acrescenta consulta, cache, confirmação e o job |
| MS-Notificacoes | Sérgio Leandro | Felipe publica o evento de despacho |

## 5. Decisões fechadas

| Tema | Decisão |
| --- | --- |
| Especialidades de leito | UTI, EMERGENCIA, ENFERMARIA |
| Abertura | Nome do paciente, local em texto, prioridade e tipo de leito (UTI, EMERGENCIA ou ENFERMARIA). O despacho usa o tipo já gravado |
| Prioridade | VERMELHA, AMARELA, VERDE |
| Prazo da reserva não confirmada | Configurável. Valor padrão: 2 minutos |
| Hospital na demonstração | A cada reserva, a unidade responde ao acaso: aceite, recusa ou silêncio |
| Status | Lista da proposta de apoio, mais EM_DESPACHO, FALHA_DESPACHO, ALOCADA, EXPIRADO e AGUARDANDO_LEITO |
| Reserva não confirmada ou recusada | O leito daquela reserva volta a LIVRE. A ambulância fica AGUARDANDO_LEITO. A nova busca pede outro leito LIVRE da mesma especialidade. Se não houver outro, reserva de novo o mesmo leito |
| Sucesso da Saga | Ocorrência DESPACHADA, leito RESERVADO, notificação publicada. A confirmação da unidade é um passo seguinte |
| Idempotência | Chave da ocorrência. Entrega at-least-once não aloca o mesmo recurso duas vezes |

AGUARDANDO_LEITO cobre a ambulância a caminho ou já no local. Nesse status a equipe presta os primeiros socorros e espera saber para qual leito, e portanto para qual unidade, a vítima será levada.

## 6. Status

### Ocorrência

| Status | Significado |
| --- | --- |
| ABERTA | Registrada, com prioridade e tipo de leito, ainda sem despacho concluído |
| EM_DESPACHO | Saga em andamento, ou nova busca de leito com ambulância já comprometida |
| DESPACHADA | Ambulância e leito reservado. A unidade ainda pode confirmar, recusar ou estourar o prazo |
| EM_TRANSPORTE | Fora do fluxo obrigatório desta entrega. Existe no modelo para não ser reaproveitado com outro sentido |
| CONCLUIDA | Fora do fluxo obrigatório desta entrega |
| CANCELADA | Ocorrência encerrada sem atendimento. Não é o destino automático da reserva expirada |
| FALHA_DESPACHO | Não houve ambulância, ou a Saga não conseguiu sequer reservar o primeiro leito |

### Ambulância

| Status | Significado |
| --- | --- |
| DISPONIVEL | Pode receber chamado |
| ALOCADA | Presa pela Saga durante o despacho, ainda sem saída para o local |
| DESPACHADA | Designada à ocorrência, com leito reservado |
| EM_ATENDIMENTO | No local, em atendimento, já com destino de leito definido |
| AGUARDANDO_LEITO | A caminho ou no local, prestando os primeiros socorros, sem destino de leito |
| INDISPONIVEL | Fora de serviço |

### Leito

| Status | Significado |
| --- | --- |
| LIVRE | Pode ser reservado |
| RESERVADO | Preso por uma ocorrência, aguardando confirmação da unidade ou já confirmado |
| OCUPADO | Sem vaga. Não entra na reserva nem na lista de disponíveis |
| EXPIRADO | Passagem da reserva não confirmada, imediatamente antes de voltar a LIVRE |

## 7. Requisitos funcionais

### Identidade e abertura — Igor

- **RF01.** O regulador informa usuário e senha. O MS-Identidade autentica e devolve um token.
- **RF02.** Com token válido, o regulador abre ocorrência informando nome do paciente, local em texto, prioridade (VERMELHA, AMARELA ou VERDE) e tipo de leito (UTI, EMERGENCIA ou ENFERMARIA).
- **RF03.** A abertura grava a ocorrência ABERTA, já com o tipo de leito, e devolve identificador, status e tipo de leito. A abertura não dispara a Saga.
- **RF04.** O gateway é a única entrada dessa chamada e das demais chamadas da regulação.
- **RF05.** A chamada ao MS-Leitos usa timeout, retry e circuit breaker. Unidade indisponível não derruba a abertura de ocorrência nem segura a central por tempo indefinido.

### Despacho e Saga — Felipe

- **RF06.** O regulador pede o despacho de uma ocorrência ABERTA. A especialidade do leito é a gravada na abertura: UTI, EMERGENCIA ou ENFERMARIA. O despacho não pede outro tipo.
- **RF07.** O MS-Ocorrencias só despacha ocorrência ABERTA. Um segundo despacho da mesma chave não aloca outra ambulância nem outro leito.
- **RF08.** O MS-Frota aloca uma ambulância DISPONIVEL de forma exclusiva. A ambulância passa a ALOCADA e fica ligada à ocorrência.
- **RF09.** Se não houver ambulância DISPONIVEL, a ocorrência vai a FALHA_DESPACHO e nenhum leito é reservado.
- **RF10.** O MS-Leitos reserva um leito LIVRE da especialidade pedida, numa unidade de destino. O leito passa a RESERVADO e guarda a ocorrência dona e o instante da reserva. Leito de outra especialidade não serve.
- **RF11.** Se não houver leito LIVRE da especialidade neste primeiro passo, a ambulância volta a DISPONIVEL e a ocorrência vai a FALHA_DESPACHO. Nesse momento a equipe ainda não saiu para o local.
- **RF12.** Havendo ambulância e leito, a ocorrência fica DESPACHADA, a ambulância fica DESPACHADA e o MS-Ocorrencias publica o evento de ocorrência despachada.
- **RF13.** Em seguida o MS-Leitos consulta a unidade simulada. O resultado de cada reserva, na demonstração, é aleatório entre aceite, recusa e silêncio.
- **RF14.** No aceite, o leito permanece RESERVADO, com marca de confirmação e horário. Reserva confirmada não entra no job de expiração. A ambulância segue DESPACHADA, com destino conhecido.
- **RF15.** Na recusa, aquele leito é cancelado (EXPIRADO e em seguida LIVRE). A ambulância passa a AGUARDANDO_LEITO. A ocorrência volta a EM_DESPACHO, continua com a mesma ambulância e dispara nova reserva da mesma especialidade. Havendo outro leito LIVRE dessa especialidade, a reserva pede esse outro. Não havendo, pede de novo o mesmo leito.
- **RF16.** A publicação da mensagem ocorre depois do commit do serviço que a originou. A operação de alocar e a de reservar são idempotentes pela chave da ocorrência.

### Ocupação, cache e expiração — Sérgio Leandro

- **RF17.** A regulação consulta, pelo gateway, as ambulâncias DISPONIVEL e os leitos LIVRE de uma especialidade.
- **RF18.** A consulta lê o Redis do serviço dono. Se não houver valor válido, lê o MySQL desse serviço e preenche o cache.
- **RF19.** Alocar, reservar, confirmar, recusar, expirar ou compensar invalida o cache do serviço que alterou a ocupação.
- **RF20.** Um job no MS-Leitos varre leitos RESERVADO sem confirmação há mais que o prazo configurado. O padrão é 2 minutos.
- **RF21.** O job aplica o mesmo desfecho da recusa (RF15): devolve o leito, põe a ambulância em AGUARDANDO_LEITO e pede nova reserva da mesma especialidade. Não devolve a ambulância para DISPONIVEL.
- **RF22.** A nova busca prefere um leito LIVRE da especialidade que esta ocorrência ainda não tenha recusado nem deixado expirar. Se esse conjunto estiver vazio e ainda existir leito LIVRE da especialidade, reserva um desses, mesmo que já tenha sido recusado ou expirado para ela. Cada reserva passa de novo pelo hospital aleatório.
- **RF23.** Se não existir nenhum leito LIVRE da especialidade, a ambulância permanece AGUARDANDO_LEITO, a ocorrência permanece EM_DESPACHO e o MS-Notificacoes registra que falta destino. O job pode repetir a busca no ciclo seguinte, quando surgir vaga. Leito já recusado ou expirado que esteja LIVRE conta como vaga.
- **RF24.** O MS-Notificacoes grava o alerta de expiração, o alerta de falta de leito e o evento de despacho. O texto basta. Não há envio real de SMS.

## 8. Regras de negócio

- **RN01.** Dois despachos não ficam com a mesma ambulância nem com o mesmo leito.
- **RN02.** Leito OCUPADO e leito de outra especialidade não aparecem como disponíveis e não são reservados.
- **RN03.** A confirmação da unidade é independente do sucesso inicial da Saga. DESPACHADA com leito RESERVADO ainda pode terminar em recusa ou prazo estourado.
- **RN04.** Recusa e prazo estourado cancelam só o leito daquela tentativa. A ambulância permanece na ocorrência.
- **RN05.** AGUARDANDO_LEITO significa primeiros socorros em curso, no deslocamento ou no local, sem unidade de destino.
- **RN06.** Quando a nova reserva é aceita, o leito fica RESERVADO e confirmado, a ocorrência volta a DESPACHADA e a ambulância volta a DESPACHADA, agora com o destino desse leito.
- **RN07.** O prazo de expiração é propriedade do MS-Leitos. Alterar o número não exige mudança de código.
- **RN08.** O acaso do hospital vale para cada tentativa de reserva, inclusive as automáticas. Aceite encerra a busca. Recusa e silêncio continuam a busca, o silêncio só depois do prazo.
- **RN09.** Mensagem repetida da mesma ocorrência não cria segunda alocação nem segunda reserva ativa.
- **RN10.** O tipo de leito fica definido na abertura. O despacho, a reserva, a nova busca após recusa ou prazo e o cache usam esse tipo. O despacho não troca a especialidade.
- **RN11.** Recusa e prazo devolvem o leito a LIVRE e guardam o histórico. Esse histórico ordena a busca seguinte: outro leito LIVRE da mesma especialidade vem primeiro. O mesmo leito volta a ser candidato quando for o único LIVRE dessa especialidade.

## 9. Dados mínimos

| Informação | Onde vive | Obrigatório |
| --- | --- | --- |
| Regulador: usuário e senha | MS-Identidade | sim |
| Paciente: nome | MS-Ocorrencias | sim |
| Ocorrência: id, status, local, prioridade, tipo de leito, ambulância, leito atual | MS-Ocorrencias | tipo de leito existe desde a abertura; ambulância e leito passam a existir no despacho |
| Ambulância: identificação, status | MS-Frota | sim |
| Unidade de saúde e leito: código, especialidade, status, ocorrência dona, instante da reserva, confirmação e horário | MS-Leitos | dono, instante e confirmação existem a partir da reserva |
| Notificação: ocorrência, mensagem, momento | MS-Notificacoes | sim, quando houver evento |

O tipo de leito entra na abertura (RF02). O despacho (RF06) reserva um leito dessa especialidade e não altera o tipo.

Carga do estudo de caso:

- duas ambulâncias, as duas começam DISPONIVEL
- leitos das três especialidades, com e sem vaga (LIVRE e OCUPADO)
- ao menos um leito LIVRE e um sem vaga

## 10. Requisitos não funcionais

- **RNF01.** Java 21, Spring Boot e Maven.
- **RNF02.** MySQL local em cada serviço que guarda dado transacional. MongoDB só no MS-Notificacoes. Redis no MS-Frota e no MS-Leitos. RabbitMQ compartilhado.
- **RNF03.** Entrega das mensagens at-least-once, com consumidor idempotente.
- **RNF04.** A interface de demonstração é o Swagger de cada serviço, acessado pela regulação através do gateway.
- **RNF05.** Testes JUnit 5 cobrem alocação exclusiva, idempotência, compensação do primeiro leito inexistente (ambulância volta a DISPONIVEL) e o desfecho de recusa ou prazo (ambulância fica AGUARDANDO_LEITO e uma nova reserva da mesma especialidade é pedida).
- **RNF06.** Docker Compose sobe MySQL, MongoDB, Redis e RabbitMQ.

## 11. Critérios de aceitação do estudo de caso

1. Regulador autentica e abre ocorrência ABERTA com nome, local, prioridade e tipo de leito.
2. Consulta de ocupação devolve ambulâncias livres e leitos livres da especialidade, a partir do cache quando ele estiver válido.
3. Despacho de ocorrência ABERTA com duas ambulâncias e leitos com e sem vaga termina, no sucesso, com ocorrência DESPACHADA, ambulância DESPACHADA e leito RESERVADO da especialidade pedida.
4. Pedido de especialidade sem leito LIVRE não reserva leito de outra especialidade. A ambulância alocada nesse primeiro passo volta a DISPONIVEL e a ocorrência fica FALHA_DESPACHO.
5. Com reserva feita, o hospital aleatório produz os três resultados ao longo dos testes: aceite confirmado, recusa e silêncio até o prazo.
6. No aceite, a reserva confirmada não é expirada pelo job.
7. Na recusa e no prazo, o leito da tentativa volta a LIVRE, a ambulância fica AGUARDANDO_LEITO e uma nova reserva da mesma especialidade é solicitada. Se houver outro leito LIVRE dessa especialidade, a reserva usa esse outro. Se não houver, usa de novo o mesmo leito.
8. Sem nenhum leito LIVRE da especialidade, ambulância e ocorrência permanecem nessa espera e a notificação de falta de destino fica registrada.
9. Notificação de despacho e notificação de expiração são consultáveis no MS-Notificacoes.
10. Repetir o despacho da mesma ocorrência não prende uma segunda ambulância.

## 12. Rastreio por membro

| Membro | Requisitos |
| --- | --- |
| Igor | RF01 a RF05, e a proteção da chamada usada em RF13 |
| Felipe | RF06 a RF16, inclusive a nova busca de leito disparada por RF15 |
| Sérgio Leandro | RF17 a RF24, sendo RF21 o job que reutiliza o desfecho de RF15 |

## 13. Leitura combinada com a proposta

A proposta dizia, na falha ou no timeout, liberar a ambulância e reenfileirar ou cancelar o chamado. A decisão de 13/10/2026 substitui esse trecho: a ambulância permanece na ocorrência, em AGUARDANDO_LEITO, e o sistema pede sozinho um leito da mesma especialidade. Havendo outro compatível, pede esse. Não havendo, pode pedir de novo o mesmo leito. Cancelar a ocorrência ou devolvê-la a ABERTA não é o caminho da reserva recusada ou expirada.

A liberação da ambulância para DISPONIVEL continua valendo só quando o primeiro despacho não consegue reservar leito nenhum (RF11) ou quando não há ambulância (RF09).
