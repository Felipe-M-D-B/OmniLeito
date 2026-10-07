# 🏥 OmniLeito - Sistema de Gerenciamento de Leitos e Despacho Hospitalar

O **OmniLeito** é uma solução **Spring Boot** com **Java 21** para a orquestração do despacho de ambulâncias e reserva de leitos hospitalares utilizando o padrão **Saga (Orquestrado)**, mensageria assíncrona com **RabbitMQ** e persistência relacional via **MySQL** com controle de migrações gerenciado pelo **Flyway**.

---

## 🚀 Pré-requisitos

* **Java 21 (JDK)**
* **Maven 3.8+**
* **MySQL Server** (rodando localmente na porta `3306`)
* **Docker Engine / Docker Desktop** (para subir o container do RabbitMQ)
* **Git**
* **IDE de sua preferência** (no caso de quem está escrevendo esse README, IntelliJ IDEA. Outras recomendações: VS Code ou Eclipse)

---

## 🛠️ Configuração da Infraestrutura Local

### 1. Criar o Banco de Dados no MySQL
Abra o terminal do MySQL ou seu cliente de banco (DBeaver, outra recomendação: MySQL Workbench) e crie a base de dados:

```sql
CREATE DATABASE omnileito;
```

> **Obs:** Não é necessário criar tabelas ou popular dados manualmente. Ao iniciar o projeto Java Spring Boot, o **Flyway** executa automaticamente os scripts de migração presentes em `src/main/resources/db/migration/V1__init_schema.sql` para criar o schema e a carga inicial de testes.

### 2. Subir o Container do RabbitMQ via Docker
Execute o comando abaixo no terminal para subir a imagem oficial do **RabbitMQ** com a interface gráfica de gerenciamento ativa:

```bash
docker run -d --name rabbitmq -p 5672:5672 -p 15672:15672 rabbitmq:3-management
```
> **Obs:** O RabbitMQ é executado diretamenta na máquina, assim como o MySQL, por exemplo. Portanto, deve ser configurado externamente ao projeto Java SpringBoot.
> 
> A configuração é fácil podendo ser feita nativamente direto em seu sistema ou através do **DOCKER**. Esta última possibilidade é a minha sugestão.


* **Porta AMQP (Spring Boot):** `5672`
* **Painel Web de Gerenciamento:** `http://localhost:15672`
* **Credenciais Padrão:** Usuário `guest` / Senha `guest`

---

## 💻 Como Clonar e Executar o Projeto

### 1. Clonar o Repositório
```bash
git clone [https://github.com/SEU-USUARIO/OmniLeito.git](https://github.com/SEU-USUARIO/OmniLeito.git)
cd OmniLeito
```

### 2. Verificar o Arquivo **application.properties**
Confira se o arquivo src/main/resources/application.properties está alinhado com as credenciais do seu MySQL local:

```properties
spring.application.name=OmniLeito

# Porta do Servidor Spring Boot
server.port=8084

# Configuração de Conexão com o MySQL
spring.datasource.url=jdbc:mysql://localhost:3306/omnileito
#username e password podem variar de máquina para máquina
spring.datasource.username=root 
spring.datasource.password=root123
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver

# Configuração do Flyway
spring.flyway.enabled=true
spring.flyway.baseline-on-migrate=true
spring.flyway.locations=classpath:db/migration

# JPA / Hibernate
spring.jpa.hibernate.ddl-auto=none
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true

# Swagger UI / OpenAPI Customizado
springdoc.api-docs.path=/api-docs
springdoc.swagger-ui.path=/swagger-ui-entity.html
springdoc.swagger-ui.operationsSorter=method
```[cite: 1]

### 3. Executar a Aplicação

**Via Terminal (Maven Wrapper):**
```bash
./mvnw clean spring-boot:run
```

### 3. Via IDE (IntelliJ IDEA):

Localize a classe principal com.unesp.omnileito.OmnileitoApplication e clique em Run

## 🧪 Como Testar a Aplicação

### 1. Acessar a Documentação Interativa (Swagger UI)
Com o Spring Boot rodando na porta `8084`, acesse no seu navegador:

👉 **`http://localhost:8084/swagger-ui-entity.html`**

### 2. Disparar uma Ocorrência (Endpoint REST)
No controller **Despachos**, execute a requisição `POST /api/despachos` enviando o seguinte JSON no corpo da requisição:

```json
{
  "ocorrenciaId": "OC-1001",
  "tipoLeitoNecessario": "UTI"
}
```
### 3. Validar a Persistência no Banco de Dados
Abra o DBeaver ou seu cliente MySQL e consulte a base omnileito para validar as atualizações de estado em tempo real:

```SQL
-- 1. Verifica se a Ocorrência foi salva com status DESPACHADA e com os IDs alocados
SELECT * FROM omnileito.ocorrencias WHERE id = 'OC-1001';

-- 2. Verifica se uma Ambulância mudou do status DISPONIVEL para ALOCADA
SELECT * FROM omnileito.ambulancias;

-- 3. Verifica se um Leito do tipo UTI mudou do status LIVRE para RESERVADO
SELECT * FROM omnileito.leitos WHERE tipo_especialidade = 'UTI';
```

## 📂 Estrutura do Projeto
A aplicação está organizada no pacote base com.unesp.omnileito:

```
com.unesp.omnileito
├── configuration/     # Declaração das Exchanges, Queues, Bindings e Conversores JSON
├── controller/        # Endpoints REST expostos pela aplicação (DespachoController)
├── domain/            # Entidades JPA (Ocorrencia, Ambulancia, Leito)
│    └── enums/        # Status do negócio (StatusOcorrencia, StatusAmbulancia, StatusLeito, StatusSaga)
├── dto/               # Objetos de Transferência de Dados (DTOs de requisição e eventos)
├── listener/          # Consumidores assíncronos das filas RabbitMQ (@RabbitListener)
├── repository/        # Repositórios Spring Data JPA (Interfaces MySQL)
└── service/           # Orquestrador da Saga e regras de compensação
```

## 🔄 Funcionamento do Padrão Saga (Orquestrado)
Abaixo o fluxo do Padrão Saga para quem tiver curiosidade de saber como é a mágica por trás: 

```
[POST /api/despachos]
        │
        ▼
 (SagaOrchestratorService) ──► Publica evento na exchange `saga.exchange`
                                         │
                                         ▼
                             [saga.alocar-ambulancia.queue]
                                         │
                                         ▼
 (SagaEventListener) ────────► Busca e aloca Ambulância DISPONIVEL no MySQL
        │
        ├─► (Sucesso) ───────► Envia para [saga.reservar-leito.queue] ──► Reserva Leito LIVRE ──► Finaliza como DESPACHADA
        │
        └─► (Falha Leito) ───► Dispara fila de compensação [saga.liberar-ambulancia.queue] ──► Devolve Ambulância para DISPONIVEL
```