# Manual da tela de regulação — OmniLeito

- Data: 16/10/2026
- Página: `http://localhost:8085/`
- Complementa `docs/roteiro-de-testes.md`. O curl daquele roteiro continua válido: a tela chama as mesmas APIs.

A tela é a interface do regulador. O navegador fala só com o API Gateway. Cada cartão mostra a API, quem gera a resposta ou a mensagem e quem consome.

## 1. O que precisa estar no ar

Os quatro containers do `docker compose` e os seis processos:

| Processo | Porta |
| --- | --- |
| API Gateway | 8085 |
| MS-Identidade | 8092 |
| MS-Ocorrencias | 8093 |
| MS-Frota | 8094 |
| MS-Leitos | 8095 |
| MS-Notificacoes | 8096 |

A carga inicial tem duas ambulâncias `DISPONIVEL` (`ABC-1234` e `XYZ-5678`) e, em cada especialidade, um leito `LIVRE` e um `OCUPADO`. Se uma passagem anterior gastou UTI ou ambulância, volte à carga pela seção 2 do roteiro antes de repetir o cenário.

Abra `http://localhost:8085/` e atualize a página se o navegador mostrar a versão antiga.

## 2. Como a página funciona

No alto, **OmniLeito / Regulação**. À direita, o estado da sessão.

Sem login, o texto é `Sem token. Entre para chamar a regulação.` Depois do `200`, passa a `Token guardado nesta aba.` O token fica em `sessionStorage` e vale só nessa aba. Fechar a aba pede um novo login. As chamadas seguintes enviam `Authorization: Bearer`.

Cada cartão tem:

- o código do requisito, no topo
- o título da ação
- o resumo **API / Gera / Consome**
- os campos e botões
- a área de resposta, em HTTP e JSON

`HTTP 0` com `SEM_CONEXAO` significa que o gateway na porta 8085 não respondeu. `401` e `TOKEN_INVALIDO` significam que o token falta ou venceu: entre de novo.

O campo **Ocorrência**, no cartão Despachar, recebe o UUID da última abertura. Segundo despacho, notificações e o prazo usam esse mesmo valor. Troque o texto do campo se quiser consultar outra ocorrência.

## 3. Ordem de uma passagem

1. Entrar.
2. Consultar a ocupação, para ver a carga antes de gastar recurso.
3. Escolher o modo do hospital.
4. Abrir a ocorrência.
5. Despachar e ler o acompanhamento.
6. Consultar notificações.
7. Seguir o cenário: segundo despacho, prazo, outro leito, falta de leito ou circuito.

O modo do hospital vale para a próxima reserva e permanece até outro clique ou até o MS-Leitos subir de novo. Na subida, o argumento `--omnileito.reserva.resultado` é o valor inicial. Sem argumento, o modo começa aleatório.

## 4. Cartões

### Entrar — RF01

Usuário e senha da carga: `regulador` / `regulador`.

| Peça | Papel |
| --- | --- |
| API | `POST /api/auth/login` |
| Gera | MS-Identidade devolve o JWT. Não há fila |
| Consome | API Gateway encaminha |

Esperado: HTTP `200`, `token` e `tipo` `Bearer`. Usuário ou senha errados: HTTP `401`, `CREDENCIAL_INVALIDA`.

### Abrir ocorrência — RF02 a RF04

Informe paciente, local, prioridade (`VERMELHA`, `AMARELA` ou `VERDE`) e tipo de leito (`UTI`, `EMERGENCIA` ou `ENFERMARIA`). O despacho usa o tipo gravado aqui e não pede outro.

| Peça | Papel |
| --- | --- |
| API | `POST /api/ocorrencias` |
| Gera | MS-Ocorrencias grava `ABERTA`. A abertura não publica mensagem |
| Consome | API Gateway encaminha |

Esperado: HTTP `201`, `status` `ABERTA`, `ambulanciaId` e `leitoId` nulos, e um UUID em `id`. Esse UUID entra sozinho no campo Ocorrência.

A abertura continua respondendo `201` com o MS-Leitos parado. Ela não passa pelo circuito.

### Ocupação — RF17 e RF18

**Ambulâncias disponíveis** lista só as `DISPONIVEL`. **Leitos livres** usa o tipo escolhido ao lado e lista só os `LIVRE` dessa especialidade. Leito `OCUPADO` e leito de outro tipo não aparecem.

| Peça | Papel |
| --- | --- |
| API | `GET /api/ambulancias/disponiveis` e `GET /api/leitos/disponiveis` |
| Gera | MS-Frota e MS-Leitos. Cada um lê o próprio Redis e, sem chave, o MySQL |
| Consome | API Gateway. A consulta de leitos passa pelo circuito |

Na carga limpa, ambulâncias devolve `ABC-1234` e `XYZ-5678`. UTI devolve `L-UTI-01`. Lista `[]` com HTTP `200` é especialidade sem vaga livre, não é falha do circuito. O `503` só aparece com o MS-Leitos parado.

A chave do Redis nasce nessa primeira leitura. O nome dela (`frota:disponiveis` ou `leitos:disponiveis:UTI`) se confere no Redis, como na seção 6 do roteiro. A tela mostra a lista, não a chave.

### Modo do hospital — RF13

Quatro botões: **Aceite**, **Recusa**, **Silêncio** e **Aleatório**.

| Peça | Papel |
| --- | --- |
| API | `GET` e `PUT /api/leitos/demonstracao/resultado` |
| Gera | MS-Leitos guarda o modo e usa na próxima reserva |
| Consome | API Gateway encaminha. Sem fila |

O texto abaixo dos botões passa a `Modo atual: ACEITE`, `RECUSA`, `SILENCIO` ou `aleatório`. Trocar o modo não reinicia a porta 8095. Com o MS-Leitos parado, o `PUT` também cai no circuito.

O modo fica valendo para toda reserva seguinte, inclusive a busca automática depois de recusa ou prazo. Volte a **Aleatório**, ou ao modo do próximo cenário, quando terminar.

### Despachar — RF06 a RF12

Usa o UUID do campo Ocorrência. **Despachar** envia o pedido. **Consultar** lê o estado uma vez, sem iniciar saga.

| Peça | Papel |
| --- | --- |
| API | `POST /api/ocorrencias/{id}/despachos` e `GET /api/ocorrencias/{id}` |
| Gera | MS-Ocorrencias publica `saga.alocar-ambulancia`. MS-Frota responde em `saga.ambulancia-alocada`. MS-Ocorrencias publica `saga.reservar-leito`. MS-Leitos responde em `saga.leito-reservado` |
| Consome | MS-Frota, MS-Leitos e MS-Ocorrencias, nessa ordem. No sucesso, MS-Ocorrencias publica `saga.atualizar-ambulancia` e `notificacao.registrar`, consumidas por MS-Frota e MS-Notificacoes |

O primeiro bloco é o HTTP do `POST`. `202` só diz que a saga começou. Em seguida a tela consulta a ocorrência a cada 2 segundos, por até 15 segundos, no bloco **Acompanhamento**.

No sucesso, com leito livre e modo aceite:

| Campo | Valor |
| --- | --- |
| status | `DESPACHADA` |
| ambulanciaId | `1` na primeira ambulância da carga (`ABC-1234`) |
| leitoId | id de `L-UTI-01`, `L-EMG-01` ou `L-ENF-01`, conforme o tipo |
| unidadeNome | `Hospital Central` ou `UPA Norte` |

`EM_DESPACHO` no meio do acompanhamento é a saga ainda andando. `FALHA_DESPACHO` com ambulância e leito nulos é o fim da primeira busca sem vaga ou sem ambulância.

Campo vazio: a área de resposta pede para abrir uma ocorrência. UUID que não existe: HTTP `404`, `OCORRENCIA_NAO_ENCONTRADA`.

### Segundo despacho — RF07

Repete o `POST /despachos` do UUID que está no campo.

| Peça | Papel |
| --- | --- |
| API | `POST /api/ocorrencias/{id}/despachos` |
| Gera | MS-Ocorrencias responde `200` com a mesma ambulância e o mesmo leito |
| Consome | API Gateway. Esta repetição não publica fila nova |

Faça isso depois de um despacho que já chegou a `DESPACHADA` ou a `FALHA_DESPACHO`. No sucesso, a ambulância que ainda estava `DISPONIVEL` continua `DISPONIVEL`. Uma ocorrência `FALHA_DESPACHO` repetida responde `200` e permanece `FALHA_DESPACHO`: a saga não recomeça.

Ocorrência `CANCELADA` ou `CONCLUIDA` responde `409`, `OCORRENCIA_NAO_ABERTA`. Este fluxo não conduz a esses dois status.

### Notificações — RF24

| Peça | Papel |
| --- | --- |
| API | `GET /api/notificacoes?ocorrenciaId=` |
| Gera | MS-Ocorrencias ou MS-Leitos publicam `notificacao.registrar` |
| Consome | MS-Notificacoes grava no Mongo e devolve a lista. API Gateway encaminha a consulta |

Os documentos ficam no MongoDB `omnileito_notificacoes`, coleção `notificacoes`, não no MySQL. O horário `ocorridaEm` vem em UTC, com sufixo `Z`.

| Tipo | Quando aparece | Texto |
| --- | --- | --- |
| `DESPACHO` | Leito reservado e ocorrência `DESPACHADA` | Ambulância, código do leito e unidade |
| `EXPIRACAO` | Reserva sem confirmação venceu o prazo | `Reserva do leito ... expirou.` |
| `FALTA_LEITO` | Busca seguinte sem nenhum leito livre | `Não há leito livre da especialidade ...` |

Uma ocorrência que terminou em `FALHA_DESPACHO` na primeira busca não ganha `DESPACHO`.

### Prazo do aceite — critério 6

Use depois de um despacho em modo **Aceite** que ficou `DESPACHADA`.

| Peça | Papel |
| --- | --- |
| API | `GET /api/notificacoes?ocorrenciaId=` depois dos 2 minutos |
| Gera | O job do MS-Leitos lê o MySQL a cada 15 segundos. Reserva confirmada não publica `saga.leito-expirado` |
| Consome | MS-Notificacoes só recebe `EXPIRACAO` quando o prazo vence uma reserva sem confirmação |

**Iniciar marcador de 2 minutos** conta na tela. Ao chegar a zero, consulta as notificações do UUID do campo. O esperado no aceite é `Nenhuma EXPIRACAO nesta ocorrência.` A prova de que o leito ficou `confirmado` verdadeiro continua no MySQL, como na seção 7 do roteiro: a lista de leitos livres não traz esse campo.

No silêncio, o mesmo marcador deve encontrar `EXPIRACAO`, e a saga reserva o mesmo leito de novo se ele for o único livre.

### Outro leito — RF22

A carga tem um único livre por especialidade, então a recusa repetiria o mesmo código. Este cartão cria um livre a mais.

| Peça | Papel |
| --- | --- |
| API | `POST /api/leitos/demonstracao/leito-extra` e `DELETE /api/leitos/demonstracao/leito-extra/L-UTI-03` |
| Gera | MS-Leitos cria ou remove o leito e apaga o cache da especialidade |
| Consome | Na recusa, MS-Ocorrencias publica `saga.reservar-leito` e MS-Leitos prefere o outro leito livre |

**Criar L-UTI-03** espera HTTP `201`, status `LIVRE`, unidade `Hospital Central`. A ocupação de UTI passa a listar esse código junto com o outro livre. **Apagar L-UTI-03** espera HTTP `204` e só funciona com o leito `LIVRE`.

`409` `LEITO_DA_CARGA` protege `L-UTI-01`, `L-UTI-02` e os códigos de emergência e enfermaria. `409` `LEITO_NAO_LIVRE` aparece se `L-UTI-03` estiver reservado: espere a reserva acabar, ou encerre o ciclo, e apague quando voltar a livre. `409` `LEITO_JA_EXISTE` aparece no segundo criar sem apagar o primeiro.

### Falta de leito — RF23

Sem nenhum livre na primeira tentativa, o desfecho é `FALHA_DESPACHO` e a ambulância volta a `DISPONIVEL`. `FALTA_LEITO` é outro caminho: a ambulância já está na ocorrência e a busca seguinte não acha vaga. A carga sempre devolve o leito recusado a `LIVRE`, e a saga o reserva de novo. A trava existe para a banca atravessar esse ramo uma vez.

| Peça | Papel |
| --- | --- |
| API | `POST /api/leitos/demonstracao/proxima-busca-sem-vaga` |
| Gera | MS-Leitos, na tentativa seguinte, publica `saga.leito-reservado` com `SEM_LEITO_COMPATIVEL` |
| Consome | MS-Ocorrencias publica `notificacao.registrar` do tipo `FALTA_LEITO`. MS-Notificacoes grava |

A trava nasce desligada a cada subida do MS-Leitos. Armar espera HTTP `204`. Ela vale uma busca de UTI e só na tentativa maior que 1. A tentativa 1 reserva normalmente.

Não há botão para desarmar. Se armou e não despachou, a próxima recusa ou expiração de UTI consome a trava.

### Circuito — RF05

| Peça | Papel |
| --- | --- |
| API | `GET /api/leitos/disponiveis` e `POST /api/ocorrencias` |
| Gera | Com o MS-Leitos parado, o API Gateway responde `503`. A abertura continua no MS-Ocorrencias |
| Consome | O circuito vale só para `/api/leitos`. Login, abertura e despacho não entram nele |

Pare só o processo da porta 8095 com Ctrl+C. Ctrl+Z suspende e a porta continua ocupada.

1. **Consultar UTI** duas vezes. Esperado: HTTP `503`, `codigo` `SERVICO_LEITOS_INDISPONIVEL`.
2. **Abrir ocorrência agora**, no mesmo momento. Esperado: HTTP `201`. O paciente gravado é `Teste Circuito`, local `Rua do Circuito, 1`, prioridade `VERDE`, tipo `ENFERMARIA`. O UUID também entra no campo Ocorrência.
3. Suba o MS-Leitos de novo. Depois da janela de 10 segundos do circuito aberto, **Consultar UTI** volta a `200`.

## 5. Cenários pela tela

Comece cada bloco com carga limpa, exceto quando o passo disser para continuar na mesma carga. Entre antes do primeiro cartão.

### Aceite, segundo despacho e prazo

1. Modo **Aceite**.
2. Ocupação: duas ambulâncias e `L-UTI-01` livre.
3. Abra uma UTI. Despache. Acompanhe até `DESPACHADA`, ambulância 1, `L-UTI-01`, `Hospital Central`.
4. Ocupação de UTI: `[]`. Ambulâncias: só `XYZ-5678`.
5. Notificações: um `DESPACHO`.
6. **Segundo despacho**: HTTP `200`, os mesmos ids. `XYZ-5678` segue disponível.
7. Marcador de 2 minutos. Nenhuma `EXPIRACAO` nova.

### Primeira busca sem leito

Continue após o aceite, com `L-UTI-01` já reservado. Abra outra UTI, com outro paciente. Despache o UUID novo.

Esperado no acompanhamento: `FALHA_DESPACHO`, ambulância e leito nulos. A ambulância 2 volta a `DISPONIVEL`. `L-UTI-02` continua `OCUPADO`. Não há `DESPACHO` para essa ocorrência. Repetir o despacho dela responde `200` e permanece `FALHA_DESPACHO`.

### Sem ambulância

Ainda no aceite, com as duas ambulâncias livres de novo só se a anterior voltou. Reserve a enfermaria com a ambulância disponível. Abra uma emergência e despache.

Esperado: a enfermaria fica `DESPACHADA`. A emergência fica `FALHA_DESPACHO`, sem ambulância e sem leito. `L-EMG-01` continua `LIVRE`. A ocupação de ambulâncias fica `[]` enquanto as duas estiverem `DESPACHADA`.

### Recusa e o mesmo leito

Carga limpa. Modo **Recusa**. Abra uma UTI e despache.

O acompanhamento oscila. Olhe a ocorrência e o MySQL por alguns segundos:

1. A ambulância fica `AGUARDANDO_LEITO`. Ela não volta para a base.
2. A ocorrência volta a `EM_DESPACHO`, com `leitoId` nulo.
3. `L-UTI-01` volta a `LIVRE` e `reserva_recusada` ganha `RECUSA_UNIDADE`.
4. Como não há outro livre de UTI, a saga reserva `L-UTI-01` de novo.

O ciclo continua enquanto o modo for **Recusa**. Confirme uma volta e clique **Aceite** ou **Aleatório** para parar. Encerrar o MS-Leitos com Ctrl+C também interrompe.

### Outro leito na recusa

Carga limpa. **Criar L-UTI-03**. Modo **Recusa**. Abra uma UTI e despache.

A primeira reserva usa `L-UTI-01`. Na recusa, a seguinte prefere `L-UTI-03`. A notificação de despacho, quando a reserva seguinte existir, cita esse código. Pare o modo e apague `L-UTI-03` quando estiver `LIVRE`.

### Silêncio e prazo

Carga limpa. Modo **Silêncio**. Abra uma UTI e despache.

Logo em seguida: ocorrência `DESPACHADA`, ambulância `DESPACHADA`, `L-UTI-01` `RESERVADO` sem confirmação. Há `DESPACHO`. Espere pouco mais de 2 minutos. O estado visível volta a `DESPACHADA` / `RESERVADO`, porque o único livre é pedido de novo. A prova é `confirmado` em `0` no MySQL, uma linha `EXPIRACAO` em `reserva_recusada` e o par `EXPIRACAO` depois `DESPACHO` nas notificações. Pare o modo para o ciclo não continuar.

### Falta de leito

Carga limpa. Arme a trava. Modo **Recusa**. Abra uma UTI e despache.

A tentativa 1 reserva. A seguinte não acha vaga: a ambulância permanece fora de `DISPONIVEL`, a ocorrência fica `EM_DESPACHO` e as notificações ganham `FALTA_LEITO`.

### Circuito

Gateway no ar. Ctrl+C só na porta 8095. Duas consultas de UTI em `503` e uma abertura em `201`. Suba o MS-Leitos. A consulta volta a `200`.

## 6. O que conferir fora da tela

A tela não mostra fila, chave do Redis nem a coluna `confirmado`.

| Prova | Onde |
| --- | --- |
| Oito filas em Ready, Unacked e Total `0` | `http://localhost:15672`, `guest` / `guest` |
| `frota:disponiveis` e `leitos:disponiveis:UTI` | Redis na porta 6379 |
| `confirmado`, `reserva_recusada` | MySQL `omnileito_leitos`, porta 3310 |
| Documento gravado | `mongosh` no database `omnileito_notificacoes` |

O painel do RabbitMQ e o Mongo estão detalhados na seção 13 do roteiro.

## 7. O que a tela não faz

- Não encerra processo Java nem container. O circuito pede Ctrl+C no terminal da porta 8095.
- Não muda `EM_TRANSPORTE`, `CONCLUIDA`, `CANCELADA`, `EM_ATENDIMENTO` nem `INDISPONIVEL`.
- Não envia SMS, não abre mapa e não junta o Swagger no gateway. O Swagger de cada serviço segue em `http://localhost:<porta>/swagger-ui.html`.
- Não substitui o roteiro em curl nem os testes `./mvnw test`.
