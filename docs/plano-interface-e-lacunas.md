# Plano — lacunas dos três membros e interface de demonstração

- Disciplina: Arquitetura de Software: Teoria e Prática 2026
- Grupo 3: Felipe Melchior de Britto; Igor Benedito Costa Gonçalves; Sérgio Leandro Alves da Silva
- Data: 16/10/2026
- Base: `docs/documento-de-requisitos.md` 0.3 e `docs/analise-situacao-e-proximos-passos.md` desta noite
- Estado: recomendações da seção 10 aceitas em 16/10/2026 e implementadas. A tela fica em `http://localhost:8085/`.

As seções 2 a 8 descrevem o caminho recomendado. A seção 10 lista as alternativas de cada decisão. Se a validação escolher outra opção, o plano das seções 2 a 8 é ajustado antes de codar.

A interface e os poucos comandos novos são um cliente e um apoio de demonstração. A saga, o JWT, o circuito, o cache e o job permanecem como estão.

---

## 1. O que já está feito e este plano não reescreve

Login, abertura, gateway, filas, alocação, reserva, silêncio, expiração, cache na primeira leitura e as notificações `DESPACHO` e `EXPIRACAO` já existem e já foram vistos no ar.

Também já existem no código, com teste de unidade, e este plano não troca a regra:

| Caminho | Onde está | O que a apresentação ainda não mostrou |
| --- | --- | --- |
| Segundo despacho em `200` | RF07, `OcorrenciaSagaServiceTest` | Seção 7.3 do roteiro |
| Primeira busca sem leito, ambulância de volta a `DISPONIVEL` | RF11 | Seção 8 |
| Frota esgotada | RF09 | Seção 9 |
| Aceite com `confirmado` verdadeiro | RF14 | Seção 7 |
| Recusa, ambulância `AGUARDANDO_LEITO`, mesmo leito de novo | RF15 | Seção 10 |
| Reserva confirmada fora do job | Critério 6 | Esperar mais de 2 minutos depois do aceite |
| Cache apagado na reserva | RF19, `cache.apagar` no MS-Leitos e no MS-Frota | Reler a lista depois do despacho |
| Circuito só em `GET /api/leitos/**` | RF05, `RotasGateway` | `503` com a porta 8095 parada |
| Outro leito `LIVRE` preferido ao já recusado | RF22, `LeitoServiceTest` | A carga tem um `LIVRE` por especialidade |
| Busca seguinte sem nenhum `LIVRE`: `FALTA_LEITO` | RF23, `semLeito` quando `tentativa > 1` | A recusa devolve o leito a `LIVRE` e a saga reserva esse de novo na hora |

O roteiro em curl continua válido. A tela chama os mesmos endereços.

---

## 2. O que entra de código novo

São quatro acréscimos. Os três primeiros existem para a apresentação alcançar um ramo que a carga atual não alcança, ou para não reiniciar o processo no meio da banca. O quarto é a tela.

| # | Acréscimo | Dono | Módulo |
| --- | --- | --- | --- |
| 1 | Trocar `ACEITE`, `RECUSA`, `SILENCIO` ou aleatório com o MS-Leitos no ar | Felipe | `ms-leitos` |
| 2 | Criar e remover um leito UTI `LIVRE` extra, para mostrar a preferência por outro leito | Felipe cria o leito. Sérgio Leandro confere cache e a escolha | `ms-leitos` |
| 3 | Uma trava de uso único: a próxima reserva com `tentativa > 1` responde `SEM_LEITO_COMPATIVEL` | Sérgio Leandro, no ramo que o MS-Ocorrencias já trata | `ms-leitos` arma. `ms-ocorrencias` já grava `FALTA_LEITO` |
| 4 | Páginas estáticas servidas pelo gateway, na porta 8085 | Igor | `api-gateway` |

Nada disso muda o comportamento com a trava desligada e sem leito extra. Sem argumento e sem chamada de demonstração, o hospital continua aleatório, como no requisito RF13.

---

## 3. Igor — gateway e tela

### Já cobre

RF01 a RF04 estão no ar. O filtro do gateway deixa passar `/api/auth/**` e qualquer caminho que não comece com `/api/`. O restante exige `Bearer`. O circuito e o retry estão só na rota `/api/leitos/**`, e o retry só em `GET`.

### O que a implementação adiciona

A tela mora em `api-gateway/src/main/resources/static/`. HTML, CSS e JavaScript, sem npm e sem processo novo. O navegador abre `http://localhost:8085/`. As chamadas saem da mesma origem para `/api/...`, com o token guardado em `sessionStorage`.

O gateway não mata processo. No cartão do circuito, a tela explica: parar só a porta 8095 com Ctrl+C, clicar em consultar UTI, ler o `503` e `SERVICO_LEITOS_INDISPONIVEL`, abrir uma ocorrência no mesmo momento e ver `201`, subir o MS-Leitos de novo.

### Fora do Igor

Swagger agregado no gateway, mapa, SMS e um front separado em outra porta.

---

## 4. Felipe — resultado do hospital e os caminhos da saga

### Já cobre

Despacho, alocação exclusiva, reserva, publicação depois do commit, idempotência e os três resultados do hospital. O resultado fixo hoje é `omnileito.reserva.resultado`, lido uma vez na subida de `SorteioUnidade`. Trocar o valor exige derrubar a porta 8095.

### O que a implementação adiciona

`SorteioUnidade` passa a guardar o resultado numa referência que a demonstração pode substituir. O valor inicial continua sendo a propriedade. Vazio continua sendo aleatório.

```
PUT /api/leitos/demonstracao/resultado
Authorization: Bearer <token>
{"resultado":"ACEITE"}
```

`resultado` aceita `ACEITE`, `RECUSA`, `SILENCIO` ou vazio. Vazio volta ao sorteio. A resposta devolve o modo atual. A rota entra em `/api/leitos/**`, então usa o gateway e o JWT. Com a porta 8095 parada, esta chamada também cai no circuito.

A tela usa esse comando no lugar de reiniciar o processo entre as seções 7, 10 e 11. O argumento de linha continua valendo como modo inicial.

Os cliques da tela, sem regra nova, cobrem o que falta mostrar de Felipe:

| Cartão | Resultado esperado |
| --- | --- |
| Aceite | `DESPACHADA`, leito `RESERVADO`, `confirmado` verdadeiro, notificação `DESPACHO` |
| Segundo despacho | HTTP `200`, mesma ambulância, mesmo leito |
| Outra UTI com a vaga já reservada | `FALHA_DESPACHO`, ambulância de volta a `DISPONIVEL` |
| Enfermaria e depois uma ocorrência sem ambulância | a segunda fica `FALHA_DESPACHO` e o leito de emergência segue `LIVRE` |
| Recusa | `AGUARDANDO_LEITO`, `EM_DESPACHO`, o mesmo leito pedido de novo |

A recusa continua em ciclo enquanto o modo for `RECUSA`. A tela avisa para voltar o modo a `ACEITE` ou vazio depois de uma volta, no lugar de deixar o ciclo girando.

---

## 5. Sérgio Leandro — cache visível, outro leito e falta de leito

### Já cobre

Consulta com Redis, job de 2 minutos lendo o MySQL, expiração com `confirmado` falso, repetição do único leito `LIVRE` e gravação de `DESPACHO` e `EXPIRACAO` no Mongo.

### Cache e prazo, sem endpoint novo

A tela lista ambulâncias disponíveis e leitos `LIVRE` por especialidade. Depois do aceite da UTI, a lista de UTI fica vazia: essa é a invalidação vista pelo regulador. A chave `leitos:disponiveis:UTI` no Redis continua sendo conferida pelo comando do roteiro, seção 6.

No cartão do aceite, um marcador de 2 minutos pede nova consulta da ocorrência e das notificações. O esperado é `confirmado` verdadeiro e nenhuma `EXPIRACAO` dessa reserva. O campo `confirmado` não volta no JSON público de leitos disponíveis. A prova do prazo segue no MySQL, como na seção 11 do roteiro. A tela mostra a ausência de `EXPIRACAO` na lista de notificações.

### Outro leito — RF22

A carga de estudo permanece com um `LIVRE` e um `OCUPADO` por especialidade. Para o outro ramo, a demonstração cria um leito a mais e apaga esse leito ao final.

```
POST /api/leitos/demonstracao/leito-extra
{"tipoLeito":"UTI","codigo":"L-UTI-03","unidadeNome":"Hospital Central"}

DELETE /api/leitos/demonstracao/leito-extra/L-UTI-03
```

`POST` só cria se o código não existir e a unidade já existir. O leito nasce `LIVRE`. Invalida `leitos:disponiveis:UTI`. `DELETE` só remove se o leito estiver `LIVRE` e o código for o extra. Recusa a remoção se estiver `RESERVADO`.

Com `L-UTI-03` livre e o modo `RECUSA`, a primeira reserva pega `L-UTI-01`. Na recusa, a busca seguinte prefere `L-UTI-03`, que esta ocorrência ainda não recusou. A tela mostra os dois códigos em sequência. Ao terminar, o `DELETE` devolve a carga ao formato do roteiro.

### Falta de leito — RF23

`escolher` só devolve vazio quando não existe leito `LIVRE` da especialidade, inclusive o que acabou de ser recusado: a recusa grava `LIVRE` antes de publicar a próxima reserva. Com a carga atual, a tentativa 2 encontra esse leito e não entra em `semLeito`. Por isso o roteiro não tem esse passo.

A implementação não muda essa preferência. Acrescenta uma trava consumida uma vez:

```
POST /api/leitos/demonstracao/proxima-busca-sem-vaga
{"tipoLeito":"UTI"}
```

A trava vale só para a próxima `reservar` daquele tipo com `tentativa > 1`. A tentativa 1 reserva normalmente. Na tentativa 2 o MS-Leitos publica `SEM_LEITO_COMPATIVEL` pelo mesmo ramo que o teste de unidade já usa, apaga a trava e invalida o cache. O MS-Ocorrencias, que já trata `tentativa > 1`, mantém a ambulância, deixa a ocorrência `EM_DESPACHO` e grava `FALTA_LEITO` com o texto `Não há leito livre da especialidade UTI.`

A ordem na tela: armar a trava, modo `RECUSA`, abrir e despachar uma UTI, esperar a notificação `FALTA_LEITO`. Sem a trava, a recusa segue o ciclo atual.

A trava começa desligada a cada subida do processo. Não entra na carga do Flyway.

---

## 6. A tela, cartão por cartão

Uma página. Cada cartão nomeia o membro e o requisito, para a banca acompanhar.

| Cartão | Quem opera na banca | Ação | Leitura na tela |
| --- | --- | --- | --- |
| Entrar | Igor | usuário e senha | token aceito ou `CREDENCIAL_INVALIDA` |
| Abrir ocorrência | Igor | nome, local, prioridade, tipo de leito | `201`, status `ABERTA`, o UUID |
| Ocupação | Sérgio Leandro | consultar ambulâncias e um tipo de leito | listas; vazio depois da reserva |
| Modo do hospital | Felipe | ACEITE, RECUSA, SILÊNCIO ou aleatório | modo gravado no MS-Leitos |
| Despachar | Felipe | um clique no UUID aberto | `202`, depois o status estável, com consulta repetida a cada 2 segundos por até 15 segundos |
| Segundo despacho | Felipe | repetir o despacho | `200` e os mesmos ids |
| Notificações | Sérgio Leandro | consultar pelo UUID | tipo, mensagem e horário |
| Prazo do aceite | Sérgio Leandro | marcador de 2 minutos | nenhuma `EXPIRACAO` nova |
| Outro leito | Felipe e Sérgio Leandro | criar `L-UTI-03`, recusar, apagar o extra | a segunda reserva cita `L-UTI-03` |
| Falta de leito | Sérgio Leandro | armar a trava e despachar em RECUSA | `FALTA_LEITO`, ambulância fora de `DISPONIVEL` |
| Circuito | Igor | consultar UTI com o MS-Leitos parado | `503`, `SERVICO_LEITOS_INDISPONIVEL`; abertura ao lado em `201` |

A consulta repetida substitui o `consultar <id>` do roteiro. O primeiro `202` só diz que a saga começou.

Estados `EM_TRANSPORTE`, `CONCLUIDA`, `CANCELADA`, `EM_ATENDIMENTO` e `INDISPONIVEL` não ganham botão.

---

## 7. Ordem de implementação, depois da validação

1. **Felipe.** Referência mutável em `SorteioUnidade` e `PUT /api/leitos/demonstracao/resultado`. Teste de unidade: vazio sorteia um dos três; valor fixo devolve esse valor; a troca em execução vale para a reserva seguinte.
2. **Felipe.** `POST` e `DELETE` do leito extra. Teste: o extra `LIVRE` é preferido a um leito já recusado; `DELETE` não apaga leito da carga nem leito `RESERVADO`.
3. **Sérgio Leandro.** Trava de uma busca. Teste no `LeitoService`: tentativa 1 reserva; tentativa 2 com a trava publica `SEM_LEITO_COMPATIVEL` e a trava some. O teste atual de `semLeito` no MS-Ocorrencias permanece a prova de `FALTA_LEITO`.
4. **Igor.** Arquivos estáticos no gateway e os cartões, na ordem da tabela da seção 6. A página em `/` não pede JWT. `/api/**` continua pedindo, exceto o login.
5. **Os três.** Uma passagem no navegador, com carga limpa, na ordem da seção 8. Só então o roteiro ganha uma seção curta apontando para `http://localhost:8085/`.

Cada passo sobe o processo do módulo alterado. `omnileito-comum` não muda; não há `install` novo por causa desta leva, a menos que um DTO compartilhado precise nascer lá. Os corpos novos ficam nos próprios módulos.

---

## 8. Como validar a implementação, quando ela existir

Carga limpa da seção 2 do roteiro. Gateway em `http://localhost:8085/`.

1. Entrar com `regulador` / `regulador` e abrir uma UTI.
2. Modo `ACEITE`. Despachar. Ver `DESPACHADA`, lista de UTI vazia, notificação `DESPACHO`. Esperar o marcador. A lista de notificações não ganha `EXPIRACAO`.
3. Segundo despacho em `200`.
4. Abrir outra UTI. Despachar. Ver `FALHA_DESPACHO`.
5. Completar a seção 9 pela tela: enfermaria despachada e emergência sem ambulância.
6. Voltar à carga. Criar `L-UTI-03`. Modo `RECUSA`. Despachar uma UTI. A reserva seguinte usa `L-UTI-03`. Apagar o extra quando estiver `LIVRE`.
7. Voltar à carga. Armar a trava de UTI. Modo `RECUSA`. Despachar. Ver `FALTA_LEITO` e a ambulância fora de `DISPONIVEL`.
8. Com a tela aberta, parar só a porta 8095. A consulta de UTI mostra `503`. A abertura mostra `201`. Subir o MS-Leitos. A consulta volta a `200`.

O painel do RabbitMQ e o `mongosh` continuam sendo a prova das filas e do documento gravado. A tela não substitui os dois.

---

## 9. O que este plano deixa de fora

- Reescrever a saga, o job ou o circuito.
- Alterar a carga Flyway para dois leitos `LIVRE` permanentes.
- Servir a tela em outra porta ou noutro repositório.
- Framework de front, WebSocket ou agregação de Swagger.
- Botão que encerra processo Java ou container.
- `EM_TRANSPORTE`, `CONCLUIDA`, `CANCELADA`, `EM_ATENDIMENTO`, `INDISPONIVEL`, SMS, mapa, protocolo, relatório e faturamento.

---

## 10. Decisões, alternativas e recomendação

Cada item fecha um ponto que muda o desenho. A recomendação é a que as seções 2 a 8 já detalham. A implementação só começa depois da escolha explícita destes cinco itens.

### 10.1 Onde a tela vive

O regulador, no requisito, entra por uma porta só. O filtro do gateway já deixa passar caminho que não começa com `/api/` e exige JWT no restante. Não existe front no repositório.

| Opção | O que é | Consequência |
| --- | --- | --- |
| A | HTML, CSS e JavaScript estáticos em `api-gateway/src/main/resources/static/`, abertos em `http://localhost:8085/` | Mesma origem das APIs. Sem CORS, sem npm, sem sétimo processo. Dono: Igor |
| B | Aplicação à parte (Vite ou similar) em outra porta, chamando `http://localhost:8085` | Exige Node na banca, build e CORS. A entrada do regulador deixa de ser uma URL só |
| C | Páginas estáticas dentro de outro serviço, por exemplo o MS-Ocorrencias na 8093 | O navegador fala com um serviço por dentro do gateway. O RF04 deixa de valer para a tela |
| D | Permanecer no Swagger de cada porta | Já existe. Não reúne login, despacho, ocupação, prazo e notificação numa passagem só |

**Recomendada: A.** A banca abre um endereço, o mesmo das chamadas do roteiro. A opção D continua disponível para quem quiser o contrato de um serviço.

### 10.2 Como fixar ACEITE, RECUSA ou SILÊNCIO

`SorteioUnidade` lê `omnileito.reserva.resultado` na subida. Vazio sorteia. Trocar o valor hoje pede Ctrl+C na porta 8095 e uma nova subida. Ctrl+Z não encerra o processo.

| Opção | O que é | Consequência |
| --- | --- | --- |
| A | `PUT /api/leitos/demonstracao/resultado`, com JWT. A propriedade da linha de comando continua sendo o valor inicial. Vazio no corpo volta ao sorteio | A tela muda o modo com o processo no ar. O argumento do roteiro segue valendo na subida |
| B | A tela só orienta o comando Maven e espera o processo reiniciar | Zero código novo em leitos. Entre as seções 7, 10 e 11 a banca derruba a 8095. É o caminho que já travou porta nesta máquina |
| C | Cada mensagem `ReservarLeito` carrega o resultado desejado | Muda o contrato da fila e o MS-Ocorrencias passa a decidir o hospital. O hospital deixa de ser do MS-Leitos |

**Recomendada: A.** O hospital continua no MS-Leitos. A banca não reinicia o processo para cada cartão. Com a porta 8095 parada, o `PUT` cai no mesmo circuito das outras chamadas de leito.

### 10.3 Como mostrar a preferência por outro leito

`escolher` pede primeiro um `LIVRE` que esta ocorrência ainda não recusou. A carga tem um `LIVRE` por especialidade, então a recusa só consegue repetir o mesmo leito. O teste `prefereOutroLeitoLivreQuandoHaRecusa` cobre o outro ramo.

| Opção | O que é | Consequência |
| --- | --- | --- |
| A | `POST` cria `L-UTI-03` `LIVRE` no Hospital Central. `DELETE` só remove se esse código estiver `LIVRE`. A carga Flyway não muda | A recusa de `L-UTI-01` passa a reservar `L-UTI-03`. Apagar o extra devolve o roteiro ao formato de um `LIVRE` por especialidade |
| B | Mudar o Flyway: duas UTI `LIVRE` para sempre | As seções 8, 10 e 11 deixam de valer. Sem vaga na primeira busca e a repetição do mesmo leito dependem de um único `LIVRE` |
| C | No meio da demonstração, passar `L-UTI-02` de `OCUPADO` para `LIVRE` e restaurar depois | Não cria linha. O leito ocupado da carga some enquanto o cenário durar. Um restore esquecido estraga o caso de estudo |
| D | Não mostrar este ramo no ar. Fica o teste de unidade | Menos código. A banca não vê a preferência |

**Recomendada: A.** O leito ocupado da carga permanece `OCUPADO`. O extra tem código próprio e o `DELETE` recusa apagar se ainda estiver `RESERVADO`. A opção D permanece aceitável se a banca dispensar este cartão.

### 10.4 Como chegar a FALTA_LEITO no ar

`FALTA_LEITO` só nasce quando `ReservarLeito` chega com `tentativa > 1` e `escolher` não acha nenhum `LIVRE` da especialidade. Na recusa, o leito volta a `LIVRE` na mesma transação, e a mensagem seguinte reserva esse leito de novo. A tentativa 1 sem vaga é outro desfecho: `FALHA_DESPACHO` e ambulância `DISPONIVEL`.

| Opção | O que é | Consequência |
| --- | --- | --- |
| A | `POST /api/leitos/demonstracao/proxima-busca-sem-vaga`. A trava vale uma vez, só com `tentativa > 1`, e publica `SEM_LEITO_COMPATIVEL` pelo ramo que já existe. Desligada na subida | O despacho em `RECUSA` reserva na tentativa 1 e, na seguinte, grava `FALTA_LEITO`. Sem a trava, silêncio e recusa seguem como hoje |
| B | Deixar a especialidade sem `LIVRE` na carga e despachar | Cai no RF11, tentativa 1: `FALHA_DESPACHO`. Não produz `FALTA_LEITO` |
| C | Entre a recusa e a nova reserva, ocupar o leito que acabou de voltar a `LIVRE` | A folga é a de uma mensagem no RabbitMQ. A banca não consegue vencer essa corrida de forma repetível |
| D | A recusa deixa de pedir o leito de novo na hora, para alguém ocupar os livres antes | Muda o ciclo já conferido no silêncio e na recusa |
| E | Não mostrar no ar. Fica o teste `buscaSeguinteSemLeitoNaoDevolveAmbulancia` | Menos código. O critério 8 da aceitação não aparece na tela |

**Recomendada: A.** A regra de `escolher` permanece. A trava só existe para a banca atravessar o ramo que a carga não atravessa. A opção E é a alternativa se esse cartão sair do escopo.

### 10.5 Quando a implementação começa

| Opção | O que é | Consequência |
| --- | --- | --- |
| A | Codar só depois da resposta que escolher A, B, C, D ou E em cada item acima | O que for escrito corresponde ao que o grupo aceitou |
| B | Codar agora o caminho recomendado e ajustar se a resposta divergir | Adianta a tela e arrisca refazer módulo |
| C | Implementar só os itens já aprovados e deixar o resto no papel | Serve se a validação for parcial. A ordem da seção 7 é refeita com o que entrar |

**Recomendada: A.** Este arquivo continua como proposta até essa resposta. Nenhum módulo será alterado por causa deste plano antes disso.
