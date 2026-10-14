# Roteiro de testes — processos já implementados

Versão 0.1 — 13/10/2026.

Este roteiro cobre o que já está nos módulos: login, abertura, despacho, saga, cache, prazo da reserva, notificações e o circuito do gateway. As chamadas do regulador entram por `http://localhost:8085`.

O arquivo `docs/como-testar-localmente-monolito.md` descreve o monolito antigo. Para o sistema atual, siga este roteiro.

## 1. O que precisa estar no ar

| Peça | Porta | Uso no teste |
| --- | --- | --- |
| MySQL do OmniLeito | 3310 | Schemas `omnileito_identidade`, `omnileito_ocorrencias`, `omnileito_frota`, `omnileito_leitos` |
| RabbitMQ | 5672 e 15672 | Filas da saga e da notificação. Painel: `http://localhost:15672`, `guest` / `guest` |
| Redis | 6379 | Cache de ambulâncias e de leitos |
| MongoDB | 27018 | Banco `omnileito_notificacoes` |
| api-gateway | 8085 | Única porta das chamadas abaixo |
| ms-identidade | 8092 | Login |
| ms-ocorrencias | 8093 | Abertura, despacho e estado da saga |
| ms-frota | 8094 | Ambulâncias |
| ms-leitos | 8095 | Leitos, sorteio da unidade e prazo |
| ms-notificacoes | 8096 | Alertas |

Carga inicial, criada pelo Flyway na primeira subida:

| Recurso | Estado inicial |
| --- | --- |
| Regulador | `regulador` / `regulador` |
| Ambulância 1 `ABC-1234` | `DISPONIVEL` |
| Ambulância 2 `XYZ-5678` | `DISPONIVEL` |
| `L-UTI-01`, Hospital Central | `UTI`, `LIVRE` |
| `L-UTI-02`, Hospital Central | `UTI`, `OCUPADO` |
| `L-EMG-01`, UPA Norte | `EMERGENCIA`, `LIVRE` |
| `L-EMG-02`, UPA Norte | `EMERGENCIA`, `OCUPADO` |
| `L-ENF-01`, Hospital Central | `ENFERMARIA`, `LIVRE` |
| `L-ENF-02`, Hospital Central | `ENFERMARIA`, `OCUPADO` |

Cada especialidade tem um único leito `LIVRE`. Os cenários usam isso de propósito.

Subir a infraestrutura, se ainda não estiver:

```bash
docker compose up -d
docker compose ps
```

Os dados ficam em volumes nomeados. `docker compose down` remove os containers e preserva os dados; `docker compose down -v` também remove os volumes e reinicia os bancos do zero.

Na pasta do projeto, um terminal por serviço:

```bash
./mvnw -pl ms-identidade spring-boot:run
./mvnw -pl ms-ocorrencias spring-boot:run
./mvnw -pl ms-frota spring-boot:run
./mvnw -pl ms-leitos spring-boot:run
./mvnw -pl ms-notificacoes spring-boot:run
./mvnw -pl api-gateway spring-boot:run
```

O MS-Leitos, sem argumento, sorteia `ACEITE`, `RECUSA` ou `SILENCIO`. Para um resultado fixo, encerre só o processo da porta 8095 e suba de novo com um destes:

```bash
./mvnw -pl ms-leitos spring-boot:run -Dspring-boot.run.arguments=--omnileito.reserva.resultado=ACEITE
./mvnw -pl ms-leitos spring-boot:run -Dspring-boot.run.arguments=--omnileito.reserva.resultado=RECUSA
./mvnw -pl ms-leitos spring-boot:run -Dspring-boot.run.arguments=--omnileito.reserva.resultado=SILENCIO
```

O prazo da reserva sem confirmação é `omnileito.reserva.expiracao-minutos=2`. O job olha a fila a cada 15 segundos.

Cada serviço publica o próprio Swagger. O gateway, na porta 8085, não tem essa página. O endereço antigo `http://localhost:8085/swagger-ui/index.html#/despacho-controller/iniciarDespacho` era do monolito e responde 404.

| Serviço | Swagger |
| --- | --- |
| Identidade | http://localhost:8092/swagger-ui.html |
| Ocorrências | http://localhost:8093/swagger-ui.html |
| Frota | http://localhost:8094/swagger-ui.html |
| Leitos | http://localhost:8095/swagger-ui.html |
| Notificações | http://localhost:8096/swagger-ui.html |

O despacho está no Swagger de ocorrências, no `OcorrenciaController`: abrir a ocorrência, consultar pelo id e `POST /api/ocorrencias/{ocorrenciaId}/despachos`. A operação `iniciarDespacho` não existe mais.

## 2. Voltar à carga inicial

O banco desta máquina já tem despachos dos testes de 13/10, inclusive a ambulância `TST-9001`. Os cenários da seção 4 em diante pedem as duas ambulâncias `DISPONIVEL` e um leito `LIVRE` por especialidade. Antes deles, pare os seis processos Java e rode:

```bash
mysql -h127.0.0.1 -P3310 -uroot -proot123 -e "
DROP DATABASE IF EXISTS omnileito_identidade;
DROP DATABASE IF EXISTS omnileito_ocorrencias;
DROP DATABASE IF EXISTS omnileito_frota;
DROP DATABASE IF EXISTS omnileito_leitos;
"

docker exec omnileito-mongo mongosh --quiet --eval 'db.getSiblingDB("omnileito_notificacoes").dropDatabase()'
docker exec omnileito-redis redis-cli FLUSHDB
```

Suba os seis processos de novo. O Flyway recria as tabelas e a carga. Confira:

```bash
mysql -h127.0.0.1 -P3310 -uroot -proot123 -e "
SELECT id, identificacao, status FROM omnileito_frota.ambulancia;
SELECT codigo, tipo_leito, status FROM omnileito_leitos.leito;
"
```

O esperado é duas ambulâncias `DISPONIVEL` e seis leitos, três `LIVRE` e três `OCUPADO`.

## 3. Testes automatizados

Na pasta do projeto, sem subir a infraestrutura:

```bash
./mvnw test
```

Eles cobrem o que o roteiro manual também exercita, e dois casos difíceis de isolar com um único leito livre:

| Módulo | O que o teste garante |
| --- | --- |
| ms-ocorrencias | Abertura não publica mensagem. Segundo despacho não aloca de novo. `FALHA_DESPACHO` não recomeça. Primeira busca sem leito devolve a ambulância. Recusa deixa a ambulância no chamado e pede outro leito. Busca seguinte sem leito publica `FALTA_LEITO` |
| ms-leitos | Havendo outro leito da especialidade, a reserva evita o recusado. Sendo ele o único `LIVRE`, o mesmo leito é reservado de novo. Sem nenhum `LIVRE`, o motivo é `SEM_LEITO_COMPATIVEL` |
| ms-frota | A mesma ocorrência recebe a mesma ambulância. Voltar a `DISPONIVEL` solta a ocorrência |

## 4. Preparar a chamada

Todos os exemplos usam o gateway. Guarde o token:

```bash
BASE=http://localhost:8085
TOKEN=$(curl -s -X POST "$BASE/api/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"usuario":"regulador","senha":"regulador"}' \
  | python3 -c 'import json,sys; print(json.load(sys.stdin)["token"])')
```

Para acompanhar um despacho até o estado final:

```bash
consultar() {
  curl -s "$BASE/api/ocorrencias/$1" -H "Authorization: Bearer $TOKEN"
  echo
}
```

Repita `consultar <id>` por alguns segundos. O `202` só diz que a saga começou.

## 5. Login, token e validação

MS-Leitos neste bloco pode estar com qualquer resultado. Nenhuma destas chamadas despacha.

### 5.1 Login válido

```bash
curl -s -D - -o /tmp/login.json -X POST "$BASE/api/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"usuario":"regulador","senha":"regulador"}'
```

Esperado: HTTP `200`, corpo com `token` e `"tipo": "Bearer"`.

### 5.2 Senha errada

```bash
curl -s -D - -X POST "$BASE/api/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"usuario":"regulador","senha":"errada"}'
```

Esperado: HTTP `401`, `codigo` `CREDENCIAL_INVALIDA`.

### 5.3 Rota protegida sem token

```bash
curl -s -D - "$BASE/api/leitos/disponiveis?tipoLeito=UTI"
```

Esperado: HTTP `401`, `codigo` `TOKEN_INVALIDO`.

### 5.4 Abertura com tipo fora da lista

```bash
curl -s -D - -X POST "$BASE/api/ocorrencias" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"paciente":{"nome":"Maria Souza"},"local":"Rua das Flores, 120","prioridade":"VERMELHA","tipoLeito":"GERAL"}'
```

Esperado: HTTP `400`, `codigo` `CAMPO_INVALIDO`. Nada é gravado e nenhuma fila recebe mensagem.

### 5.5 Consulta de id inexistente

```bash
curl -s -D - "$BASE/api/ocorrencias/00000000-0000-0000-0000-000000000000" \
  -H "Authorization: Bearer $TOKEN"
```

Esperado: HTTP `404`, `codigo` `OCORRENCIA_NAO_ENCONTRADA`.

## 6. Consultas e cache

Suba o MS-Leitos com `ACEITE` antes da seção 7. Esta seção só lê.

```bash
curl -s "$BASE/api/ambulancias/disponiveis" -H "Authorization: Bearer $TOKEN"
echo
curl -s "$BASE/api/leitos/disponiveis?tipoLeito=UTI" -H "Authorization: Bearer $TOKEN"
echo
curl -s "$BASE/api/leitos/disponiveis?tipoLeito=EMERGENCIA" -H "Authorization: Bearer $TOKEN"
echo
curl -s "$BASE/api/leitos/disponiveis?tipoLeito=ENFERMARIA" -H "Authorization: Bearer $TOKEN"
echo
```

Esperado:

- Duas ambulâncias, `ABC-1234` e `XYZ-5678`, as duas `DISPONIVEL`.
- UTI devolve só `L-UTI-01`. `L-UTI-02` está `OCUPADO` e não entra.
- Emergência devolve só `L-EMG-01`. Enfermaria devolve só `L-ENF-01`.

Repita a consulta de UTI. A segunda resposta vem do Redis. As chaves ficam em `frota:disponiveis` e `leitos:disponiveis:UTI`.

```bash
docker exec omnileito-redis redis-cli KEYS 'frota:*'
docker exec omnileito-redis redis-cli KEYS 'leitos:*'
```

Se o Redis estiver fechado, a mesma consulta ainda responde `200`: frota e leitos leem o MySQL.

## 7. Aceite

MS-Leitos com `--omnileito.reserva.resultado=ACEITE`. Banco na carga inicial.

### 7.1 Abrir

```bash
curl -s -D - -X POST "$BASE/api/ocorrencias" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"paciente":{"nome":"Maria Souza"},"local":"Rua das Flores, 120","prioridade":"VERMELHA","tipoLeito":"UTI"}'
```

Esperado: HTTP `201`, `status` `ABERTA`, `ambulanciaId` e `leitoId` nulos. Copie o `id`.

### 7.2 Despachar

```bash
curl -s -D - -X POST "$BASE/api/ocorrencias/ID/despachos" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{}'
```

Esperado na hora: HTTP `202`, `status` `EM_DESPACHO`.

Alguns segundos depois, `consultar ID` devolve:

| Campo | Valor |
| --- | --- |
| status | `DESPACHADA` |
| ambulanciaId | `1` |
| leitoId | id de `L-UTI-01` |
| unidadeNome | `Hospital Central` |

No MySQL, a ambulância 1 está `DESPACHADA` com essa ocorrência. `L-UTI-01` está `RESERVADO` e `confirmado` verdadeiro. `L-UTI-02` continua `OCUPADO`.

```bash
curl -s "$BASE/api/notificacoes?ocorrenciaId=ID" -H "Authorization: Bearer $TOKEN"
```

Esperado: uma notificação `DESPACHO` citando `ABC-1234`, `L-UTI-01` e Hospital Central. O horário pode vir em UTC (`Z`); é o mesmo instante do despacho.

A lista de UTI fica vazia, e a de ambulâncias fica só com `XYZ-5678`. O cache de `leitos:disponiveis:UTI` acompanha a lista vazia.

### 7.3 Segundo despacho da mesma ocorrência

Repita o `POST /despachos` do mesmo id.

Esperado: HTTP `200`, o mesmo `ambulanciaId` e o mesmo `leitoId`. A ambulância 2 continua `DISPONIVEL`.

## 8. Primeira busca sem leito

Ainda com `ACEITE`. A UTI livre já foi reservada na seção 7. A ambulância 2 continua livre.

Abra outra ocorrência, paciente `Ana Costa`, local `Rua Sem Vaga, 1`, prioridade `VERMELHA`, tipo `UTI`. Despache.

Esperado, depois de alguns segundos:

| Campo | Valor |
| --- | --- |
| status | `FALHA_DESPACHO` |
| ambulanciaId | nulo |
| leitoId | nulo |

A ambulância 2 volta a `DISPONIVEL`. Nenhum leito de UTI muda: `L-UTI-01` segue reservado para Maria, `L-UTI-02` segue ocupado. Não há notificação `DESPACHO` para Ana.

Um novo `POST /despachos` dessa ocorrência responde `200` e permanece `FALHA_DESPACHO`. A saga não recomeça.

## 9. Sem ambulância

Ainda com `ACEITE`. Reserve a enfermaria com a ambulância que voltou, para esgotar a frota.

Abra João Lima, `Av. Brasil, 50`, `AMARELA`, `ENFERMARIA`. Despache.

Esperado: `DESPACHADA`, ambulância 2, leito `L-ENF-01`, Hospital Central, notificação `DESPACHO`. As duas ambulâncias ficam `DESPACHADA`. `GET /api/ambulancias/disponiveis` devolve `[]`.

Abra Carlos Dias, `Rua do Cache, 9`, `VERDE`, `EMERGENCIA`. Despache.

Esperado: `FALHA_DESPACHO`, sem ambulância e sem leito. `L-EMG-01` continua `LIVRE`. Não há `ReservarLeito` para essa ocorrência.

## 10. Recusa e repetição do mesmo leito

Volte à carga inicial pela seção 2. Na subida, o MS-Leitos entra com `RECUSA`, no lugar do comando sem argumento. Sem esse reset não há ambulância nem leito livre.

Abra uma UTI e despache.

O que observar, olhando a ocorrência e o MySQL por alguns segundos:

1. A ambulância sai de `DISPONIVEL` e fica `AGUARDANDO_LEITO`. Ela não volta para a base.
2. A ocorrência volta a `EM_DESPACHO`, com `leitoId` nulo.
3. `L-UTI-01` volta a `LIVRE`. A tabela `reserva_recusada` ganha uma linha com motivo `RECUSA_UNIDADE`.
4. Como não existe outro leito `LIVRE` de UTI, a saga reserva `L-UTI-01` de novo. Nova recusa, nova linha em `reserva_recusada`.

Enquanto `resultado=RECUSA`, esse ciclo continua. Confirme uma volta e encerre o MS-Leitos para não deixar a reserva girando. A ambulância permanece `AGUARDANDO_LEITO`.

## 11. Silêncio, prazo e a mesma vaga de novo

Volte à carga inicial pela seção 2. Na subida, o MS-Leitos entra com `SILENCIO`.

Abra uma UTI e despache.

Esperado logo em seguida: ocorrência `DESPACHADA`, ambulância `DESPACHADA`, `L-UTI-01` `RESERVADO` com `confirmado` falso. Há notificação `DESPACHO`. O prazo ainda não correu para uma reserva confirmada; esta não está confirmada, então o job pode expirar.

Espere pouco mais de 2 minutos e consulte de novo.

Esperado nessa janela:

1. Notificação `EXPIRACAO` para a mesma ocorrência.
2. A ambulância passa por `AGUARDANDO_LEITO` e a ocorrência por `EM_DESPACHO`.
3. `L-UTI-01` é o único `LIVRE` de UTI, então é reservado outra vez. Com `SILENCIO`, a ocorrência volta a `DESPACHADA` e a ambulância a `DESPACHADA`, no mesmo leito.
4. `reserva_recusada` registra o motivo da expiração. O status gravado do leito não fica `EXPIRADO`: depois do commit ele está `LIVRE` ou, na reserva seguinte, `RESERVADO`.

## 12. Circuito da consulta de leitos

Com o gateway no ar, pare só o processo da porta 8095. Login, abertura e despacho não passam por esse circuito.

Chame duas vezes:

```bash
curl -s -D - "$BASE/api/leitos/disponiveis?tipoLeito=UTI" -H "Authorization: Bearer $TOKEN"
```

Esperado: HTTP `503`, `codigo` `SERVICO_LEITOS_INDISPONIVEL`.

No mesmo momento, o login e um `POST /api/ocorrencias` continuam respondendo `200` e `201`.

Suba o MS-Leitos de novo. Depois da janela de 10 segundos do circuito aberto, a consulta de UTI volta a `200`.

## 13. Conferência das filas

No painel `http://localhost:15672`, ao final de um aceite estável, estas filas ficam com 0 mensagens prontas e 1 consumidor cada:

- `saga.alocar-ambulancia`
- `saga.ambulancia-alocada`
- `saga.reservar-leito`
- `saga.leito-reservado`
- `saga.leito-expirado`
- `saga.atualizar-ambulancia`
- `saga.retentar-busca-leito`
- `notificacao.registrar`

Mensagem presa em `notificacao.registrar` indica que o MS-Notificacoes não gravou. A consulta `GET /api/notificacoes` tem de devolver o alerta correspondente.

Filas com sufixo `.queue` ou nome `saga.cancelar-leito.queue` e `saga.liberar-ambulancia.queue` são restos do monolito. O fluxo atual não publica nelas.
