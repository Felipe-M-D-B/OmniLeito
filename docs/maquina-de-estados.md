# Máquina de estados — OmniLeito

Documento local. Não vai para o Git.

- Versão: 0.2 — 13/10/2026. Recusa e prazo não impedem reservar de novo o mesmo leito quando ele for o único compatível
- Base: `docs/documento-de-requisitos.md` versão 0.3, `docs/contratos-entre-servicos.md` versão 0.2 e `docs/modelo-de-dados.md` versão 0.2

Ocorrência, ambulância e leito mudam juntos. O quadro da seção 4 é a referência da implementação. As seções 1 a 3 detalham cada um.

Nesta entrega, o fluxo automático não usa `EM_TRANSPORTE`, `CONCLUIDA`, `CANCELADA`, `EM_ATENDIMENTO` nem `INDISPONIVEL`. Esses status existem no modelo para não serem reaproveitados com outro sentido. `OCUPADO` só aparece na carga: leito sem vaga, fora da Saga.

`EXPIRADO` não fica gravado. Na recusa e no prazo, a transação do MS-Leitos passa por ele e o commit deixa o leito `LIVRE`. O histórico guarda o motivo.

## 1. Ocorrência

```text
                    despacho aceito
        +-------------------------------------------+
        |                                           |
        v                                           |
     ABERTA --despacho--> EM_DESPACHO --aceite ou silêncio--> DESPACHADA
                              |    ^                      |
                              |    |                      |
                              |    +---- recusa ou prazo -+
                              |
                              +-- sem ambulância, ou sem leito na 1ª tentativa --> FALHA_DESPACHO
```

| De | Evento | Para |
| --- | --- | --- |
| — | Abertura com nome, local, prioridade e tipo de leito | `ABERTA` |
| `ABERTA` | `POST /despachos` | `EM_DESPACHO` |
| `EM_DESPACHO` | `AmbulanciaAlocada` com `SEM_AMBULANCIA` | `FALHA_DESPACHO` |
| `EM_DESPACHO` | `LeitoReservado` com `SEM_LEITO_COMPATIVEL` e `tentativa` 1 | `FALHA_DESPACHO` |
| `EM_DESPACHO` | `LeitoReservado` com `ACEITE` ou `SILENCIO` | `DESPACHADA` |
| `DESPACHADA` | `LeitoReservado` com `RECUSA_UNIDADE`, ou `LeitoExpirado` | `EM_DESPACHO` |
| `EM_DESPACHO` | `SEM_LEITO_COMPATIVEL` com `tentativa` maior que 1 | `EM_DESPACHO` |

`EM_DESPACHO` na primeira busca é a Saga ainda sem destino. `EM_DESPACHO` depois da recusa ou do prazo é a mesma ocorrência, com a ambulância já no chamado, à espera de um leito da mesma especialidade.

Um segundo despacho não muda status e não aloca outro recurso. `FALHA_DESPACHO` não volta sozinho para `ABERTA`.

## 2. Ambulância

```text
DISPONIVEL --alocou--> ALOCADA --aceite ou silêncio--> DESPACHADA
                          |                                |
                          | recusa na 1ª reserva           | prazo, ou recusa
                          v                                v
                       AGUARDANDO_LEITO <------------------+
                          |
                          | aceite ou silêncio da nova busca
                          v
                       DESPACHADA

ALOCADA --sem leito na 1ª tentativa--> DISPONIVEL
```

| De | Evento | Para |
| --- | --- | --- |
| `DISPONIVEL` | `AlocarAmbulancia` com sucesso | `ALOCADA` |
| `ALOCADA` | Aceite ou silêncio da reserva | `DESPACHADA` |
| `ALOCADA` | Sem leito na tentativa 1 | `DISPONIVEL` |
| `ALOCADA` | Recusa da primeira reserva | `AGUARDANDO_LEITO` |
| `DESPACHADA` | Recusa ou prazo da reserva atual | `AGUARDANDO_LEITO` |
| `AGUARDANDO_LEITO` | Aceite ou silêncio de uma nova reserva | `DESPACHADA` |
| `AGUARDANDO_LEITO` | Nova busca sem leito, ou nova recusa | `AGUARDANDO_LEITO` |

`AGUARDANDO_LEITO` vale a caminho ou já no local. A equipe presta os primeiros socorros e ainda não tem unidade de destino.

A volta para `DISPONIVEL` só acontece quando o primeiro despacho não reserva leito nenhum. Sem ambulância livre, nenhuma delas muda de status.

`INDISPONIVEL` não entra na alocação. `EM_ATENDIMENTO` não é alcançado por este fluxo.

## 3. Leito

```text
LIVRE --reservou e a unidade aceitou ou calou--> RESERVADO
                                                      |
                                                      | recusa ou prazo
                                                      v
                                                    LIVRE

OCUPADO permanece OCUPADO. Não é candidato a reserva.
```

| De | Evento | Para |
| --- | --- | --- |
| `LIVRE` | Reserva da especialidade pedida, seguida de aceite | `RESERVADO` confirmado |
| `LIVRE` | Reserva da especialidade pedida, seguida de silêncio | `RESERVADO` sem confirmação, com `reservado_em` |
| `RESERVADO` sem confirmação | Prazo estourado | `LIVRE` |
| `RESERVADO` | Recusa da unidade | `LIVRE` |
| `LIVRE` ou `OCUPADO` | A especialidade não é a pedida, ou o leito está `OCUPADO` | permanece como está |
| `LIVRE` | Nova reserva da mesma ocorrência, quando este é o único leito `LIVRE` da especialidade | `RESERVADO` de novo, mesmo depois de recusa ou prazo |

O leito confirmado não entra no job. `OCUPADO` da carga não muda.

Na recusa e no prazo, o id do leito entra em `reserva_recusada` e em `leito_excluido`. A busca seguinte pede primeiro um leito `LIVRE` da mesma especialidade que não esteja nessa lista. Se não houver, pede de novo um leito da lista, inclusive o que acabou de voltar a `LIVRE`. Cada nova reserva passa outra vez pelo hospital aleatório.

## 4. Quadro conjunto

Este é o quadro que a implementação deve reproduzir. Cada linha é um evento e o estado dos três depois dele.

| Evento | Ocorrência | Ambulância | Leito |
| --- | --- | --- | --- |
| Abertura | `ABERTA` | nenhuma ligada, as livres seguem `DISPONIVEL` | nenhum ligado |
| Despacho iniciado | `EM_DESPACHO` | ainda nenhuma | nenhum |
| Ambulância alocada | `EM_DESPACHO` | `ALOCADA` | nenhum |
| Sem ambulância livre | `FALHA_DESPACHO` | todas continuam como estavam | nenhum |
| Primeira reserva aceita | `DESPACHADA` | `DESPACHADA` | `RESERVADO` confirmado |
| Primeira reserva em silêncio | `DESPACHADA` | `DESPACHADA` | `RESERVADO` sem confirmação |
| Sem leito na primeira tentativa | `FALHA_DESPACHO` | `DISPONIVEL` | nenhum reservado |
| Recusa, na primeira ou numa busca seguinte | `EM_DESPACHO` | `AGUARDANDO_LEITO` | o recusado volta a `LIVRE` |
| Prazo da reserva não confirmada | `EM_DESPACHO` | `AGUARDANDO_LEITO` | o expirado volta a `LIVRE` |
| Nova busca sem nenhum leito `LIVRE` da especialidade | `EM_DESPACHO` | `AGUARDANDO_LEITO` | nenhum reservado |
| Nova reserva do mesmo leito, por ser o único `LIVRE` | conforme o hospital: `DESPACHADA` no aceite ou no silêncio; `EM_DESPACHO` numa nova recusa | `DESPACHADA` no aceite ou no silêncio; `AGUARDANDO_LEITO` na recusa | `RESERVADO` no aceite ou no silêncio; `LIVRE` numa nova recusa |
| Nova reserva aceita em outro leito | `DESPACHADA` | `DESPACHADA` | `RESERVADO` confirmado, leito da mesma especialidade ainda não recusado |
| Nova reserva em silêncio | `DESPACHADA` | `DESPACHADA` | `RESERVADO` sem confirmação |

Invariantes depois de cada evento:

- `ABERTA` não tem ambulância nem leito.
- `FALHA_DESPACHO` não tem ambulância ligada nem leito reservado para essa ocorrência.
- `DESPACHADA` tem uma ambulância `DESPACHADA` e um leito `RESERVADO` da especialidade gravada na abertura.
- `EM_DESPACHO` com ambulância `AGUARDANDO_LEITO` não tem leito `RESERVADO` ativo.
- Duas ocorrências não ficam com a mesma ambulância nem com o mesmo leito.

## 5. Especialidade

O tipo de leito não é um status. Ele é fixo desde a abertura. Toda reserva, toda nova busca e o cache usam esse tipo. Um leito `LIVRE` de outra especialidade não muda de estado por causa desse chamado.

## 6. Quem grava a transição

| Transição | Quem grava |
| --- | --- |
| Abertura para `ABERTA` | MS-Ocorrencias, na API |
| `ABERTA` para `EM_DESPACHO`, e as demais mudanças de status da ocorrência | MS-Ocorrencias, ao tratar a mensagem |
| `DISPONIVEL` para `ALOCADA` | MS-Frota, ao consumir `AlocarAmbulancia` |
| `ALOCADA`, `DESPACHADA` e `AGUARDANDO_LEITO` entre si, e a volta a `DISPONIVEL` | MS-Frota, ao consumir `AtualizarAmbulancia` |
| `LIVRE` para `RESERVADO`, confirmação, recusa e expiração | MS-Leitos |

A mensagem só sai depois do commit de quem gravou. Repetir a mensagem não cria a segunda ambulância nem o segundo leito.
