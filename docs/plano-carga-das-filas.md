# Plano — carga das filas para inspeção no RabbitMQ

- Disciplina: Arquitetura de Software: Teoria e Prática 2026
- Grupo 3: Felipe Melchior de Britto; Igor Benedito Costa Gonçalves; Sérgio Leandro Alves da Silva
- Data: 18/10/2026
- Base: as oito filas já declaradas em `omnileito-comum` (`Filas` e `TopologiaRabbit`) e o painel em `http://localhost:8085/`
- Estado: recomendações da seção 8 aceitas em 18/10/2026 e implementadas. O cartão fica em `http://localhost:8085/`.

As seções 2 a 6 descrevem o caminho implementado. A seção 8 registra as alternativas já escolhidas. A seção 9 é o funcionamento e o ensaio passo a passo, com a leitura da tela do RabbitMQ.

O ensaio não cria fila nova, não troca a saga e não substitui o roteiro. Ele torna visível, no painel do RabbitMQ, o que uma saga terminada deixa em zero.

---

## 1. O que a tela do RabbitMQ mostra hoje

`http://localhost:15672`, usuário `guest`, senha `guest`, aba Queues. São oito filas classic, duráveis, estado running:

| Fila | Quem publica | Quem consome |
| --- | --- | --- |
| `saga.alocar-ambulancia` | MS-Ocorrencias | MS-Frota |
| `saga.ambulancia-alocada` | MS-Frota | MS-Ocorrencias |
| `saga.reservar-leito` | MS-Ocorrencias | MS-Leitos |
| `saga.leito-reservado` | MS-Leitos | MS-Ocorrencias |
| `saga.leito-expirado` | MS-Leitos | MS-Ocorrencias |
| `saga.atualizar-ambulancia` | MS-Ocorrencias | MS-Frota |
| `saga.retentar-busca-leito` | MS-Leitos | MS-Ocorrencias |
| `notificacao.registrar` | MS-Ocorrencias ou MS-Leitos | MS-Notificacoes |

Na inspeção de 18/10/2026, Ready, Unacked e Total estão em `0`. As taxas incoming, deliver/get e ack estão em `0.00/s`, ou em branco quando a fila não teve movimento recente. Isso é o sistema ocioso depois de uma saga concluída. Não é falha do broker.

O olho não segura essa passagem. Um despacho publica e o consumidor tira a mensagem antes do refresh. Para a banca ver número maior que zero, a fila precisa encher com o consumidor parado, e a taxa precisa aparecer numa rajada com o consumidor no ar.

---

## 2. Dois ensaios, um cartão

O cartão novo fica no painel, depois de Estado inicial. O navegador continua falando só com o gateway. O RabbitMQ Management não é chamado pelo browser: o guest da porta 15672 fica para o olho humano.

### 2.1 Rajada — testa os microserviços

Os seis processos estão no ar e os consumidores escutam.

O botão **Rajada** pede uma quantidade. O padrão é 20. O teto é 100. Para cada unidade, o MS-Ocorrencias abre uma ocorrência (`Carga Fila`, local `Rua da Fila`, prioridade `VERDE`, tipo `ENFERMARIA`) e dispara o despacho que já existe. A saga real percorre frota, leitos e notificações. Não há mensagem inventada.

Entre um despacho e o seguinte há uma pausa de 200 ms, para o refresh do RabbitMQ pegar incoming e deliver/get acima de zero. No fim, as filas voltam a zero se os consumidores acompanharam. A carga de ambulância e de leito fica gasta. O botão **Estado inicial** devolve a carga.

O que se confere:

| Onde | Durante a rajada | Depois |
| --- | --- | --- |
| RabbitMQ, taxas | incoming, deliver/get e ack acima de `0.00/s` nas filas que a saga tocou | voltam a zero |
| RabbitMQ, Ready | pode piscar acima de zero; não é a prova | `0` |
| Painel, ocupação | as duas ambulâncias saem de `DISPONIVEL` e os leitos livres se esgotam | Estado inicial restaura |
| Mongo | surgem notificações das ocorrências que chegaram a `DESPACHADA` | Estado inicial esvazia |

Uma ocorrência sem ambulância livre termina em `FALHA_DESPACHO` e ainda assim passou por `saga.alocar-ambulancia` e `saga.ambulancia-alocada`. Isso é o teste da frota esgotada, não um erro da rajada.

### 2.2 Acúmulo — deixa a coluna Ready parada para a foto

Os consumidores das oito filas são pausados sem derrubar a porta Java. Em seguida o MS-Ocorrencias publica a quantidade pedida em cada uma das oito filas. O padrão é 40. O teto é 200.

O refresh do RabbitMQ deve mostrar, nas oito linhas, Ready igual à quantidade, Unacked `0` e Total igual à quantidade. A taxa incoming sobe na publicação e depois volta a zero, com Ready parado. Essa é a inspeção visual.

O botão **Esvaziar e religar** apaga as oito filas e só então religa os consumidores. As mensagens do acúmulo não são processadas. A saga não muda.

---

## 3. Por que o consumidor precisa pausar, e não o processo

Parar o processo da porta 8095 abre o circuito de leitos e mistura este ensaio com o cartão Circuito. Parar o da 8093 impede a própria rajada. O acúmulo só precisa que o `@RabbitListener` deixe de tirar mensagem.

Cada serviço que consome fila ganha dois comandos, com o processo no ar:

| Serviço | Pausa e retomada |
| --- | --- |
| MS-Ocorrencias | `POST /api/ocorrencias/demonstracao/filas/pausa` e `.../retomada` |
| MS-Frota | `POST /api/ambulancias/demonstracao/filas/pausa` e `.../retomada` |
| MS-Leitos | `POST /api/leitos/demonstracao/filas/pausa` e `.../retomada` |
| MS-Notificacoes | `POST /api/notificacoes/demonstracao/filas/pausa` e `.../retomada` |

A pausa usa o registro de listeners do Spring, com um `id` em cada `@RabbitListener`. A retomada liga o mesmo container. Na subida do processo os listeners nascem ligados, como hoje.

A publicação do acúmulo é uma chamada só, no MS-Ocorrencias, que já fala com o Rabbit:

`POST /api/ocorrencias/demonstracao/filas/acumulo`

Corpo: `{"quantidade": 40}`.

O painel só publica depois que as quatro pausas responderam `204`. Se uma pausa falhar, não publica e mostra qual serviço ficou de fora.

A chamada de leitos passa pelo circuito do gateway. Com o MS-Leitos no ar, a pausa responde `204`. Com ele parado, responde `503` e o acúmulo não começa.

---

## 4. O que vai dentro da mensagem acumulada

A mensagem tem o formato que o consumidor daquela fila já desserializa. O `ocorrenciaId` é `inspecao-fila`, que não é UUID de ocorrência aberta. A quantidade se repete em cada fila.

| Fila | Corpo |
| --- | --- |
| `saga.alocar-ambulancia` | `AlocarAmbulancia` |
| `saga.ambulancia-alocada` | `AmbulanciaAlocada` com sucesso falso |
| `saga.reservar-leito` | `ReservarLeito`, tipo `ENFERMARIA`, tentativa `1` |
| `saga.leito-reservado` | `LeitoReservado` com sucesso falso |
| `saga.leito-expirado` | `LeitoExpirado` |
| `saga.atualizar-ambulancia` | `AtualizarAmbulancia` |
| `saga.retentar-busca-leito` | `RetentarBuscaLeito` |
| `notificacao.registrar` | `RegistrarNotificacao`, tipo `DESPACHO` |

Esses corpos existem para o broker aceitar a publicação e para a fila mostrar o total. O ensaio recomendado não os entrega ao listener. **Esvaziar e religar** purga antes da retomada.

Não há fila de erro. Uma mensagem que o listener rejeita volta para Ready e fica em ciclo. Por isso o acúmulo não usa JSON quebrado, não usa fila com nome novo e não tem botão de "processar o acúmulo".

---

## 5. O cartão no painel

Título: **Filas**. Rótulo de cima: `RabbitMQ`.

| Peça | Papel |
| --- | --- |
| API | `POST .../filas/pausa`, `POST .../filas/retomada`, `POST /api/ocorrencias/demonstracao/filas/acumulo` e a rajada `POST /api/ocorrencias/demonstracao/rajada` |
| Gera | MS-Ocorrencias publica. Na rajada, a saga segue com MS-Frota, MS-Leitos e MS-Notificacoes |
| Consome | Na rajada, os listeners de sempre. No acúmulo, ninguém consome até **Esvaziar e religar** |

Campos e botões:

1. Quantidade da rajada, padrão 20. Botão **Rajada**.
2. Quantidade do acúmulo, padrão 40. Botão **Pausar e acumular**.
3. Botão **Esvaziar e religar**.
4. A área de resposta lista o HTTP de cada serviço e lembra: abrir `http://localhost:15672` e atualizar a aba Queues.

**Pausar e acumular** chama as quatro pausas e, se todas forem `204`, o acúmulo. **Esvaziar e religar** chama o esvaziamento que o Estado inicial já usa e depois as quatro retomadas.

O Estado inicial esvazia as filas e, em seguida, religa os listeners. Sem isso, um acúmulo interrompido deixaria a regulação surda com os processos no ar.

A rajada e o acúmulo recusam quantidade fora de 1 a 100 e de 1 a 200, com `400` e `CAMPO_INVALIDO`. Uma segunda pausa, com o listener já parado, responde `204` de novo.

---

## 6. Roteiro da inspeção

Com os seis processos no ar e a carga limpa. O RabbitMQ Management aberto na aba Queues.

### Acúmulo

1. No painel, quantidade 40, **Pausar e acumular**. Esperado: quatro pausas em `204` e o acúmulo em `204`.
2. Atualizar o RabbitMQ. As oito filas com Ready `40`, Unacked `0`, Total `40`.
3. Esperar alguns segundos e atualizar de novo. Ready continua `40`. A taxa incoming volta a `0.00/s`.
4. **Esvaziar e religar**. Atualizar. As oito filas em zero. Um despacho manual depois disso ainda percorre a saga: a pausa não ficou presa.

### Rajada

1. Estado inicial, se o acúmulo anterior gastou recurso. O acúmulo recomendado não gasta.
2. Quantidade 20, **Rajada**. Enquanto a resposta não fecha, atualizar o RabbitMQ duas ou três vezes.
3. Esperado nas taxas: números acima de zero em `saga.alocar-ambulancia` e `saga.ambulancia-alocada`. As demais sobem enquanto ainda houver ambulância e leito.
4. Ao terminar, Ready e Unacked em zero. A ocupação mostra a frota gasta. Estado inicial devolve `ABC-1234`, `XYZ-5678` e um livre por especialidade.

### O que fica de fora deste ensaio

- Não mede tempo de resposta nem cria gráfico.
- Não aumenta a concorrência do listener. Continua um consumidor por fila.
- Não publica pelo formulário do RabbitMQ Management.
- Não usa o botão para derrubar Java nem container.

---

## 7. Testes de unidade e documentos

Sem Docker e sem broker, no mesmo estilo dos testes atuais.

| Teste | O que prova |
| --- | --- |
| Rajada com quantidade 2 | Abre duas ocorrências e despacha as duas. Não publica direto na fila, deixando a saga existente fazer isso |
| Rajada com quantidade 0 e com 101 | `CAMPO_INVALIDO` e nenhuma abertura |
| Pausa e retomada | O container do listener recebe stop e depois start. A segunda pausa não falha |
| Acúmulo com quantidade 2 | O publicador é chamado duas vezes para cada uma das oito filas, no exchange que a fila já usa |
| Estado inicial com listener pausado | A purga acontece e, depois dela, a retomada. Assim a mensagem de inspeção não entra na saga |

O manual da tela ganha o cartão Filas. O roteiro ganha uma seção curta que aponta para este ensaio e para `http://localhost:15672`. O `DROP DATABASE` da seção 2 do roteiro continua valendo quando o schema precisa nascer de novo.

---

## 8. Decisões para validar

Cada item tem a opção recomendada. A opção A de cada item foi aceita em 18/10/2026 e está no cartão.

### 8.1 Onde o ensaio é disparado

| Opção | Efeito |
| --- | --- |
| A. Cartão no painel da porta 8085 | Mesma origem, mesmo token, a banca não muda de ferramenta |
| B. Só curl no roteiro | Visível no terminal, invisível ao lado dos outros cartões |
| C. Publicar na tela do RabbitMQ, fila por fila | Não passa pelos microserviços e é manual nas oito filas |
| D. Processo Java novo só para a carga | Mais um terminal, fora dos seis já combinados |

Recomendada: **A**.

### 8.2 Como segurar mensagem bastante tempo para a foto

| Opção | Efeito |
| --- | --- |
| A. Pausar o listener e manter o processo no ar | Ready fica parado. A porta não cai. O circuito de leitos não abre |
| B. Encerrar o processo consumidor com Ctrl+C | Ready também sobe, mas a 8095 abre o circuito e a 8093 mata a regulação |
| C. Publicar mais rápido do que o consumidor, sem pausar | A taxa aparece. Ready só pisca e a foto falha |
| D. Fila nova que ninguém consome | O número sobe numa nona linha. A inspeção deixa de ser das oito filas da saga |

Recomendada: **A**.

### 8.3 O que acontece com as mensagens acumuladas

| Opção | Efeito |
| --- | --- |
| A. Purgar e só então religar o listener | A foto fica limpa. A saga não corre em cima de `inspecao-fila` |
| B. Religar e deixar os listeners processarem o acúmulo | Gasta ambulância e leito, pode reencadear reserva e expiração, e uma falha volta a mensagem para Ready em ciclo |
| C. Ensinar cada listener a ignorar `inspecao-fila` | O dreno vira teste do consumidor, mas altera o caminho da saga para um id artificial |
| D. JSON inválido de propósito | Sem fila de erro, a mensagem rejeitada retorna e a fila não esvazia |

Recomendada: **A**. O teste dos microserviços fica na rajada, que usa a saga de verdade.

### 8.4 Tamanho da carga

| Opção | Efeito |
| --- | --- |
| A. Campo no cartão, rajada até 100, acúmulo até 200, padrões 20 e 40 | Dá para a foto e cabe num refresh |
| B. Quantidade fixa no código | Menos um campo, menos escolha na hora da banca |
| C. Sem teto | Um número alto enche o disco do broker e atrasa a purga |

Recomendada: **A**.

### 8.5 Quando codar

| Opção | Efeito |
| --- | --- |
| A. Só depois da validação desta seção | O cartão e os endpoints nascem no formato escolhido |
| B. Codar agora a recomendação | Antecipa a tela se a seção 8 for aceita como está |

Recomendada: **A**. Aceita e implementada em 18/10/2026. O ensaio de uso está na seção 9.

---

## 9. Funcionamento e forma de testar

O cartão **Filas** fica no painel, em `http://localhost:8085/`, depois de Estado inicial. O login é `regulador` / `regulador`. O RabbitMQ Management fica em outra aba: `http://localhost:15672`, `guest` / `guest`, **Queues and Streams**. O painel não abre essa aba. Quem testa atualiza a tabela no momento certo.

Os seis processos precisam estar no ar: gateway `8085`, identidade `8092`, ocorrências `8093`, frota `8094`, leitos `8095`, notificações `8096`. A coluna State `running` do RabbitMQ significa que a fila existe. Ela permanece `running` com o listener pausado. O sinal de pausa é Ready parado e deliver/get em `0.00/s`.

Há dois ensaios. A rajada prova a saga. O acúmulo prova a foto de fila cheia. Um não substitui o outro.

### 9.1 Rajada

**Rajada** pede uma quantidade, padrão 20, teto 100. O MS-Ocorrencias, para cada unidade, abre uma ocorrência e chama o despacho que a regulação já usa.

| Campo da ocorrência | Valor |
| --- | --- |
| Paciente | `Carga Fila 1`, `Carga Fila 2`, e assim por diante |
| Local | `Rua da Fila` |
| Prioridade | `VERDE` |
| Tipo de leito | `ENFERMARIA` |

Entre um despacho e o seguinte há 200 ms. Vinte unidades levam pouco mais de 4 segundos, mais o tempo da saga. A resposta do cartão fecha só no fim e traz HTTP `200` com `{"quantidade": 20}`. Esse `200` diz que as vinte aberturas e os vinte despachos foram pedidos. O estado final de cada ocorrência aparece na ocupação e no MySQL, não nesse JSON.

Os listeners ficam ligados. Cada mensagem entra e sai. Por isso Ready, Unacked e Total tendem a ficar em `0` durante o ensaio e ficam em `0` quando ele termina. A prova da rajada é a coluna **Message rates**:

| Coluna | O que significa na rajada |
| --- | --- |
| incoming | Alguém publicou naquela fila na janela recente |
| deliver / get | Um consumidor recebeu mensagem na janela recente |
| ack | O consumidor confirmou a mensagem |
| Ready | Mensagem ainda esperando. Na rajada, o valor esperado no fim é `0` |

A taxa do management é uma média da janela recente. Um pico de vinte mensagens em poucos segundos continua aparecendo, por exemplo como `0.40/s`, depois que Ready já voltou a zero. Mais um refresh, com o sistema quieto, devolve `0.00/s`.

A carga da semente tem duas ambulâncias e um leito livre de enfermaria. As primeiras ocorrências da rajada percorrem alocação, reserva e notificação. As seguintes encontram a frota esgotada, passam por `saga.alocar-ambulancia` e `saga.ambulancia-alocada` e terminam em `FALHA_DESPACHO`. Esse fim é o teste da frota esgotada.

`saga.leito-expirado` só recebe mensagem quando uma reserva sem confirmação completa dois minutos. Uma rajada que acabou de terminar deixa essa taxa em `0.00/s`. `notificacao.registrar` sobe nas ocorrências que chegaram a publicar despacho; se o refresh vier depois da janela, a taxa já voltou a `0.00/s` e o documento está no Mongo.

`saga.retentar-busca-leito` continua se movendo com o MS-Leitos no ar. A cada 15 segundos a varredura publica uma retentativa para cada especialidade que ainda tem leito livre, e o MS-Ocorrencias consome. Ready segue em `0` porque o consumidor acompanha. Essa fila é a única que pode mostrar deliver/get e ack acima de zero com o painel já em HTTP `200`.

**Estado inicial** depois da rajada. Ele devolve `ABC-1234` e `XYZ-5678` a `DISPONIVEL`, um leito livre por especialidade, apaga as ocorrências `Carga Fila` e esvazia notificações.

### 9.2 Acúmulo

**Pausar e acumular** pede uma quantidade, padrão 40, teto 200. A ordem é fixa:

1. Pausa o listener do MS-Ocorrencias (`saga.ambulancia-alocada`, `saga.leito-reservado`, `saga.leito-expirado`, `saga.retentar-busca-leito`).
2. Pausa o listener do MS-Frota (`saga.alocar-ambulancia`, `saga.atualizar-ambulancia`).
3. Pausa o listener do MS-Leitos (`saga.reservar-leito`) e a varredura de 15 segundos.
4. Pausa o listener do MS-Notificacoes (`notificacao.registrar`).
5. Só com as quatro respostas em `204`, publica a quantidade em cada uma das oito filas.

O processo Java de cada porta continua no ar. A pausa da porta 8095 não abre o circuito. Se uma pausa não for `204`, o acúmulo não publica e o cartão religa quem já tinha pausado. A área de resposta termina com `Acúmulo não começou.`

Cada mensagem usa o formato que o listener daquela fila já conhece. O `ocorrenciaId` é `inspecao-fila`, que não é ocorrência aberta. O corpo está na seção 4. A publicação existe para o broker contar. Ela não deve ser entregue.

Com os listeners parados, o refresh mostra nas oito linhas Ready igual à quantidade, Unacked `0` e Total igual à quantidade. incoming sobe na hora da publicação e depois volta a `0.00/s`, com Ready parado. A varredura de leitos também fica parada: sem isso, `saga.retentar-busca-leito` ganharia uma mensagem nova a cada 15 segundos e a foto deixaria de bater com a quantidade.

**Esvaziar e religar** apaga as oito filas e só então religa os quatro listeners e a varredura. As mensagens de `inspecao-fila` não entram na saga. Ambulância e leito permanecem como estavam. O refresh seguinte mostra Ready, Unacked e Total em `0`.

O **Estado inicial** faz a mesma purga, restaura MySQL, Redis e Mongo, e religa os listeners depois de esvaziar. Serve tanto para encerrar uma rajada quanto para desfazer um acúmulo que ficou no meio.

### 9.3 Preparar

1. Confira as seis portas. Entre no painel com `regulador` / `regulador`.
2. Abra `http://localhost:15672` na aba Queues.
3. No painel, **Reiniciar no estado inicial**. Esperado: cinco linhas `HTTP 204` e o texto de carga restaurada.
4. Atualize o RabbitMQ. As oito filas em Ready `0`. `saga.retentar-busca-leito` pode mostrar uma taxa baixa: é a varredura, com Ready ainda em `0`.

### 9.4 Testar o acúmulo

Faça este ensaio primeiro. Ele não gasta ambulância nem leito, e a foto pede a carga estável.

1. Deixe a quantidade do acúmulo em `40`. Clique **Pausar e acumular**.
2. Esperado no cartão, nesta ordem: `Ocorrências pausa: HTTP 204`, `Frota pausa: HTTP 204`, `Leitos pausa: HTTP 204`, `Notificações pausa: HTTP 204`, `Acúmulo: HTTP 204`.
3. Atualize o RabbitMQ. As oito filas com Ready `40`, Unacked `0`, Total `40`. State continua `running`.
4. Espere mais de 15 segundos e atualize de novo. Ready continua `40` nas oito, inclusive em `saga.retentar-busca-leito`. incoming volta a `0.00/s`.
5. No painel, ocupação: `ABC-1234` e `XYZ-5678` seguem `DISPONIVEL`. O acúmulo não alocou ambulância.
6. Clique **Esvaziar e religar**. Esperado: `Esvaziar: HTTP 204`, as quatro retomadas em `204` e o texto de consumidores religados.
7. Atualize o RabbitMQ. As oito filas em zero.
8. Abra uma ocorrência qualquer e despache. A saga percorre de novo. A pausa não ficou presa. Em seguida, **Estado inicial**, para o próximo ensaio achar a semente limpa.

### 9.5 Testar a rajada

1. Carga limpa, pelo Estado inicial do passo anterior.
2. Deixe a quantidade da rajada em `20`. Deixe a aba do RabbitMQ visível. Clique **Rajada**.
3. Enquanto o botão estiver desabilitado e o cartão disser que a rajada está em andamento, atualize Queues duas ou três vezes.
4. Durante esses refreshes, incoming, e em seguida deliver/get e ack, passam de `0.00/s` em `saga.alocar-ambulancia` e `saga.ambulancia-alocada`. `saga.reservar-leito`, `saga.leito-reservado`, `saga.atualizar-ambulancia` e `notificacao.registrar` sobem enquanto ainda houver ambulância e leito. Ready pode piscar acima de zero. O número parado não é a prova deste ensaio.
5. O cartão fecha com HTTP `200` e `{"quantidade": 20}`.
6. Atualize o RabbitMQ de novo. Ready, Unacked e Total em `0`. A média ainda pode mostrar `0.40/s` em incoming nas filas que a saga tocou. `saga.retentar-busca-leito` pode manter deliver/get e ack acima de zero. `saga.leito-expirado` fica em `0.00/s`.
7. Ocupação: as duas ambulâncias saíram de `DISPONIVEL`. O leito livre de enfermaria foi usado. Parte das ocorrências `Carga Fila` fica `DESPACHADA` e o restante `FALHA_DESPACHO`.
8. **Reiniciar no estado inicial**. Cinco `HTTP 204`. Ocupação de novo com as duas ambulâncias e um livre por especialidade. Mais um refresh no RabbitMQ: Ready em `0` e as taxas a caminho de `0.00/s`.

### 9.6 Leitura da rajada de 20 em 18/10/2026

O cartão respondeu HTTP `200` e `{"quantidade": 20}`. No mesmo momento, a aba Queues mostrava as oito filas em Ready `0`, Unacked `0` e Total `0`. incoming em `0.40/s` nas filas `saga.alocar-ambulancia`, `saga.ambulancia-alocada`, `saga.atualizar-ambulancia`, `saga.leito-reservado`, `saga.reservar-leito` e `saga.retentar-busca-leito`. deliver/get e ack em `0.40/s` só em `saga.retentar-busca-leito`. `notificacao.registrar` e `saga.leito-expirado` em `0.00/s`.

Essa tela é o fim da rajada, com os consumidores no ar. O `0.40/s` é a média do pico que já passou, não uma fila enchendo. Para ver a taxa subir ao vivo, o refresh tem que acontecer com o botão **Rajada** ainda desabilitado. A foto de Ready `40` é o outro botão, **Pausar e acumular**.

Depois dessa rajada a semente fica gasta. O passo seguinte, antes de outro cenário do manual, é **Estado inicial**.

### 9.7 Quando o resultado foge do esperado

| O que aparece | O que fazer |
| --- | --- |
| Alguma pausa em HTTP `504` e o texto `Acúmulo não começou.` | O acúmulo não publicou. Quem chegou a pausar foi religado. Repita **Pausar e acumular** com os seis processos no ar |
| Acúmulo em `204`, mas Ready volta a zero na hora | O listener daquela fila ainda consumiu. Confira se a pausa daquele serviço foi `204` antes da publicação |
| Ready sobe sozinho em `saga.retentar-busca-leito` durante a pausa | A varredura de leitos ainda está publicando. A pausa do MS-Leitos precisa ser a que também segura essa varredura. **Esvaziar e religar** e repetir o acúmulo |
| Rajada em HTTP `400` | A quantidade está fora de 1 a 100 |
| Acúmulo em HTTP `400` | A quantidade está fora de 1 a 200 |
| Leitos pausa em HTTP `503` | O processo da porta 8095 está parado. Sobe o MS-Leitos. O circuito de consulta continua no limite de 2 segundos; a rajada e a pausa usam o tempo maior do gateway |
| Depois da rajada a ocupação mostra a frota gasta | Esperado. **Estado inicial** restaura |
