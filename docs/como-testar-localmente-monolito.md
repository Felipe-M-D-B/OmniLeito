# Como testar localmente o monólito legado

> Este roteiro é específico para o monólito antigo e não representa o procedimento recomendado para a arquitetura atual baseada em microsserviços e Docker Compose. Para o sistema atual, use [`docs/roteiro-de-testes.md`](./roteiro-de-testes.md).

Leitura de 13/10/2026. Vale para a Saga monolítica que estava em `develop`. Não sobe cache, gateway nem job.

O README daquele monolito pede MySQL em `localhost:3306` e a aplicação na porta `8084`. O teste abaixo usou MySQL na porta 3310 e a aplicação na porta 8085.

## 1. Subir só a infraestrutura do OmniLeito

MySQL novo, na porta 3310, com a base `omnileito` já criada:

```bash
docker run -d --name omnileito-mysql \
  -e MYSQL_ROOT_PASSWORD=root123 \
  -e MYSQL_DATABASE=omnileito \
  -p 3310:3306 \
  mysql:8.4
```

RabbitMQ novo. As portas 5672 e 15672 estão livres:

```bash
docker run -d --name omnileito-rabbitmq \
  -p 5672:5672 -p 15672:15672 \
  rabbitmq:3-management
```

Conferir, sem listar ação sobre os outros containers além de olhar o nome:

```bash
docker ps --filter name=omnileito --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'
```

Esperar o MySQL ficar saudável (alguns segundos) antes de subir o Java.

O `application.properties` do Git continua apontando para a porta 3306. Não editar esse arquivo para a máquina local: o remoto é compartilhado com o grupo. A porta certa entra só na hora de executar, pelas variáveis abaixo.

## 2. Subir a aplicação numa porta livre

Na pasta do projeto:

```bash
cd /home/leandro_alves/Documentos/GitHub/OmniLeito

SERVER_PORT=8085 \
SPRING_DATASOURCE_URL='jdbc:mysql://localhost:3310/omnileito?allowPublicKeyRetrieval=true&useSSL=false' \
SPRING_DATASOURCE_USERNAME=root \
SPRING_DATASOURCE_PASSWORD=root123 \
./mvnw spring-boot:run
```

O Flyway aplica `V1__init_schema.sql` na primeira subida: tabelas `ocorrencias`, `ambulancias`, `leitos`, duas ambulâncias `DISPONIVEL` e dois leitos `LIVRE` (UTI e GERAL).

Swagger: http://localhost:8085/swagger-ui-entity.html

Painel do RabbitMQ: http://localhost:15672 (usuário `guest`, senha `guest`).

Se a aplicação falhar ao conectar no MySQL, o container ainda está inicializando. Esperar e rodar o mesmo comando de novo.

## 3. Disparar a Saga

Há duas ambulâncias e dois leitos livres. As duas primeiras chamadas devem terminar em sucesso. A terceira não encontra ambulância e a ocorrência fica em falha, sem reservar leito.

```bash
curl -s -X POST http://localhost:8085/api/despachos \
  -H 'Content-Type: application/json' \
  -d '{"ocorrenciaId":"OC-1001","tipoLeitoNecessario":"UTI"}'

curl -s -X POST http://localhost:8085/api/despachos \
  -H 'Content-Type: application/json' \
  -d '{"ocorrenciaId":"OC-1002","tipoLeitoNecessario":"GERAL"}'

curl -s -X POST http://localhost:8085/api/despachos \
  -H 'Content-Type: application/json' \
  -d '{"ocorrenciaId":"OC-1003","tipoLeitoNecessario":"UTI"}'
```

Cada resposta esperada é o texto `Saga de despacho iniciada para ocorrência: ...`. O estado final aparece no banco, não nesse texto. A Saga é assíncrona: consultar o MySQL um ou dois segundos depois do `curl`.

## 4. Ver o resultado

```bash
mysql -h 127.0.0.1 -P 3310 -uroot -proot123 omnileito -e \
"SELECT id, status, tipo_leito_necessario, ambulancia_alocada_id, leito_reservado_id FROM ocorrencias;
SELECT id, placa, status FROM ambulancias;
SELECT id, codigo, tipo_especialidade, status FROM leitos;"
```

Leitura esperada do que o Felipe entregou:

- `OC-1001` e `OC-1002` com status `DESPACHADA`, cada uma com uma ambulância e um leito.
- As duas ambulâncias em `ALOCADA`.
- Os dois leitos em `RESERVADO`.
- `OC-1003` com status `FALHA_DESPACHO`, sem ambulância e sem leito.

O tipo pedido (`UTI` ou `GERAL`) ainda não filtra o leito. A reserva pega o primeiro `LIVRE`.

Para ver a compensação da ambulância, com os dois leitos já `RESERVADO`, liberar uma ambulância direto no banco e despachar de novo:

```bash
mysql -h 127.0.0.1 -P 3310 -uroot -proot123 omnileito -e \
"UPDATE ambulancias SET status = 'DISPONIVEL' WHERE id = 1;"

curl -s -X POST http://localhost:8085/api/despachos \
  -H 'Content-Type: application/json' \
  -d '{"ocorrenciaId":"OC-1004","tipoLeitoNecessario":"UTI"}'
```

Como não há leito `LIVRE`, a ocorrência `OC-1004` fica `FALHA_DESPACHO` e a ambulância 1 volta para `DISPONIVEL`.

## 5. Encerrar só o que for do OmniLeito

Parar o Java com Ctrl+C no terminal do Maven.

```bash
docker stop omnileito-mysql omnileito-rabbitmq
```

Para subir outra vez, `docker start omnileito-mysql omnileito-rabbitmq`. Não apagar o volume se quiser conservar as ocorrências de teste.
