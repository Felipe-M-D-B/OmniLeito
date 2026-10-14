# Contratos entre os serviços — OmniLeito

Documento local. Não vai para o Git.

- Versão: 0.2 — 13/10/2026. `leitosRecusados` ordena a escolha: o mesmo leito pode ser reservado de novo quando for o único LIVRE da especialidade
- Base: `docs/documento-de-requisitos.md` versão 0.3
- Vale para a implementação dos módulos. O regulador só enxerga o gateway.

Convenções deste contrato, onde o requisito não fixou o formato:

- JSON em UTF-8, campos em camelCase, data e hora em ISO-8601 com fuso (`2026-10-13T19:10:00-03:00`).
- O id da ocorrência é uma string gerada pelo MS-Ocorrencias. O id da ambulância e o id do leito são numéricos e locais de cada serviço. A ocorrência guarda esses números como referência, sem chave estrangeira entre bancos.
- O token é um JWT emitido pelo MS-Identidade. O gateway valida a assinatura. As demais rotas exigem `Authorization: Bearer`.
- A abertura é síncrona. O despacho responde na hora que a Saga começou e o estado final se consulta depois.
- A unidade hospitalar da demonstração não tem endpoint. O MS-Leitos sorteia aceite, recusa ou silêncio a cada reserva.
- A mensagem só é publicada depois do commit de quem a originou. Consumidor repetido da mesma ocorrência não cria segunda alocação nem segunda reserva ativa.

## 1. Rotas públicas

Todas passam pelo gateway. O prefixo público é `/api`.

| Método e caminho | Serviço interno | Auth | Resilience4j |
| --- | --- | --- | --- |
| `POST /api/auth/login` | MS-Identidade | não | não |
| `POST /api/ocorrencias` | MS-Ocorrencias | sim | não |
| `GET /api/ocorrencias/{ocorrenciaId}` | MS-Ocorrencias | sim | não |
| `POST /api/ocorrencias/{ocorrenciaId}/despachos` | MS-Ocorrencias | sim | não |
| `GET /api/ambulancias/disponiveis` | MS-Frota | sim | não |
| `GET /api/leitos/disponiveis?tipoLeito=` | MS-Leitos | sim | sim: timeout, retry e circuit breaker |
| `GET /api/notificacoes?ocorrenciaId=` | MS-Notificacoes | sim | não |

A abertura da ocorrência e o despacho não dependem do MS-Leitos. Com o circuito de leitos aberto, `GET /api/leitos/disponiveis` responde `503`. Login, abertura e despacho continuam no ar.

Corpo de erro, em qualquer serviço:

```json
{
  "codigo": "OCORRENCIA_NAO_ENCONTRADA",
  "mensagem": "Ocorrência não encontrada."
}
```

| HTTP | Quando |
| --- | --- |
| 400 | Campo ausente ou valor fora da lista fechada |
| 401 | Token ausente ou inválido |
| 404 | Id que não existe |
| 409 | Despacho de ocorrência que não está `ABERTA` |
| 503 | Gateway com circuito aberto na chamada ao MS-Leitos. `codigo`: `SERVICO_LEITOS_INDISPONIVEL` |

## 2. MS-Identidade

### POST /api/auth/login

```json
{
  "usuario": "regulador",
  "senha": "regulador"
}
```

Resposta `200`:

```json
{
  "token": "jwt",
  "tipo": "Bearer"
}
```

Usuário ou senha inválidos: `401`, `codigo` `CREDENCIAL_INVALIDA`.

A carga de demonstração tem um regulador: usuário `regulador`, senha `regulador`.

## 3. MS-Ocorrencias

### POST /api/ocorrencias

Abre a ocorrência. Não publica mensagem e não dispara a Saga.

```json
{
  "paciente": { "nome": "Maria Souza" },
  "local": "Rua das Flores, 120",
  "prioridade": "VERMELHA",
  "tipoLeito": "UTI"
}
```

`prioridade`: `VERMELHA`, `AMARELA`, `VERDE`. `tipoLeito`: `UTI`, `EMERGENCIA`, `ENFERMARIA`.

Resposta `201`:

```json
{
  "id": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "status": "ABERTA",
  "paciente": { "nome": "Maria Souza" },
  "local": "Rua das Flores, 120",
  "prioridade": "VERMELHA",
  "tipoLeito": "UTI",
  "ambulanciaId": null,
  "leitoId": null,
  "unidadeNome": null,
  "criadaEm": "2026-10-13T19:10:00-03:00"
}
```

### GET /api/ocorrencias/{ocorrenciaId}

Devolve o mesmo corpo, com o status e as referências já preenchidas pela Saga. É a consulta usada para ver o estado final do despacho.

### POST /api/ocorrencias/{ocorrenciaId}/despachos

Corpo vazio. O tipo de leito é o gravado na abertura. O despacho não aceita outro tipo.

- Ocorrência `ABERTA`: responde `202` com o corpo da ocorrência já em `EM_DESPACHO` e, depois do commit, publica `AlocarAmbulancia`.
- A mesma ocorrência chamada de novo, em qualquer status que não seja `ABERTA` por uma Saga já iniciada para essa chave: responde `200` com o estado atual e não aloca outra ambulância nem outro leito.
- Ocorrência inexistente: `404`.

`409` com `codigo` `OCORRENCIA_NAO_ABERTA` fica para uma ocorrência `CANCELADA` ou `CONCLUIDA`. `FALHA_DESPACHO` também não reinicia sozinho: a resposta é `200` com o estado de falha, sem nova alocação.

## 4. MS-Frota

### GET /api/ambulancias/disponiveis

Lista só ambulâncias `DISPONIVEL`. Lê o cache e, se não houver valor válido, lê o MySQL e preenche o cache.

Resposta `200`:

```json
[
  { "id": 1, "identificacao": "ABC-1234", "status": "DISPONIVEL" }
]
```

Alocar, voltar para `DISPONIVEL`, passar a `DESPACHADA` ou a `AGUARDANDO_LEITO` invalida esse cache.

## 5. MS-Leitos

### GET /api/leitos/disponiveis?tipoLeito=UTI

`tipoLeito` é obrigatório. Lista só leitos `LIVRE` daquela especialidade. Mesma regra de cache da frota. Leito `OCUPADO` e leito de outra especialidade não entram.

Resposta `200`:

```json
[
  {
    "id": 10,
    "codigo": "L-UTI-01",
    "tipoLeito": "UTI",
    "status": "LIVRE",
    "unidade": { "id": 1, "nome": "Hospital Central" }
  }
]
```

Reservar, confirmar, recusar, expirar ou devolver o leito a `LIVRE` invalida o cache dessa especialidade.

O prazo de expiração é propriedade deste serviço. Nome: `omnileito.reserva.expiracao-minutos`. Padrão: `2`.

## 6. MS-Notificacoes

### GET /api/notificacoes?ocorrenciaId={ocorrenciaId}

`ocorrenciaId` é obrigatório. Resposta `200`, da mais recente para a mais antiga:

```json
[
  {
    "id": "6b1f...",
    "ocorrenciaId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
    "tipo": "DESPACHO",
    "mensagem": "Ocorrência despachada. Ambulância ABC-1234, leito L-UTI-01, Hospital Central.",
    "ocorridaEm": "2026-10-13T19:12:00-03:00"
  }
]
```

`tipo`: `DESPACHO`, `EXPIRACAO`, `FALTA_LEITO`.

Não há envio de SMS. O canal de alerta é este registro.

## 7. Mensagens

Exchange direct `omnileito.saga` para a Saga. Exchange direct `omnileito.notificacao` para o alerta. Filas duráveis. Corpo JSON.

Toda mensagem de Saga leva `ocorrenciaId`. Esse campo é a chave de idempotência.

| Mensagem | Fila | Publica | Consome |
| --- | --- | --- | --- |
| `AlocarAmbulancia` | `saga.alocar-ambulancia` | MS-Ocorrencias | MS-Frota |
| `AmbulanciaAlocada` | `saga.ambulancia-alocada` | MS-Frota | MS-Ocorrencias |
| `ReservarLeito` | `saga.reservar-leito` | MS-Ocorrencias | MS-Leitos |
| `LeitoReservado` | `saga.leito-reservado` | MS-Leitos | MS-Ocorrencias |
| `LeitoExpirado` | `saga.leito-expirado` | MS-Leitos | MS-Ocorrencias |
| `AtualizarAmbulancia` | `saga.atualizar-ambulancia` | MS-Ocorrencias | MS-Frota |
| `RetentarBuscaLeito` | `saga.retentar-busca-leito` | MS-Leitos | MS-Ocorrencias |
| `RegistrarNotificacao` | `notificacao.registrar` | MS-Ocorrencias e MS-Leitos | MS-Notificacoes |

### AlocarAmbulancia

```json
{
  "ocorrenciaId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "prioridade": "VERMELHA"
}
```

O MS-Frota escolhe uma ambulância `DISPONIVEL`, grava `ALOCADA` ligada à ocorrência e responde. Se essa ocorrência já tiver ambulância alocada, devolve a mesma ambulância e não escolhe outra.

### AmbulanciaAlocada

```json
{
  "ocorrenciaId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "sucesso": true,
  "ambulanciaId": 1,
  "identificacao": "ABC-1234",
  "motivo": null
}
```

Sem ambulância livre: `sucesso` false, `ambulanciaId` null, `motivo` `SEM_AMBULANCIA`.

### ReservarLeito

```json
{
  "ocorrenciaId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "ambulanciaId": 1,
  "tipoLeito": "UTI",
  "leitosRecusados": [],
  "tentativa": 1
}
```

`tipoLeito` é o da abertura. `leitosRecusados` lista os leitos já recusados ou expirados para esta ocorrência. A lista não proíbe esses leitos: ela só os deixa por último. `tentativa` 1 é a primeira reserva do despacho. Da segunda em diante a ambulância já está comprometida com o chamado.

O MS-Leitos reserva primeiro um leito `LIVRE` dessa especialidade cujo id não esteja em `leitosRecusados`. Se não houver, reserva um leito `LIVRE` da especialidade mesmo que o id esteja na lista. `SEM_LEITO_COMPATIVEL` só sai quando não existe nenhum `LIVRE` dessa especialidade. Se a ocorrência já tiver reserva ativa não confirmada, não cria outra.

### LeitoReservado

```json
{
  "ocorrenciaId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "ambulanciaId": 1,
  "tentativa": 1,
  "sucesso": true,
  "leitoId": 10,
  "codigo": "L-UTI-01",
  "tipoLeito": "UTI",
  "unidadeId": 1,
  "unidadeNome": "Hospital Central",
  "resultadoUnidade": "ACEITE",
  "motivo": null
}
```

`resultadoUnidade`: `ACEITE`, `RECUSA` ou `SILENCIO`.

| Situação | O que o MS-Leitos grava antes de publicar | Campos da mensagem |
| --- | --- | --- |
| Aceite | Leito `RESERVADO`, confirmado, com horário | `sucesso` true, `resultadoUnidade` `ACEITE` |
| Silêncio | Leito `RESERVADO`, sem confirmação, com instante da reserva | `sucesso` true, `resultadoUnidade` `SILENCIO` |
| Recusa | Leito passa por `EXPIRADO` e volta a `LIVRE` | `sucesso` false, `motivo` `RECUSA_UNIDADE`, `leitoId` do leito recusado |
| Nenhum leito `LIVRE` da especialidade, nem entre os já recusados ou expirados | Nada é reservado | `sucesso` false, `motivo` `SEM_LEITO_COMPATIVEL`, `leitoId` null |

### LeitoExpirado

O job publica uma mensagem por leito `RESERVADO` sem confirmação há mais que o prazo. Antes de publicar, o leito passa por `EXPIRADO` e volta a `LIVRE`.

```json
{
  "ocorrenciaId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "ambulanciaId": 1,
  "leitoId": 10,
  "tipoLeito": "UTI"
}
```

O efeito no restante da Saga é o mesmo da recusa.

### AtualizarAmbulancia

```json
{
  "ocorrenciaId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "ambulanciaId": 1,
  "status": "DESPACHADA"
}
```

`status`: `DISPONIVEL`, `DESPACHADA` ou `AGUARDANDO_LEITO`. Mensagem repetida com o mesmo par ocorrência e status não muda uma segunda ambulância.

### RetentarBuscaLeito

```json
{
  "tipoLeito": "UTI"
}
```

O job do MS-Leitos publica isto, no fim do ciclo, para cada especialidade que tenha ao menos um leito `LIVRE`. O MS-Ocorrencias republica `ReservarLeito` para cada ocorrência `EM_DESPACHO` daquele tipo cuja ambulância já esteja em `AGUARDANDO_LEITO`.

### RegistrarNotificacao

```json
{
  "ocorrenciaId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "tipo": "DESPACHO",
  "mensagem": "Ocorrência despachada. Ambulância ABC-1234, leito L-UTI-01, Hospital Central.",
  "ocorridaEm": "2026-10-13T19:12:00-03:00"
}
```

Quem publica:

| tipo | Quem publica | Quando |
| --- | --- | --- |
| `DESPACHO` | MS-Ocorrencias | Leito reservado e ocorrência `DESPACHADA` |
| `EXPIRACAO` | MS-Leitos | Job expira uma reserva |
| `FALTA_LEITO` | MS-Ocorrencias | Nova busca não encontra leito e a ambulância já está no chamado |

O MS-Notificacoes grava e não devolve mensagem. Repetir o mesmo `ocorrenciaId`, `tipo` e `ocorridaEm` não duplica o registro.

## 8. Conversas da Saga

Quem decide o próximo passo da ocorrência é o MS-Ocorrencias. O MS-Leitos decide o leito e o sorteio da unidade. O MS-Frota só muda o status da ambulância que lhe for pedido.

### Aceite

1. Despacho grava `EM_DESPACHO` e publica `AlocarAmbulancia`.
2. Frota responde `AmbulanciaAlocada` com sucesso.
3. Ocorrencias publica `ReservarLeito` com `tentativa` 1.
4. Leitos reserva, sorteia `ACEITE` e publica `LeitoReservado`.
5. Ocorrencias grava `DESPACHADA`, o id da ambulância, o id do leito e o nome da unidade.
6. Ocorrencias publica `AtualizarAmbulancia` com `DESPACHADA` e `RegistrarNotificacao` com `DESPACHO`.

### Sem ambulância

`AmbulanciaAlocada` vem com `SEM_AMBULANCIA`. A ocorrência fica `FALHA_DESPACHO`. Não há `ReservarLeito`.

### Sem leito na primeira tentativa

`LeitoReservado` vem com `SEM_LEITO_COMPATIVEL` e `tentativa` 1. Ocorrencias publica `AtualizarAmbulancia` com `DISPONIVEL` e grava `FALHA_DESPACHO`. A equipe ainda não saiu.

### Recusa

`LeitoReservado` vem com `RECUSA_UNIDADE`. Ocorrencias grava `EM_DESPACHO`, mantém a ambulância, publica `AtualizarAmbulancia` com `AGUARDANDO_LEITO` e publica de novo `ReservarLeito`, com `tentativa` somada e o leito recusado em `leitosRecusados`.

### Silêncio e prazo

`LeitoReservado` vem com `SILENCIO`. Ocorrencias grava `DESPACHADA` e publica `AtualizarAmbulancia` com `DESPACHADA` e a notificação de despacho. O leito segue `RESERVADO` sem confirmação.

No prazo, o job publica `LeitoExpirado` e `RegistrarNotificacao` com `EXPIRACAO`. Ocorrencias trata como recusa: `EM_DESPACHO`, ambulância `AGUARDANDO_LEITO` e novo `ReservarLeito`.

### Nova busca sem leito

`SEM_LEITO_COMPATIVEL` com `tentativa` maior que 1: a ocorrência permanece `EM_DESPACHO`, a ambulância permanece `AGUARDANDO_LEITO` e Ocorrencias publica `RegistrarNotificacao` com `FALTA_LEITO`. Não devolve a ambulância para `DISPONIVEL`. Esse motivo só aparece quando não há leito `LIVRE` da especialidade. Leito já recusado ou expirado que esteja `LIVRE` é reservado de novo, em vez de gerar falta de leito.

No ciclo seguinte, se existir leito `LIVRE` da especialidade, o job publica `RetentarBuscaLeito` e a busca recomeça.

### Aceite numa tentativa seguinte

O mesmo desenho do aceite. A ocorrência volta a `DESPACHADA` e a ambulância volta a `DESPACHADA`, com o destino do leito desta tentativa. Esse leito é outro da mesma especialidade quando houver alternativa, ou o mesmo leito de antes quando ele for o único `LIVRE`.

## 9. O que cada módulo implementa

| Módulo | Contratos sob sua responsabilidade |
| --- | --- |
| Igor — gateway e MS-Identidade | Login, JWT, as rotas da seção 1 e o Resilience4j só em `GET /api/leitos/disponiveis` |
| Felipe — MS-Ocorrencias | Abertura, despacho, consulta da ocorrência e o miolo da seção 8 |
| Felipe — MS-Frota | `GET /api/ambulancias/disponiveis`, consumo de `AlocarAmbulancia` e de `AtualizarAmbulancia`, publicação de `AmbulanciaAlocada` |
| Felipe — MS-Leitos | Reserva, sorteio da unidade, publicação de `LeitoReservado` |
| Sérgio Leandro — MS-Frota e MS-Leitos | Cache e invalidação nas escritas já definidas. No MS-Leitos, o prazo, o job, `LeitoExpirado`, `RetentarBuscaLeito` e a notificação `EXPIRACAO` |
| Sérgio Leandro — MS-Notificacoes | Consumo de `RegistrarNotificacao` e `GET /api/notificacoes` |

A consulta de leitos e a de ambulâncias entram no gateway junto com as rotas do Igor. O cache dessas consultas é do Sérgio Leandro, dentro do serviço que guarda o dado.
