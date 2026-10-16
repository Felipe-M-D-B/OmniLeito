# Roteiro de testes

Versão 0.2 — 15/10/2026.

Este roteiro cobre login, abertura, despacho, saga, cache, prazo da reserva, notificações e o circuito do gateway. As chamadas do regulador entram por `http://localhost:8085`.

É preciso Java 21. O `./mvnw` com o Java 11 do sistema não sobe os serviços.

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

O MS-Notificacoes lê `spring.mongodb.uri`. O padrão é `mongodb://localhost:27018/omnileito_notificacoes`. Se o terminal tiver `OMNILEITO_MONGO_URI`, esse valor substitui o padrão. A porta do host é 27018. Dentro do container o Mongo continua na 27017.

Na pasta do projeto, uma vez, para publicar o `omnileito-comum`:

```bash
./mvnw -DskipTests install
```

O `-pl` não monta esse módulo. Sem o jar no Maven local, o serviço não sobe. Um terminal por serviço, o gateway por último:

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

O prazo da reserva sem confirmação é `omnileito.reserva.expiracao-minutos=2`. A cada 15 segundos (`omnileito.reserva.varredura-ms=15000`) o MS-Leitos procura, no MySQL, leito `RESERVADO`, `confirmado` falso e `reservado_em` anterior ao prazo. Reserva com aceite não entra. O job não lê fila do RabbitMQ.

Cada serviço publica o próprio Swagger. O gateway, na porta 8085, não tem essa página.

| Serviço | Swagger |
| --- | --- |
| Identidade | http://localhost:8092/swagger-ui.html |
| Ocorrências | http://localhost:8093/swagger-ui.html |
| Frota | http://localhost:8094/swagger-ui.html |
| Leitos | http://localhost:8095/swagger-ui.html |
| Notificações | http://localhost:8096/swagger-ui.html |

O despacho está no Swagger de ocorrências, no `OcorrenciaController`: abrir a ocorrência, consultar pelo id e `POST /api/ocorrencias/{ocorrenciaId}/despachos`.

## 2. Voltar à carga inicial

A primeira subida, com MySQL vazio, já grava a carga pelo Flyway. Os cenários a partir da seção 8 gastam ambulâncias e leitos. Para recomeçar, pare os seis processos Java e rode:

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

Na pasta do projeto:

```bash
./mvnw test
```

O comando roda o JUnit. Não usa o Docker, não abre as portas 8085 e 8092 a 8096 e não grava no MySQL, no Redis, no Mongo nem no RabbitMQ. Pode ser executado com a infraestrutura desligada. Se ela estiver no ar, os testes também não a tocam.

O fim esperado é `BUILD SUCCESS`. Nesta suíte são 7 testes em ocorrências, 2 na frota e 3 em leitos, todos com `Failures: 0`. As linhas `WARNING` do Mockito são um aviso do Java para versões futuras. Não são falha.

O Maven não aceita o nome da classe no lugar de `test`. `./mvnw OcorrenciaSagaServiceTest` para com `Unknown lifecycle phase`. Para rodar uma classe só, o `-pl` limita o módulo e o `-Dtest` escolhe a classe:

```bash
./mvnw -pl ms-ocorrencias test -Dtest=OcorrenciaSagaServiceTest
./mvnw -pl ms-leitos test -Dtest=LeitoServiceTest
./mvnw -pl ms-frota test -Dtest=FrotaServiceTest
```

Cada teste chama a regra de negócio com repositórios falsos. O serviço decide o próximo passo e o teste confere essa decisão. São três classes.

`OcorrenciaSagaServiceTest` cobre a saga:

- Abrir uma ocorrência não publica mensagem. O despacho é que começa a saga.
- Despachar de novo a mesma ocorrência não pede outra ambulância.
- Ocorrência em `FALHA_DESPACHO` não recomeça.
- Ocorrência `CANCELADA` responde conflito.
- Na primeira busca, se não há leito livre, a ambulância volta a `DISPONIVEL`.
- Na recusa, a ambulância fica no chamado e a saga pede outro leito.
- Se essa busca seguinte também não acha leito, a ambulância não volta para a base e sai a notificação `FALTA_LEITO`.

`LeitoServiceTest` cobre a escolha do leito. A carga real tem um único leito `LIVRE` por especialidade, então estes casos não aparecem nas seções manuais:

- Se existe outro leito livre da mesma especialidade, a reserva não repete o que foi recusado.
- Se o recusado é o único `LIVRE`, ele é pedido de novo.
- Se não há nenhum `LIVRE`, o motivo é `SEM_LEITO_COMPATIVEL`.

`FrotaServiceTest` cobre a ambulância. A mesma ocorrência recebe sempre a mesma ambulância, e ela só volta a `DISPONIVEL` quando a saga pede isso.

Estes testes não substituem as seções 4 a 13. O `./mvnw test` mostra que a regra está certa no código. O `curl` no gateway mostra que MySQL, RabbitMQ, Redis, Mongo e os seis processos fazem essa regra acontecer de ponta a ponta.

## 4. Preparar a chamada

Todos os exemplos usam o gateway. Guarde o token:

```bash
BASE=http://localhost:8085
TOKEN=$(curl -s -X POST "$BASE/api/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"usuario":"regulador","senha":"regulador"}' \
  | python3 -c 'import json,sys; print(json.load(sys.stdin)["token"])')
  echo "$TOKEN"
```

Para acompanhar um despacho até o estado final:

```bash
consultar() {
  curl -s "$BASE/api/ocorrencias/$1" -H "Authorization: Bearer $TOKEN"
  echo
}
```

Definir a função não imprime nada. O token e a função valem só neste terminal.

`consultar` não abre ocorrência e não despacha. Ela só lê uma ocorrência que já existe. O `<id>` do texto não se digita assim: é o valor do campo `id` que o `POST /api/ocorrencias` devolve, na seção 7. É um UUID. O número `3f2a1c0e-7b44-4d1a-9c88-0a1b2c3d4e5f` abaixo é só o formato. Consultá-lo responde `404` e `OCORRENCIA_NAO_ENCONTRADA`, porque essa ocorrência não foi aberta.

A ordem é esta:

1. Abra a ocorrência. A resposta `201` traz o `id` e `status` `ABERTA`. Copie o `id`.
2. Despache com `POST /api/ocorrencias/ID/despachos`, trocando `ID` por esse valor. A resposta na hora é `202` e `status` `EM_DESPACHO`. Isso só diz que a saga começou. Ainda não há ambulância nem leito.
3. No mesmo terminal, chame `consultar ID`. Repita o mesmo comando por alguns segundos, até o `status` sair de `EM_DESPACHO`.

A abertura, no mesmo terminal em que `BASE` e `TOKEN` existem:

```bash
curl -s -X POST "$BASE/api/ocorrencias" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"paciente":{"nome":"Maria Souza"},"local":"Rua das Flores, 120","prioridade":"VERMELHA","tipoLeito":"UTI"}'
```

Esse comando não despacha. O `POST` vai ao gateway na porta 8085. O cabeçalho `Authorization` usa o token deste terminal; sem ele a resposta é `401`. O JSON pede leito de UTI, prioridade vermelha, em `Rua das Flores, 120`.

A resposta esperada traz `"status":"ABERTA"`, `ambulanciaId` e `leitoId` nulos, e um `id` novo. Copie esse `id` para o comando seguinte. Para conferir o token antes: `echo "$TOKEN"`. Um texto começando com `eyJ` confirma o login.

```bash
consultar COLE_AQUI_O_ID_DA_ABERTURA
```

No aceite, o estado final é `DESPACHADA`, com `ambulanciaId` e `leitoId` preenchidos. Sem ambulância ou sem leito na primeira busca, o estado final é `FALHA_DESPACHO`. Para ver o token guardado: `echo "$TOKEN"`. Um texto começando com `eyJ` confirma o login.

## 5. Login, token e validação

MS-Leitos neste bloco pode estar com qualquer resultado. Nenhuma destas chamadas despacha.

`BASE` e `TOKEN` não passam para um terminal novo. Sem `BASE`, o `curl` não tem endereço, não imprime nada e fica esperando. Ctrl+C devolve o prompt. No terminal novo, antes dos comandos desta seção:

```bash
BASE=http://localhost:8085
```

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

Este comando não envia `Authorization`. É o caso de quem chama a rota sem ter feito login.

```bash
curl -s -D - "$BASE/api/leitos/disponiveis?tipoLeito=UTI"
```

Esperado: HTTP `401`, `codigo` `TOKEN_INVALIDO`. A primeira linha da resposta é `HTTP/1.1 401`. O JSON vem em seguida.

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

Esta seção só lê. Não abre ocorrência e não despacha. O resultado `ACEITE` do MS-Leitos entra na seção 7.

Use o mesmo terminal em que `BASE` e `TOKEN` já existem. Num terminal novo, defina os dois de novo. Sem `BASE`, o `curl` fica parado. Sem `TOKEN`, a resposta é `401`.

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

Com a carga inicial, o esperado é:

- Duas ambulâncias, `ABC-1234` e `XYZ-5678`, as duas `DISPONIVEL`.
- UTI devolve só `L-UTI-01`. `L-UTI-02` está `OCUPADO` e não entra na lista.
- Emergência devolve só `L-EMG-01`. Enfermaria devolve só `L-ENF-01`.

Cada especialidade tem a própria lista. UTI, emergência e enfermaria não se misturam.

O cache nasce na primeira leitura. Ninguém grava essas chaves na subida do serviço. A primeira consulta de ambulâncias lê o MySQL e grava `frota:disponiveis`. A primeira consulta de UTI lê o MySQL e grava `leitos:disponiveis:UTI`. Emergência e enfermaria fazem o mesmo, com o tipo no fim da chave.

Repita só a consulta de UTI. A segunda resposta vem do Redis e o JSON é o mesmo. Antes de qualquer consulta, `KEYS` pode voltar vazio. Depois das quatro chamadas acima, as chaves existem:

```bash
docker exec omnileito-redis redis-cli KEYS 'frota:*'
docker exec omnileito-redis redis-cli KEYS 'leitos:*'
```

Se o Redis estiver fechado, a consulta continua em `200`. Frota e leitos ignoram a falha do cache e leem o MySQL. Uma reserva ou uma mudança de ambulância apaga a chave daquela lista. A consulta seguinte reconstrói a lista a partir do MySQL.

## 7. Aceite

O hospital desta demonstração não tem tela. A cada reserva, o MS-Leitos sorteia `ACEITE`, `RECUSA` ou `SILENCIO`. Sem um resultado fixo, o despacho da Maria pode aceitar, recusar ou ficar em silêncio, e esta seção deixa de ser reproduzível.

`ACEITE` significa que a unidade confirma o leito. A ocorrência vai a `DESPACHADA`, a ambulância a `DESPACHADA` e o leito fica `RESERVADO` com `confirmado` verdadeiro. O job de 2 minutos não expira reserva confirmada.

O sorteio lê `omnileito.reserva.resultado` só na subida do processo. O MS-Leitos que já está na porta 8095, se subiu sem argumento, continua sorteando. Encerre só esse processo, com Ctrl+C no terminal dele. Identidade, ocorrências, frota, notificações e o gateway ficam no ar. Se subir outro MS-Leitos antes de encerrar o antigo, a porta 8095 responde `Endereço já em uso`.

No terminal que ficou livre, na pasta do projeto:

```bash
./mvnw -pl ms-leitos spring-boot:run -Dspring-boot.run.arguments=--omnileito.reserva.resultado=ACEITE
```

Espere `Tomcat started on port 8095`. A partir daí, toda reserva deste processo é aceite.

O banco precisa estar na carga inicial: ambulâncias `ABC-1234` e `XYZ-5678` em `DISPONIVEL`, e um leito `LIVRE` por especialidade (`L-UTI-01`, `L-EMG-01`, `L-ENF-01`). Abrir uma ocorrência, sem despachar, não gasta ambulância nem leito. Se a seção 6 ainda mostrou essas duas ambulâncias e o `L-UTI-01`, a carga serve. Se um despacho anterior já reservou a UTI ou segurou uma ambulância, volte à carga pela seção 2 antes de continuar.

### 7.1 Abrir

```bash
curl -s -D - -X POST "$BASE/api/ocorrencias" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"paciente":{"nome":"Maria Souza"},"local":"Rua das Flores, 120","prioridade":"VERMELHA","tipoLeito":"UTI"}'
```

Esperado: HTTP `201`, `status` `ABERTA`, `ambulanciaId` e `leitoId` nulos. Copie o `id`.

### 7.2 Despachar

No mesmo terminal de `BASE` e `TOKEN`, com o MS-Leitos já no ar em `ACEITE`:

```bash
curl -s -D - -X POST "$BASE/api/ocorrencias/COLE_O_ID_DA_ABERTURA/despachos" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{}'
```

Obs.: O `ID` da URL não se digita assim. É o valor do campo `id` copiado da resposta `201` da seção 7.1. É um UUID, no formato `14a15e94-5ef6-4da9-8ebd-6e25bba7cb4e`. Deixar as letras `ID` chama uma ocorrência que não existe e a resposta é `404`, `codigo` `OCORRENCIA_NAO_ENCONTRADA`.

Esperado na hora: HTTP `202`, `status` `EM_DESPACHO`. Isso só diz que a saga começou. Ainda não há ambulância nem leito.

Alguns segundos depois, no mesmo terminal, `consultar` com o mesmo UUID. Não use a palavra `ID`:

```bash
consultar COLE_O_ID_DA_ABERTURA
```

Repita até o `status` sair de `EM_DESPACHO`. O esperado é:

| Campo | Valor |
| --- | --- |
| status | `DESPACHADA` |
| ambulanciaId | `1` |
| leitoId | id de `L-UTI-01` |
| unidadeNome | `Hospital Central` |

No MySQL, a ambulância 1 está `DESPACHADA` com essa ocorrência. `L-UTI-01` está `RESERVADO` e `confirmado` verdadeiro. `L-UTI-02` continua `OCUPADO`.

```bash
curl -s "$BASE/api/notificacoes?ocorrenciaId=COLE_O_ID_DA_ABERTURA" -H "Authorization: Bearer $TOKEN"
```

Esperado: uma notificação `DESPACHO` citando `ABC-1234`, `L-UTI-01` e Hospital Central. O horário pode vir em UTC (`Z`); é o mesmo instante do despacho.

A lista de UTI fica vazia, e a de ambulâncias fica só com `XYZ-5678`. O cache de `leitos:disponiveis:UTI` acompanha a lista vazia.

### 7.3 Segundo despacho da mesma ocorrência

Repita o `POST /despachos` do mesmo id.

Esperado: HTTP `200`, o mesmo `ambulanciaId` e o mesmo `leitoId`. A ambulância 2 continua `DISPONIVEL`.

## 8. Primeira busca sem leito

Ainda com `ACEITE`. A UTI livre já foi reservada na seção 7. A ambulância 2 continua livre.

Abra outra ocorrência, no mesmo terminal de `BASE` e `TOKEN`:

```bash
curl -s -D - -X POST "$BASE/api/ocorrencias" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"paciente":{"nome":"Ana Costa"},"local":"Rua Sem Vaga, 1","prioridade":"VERMELHA","tipoLeito":"UTI"}'
```

Esperado: HTTP `201`, `status` `ABERTA`. Copie o `id` desta resposta. É outro UUID, não o da Maria. Despache com esse id, como na seção 7.2.

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

Volte à carga inicial pela seção 2. Encerre o MS-Leitos que estiver na porta 8095 e suba de novo com `SILENCIO`. Se a porta responder `Endereço já em uso`, o processo anterior ainda está no ar. Ctrl+Z só suspende: o processo continua ocupando a 8095 e um `kill` comum não o encerra. Use Ctrl+C no terminal dele.

```bash
./mvnw -pl ms-leitos spring-boot:run -Dspring-boot.run.arguments=--omnileito.reserva.resultado=SILENCIO
```

Abra uma UTI e despache, como na seção 7. O `202` só diz que a saga começou.

Esperado logo em seguida: ocorrência `DESPACHADA`, ambulância `DESPACHADA`, `L-UTI-01` `RESERVADO` com `confirmado` falso e `confirmado_em` vazio. Há notificação `DESPACHO`. Reserva com aceite não entra no job. Esta não está confirmada, então o prazo de 2 minutos corre.

Espere pouco mais de 2 minutos. A varredura é a cada 15 segundos, então a liberação aparece na consulta seguinte, não no instante em que o relógio faz 2 minutos.

O estado visível volta ao mesmo lugar. A ocorrência segue `DESPACHADA`, a ambulância segue `DESPACHADA` e `L-UTI-01` segue `RESERVADO`. Isso não é falha. No meio do ciclo a ambulância passa por `AGUARDANDO_LEITO` e a ocorrência por `EM_DESPACHO`, mas a nova reserva com `SILENCIO` devolve os dois ao estado anterior antes de uma consulta espaçada pegar essa passagem. `L-UTI-01` é o único `LIVRE` de UTI, então é o leito pedido de novo.

A prova está no MySQL, não na lista de status:

```bash
mysql -h127.0.0.1 -P3310 -uroot -proot123 -e "
SELECT codigo, status, confirmado, confirmado_em, reservado_em
FROM omnileito_leitos.leito WHERE codigo='L-UTI-01';
SELECT ocorrencia_id, leito_id, motivo, ocorrido_em
FROM omnileito_leitos.reserva_recusada;
"
```

`confirmado` fica `0`. `reserva_recusada` ganha uma linha com motivo `EXPIRACAO` para essa ocorrência e o leito 1. O status gravado do leito não fica `EXPIRADO`: depois do commit ele está `LIVRE` por um instante e, na reserva seguinte, `RESERVADO`. Há também notificação `EXPIRACAO`.

Enquanto `resultado=SILENCIO` e não houver outro leito `LIVRE` de UTI, o ciclo se repete a cada pouco mais de 2 minutos: expira, pede o mesmo leito e volta a `DESPACHADA`. Cada volta acrescenta outra linha `EXPIRACAO`. Confirme uma e encerre o MS-Leitos com Ctrl+C para o prazo não continuar girando.

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

Abra `http://localhost:15672`, usuário `guest`, senha `guest`, aba Queues and Streams. São oito filas, todas `classic`, duráveis e `running`. A mensagem só entra na fila depois do commit do serviço que a publicou.

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

Ao final de um despacho estável, cada fila fica com Ready `0`, Unacked `0` e Total `0`. As taxas incoming, deliver e ack em `0.00/s` só dizem que nada está passando agora. O trabalho da saga já foi consumido.

A coluna de consumidores deve mostrar `1` em cada fila cujo serviço está no ar. Se o MS-Leitos estiver parado, como na seção 12, `saga.reservar-leito` fica com `0` consumidores. As outras filas de leito (`saga.leito-reservado`, `saga.leito-expirado`, `saga.retentar-busca-leito`) continuam com o consumidor do MS-Ocorrencias. Voltar o processo da porta 8095 devolve o consumidor de `saga.reservar-leito`.

Ready maior que zero, ou Unacked que não cai, é mensagem presa. Em `notificacao.registrar`, isso indica que o MS-Notificacoes não gravou. A consulta `GET /api/notificacoes?ocorrenciaId=...` tem de devolver o alerta correspondente quando essa fila volta a zero.
