# Fonte de estudo — problemas de controle e soluções

Para a apresentação do Grupo 3. Disciplina Arquitetura de Software: Teoria e Prática, 2026.

- Sistema: OmniLeito, plataforma de despacho de ocorrência e reserva de leito
- Data desta leitura: 20/10/2026
- Base: `docs/proposta-workshop-praticas.md`, `docs/documento-de-requisitos.md` (0.3) e `docs/maquina-de-estados.md` (0.2)
- Onde isso aparece nos slides: blocos do Igor (0–12), do Felipe (12–24), do Sérgio (24–45, explicação e ensaio)
- Os caminhos de arquivo são relativos à raiz do repositório. O trecho ao lado do parágrafo é o que a frase descreve.

O objetivo do estudo de caso, no texto da proposta, é tornar observável e tratável a inconsistência entre ocorrência, frota e leito. Este arquivo organiza essa frase em problemas de controle: o que o sistema precisa manter verdadeiro quando não existe uma transação única, qual mecanismo faz isso, e por que esse mecanismo cabe na disciplina.

---

## 1. O que é problema de controle neste estudo

Controle, aqui, não é a tela. É o mecanismo que impede um estado inválido e que restaura um estado válido quando um passo falha no meio.

Ocorrência, ambulância e leito são contextos com donos diferentes. Cada um tem o próprio processo e o próprio banco. Uma escrita em um deles faz commit sozinha. Não há `commit` que grave os três ao mesmo tempo, e não deve haver: o hospital pode demorar, recusar ou calar, e a central não pode ficar com a ambulância e o leito travados à espera dessa resposta.

O controle passa a ser cinco peças. As seções 3 a 12 mostram onde cada uma está no código. Aqui fica o que a peça significa, com um fato do OmniLeito.

### Invariante, escrita na máquina de estados

Invariante é uma regra que continua verdadeira depois de cada evento. Ela descreve o trio ocorrência, ambulância e leito, não um campo isolado.

Exemplos que a banca pode cobrar:

- `ABERTA` não tem ambulância nem leito ligados.
- `DESPACHADA` tem uma ambulância `DESPACHADA` e um leito `RESERVADO` da especialidade gravada na abertura.
- `FALHA_DESPACHO` não tem ambulância ligada nem leito reservado para essa ocorrência.
- `EM_DESPACHO` com ambulância `AGUARDANDO_LEITO` não tem leito `RESERVADO` ativo.
- Duas ocorrências não ficam com a mesma ambulância nem com o mesmo leito.

A máquina conjunta em `docs/maquina-de-estados.md`, seção 4, é a lista dessas regras. Cada linha é um evento e o estado dos três depois dele. A implementação existe para reproduzir esse quadro. Um evento que deixe o trio numa combinação ausente do quadro é falha de controle, mesmo com os três bancos gravados.

Quem grava cada transição está na seção 6 do mesmo arquivo: abertura no MS-Ocorrências pela API; os demais status da ocorrência, ao tratar a mensagem; ambulância no MS-Frota; leito no MS-Leitos.

```text
docs/maquina-de-estados.md
```

```text
Invariantes depois de cada evento:

- ABERTA não tem ambulância nem leito.
- FALHA_DESPACHO não tem ambulância ligada nem leito reservado para essa ocorrência.
- DESPACHADA tem uma ambulância DESPACHADA e um leito RESERVADO da especialidade gravada na abertura.
- EM_DESPACHO com ambulância AGUARDANDO_LEITO não tem leito RESERVADO ativo.
- Duas ocorrências não ficam com a mesma ambulância nem com o mesmo leito.
```

### Transação local, dona de um agregado só

Agregado é o conjunto de dados que muda junto e tem um escritor. No OmniLeito há três agregados de negócio, cada um no próprio MySQL:

- a ocorrência, escrita só pelo MS-Ocorrências
- a ambulância, escrita só pelo MS-Frota
- o leito, escrito só pelo MS-Leitos

Transação local é o `commit` desse escritor. Ela é ACID por dentro: as quatro garantias valem para o MySQL daquele serviço e param na fronteira dele. O trio ocorrência, ambulância e leito não forma uma transação ACID.

No exemplo da frota, `alocarSeDisponivel` é um único `UPDATE`: a ambulância passa a `ALOCADA` e ganha a ocorrência somente se o status ainda for `DISPONIVEL`. As quatro letras, nesse `UPDATE`:

- **Atomicidade.** Status e ocorrência mudam juntos. O banco devolve 1 se a linha foi atualizada, ou 0 se nada mudou. Não fica ambulância `ALOCADA` sem ocorrência, nem ocorrência gravada numa ambulância que continuou `DISPONIVEL`.
- **Consistência.** A escrita obedece à regra local da frota: só sai de `DISPONIVEL` quem está `DISPONIVEL`, e a ambulância fica ligada a uma ocorrência. O que a regra exige dos outros dois bancos — leito reservado, ocorrência `DESPACHADA` — esta transação não verifica. Isso é trabalho da saga.
- **Isolamento.** Duas alocações simultâneas na mesma ambulância disputam a mesma linha. Uma recebe 1 e fica com ela. A outra recebe 0 e tenta a próxima livre, ou publica `SEM_AMBULANCIA`. As duas não commitam `ALOCADA` para chamados diferentes.
- **Durabilidade.** Depois do commit, `ALOCADA` permanece se o processo da porta 8094 cair. O passo seguinte, a reserva do leito, pode ainda nem ter começado.

O `UPDATE` condicional está em `ms-frota/src/main/java/com/unesp/omnileito/frota/AmbulanciaRepository.java`. Quem o chama, e desiste da candidata quando o retorno é 0, é `FrotaService.alocar`, no mesmo pacote.

```java
// AmbulanciaRepository.java
@Query("update Ambulancia a set a.status = com.unesp.omnileito.frota.StatusAmbulancia.ALOCADA, "
		+ "a.ocorrenciaId = :ocorrenciaId "
		+ "where a.id = :id and a.status = com.unesp.omnileito.frota.StatusAmbulancia.DISPONIVEL")
int alocarSeDisponivel(@Param("id") Long id, @Param("ocorrenciaId") String ocorrenciaId);
```

```java
// FrotaService.java — alocar
for (Ambulancia candidata : ambulancias.findByStatusOrderById(StatusAmbulancia.DISPONIVEL)) {
	if (ambulancias.alocarSeDisponivel(candidata.getId(), pedido.ocorrenciaId()) == 1) {
		cache.apagar(CacheOcupacao.FROTA);
		// publica AmbulanciaAlocada com sucesso, depois do commit
		return;
	}
}
publicar(new AmbulanciaAlocada(pedido.ocorrenciaId(), false, null, null, "SEM_AMBULANCIA"));
```

O leito tem o mesmo desenho em `ms-leitos/src/main/java/com/unesp/omnileito/leitos/LeitoRepository.java`, método `reservarSeLivre`: o `UPDATE` só grava `RESERVADO` se o status ainda for `LIVRE`. `LeitoService.marcarPrimeiro` percorre os candidatos e fica com o primeiro que devolver 1.

A transação da frota não abre linha no banco de leitos. A do leito não abre linha no banco da ocorrência.

Por isso o despacho anda em saltos. O MS-Frota pode já ter commitado `ALOCADA` quando o MS-Leitos ainda vai responder. Esse intervalo é legítimo. O controle não tenta apagá-lo com um commit dos três. Ele o atravessa com o passo seguinte e, se esse passo falhar, com a compensação.

### Passo seguinte, publicado depois dessa transação

O passo seguinte é o comando que acorda o outro serviço. Ele sai numa fila, depois do commit de quem o originou.

No despacho, a sequência é esta: o MS-Ocorrências grava `EM_DESPACHO` e, após o commit, publica `AlocarAmbulancia`. O MS-Frota grava `ALOCADA` e, após o commit, publica `AmbulanciaAlocada`. O MS-Ocorrências, se houve ambulância, publica `ReservarLeito`. O MS-Leitos grava `RESERVADO` e publica `LeitoReservado`.

`depoisDoCommit` registra esse envio em `afterCommit`. A transação que desfaz não deixa comando na fila. O serviço seguinte só começa um trabalho que o anterior já tornou durável. Publicar no meio da transação, antes do commit, faria a frota alocar uma ambulância para uma ocorrência que o MS-Ocorrências ainda pode desfazer.

O hook está em `omnileito-comum/src/main/java/com/unesp/omnileito/comum/PublicadorRabbit.java`. Os nomes das filas estão em `omnileito-comum/src/main/java/com/unesp/omnileito/comum/Filas.java`. O primeiro uso, no despacho, está em `ms-ocorrencias/src/main/java/com/unesp/omnileito/ocorrencias/OcorrenciaSagaService.java`.

```java
// PublicadorRabbit.java
public void depoisDoCommit(Runnable acao) {
	if (TransactionSynchronizationManager.isSynchronizationActive()) {
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				acao.run();
			}
		});
		return;
	}
	acao.run();
}
```

```java
// Filas.java
public static final String EXCHANGE_SAGA = "omnileito.saga";
public static final String EXCHANGE_NOTIFICACAO = "omnileito.notificacao";
public static final String ALOCAR_AMBULANCIA = "saga.alocar-ambulancia";
public static final String AMBULANCIA_ALOCADA = "saga.ambulancia-alocada";
public static final String RESERVAR_LEITO = "saga.reservar-leito";
public static final String LEITO_RESERVADO = "saga.leito-reservado";
public static final String LEITO_EXPIRADO = "saga.leito-expirado";
public static final String ATUALIZAR_AMBULANCIA = "saga.atualizar-ambulancia";
public static final String RETENTAR_BUSCA_LEITO = "saga.retentar-busca-leito";
public static final String REGISTRAR_NOTIFICACAO = "notificacao.registrar";
```

```java
// OcorrenciaSagaService.java — despachar
ocorrencia.setStatus(StatusOcorrencia.EM_DESPACHO);
despachos.save(new Despacho(ocorrencia.getId(), agora));
publicador.depoisDoCommit(() -> publicador.saga(Filas.ALOCAR_AMBULANCIA,
		new AlocarAmbulancia(ocorrencia.getId(), ocorrencia.getPrioridade())));
return new DespachoHttp(202, resposta(ocorrencia));
```

### Compensação de negócio, quando o passo seguinte falha

O commit anterior já aconteceu. Um `rollback` no banco da frota não alcança essa ambulância: a transação dela encerrou. A compensação é uma ação nova, com regra de negócio, que leva o trio de volta a uma linha válida da máquina de estados.

O desfecho depende do ponto do processo:

- Ainda na primeira busca, sem leito. A equipe não saiu. A compensação devolve a ambulância a `DISPONIVEL` e a ocorrência a `FALHA_DESPACHO`.
- Recusa ou prazo, com a equipe já no chamado. A compensação devolve só aquele leito a `LIVRE`. A ambulância vai a `AGUARDANDO_LEITO` e a ocorrência volta a `EM_DESPACHO`, com a mesma ambulância, para pedir outro leito da mesma especialidade.

Os dois desfechos estão em `OcorrenciaSagaService`: `semLeito` para a primeira busca, `recusarOuExpirar` para recusa e prazo. O leito em si volta a `LIVRE` em `ms-leitos/src/main/java/com/unesp/omnileito/leitos/LeitoService.java`, método `recusar`.

```java
// OcorrenciaSagaService.java — semLeito, tentativa 1
ocorrencia.setStatus(StatusOcorrencia.FALHA_DESPACHO);
ocorrencia.setAmbulanciaId(null);
ocorrencia.setLeitoId(null);
publicador.depoisDoCommit(() -> publicador.saga(Filas.ATUALIZAR_AMBULANCIA,
		new AtualizarAmbulancia(ocorrencia.getId(), evento.ambulanciaId(), "DISPONIVEL")));
```

```java
// OcorrenciaSagaService.java — recusarOuExpirar
ocorrencia.setStatus(StatusOcorrencia.EM_DESPACHO);
ocorrencia.setLeitoId(null);
publicador.depoisDoCommit(() -> {
	publicador.saga(Filas.ATUALIZAR_AMBULANCIA,
			new AtualizarAmbulancia(ocorrencia.getId(), ambulancia, "AGUARDANDO_LEITO"));
	publicador.saga(Filas.RESERVAR_LEITO, pedidoReserva(ocorrencia, ambulancia));
});
```

```java
// LeitoService.java — recusar
leito.setStatus(StatusLeito.EXPIRADO);
leito.setStatus(StatusLeito.LIVRE);
leito.setOcorrenciaId(null);
leito.setConfirmado(false);
cache.apagar(leito.getTipoLeito());
```

Os dois casos tratam “este leito não ficou com a ocorrência”. O estado certo da ambulância muda porque o fato em volta mudou. Liberar a equipe para a base nos dois casos quebraria a invariante de `AGUARDANDO_LEITO`: primeiros socorros em curso, sem destino.

### Limite de falha, para um serviço instável não parar os outros

Limite de falha é o teto de espera e o compartimento da dependência que adoece. Sem esse teto, a central fica presa na chamada do hospital e deixa de abrir ocorrência.

No OmniLeito o compartimento é a rota `/api/leitos/**`. Ela tem timeout de 2 segundos, retry só em `GET` e circuit breaker. Com o MS-Leitos parado, a consulta de leito responde 503 na hora, código `SERVICO_LEITOS_INDISPONIVEL`. Login, abertura e despacho seguem, porque não passam por esse filtro. A falha do leito fica no leito.

A rota está em `api-gateway/src/main/java/com/unesp/omnileito/gateway/RotasGateway.java`. O corpo do 503 está em `api-gateway/src/main/java/com/unesp/omnileito/gateway/FallbackLeitosController.java`. O timeout de 2 segundos está em `api-gateway/src/main/resources/application.properties`, propriedade `resilience4j.timelimiter.instances.leitos.timeout-duration`.

```java
// RotasGateway.java — só esta rota leva circuito e retry
.route("leitos", rota -> rota.path("/api/leitos/**")
		.filters(filtro -> filtro
				.circuitBreaker(config -> config.setName("leitos")
						.setFallbackUri("forward:/fallback/leitos"))
				.retry(config -> config.setRetries(2).setMethods(HttpMethod.GET)))
		.uri(leitos))
```

```java
// FallbackLeitosController.java
@RequestMapping("/fallback/leitos")
public ResponseEntity<ErroApi> leitosIndisponivel() {
	return ResponseEntity.status(503).body(new ErroApi("SERVICO_LEITOS_INDISPONIVEL",
			"A consulta de leitos está indisponível."));
}
```

---

## 2. Mapa para a banca

| Problema de controle | Invariante que se protege | Solução no OmniLeito | Quem explica |
| --- | --- | --- | --- |
| Três contextos, nenhuma transação global | Cada dado tem um escritor | Microsserviço, banco por serviço, Saga orquestrada | Felipe |
| Dois chamados no mesmo recurso | Uma ambulância e um leito por ocorrência | Alocação exclusiva na transação local, chave da ocorrência | Felipe |
| Falha no meio do despacho | O trio não fica pela metade | Compensação semântica, dois desfechos diferentes | Felipe |
| Mensagem repetida ou publicada cedo demais | A repetição não cria o segundo recurso | Publicar depois do commit e consumidor idempotente | Felipe |
| O hospital não responde | Reserva sem confirmação não fica presa para sempre | Job no dono do leito, mesmo desfecho da recusa | Sérgio |
| A consulta de vaga precisa ser rápida e verdadeira | A lista não oferece recurso já tomado | Cache-aside no Redis, invalidado em toda escrita | Sérgio |
| A unidade fora do ar para a central | Abrir ocorrência não depende de leito | Gateway com timeout, retry e circuit breaker só em leitos | Igor |
| Várias portas e senha em toda chamada | Uma entrada, uma autenticação | API Gateway e JWT validado na borda | Igor |
| O alerta não pode entrar na transação do leito | Despacho, prazo e falta de leito ficam registrados | Mongo no MS-Notificações, índice único | Sérgio |
| Saga concluída some da fila | Dá para ver o controle acontecer | Painel e RabbitMQ: taxas na rajada, Ready parado no acúmulo | Sérgio, no ensaio |

A tabela é o índice. Cada linha abaixo diz o que quebra se o controle faltar, o que a solução segura e onde o código está.

### Três contextos, nenhuma transação global

Felipe. O código está na seção 3.

Ocorrência, ambulância, leito e alerta mudam em processos diferentes. Cada um faz o próprio `commit`. Não existe uma transação que grave os três bancos juntos, e o hospital pode demorar minutos até aceitar, recusar ou calar. Segurar a ambulância e o leito abertos até essa resposta deixaria o recurso escasso preso.

A invariante é: cada dado tem um escritor. A ocorrência só é gravada pelo MS-Ocorrências, a ambulância só pelo MS-Frota, o leito só pelo MS-Leitos. O MS-Ocorrências guarda o status, que é a memória do fluxo, e publica o comando do próximo passo. Frota e leitos não se chamam. Cada um confirma o passo de que é dono, numa transação ACID local. A consistência entre os bancos é a saga: uma sequência de commits locais, com compensação para o que já commitou.

A abertura grava `ABERTA` e não publica fila. A saga começa no `POST` de despacho, que grava `EM_DESPACHO` e devolve HTTP 202. Esse 202 significa que a coordenação começou, não que a ambulância e o leito já existem.

### Dois chamados no mesmo recurso

Felipe. O código está na seção 4.

Duas ocorrências não podem ficar com a mesma ambulância nem com o mesmo leito. Leito de outra especialidade, ou leito `OCUPADO`, também não serve. O tipo fica gravado na abertura e o despacho não o troca.

A exclusão acontece dentro do banco de quem é dono. Na frota, `alocarSeDisponivel` é um `UPDATE` que passa a ambulância a `ALOCADA` e grava a ocorrência somente se o status ainda for `DISPONIVEL`. O retorno 1 é a concessão. O retorno 0 manda tentar a próxima livre. No leito, `reservarSeLivre` faz o mesmo com `LIVRE` e `RESERVADO`. A chave é o id da ocorrência, gerado pelo MS-Ocorrências. O id da ambulância e o id do leito são locais. A ocorrência guarda esses números como referência, sem chave estrangeira entre bancos.

O segundo despacho da mesma ocorrência, quando ela já não está `ABERTA`, devolve HTTP 200 com o mesmo par. Não publica `AlocarAmbulancia` de novo.

Há dois “não há leito”, e o desfecho da ambulância muda:

- Na primeira busca, a equipe ainda não saiu. Sem leito, a ambulância volta a `DISPONIVEL` e a ocorrência vai a `FALHA_DESPACHO`.
- Numa busca seguinte, a equipe já está no chamado. Sem nenhum livre, a ambulância fica `AGUARDANDO_LEITO`, a ocorrência fica `EM_DESPACHO` e o alerta é `FALTA_LEITO`.

### Falha no meio do despacho

Felipe. O código está na seção 5.

O passo anterior já sofreu commit quando o passo seguinte falha. Um `rollback` no banco da frota não alcança a ambulância que já está `ALOCADA`. A compensação é uma ação nova, que leva o trio a uma linha válida da máquina de estados. O desfecho depende do ponto do processo, não de desfazer a operação pelo inverso simétrico.

| Momento | Ocorrência | Ambulância | Leito |
| --- | --- | --- | --- |
| Sem ambulância livre | `FALHA_DESPACHO` | nenhuma muda | nenhum |
| Sem leito na primeira tentativa | `FALHA_DESPACHO` | volta a `DISPONIVEL` | nenhum reservado |
| Aceite | `DESPACHADA` | `DESPACHADA` | `RESERVADO` confirmado |
| Silêncio da unidade | `DESPACHADA` | `DESPACHADA` | `RESERVADO` sem confirmação |
| Recusa ou prazo | `EM_DESPACHO` | `AGUARDANDO_LEITO` | aquele leito volta a `LIVRE` |
| Nova busca sem nenhum livre | `EM_DESPACHO` | continua `AGUARDANDO_LEITO` | nenhum reservado |

`AGUARDANDO_LEITO` é primeiros socorros em curso, a caminho ou no local, sem unidade de destino. Recusa e prazo cancelam só o leito daquela tentativa. A mesma ambulância pede outro leito da mesma especialidade. Se não houver outro que esta ocorrência ainda não tenha recusado nem deixado expirar, reserva de novo o mesmo leito. Cada reserva passa outra vez pelo hospital.

O hospital da demonstração não é endpoint. `SorteioUnidade.sortear` devolve aceite, recusa ou silêncio, ou obedece a um modo fixo trocado em execução. Aceite encerra a busca. Recusa pede outro leito na hora. Silêncio deixa o leito `RESERVADO` sem confirmação e espera o prazo. `DESPACHADA` com leito `RESERVADO` ainda pode terminar em recusa ou prazo: a confirmação da unidade é um passo seguinte ao sucesso inicial da saga.

### Mensagem repetida ou publicada cedo demais

Felipe. O código está na seção 6, e o hook de publicação está na seção 1.

O RabbitMQ entrega pelo menos uma vez. A mesma mensagem pode chegar de novo. Se o consumidor alocar outra vez, dois chamados disputam a frota, ou a mesma ocorrência prende dois leitos. O outro lado é publicar antes do commit: a fila recebe um comando de um estado que a transação ainda pode desfazer.

Duas regras fecham isso. A mensagem só sai em `afterCommit`, no `PublicadorRabbit.depoisDoCommit`. Rollback não publica. O consumidor reconhece a ocorrência: se a frota já tem ambulância para aquele id, devolve a mesma; se o leito já está `RESERVADO` para aquele id, devolve a reserva atual. No Mongo, a tríade ocorrência, tipo e instante grava um alerta só. A consulta antecede o `save`, e o índice único `uk_notificacao` segura a entrega que passou pela consulta, via `DuplicateKeyException`.

O hook garante que rollback não publica. Uma queda do processo no intervalo entre o commit e o envio ao broker ainda pode perder a mensagem. Neste recorte não há tabela de outbox. A defesa descreve o depois do commit que o código tem.

### O hospital não responde

Sérgio. O código está na seção 7.

Silêncio não gera evento. Sem controle de tempo, o leito `RESERVADO` sem confirmação fica fora da lista de livres e a ocorrência acredita ter destino. O prazo é propriedade da reserva: padrão de 2 minutos, em `omnileito.reserva.expiracao-minutos`, no MS-Leitos.

`LeitoService.expirarReservas` roda a cada 15 segundos. A consulta pega leito `RESERVADO`, `confirmado = false` e `reservado_em` anterior ao limite. Reserva aceita não entra. Para cada vencida, o leito volta a `LIVRE` pelo mesmo `recusar` da recusa da unidade, e saem `LeitoExpirado` e a notificação `EXPIRACAO`. O MS-Ocorrências trata o prazo com `recusarOuExpirar`: ocorrência `EM_DESPACHO`, ambulância `AGUARDANDO_LEITO`, nova reserva da mesma especialidade. O job não devolve a ambulância a `DISPONIVEL`.

O job vive no dono do leito. O MS-Ocorrências não fica lendo o banco de leitos para saber se o prazo estourou.

### A consulta de vaga precisa ser rápida e verdadeira

Sérgio. O código está na seção 8.

A regulação consulta ambulâncias livres e leitos por especialidade o tempo todo. Ir ao MySQL em toda leitura alonga o despacho. Uma cópia no Redis que sobreviva à alocação ou à reserva oferece um recurso que já não existe.

O desenho é cache-aside no serviço dono. `CacheLeitos.ler` devolve a cópia. Se a chave não existe, ou se o Redis lança exceção, `LeitoService.disponiveis` lê o MySQL e tenta preencher o cache. A chave é `leitos:disponiveis:` mais `UTI`, `EMERGENCIA` ou `ENFERMARIA`. Na frota, a chave é `frota:disponiveis`. Toda escrita de ocupação apaga a chave daquele serviço: alocar, reservar, confirmar, recusar, expirar, compensar. A especialidade entra na chave porque a vaga de UTI não responde pela enfermaria.

O Redis acelera a leitura. A fonte da vaga continua o MySQL. Apagar na escrita fecha a janela no momento em que o estado muda. Um tempo de vida sozinho ainda serviria a lista velha enquanto a ambulância já estivesse alocada.

### A unidade fora do ar para a central

Igor. O código está na seção 9, e o filtro da rota está na seção 1.

A unidade, e o MS-Leitos que fala com ela, falha. Sem um teto na borda, a central espera essa falha em toda chamada e deixa de abrir ocorrência.

O compartimento é só `/api/leitos/**`, em `RotasGateway`. Timeout de 2 segundos (`resilience4j.timelimiter.instances.leitos.timeout-duration`), retry de duas tentativas só em `GET`, e circuit breaker com fallback `forward:/fallback/leitos`. O fallback responde HTTP 503, código `SERVICO_LEITOS_INDISPONIVEL`. Com a porta 8095 parada, consultar leito devolve 503 e `POST /api/ocorrencias` continua em 201, porque abertura, despacho e login não passam por esse filtro.

O retry fica na leitura. Repetir um `POST` de reserva na falha ambígua poderia reservar duas vezes, e esse risco já é da saga. Os 60 segundos de `spring.cloud.gateway.server.webflux.httpclient.response-timeout` existem para a rajada e a pausa dos listeners. Eles não mudam o corte de 2 segundos da chamada de leito.

### Várias portas e senha em toda chamada

Igor. O código está na seção 10.

Cinco processos com endereço próprio obrigam a regulação a conhecer a topologia e a repetir a senha em cada um. A porta única é o gateway, em 8085. O MS-Identidade, em 8092, autentica `regulador` / `regulador` e emite um JWT HMAC. O gateway valida a assinatura em `JwtGatewayFilter`. Os outros serviços não são reconsultados para checar a senha a cada request.

O filtro deixa passar `/api/auth/`, `/fallback/` e qualquer caminho que não comece com `/api/`. A página do painel entra nesse último caso: o HTML abre sem token. O restante exige `Authorization: Bearer`. Token ausente ou inválido responde 401, código `TOKEN_INVALIDO`. O token fica na aba. Fechar a aba pede login de novo.

### O alerta não pode entrar na transação do leito

Sérgio. O código está na seção 11, e a gravação idempotente está na seção 6.

Despacho, expiração e falta de leito precisam ficar registrados. Esse registro é efeito do fluxo, não ocupação de vaga. Gravá-lo na mesma transação do leito acoplaria o canal de alerta ao banco que reserva. SMS real está fora do recorte. O texto basta.

O MS-Notificações, porta 8096, grava no Mongo as coleções `notificacoes` e `auditoria`. Os tipos são `DESPACHO`, `EXPIRACAO` e `FALTA_LEITO`. `DESPACHO` sai de `publicarDestino`, junto com a ambulância indo a `DESPACHADA`. `EXPIRACAO` sai do job, junto com `LeitoExpirado`. `FALTA_LEITO` sai de `semLeito` quando a tentativa é maior que 1 e não há nenhum livre. Os três saem por `notificacao.registrar`, depois do commit de quem publicou. A saga não espera o alerta para concluir o passo local.

### Saga concluída some da fila

Sérgio, no ensaio dos minutos 36 a 45. O código está na seção 12.

Uma saga que terminou deixa Ready, Unacked e Total em 0. O estado `running` só diz que a fila existe. O estado final no painel não mostra se a mensagem passou ou se nunca foi publicada.

Há duas provas, no cartão Filas, sem fila nova e sem processo novo.

- A rajada abre e despacha ocorrências de verdade (`Carga Fila i`, enfermaria, prioridade verde). Os listeners ficam ligados. A prova é a coluna de taxas enquanto o botão ainda está desabilitado. Ready em 0 no fim é saga drenada. Uma taxa que sobra depois do HTTP 200, por exemplo 0,40/s, é a média da janela. Com duas ambulâncias e um leito livre de enfermaria, o restante das ocorrências termina em `FALHA_DESPACHO`.
- O acúmulo pausa o `@RabbitListener` e mantém o processo no ar, então o circuito de leitos não abre. Só depois das quatro respostas 204 publica a quantidade pedida nas oito filas, com `ocorrenciaId` `inspecao-fila`. A foto é Ready parado e deliver/get em 0,00/s. A varredura de leitos pausa junto. Sem isso, a cada 15 segundos ela publicaria `RetentarBuscaLeito` e o Ready dessa fila passaria da quantidade. Esvaziar e religar faz `purgeQueue` síncrono e só então religa, para `inspecao-fila` não entrar na saga.

O ensaio começa pelo estado inicial, porque a rajada gasta a semente. Se sobrar um minuto, parar só a porta 8095 mostra o circuito: consulta em 503, abertura em 201.

---

## 3. Três contextos e nenhuma transação global

**Problema.** A regulação atravessa ocorrência, frota, leito e alerta. Esses contextos não compartilham ciclo de implantação nem a mesma transação. Uma central monolítica acopla o despacho à ocupação e ao aviso. Uma divisão sem coordenação produz ambulância em deslocamento sem leito, leito reservado duas vezes, ou chamado que segue depois de a equipe já ter sido liberada. É o problema escrito na proposta: coordenação de um fluxo que não cabe numa transação curta.

**Solução.** Seis processos. MySQL em quem guarda dado transacional (identidade, ocorrências, frota, leitos). Redis só como cache de leitura na frota e nos leitos. Mongo só no alerta. RabbitMQ entre os passos. O MS-Ocorrências é o orquestrador: ele guarda o status da ocorrência, que é o estado do processo, e publica o comando do próximo passo. Frota e leitos não chamam um ao outro. Cada um confirma o passo de que é dono.

**Fundamento.** Banco por serviço e contexto delimitado: o agregado tem um escritor. A consistência interna de cada banco continua ACID. A consistência entre bancos é saga, não commit de duas fases.

A saga, no sentido de Garcia-Molina e Salem (1987), troca a transação longa por uma sequência de transações locais, cada uma com commit próprio, mais uma compensação para o que já commitou. Richardson separa orquestração e coreografia. Aqui a orquestração cabe porque o status da ocorrência é a memória do fluxo: alguém precisa decidir se o próximo comando é reservar leito, compensar a ambulância ou encerrar. Na coreografia pura, frota e leitos reagiriam a eventos sem um dono do processo, e o quadro da máquina de estados ficaria espalhado.

O commit de duas fases seguraria o lock da ambulância e do leito até o hospital responder. O hospital, neste caso, pode ficar em silêncio por minutos. O recurso escasso ficaria preso num coordenador. A saga solta cada lock no commit local e trata o tempo longo como outro passo, não como uma transação aberta.

A abertura grava `ABERTA` e não publica fila. A saga começa no despacho. Separar o registro do chamado e a coordenação dos recursos evita disparar alocação num formulário que a regulação ainda pode só consultar.

`abrir` e `despachar` estão em `ms-ocorrencias/src/main/java/com/unesp/omnileito/ocorrencias/OcorrenciaSagaService.java`.

```java
// OcorrenciaSagaService.java — abrir: grava e devolve, sem fila
Paciente paciente = pacientes.save(new Paciente(pedido.paciente().nome().trim()));
Ocorrencia ocorrencia = new Ocorrencia(UUID.randomUUID().toString(), paciente, pedido.local().trim(),
		pedido.prioridade(), pedido.tipoLeito(), LocalDateTime.now());
return resposta(ocorrencias.save(ocorrencia));
```

---

## 4. Recurso escasso disputado

**Problema.** Duas ocorrências não podem ficar com a mesma ambulância nem com o mesmo leito (RN01). A especialidade também é controle: leito de outra especialidade, ou leito `OCUPADO`, não serve (RN02). O tipo fica gravado na abertura e o despacho não o troca (RN10).

**Solução.** A exclusão acontece dentro da transação de quem é dono do recurso. O MS-Frota escolhe uma ambulância `DISPONIVEL` e a passa a `ALOCADA` ligada à ocorrência. O MS-Leitos escolhe um leito `LIVRE` da especialidade pedida e o passa a `RESERVADO`, com a ocorrência dona e o instante. A chave de tudo é o id da ocorrência, gerado pelo MS-Ocorrências. Ids de ambulância e de leito são locais; a ocorrência guarda o número como referência, sem chave estrangeira entre bancos.

O segundo `POST` de despacho da mesma ocorrência, se ela já não está `ABERTA`, devolve HTTP 200 com o mesmo par. Não começa outra saga. O leito exclusivo está em `ms-leitos/src/main/java/com/unesp/omnileito/leitos/LeitoRepository.java`.

```java
// OcorrenciaSagaService.java — despachar
if (ocorrencia.getStatus() != StatusOcorrencia.ABERTA) {
	return new DespachoHttp(200, resposta(ocorrencia));
}
```

```java
// LeitoRepository.java — reservarSeLivre
@Query("update Leito l set l.status = com.unesp.omnileito.leitos.StatusLeito.RESERVADO, "
		+ "l.ocorrenciaId = :ocorrenciaId, l.reservadoEm = :agora, l.confirmado = false, "
		+ "l.confirmadoEm = null where l.id = :id and l.status = com.unesp.omnileito.leitos.StatusLeito.LIVRE")
int reservarSeLivre(@Param("id") Long id, @Param("ocorrenciaId") String ocorrenciaId,
		@Param("agora") LocalDateTime agora);
```

**Fundamento.** Exclusão mútua de recurso disputado é responsabilidade do agregado que o possui. Colocar essa trava no orquestrador, lendo os dois bancos, recriaria a transação global que a divisão removeu. O orquestrador pede. O dono concede ou recusa, na transação dele.

Há dois “não há leito”, e misturá-los é erro de controle:

- Na primeira busca, a equipe ainda não saiu. Sem leito, a ambulância volta a `DISPONIVEL` e a ocorrência vai a `FALHA_DESPACHO`.
- Numa busca seguinte, a equipe já está no chamado. Sem nenhum livre, a ambulância fica `AGUARDANDO_LEITO`, a ocorrência fica `EM_DESPACHO`, e o alerta é `FALTA_LEITO`.

---

## 5. Falha no meio e compensação semântica

**Problema.** Falha parcial é o centro da proposta: hospital indisponível, recusa de leito, prazo da equipe. Sem compensação, frota e leito divergem. A proposta de duas páginas descrevia a compensação como liberar ambulância, cancelar leito e reabrir ou cancelar o chamado. O requisito 0.3 corrigiu esse desfecho, e a correção é parte da defesa.

**Solução.** A compensação é uma transição de negócio, não um `rollback` do banco alheio. O quadro é este:

| Momento | Ocorrência | Ambulância | Leito |
| --- | --- | --- | --- |
| Sem ambulância livre | `FALHA_DESPACHO` | nenhuma muda | nenhum |
| Sem leito na primeira tentativa | `FALHA_DESPACHO` | volta a `DISPONIVEL` | nenhum reservado |
| Aceite | `DESPACHADA` | `DESPACHADA` | `RESERVADO` confirmado |
| Silêncio da unidade | `DESPACHADA` | `DESPACHADA` | `RESERVADO` sem confirmação |
| Recusa ou prazo | `EM_DESPACHO` | `AGUARDANDO_LEITO` | aquele leito volta a `LIVRE` |
| Nova busca sem nenhum livre | `EM_DESPACHO` | continua `AGUARDANDO_LEITO` | nenhum reservado |

`AGUARDANDO_LEITO` significa primeiros socorros em curso, a caminho ou no local, sem unidade de destino. Devolver a ambulância à base nesse momento liberaria uma equipe que já saiu. Por isso recusa e prazo cancelam só o leito daquela tentativa (RN04). A nova busca pede outro leito `LIVRE` da mesma especialidade que esta ocorrência ainda não tenha recusado nem deixado expirar. Se esse conjunto estiver vazio e ainda houver livre, reserva de novo o mesmo leito. Cada reserva passa outra vez pelo hospital.

O hospital da demonstração não é endpoint. `SorteioUnidade` sorteia aceite, recusa ou silêncio em cada tentativa, ou obedece a um modo fixo trocado em execução. Aceite encerra. Recusa pede outro leito na hora. Silêncio espera o prazo. A classe está em `ms-leitos/src/main/java/com/unesp/omnileito/leitos/SorteioUnidade.java`. Quem consome o sorteio é `LeitoService.reservar`, no mesmo pacote.

```java
// SorteioUnidade.java
public ResultadoUnidade sortear() {
	String fixo = resultadoFixo.get();
	if (!fixo.isBlank()) {
		return ResultadoUnidade.valueOf(fixo);
	}
	ResultadoUnidade[] valores = ResultadoUnidade.values();
	return valores[ThreadLocalRandom.current().nextInt(valores.length)];
}
```

```java
// LeitoService.java — reservar, depois de escolher o leito livre
ResultadoUnidade resultado = sorteio.sortear();
if (resultado == ResultadoUnidade.ACEITE) {
	escolhido.setConfirmado(true);
	escolhido.setConfirmadoEm(agora);
	cache.apagar(escolhido.getTipoLeito());
	publicar(evento(pedido, escolhido, true, "ACEITE", null));
	return;
}
if (resultado == ResultadoUnidade.SILENCIO) {
	cache.apagar(escolhido.getTipoLeito());
	publicar(evento(pedido, escolhido, true, "SILENCIO", null));
	return;
}
recusar(escolhido, pedido.ocorrenciaId(), "RECUSA_UNIDADE", agora);
publicar(evento(pedido, escolhido, false, null, "RECUSA_UNIDADE"));
```

**Fundamento.** Na saga, a compensação desfaz o efeito de negócio do passo que já sofreu commit. Ela não é o inverso simétrico da operação. O inverso de “alocar ambulância” seria “liberar ambulância”, e esse inverso só é verdadeiro enquanto a equipe não saiu. Depois da saída, o estado correto é outro. Isso é compensação semântica: o desfecho depende de em que ponto o processo está, e esse ponto está na máquina de estados.

A confirmação da unidade é independente do sucesso inicial da saga (RN03). `DESPACHADA` com leito `RESERVADO` ainda pode terminar em recusa ou prazo. Tratar o aceite como obrigatório para concluir o despacho prenderia a central no tempo do hospital.

---

## 6. Mensagem repetida e publicação cedo demais

**Problema.** O RabbitMQ entrega at-least-once (RNF03). A mesma mensagem pode chegar de novo depois de um redelivery. Se o consumidor alocar de novo, dois chamados disputam a frota, ou a mesma ocorrência prende dois leitos. O outro lado do problema é publicar antes do commit: a fila recebe um comando de um estado que a transação ainda pode desfazer.

**Solução.** Duas regras, as duas no requisito (RF16):

1. A mensagem só sai depois do commit de quem a originou. `PublicadorRabbit.depoisDoCommit`, em `omnileito-comum/src/main/java/com/unesp/omnileito/comum/PublicadorRabbit.java`, registra a publicação em `afterCommit` da transação local. Se a transação desfaz, a fila não recebe o comando. O método está na seção 1.
2. O consumidor é idempotente pela ocorrência. Alocar e reservar reconhecem a chave. Repetir a mensagem não cria segunda alocação nem segunda reserva ativa (RN09). No MS-Notificações, a mesma tríade ocorrência, tipo e instante grava um alerta só; o índice único e a `DuplicateKeyException` seguram a entrega que passou pela consulta.

A repetição da alocação sai cedo em `ms-frota/src/main/java/com/unesp/omnileito/frota/FrotaService.java`. A da reserva, em `LeitoService.reservar`, devolve o leito que já está `RESERVADO` para aquela ocorrência. O alerta está em `ms-notificacoes/src/main/java/com/unesp/omnileito/notificacoes/NotificacaoService.java`, com o índice em `NotificacaoDocumento.java`.

```java
// FrotaService.java — alocar, mensagem repetida da mesma ocorrência
Ambulancia jaAlocada = ambulancias.findByOcorrenciaId(pedido.ocorrenciaId()).orElse(null);
if (jaAlocada != null) {
	publicar(new AmbulanciaAlocada(pedido.ocorrenciaId(), true, jaAlocada.getId(),
			jaAlocada.getIdentificacao(), null));
	return;
}
```

```java
// LeitoService.java — reservar, já existe RESERVADO para esta ocorrência
Leito ativo = leitos.findByOcorrenciaId(pedido.ocorrenciaId()).orElse(null);
if (ativo != null && ativo.getStatus() == StatusLeito.RESERVADO) {
	publicar(eventoDaReservaAtual(pedido, ativo));
	return;
}
```

```java
// NotificacaoDocumento.java
@Document("notificacoes")
@CompoundIndex(name = "uk_notificacao",
		def = "{'ocorrenciaId': 1, 'tipo': 1, 'ocorridaEm': 1}", unique = true)
```

```java
// NotificacaoService.java — registrar
if (notificacoes.findByOcorrenciaIdAndTipoAndOcorridaEm(
		pedido.ocorrenciaId(), pedido.tipo(), pedido.ocorridaEm()).isPresent()) {
	return;
}
try {
	notificacoes.save(new NotificacaoDocumento(UUID.randomUUID().toString(), pedido.ocorrenciaId(),
			pedido.tipo(), pedido.mensagem(), pedido.ocorridaEm()));
	auditorias.save(new AuditoriaDocumento(UUID.randomUUID().toString(), pedido.ocorrenciaId(),
			pedido.tipo(), pedido.mensagem(), pedido.ocorridaEm(), origem));
} catch (DuplicateKeyException ignorado) {
	// Entrega repetida da mesma chave não cria outro alerta.
}
```

**Fundamento.** Receptor idempotente é o par obrigatório da entrega pelo menos uma vez: o canal não promete exatamente uma entrega, então o efeito não pode depender de a mensagem chegar uma vez só. A publicação depois do commit trata a escrita dupla entre banco e broker. O hook não é uma tabela de outbox. Ele garante que rollback não publica. Uma queda do processo no intervalo entre o commit e o envio ao broker ainda pode perder a mensagem. A disciplina pede o depois do commit; a outbox persistida seria o fechamento desse intervalo, e não está neste recorte. Dizer o limite evita defender um mecanismo que o código não tem.

---

## 7. O hospital que não responde

**Problema.** Silêncio não gera evento. Sem um controle de tempo, o leito `RESERVADO` sem confirmação fica fora da lista de livres e a ocorrência acredita ter destino. A proposta pedia expirar a reserva não confirmada e reutilizar a compensação. O requisito fixou o prazo padrão em 2 minutos, configurável no MS-Leitos (RN07), e repetiu a correção da seção 5: o job não devolve a ambulância a `DISPONIVEL`.

**Solução.** Um job no MS-Leitos varre, a cada 15 segundos, leito `RESERVADO`, não confirmado, com `reservado_em` anterior ao prazo. Para cada um, aplica o mesmo desfecho da recusa: devolve o leito a `LIVRE`, publica `LeitoExpirado` e pede nova busca. Reserva confirmada não entra na consulta. O MS-Ocorrências, ao receber o prazo, põe a ocorrência em `EM_DESPACHO` e a ambulância em `AGUARDANDO_LEITO`.

O método é `LeitoService.expirarReservas`, em `ms-leitos/src/main/java/com/unesp/omnileito/leitos/LeitoService.java`. O prazo e o intervalo estão em `ms-leitos/src/main/resources/application.properties`: `omnileito.reserva.expiracao-minutos=2` e `omnileito.reserva.varredura-ms=15000`. Quem recebe `LeitoExpirado` é `OcorrenciaSagaService.receberExpiracao`, que chama `recusarOuExpirar` com motivo `EXPIRACAO`.

```java
// LeitoService.java — expirarReservas
@Scheduled(fixedDelayString = "${omnileito.reserva.varredura-ms:15000}")
public void expirarReservas() {
	if (varredura.pausada()) {
		return;
	}
	LocalDateTime limite = LocalDateTime.now().minusMinutes(expiracaoMinutos);
	List<Leito> vencidas = leitos
			.findByStatusAndConfirmadoFalseAndReservadoEmBefore(StatusLeito.RESERVADO, limite);
	for (Leito leito : vencidas) {
		String ocorrenciaId = leito.getOcorrenciaId();
		Long ambulanciaId = ambulanciaPorOcorrencia.get(ocorrenciaId);
		String tipo = leito.getTipoLeito();
		Long leitoId = leito.getId();
		recusar(leito, ocorrenciaId, "EXPIRACAO", LocalDateTime.now());
		OffsetDateTime quando = LocalDateTime.now().atZone(ZONA).toOffsetDateTime();
		String texto = "Reserva do leito " + leito.getCodigo() + " expirou.";
		publicador.depoisDoCommit(() -> {
			publicador.saga(Filas.LEITO_EXPIRADO,
					new LeitoExpirado(ocorrenciaId, ambulanciaId, leitoId, tipo));
			publicador.notificacao(new RegistrarNotificacao(ocorrenciaId, "EXPIRACAO", texto, quando));
		});
	}
}
```

O job vive no dono do leito porque o prazo é propriedade da reserva, não um orquestrador externo. O MS-Ocorrências não fica polando o banco de leitos.

**Fundamento.** Quando a parte externa não emite evento, o controle de tempo é uma leitura periódica do estado local, não uma espera bloqueante na saga. Reutilizar o caminho da recusa evita uma segunda compensação com regra diferente para o mesmo fato de negócio: “este leito não está confirmado para esta ocorrência”.

---

## 8. Consulta rápida que não pode mentir

**Problema.** A regulação consulta ambulâncias livres e leitos por especialidade o tempo todo. Ir ao MySQL em toda leitura alonga o despacho. Guardar a lista num cache e não apagá-la depois de alocar ou reservar faz a central escolher um recurso que já não existe. Os dois erros estão na proposta: latência de um lado, cache sem invalidação do outro.

**Solução.** Cache-aside no serviço dono do dado. A primeira leitura da chave, se o Redis não tem valor, lê o MySQL e preenche o cache. Se o Redis falha, a consulta segue no MySQL: o cache acelera e não é a fonte da verdade. Toda escrita de ocupação apaga a chave daquele serviço: alocar, reservar, confirmar, recusar, expirar, compensar (RF19).

Chaves: `frota:disponiveis` e `leitos:disponiveis:UTI`, `EMERGENCIA` ou `ENFERMARIA`. A especialidade entra na chave porque a vaga de UTI não responde pela enfermaria.

A leitura dos leitos está em `LeitoService.disponiveis`. A chave e o acesso ao Redis estão em `ms-leitos/src/main/java/com/unesp/omnileito/leitos/CacheLeitos.java`. Na frota, a chave `frota:disponiveis` é a constante `CacheOcupacao.FROTA`, em `ms-frota/src/main/java/com/unesp/omnileito/frota/CacheOcupacao.java`. `apagar` é chamado em `FrotaService.alocar` e em `LeitoService` na reserva, no aceite, no silêncio, na recusa e na expiração.

```java
// CacheLeitos.java
public static String chave(String tipoLeito) {
	return "leitos:disponiveis:" + tipoLeito;
}

public Optional<String> ler(String chave) {
	try {
		return Optional.ofNullable(redis.opsForValue().get(chave));
	} catch (RuntimeException ignorado) {
		return Optional.empty();
	}
}

public void apagar(String tipoLeito) {
	try {
		redis.delete(chave(tipoLeito));
	} catch (RuntimeException ignorado) {
		// A próxima consulta reconstrói a lista a partir do MySQL.
	}
}
```

```java
// LeitoService.java — disponiveis
String chave = CacheLeitos.chave(tipoLeito);
return cache.ler(chave).flatMap(this::lerJson).orElseGet(() -> {
	List<LeitoResponse> lista = leitos.findByStatusAndTipoLeitoOrderById(StatusLeito.LIVRE, tipoLeito).stream()
			.map(LeitoResponse::de).toList();
	try {
		cache.gravar(chave, json.writeValueAsString(lista));
	} catch (Exception ignorado) {
		// Sem cache preenchido, a próxima consulta lê o MySQL de novo.
	}
	return lista;
});
```

**Fundamento.** Cache-aside coloca a aplicação no controle da leitura: acerto devolve a cópia, falta busca a fonte e repõe a cópia. A invalidação na escrita, e não um tempo de vida longo como única política, é o que mantém a invariante da vaga. Um TTL sozinho ainda serviria uma lista velha durante a janela em que a ambulância já foi alocada. Apagar na escrita fecha essa janela no momento em que o estado muda.

---

## 9. A falha de um subordinado não para a central

**Problema.** A unidade, e o serviço que fala com ela, falha. Sem um limite na borda, a central espera essa falha em toda chamada e o chamado fica sem resposta. A proposta localiza esse limite na chamada ao MS-Leitos.

**Solução.** Resilience4j só na rota `/api/leitos/**`: timeout de 2 segundos, retry nas leituras (`GET`, duas tentativas) e circuit breaker com fallback HTTP 503, código `SERVICO_LEITOS_INDISPONIVEL`. Abertura, despacho, login e as outras consultas não entram nesse filtro. Com a porta 8095 parada, consultar leito devolve 503 e abrir ocorrência continua em 201.

O tempo de 60 segundos do cliente HTTP do gateway é outra coisa: a rajada e a pausa dos listeners passam de 2 segundos. Ele não afrouxa o circuito. O circuito continua cortando a chamada de leito em 2 segundos. As duas propriedades estão juntas em `api-gateway/src/main/resources/application.properties`.

```properties
resilience4j.timelimiter.instances.leitos.timeout-duration=2s
resilience4j.circuitbreaker.instances.leitos.wait-duration-in-open-state=10s
spring.cloud.gateway.server.webflux.httpclient.response-timeout=60s
```

**Fundamento.** Circuit breaker, no sentido de Nygard (*Release It!*), deixa de chamar uma dependência doente e responde rápido por um caminho de reserva, para o chamador não esgotar a própria thread na espera. O retry fica no `GET` porque repetir uma leitura é seguro. Repetir um `POST` de reserva na falha ambígua poderia reservar duas vezes, e esse risco já é tratado pela saga, não pelo retry da borda.

O recorte do circuito é o controle. Envolver o gateway inteiro abriria o circuito por causa de leitos e derrubaria a abertura, que não precisa de leito. Isolar a dependência instável é o mesmo princípio do bulkhead: a falha fica no compartimento dela.

---

## 10. Uma entrada e uma autenticação

**Problema.** Cinco processos com endereço próprio obrigam a regulação a conhecer a topologia e a repetir a senha em cada um. A proposta pede um ponto único, sem expor o endereço interno à central nem ao hospital.

**Solução.** O Spring Cloud Gateway, na porta 8085, é a única porta da regulação. O MS-Identidade autentica e emite um JWT HMAC. O gateway valida a assinatura. Caminho de login (`/api/auth/`), fallback e o que não começa com `/api/` seguem sem token. O restante exige `Authorization: Bearer`. A página do painel é estática, servida pelo próprio gateway, fora desse filtro: o HTML abre, e cada chamada de API carrega o token da aba.

Os outros serviços não são reconsultados para validar a senha a cada request. A identidade entra na emissão. A borda entra na verificação.

O filtro está em `api-gateway/src/main/java/com/unesp/omnileito/gateway/JwtGatewayFilter.java`. A emissão do token fica no MS-Identidade, porta 8092, rota `POST /api/auth/login`, que esta classe deixa passar.

```java
// JwtGatewayFilter.java — filter
String caminho = exchange.getRequest().getURI().getPath();
if (caminho.startsWith("/api/auth/") || caminho.startsWith("/fallback/")) {
	return chain.filter(exchange);
}
if (!caminho.startsWith("/api/")) {
	return chain.filter(exchange);
}
String autorizacao = exchange.getRequest().getHeaders().getFirst("Authorization");
String token = autorizacao != null && autorizacao.startsWith("Bearer ") ? autorizacao.substring(7) : null;
if (jwtServico.validar(token) == null) {
	byte[] corpo = "{\"codigo\":\"TOKEN_INVALIDO\",\"mensagem\":\"Token ausente ou inválido.\"}"
			.getBytes(StandardCharsets.UTF_8);
	exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
	exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
	DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(corpo);
	return exchange.getResponse().writeWith(Mono.just(buffer));
}
return chain.filter(exchange);
```

**Fundamento.** Gateway como borda concentra roteamento e política. Autenticar na borda evita que cada serviço vire uma porta pública com a própria regra. O token assinado separa emitir credencial e apresentar credencial: a senha não viaja de novo, e um serviço interno não precisa guardar sessão da regulação.

---

## 11. O alerta fora da transação do leito

**Problema.** Despacho, expiração e falta de leito precisam ficar registrados para a equipe e para a auditoria. Esse registro é efeito do fluxo, não ocupação de recurso. Colocá-lo na mesma transação do leito acoplaria o canal de alerta ao banco que reserva vaga. Enviar SMS de verdade está fora do recorte.

**Solução.** O MS-Notificações, com MongoDB, grava as coleções `notificacoes` e `auditoria`. Os tipos são `DESPACHO`, `EXPIRACAO` e `FALTA_LEITO`. O texto basta. Quem publica é o MS-Ocorrências ou o MS-Leitos, na fila `notificacao.registrar`, também depois do commit. O índice único em ocorrência, tipo e instante segura a repetição.

`DESPACHO` sai de `OcorrenciaSagaService.publicarDestino`. `FALTA_LEITO` sai de `semLeito` quando `tentativa > 1`. `EXPIRACAO` sai de `LeitoService.expirarReservas`. A gravação e o índice estão na seção 6: `NotificacaoService.registrar` e `NotificacaoDocumento`.

```java
// OcorrenciaSagaService.java — publicarDestino
publicador.depoisDoCommit(() -> {
	publicador.saga(Filas.ATUALIZAR_AMBULANCIA,
			new AtualizarAmbulancia(ocorrencia.getId(), ocorrencia.getAmbulanciaId(), "DESPACHADA"));
	publicador.notificacao(new RegistrarNotificacao(ocorrencia.getId(), "DESPACHO", texto, quando));
});
```

```java
// OcorrenciaSagaService.java — semLeito, tentativa maior que 1
publicador.depoisDoCommit(() -> publicador.notificacao(
		new RegistrarNotificacao(ocorrencia.getId(), "FALTA_LEITO", texto, quando)));
```

**Fundamento.** O alerta é outro modelo de dados: documento de trilha, append-only, com chave de idempotência. Mongo nesse serviço separa essa trilha do modelo relacional que guarda status e vaga. O canal mockado continua sendo um subordinado: a saga não espera o SMS para concluir o passo local.

---

## 12. O controle precisa ser visível

**Problema.** Uma saga que terminou deixa Ready, Unacked e Total em 0. A fila em `running` só diz que ela existe. Sem uma forma de olhar o meio do caminho, a banca vê o estado final e não vê a coordenação.

**Solução.** Duas provas, no cartão Filas, sem fila nova e sem processo novo.

- A rajada percorre a saga de verdade. A prova é a coluna de taxas (incoming, deliver/get, ack) enquanto o consumidor acompanha. Ready em 0 no fim é saga drenada. Uma taxa que ainda aparece depois do HTTP 200 é a média da janela, não fila enchendo.
- O acúmulo pausa o listener e mantém o processo no ar, publica uma quantidade conhecida nas oito filas e só então permite a foto: Ready parado, deliver/get em 0. Esvaziar e religar purga antes de religar, para a mensagem de inspeção não entrar na saga. A varredura de leitos pausa junto; senão, a cada 15 segundos ela publicaria retentativa e a foto deixaria de bater com a quantidade.

Parar o processo da porta 8095 para “encher a fila” abriria o circuito e misturaria dois controles. A pausa do listener separa os dois.

A rajada está em `ms-ocorrencias/src/main/java/com/unesp/omnileito/ocorrencias/RajadaService.java`: para cada unidade, `saga.abrir` e `saga.despachar`. A pausa compartilhada está em `omnileito-comum/src/main/java/com/unesp/omnileito/comum/PausaConsumidores.java`. No MS-Leitos, `ms-leitos/src/main/java/com/unesp/omnileito/leitos/PausaFilas.java` segura o listener de `saga.reservar-leito` e a varredura (`VarreduraLeitos`). A purga síncrona está em `ms-ocorrencias/src/main/java/com/unesp/omnileito/ocorrencias/EsvaziadorFilas.java`, método `esvaziar`, com `admin.purgeQueue(fila)`. As rotas do cartão estão em `FilasDemonstracaoController`.

```java
// RajadaService.java — disparar
OcorrenciaResponse aberta = saga.abrir(new AberturaRequest(
		new AberturaRequest.PacienteRequest("Carga Fila " + indice),
		"Rua da Fila", "VERDE", "ENFERMARIA"));
saga.despachar(aberta.id());
```

```java
// PausaConsumidores.java
public static void pausar(RabbitListenerEndpointRegistry registry, List<String> ids) {
	for (String id : ids) {
		MessageListenerContainer container = exigir(registry, id);
		if (container.isRunning()) {
			container.stop();
		}
	}
}
```

```java
// PausaFilas.java — MS-Leitos
public void pausar() {
	varredura.pausar();
	PausaConsumidores.pausar(registry, IDS);
}
```

```java
// LeitoService.java — a varredura pausada não publica retentativa
if (varredura.pausada()) {
	return;
}
```

```java
// EsvaziadorFilas.java
public void esvaziar() {
	for (String fila : FILAS) {
		admin.purgeQueue(fila);
	}
}
```

**Fundamento.** O objetivo do estudo é tornar a inconsistência observável. O estado final sozinho não distingue “não houve mensagem” de “a mensagem já foi consumida”. Profundidade da fila e taxa de entrega são leituras diferentes do mesmo canal. O ensaio usa as duas.

---

## 13. Frase por problema, para falar em voz alta

- **Felipe, coordenação.** “Não há commit dos três. Cada serviço commita o seu, e o MS-Ocorrências conduz o próximo passo. A saga existe porque o hospital demora; o commit de duas fases seguraria a vaga esse tempo todo.”
- **Felipe, exclusão.** “Quem trava a ambulância é a frota. Quem trava o leito é o leito. O segundo despacho devolve o mesmo par.”
- **Felipe, compensação.** “Antes de a equipe sair, a falha devolve a ambulância à base. Depois que ela saiu, recusa e prazo devolvem só o leito. A equipe fica em primeiros socorros, à espera de outro destino.”
- **Felipe, mensagem.** “A fila pode entregar de novo. A publicação espera o commit, e o consumidor reconhece a ocorrência.”
- **Sérgio, prazo.** “Silêncio não manda evento. O job no dono do leito aplica, depois de dois minutos, o mesmo desfecho da recusa. Reserva aceita não expira.”
- **Sérgio, cache.** “A lista sai do Redis para a consulta ser rápida. A escrita apaga a chave. Se o Redis cai, a leitura segue no MySQL. O cache não é a fonte da vaga.”
- **Sérgio, alerta.** “Despacho, prazo e falta de leito ficam no Mongo, fora da transação do leito. O texto registra. Não há SMS.”
- **Igor, borda.** “A regulação fala com uma porta. O token vale para o resto. A senha não volta em cada chamada.”
- **Igor, circuito.** “Só a chamada de leitos tem prazo de dois segundos, retry de leitura e circuito. Leitos parado responde 503. Abrir ocorrência continua em 201.”
- **Sérgio, ensaio.** “Ready zero é saga que já passou. A rajada se vê na taxa. A fila cheia se vê com o listener pausado e o processo no ar.”

---

## 14. O que o ensaio mostra de cada controle

Ordem do slide, com a carga limpa no início.

| Passo no painel | Controle que fica visível |
| --- | --- |
| Estado inicial, cinco HTTP 204, Ready 0 | A semente e as filas voltam sem derrubar processo |
| Aceite, UTI, despacho até `DESPACHADA`, ambulância 1, L-UTI-01 | Saga feliz: ocorrência, frota, leito e um `DESPACHO` |
| Segundo despacho, HTTP 200, mesmos ids | Idempotência; XYZ-5678 continua livre |
| Pausar e acumular 40, Ready parado por mais de 15 s | A fila existe sem consumidor; a varredura também está segurada |
| Esvaziar e religar, Ready 0, ambulâncias ainda `DISPONIVEL` | Purga antes de religar; inspeção não vira saga |
| Rajada 20, taxas sobem, Ready termina em 0 | At-least-once com consumidor no ritmo; frota esgotada vira `FALHA_DESPACHO` |
| Estado inicial de novo | A semente volta para o próximo cenário |
| Se sobrar um minuto: parar só a 8095 | Circuito em 503 na consulta, abertura em 201 |

O marcador de dois minutos não entra neste bloco. Aceite confirmado não gera `EXPIRACAO`. Prazo, outro leito e `FALTA_LEITO` ficam na explicação do Sérgio e no manual da tela, se a banca pedir o ramo longo.
