# Relatório Técnico — Containerização e Deploy Cloud Native

## Visão Geral

**Entrega:** Containerização da API REST com Docker e Docker Compose  
**Tecnologias:** Docker Engine, Docker Compose v2, Spring Boot, PostgreSQL 16, Nginx  
**Data:** Março 2025

---

## 1. Decisões de Arquitetura

### 1.1 Multi-stage Build no Dockerfile da API

A escolha de um build multi-stage não foi arbitrária. Sem ela, a imagem da API incluiria o JDK completo, o Gradle, os arquivos de configuração de build e potencialmente o código-fonte — resultando em uma imagem de ~600 MB que vaza informações desnecessárias em produção.

```
Stage builder  →  eclipse-temurin:17-jdk-alpine  (~400 MB)
                  Compila + extrai layers do JAR

Stage runtime  →  eclipse-temurin:17-jre-alpine  (~85 MB)
                  Apenas JRE + classes compiladas + dependências
```

**Imagem final: ~150 MB** — uma redução de 75% com zero perda de funcionalidade.

Além do tamanho, a extração de layers do Spring Boot (`java -Djarmode=layertools`) garante que um rebuild motivado apenas por alteração no código da aplicação **não invalida o layer de dependências** (que raramente mudam). Em um cenário com pushes frequentes, isso reduz drasticamente o tempo de deploy.

### 1.2 Dockerfile Customizado para o PostgreSQL

A imagem oficial `postgres:16-alpine` funciona, mas seus defaults de configuração são conservadores — feitos para rodar em qualquer hardware. Ao criar um Dockerfile customizado para o PostgreSQL, o projeto ganha:

- **Tuning de performance**: `shared_buffers = 512MB`, `work_mem = 16MB`, `effective_cache_size = 1536MB` — calibrados para um servidor com 2 GB de RAM.
- **Autenticação moderna**: `scram-sha-256` no `pg_hba.conf` em vez do `md5` legado.
- **Scripts de init versionados**: `01_extensions.sql` e `02_seed.sh` são executados exatamente uma vez, no primeiro boot do volume — garantindo que todo desenvolvedor parte do mesmo estado de banco.
- **Extensões pré-instaladas**: `uuid-ossp`, `pgcrypto` e `pg_stat_statements` são necessidades comuns em APIs REST modernas.

### 1.3 Três Arquivos Compose para Três Ambientes

Uma única configuração Compose não serve bem a múltiplos ambientes. A abordagem adotada usa composição de arquivos:

| Arquivo | Carregado quando |
|---|---|
| `docker-compose.yml` | Sempre (base) |
| `docker-compose.override.yml` | Automaticamente em desenvolvimento |
| `docker-compose.prod.yml` | Explicitamente em produção: `-f docker-compose.prod.yml` |

Isso elimina variáveis condicionais e flags especiais dentro dos arquivos — cada arquivo expressa apenas as diferenças do seu ambiente.

A diferença mais crítica entre desenvolvimento e produção está no mapeamento de portas:

```yaml
# Em desenvolvimento (docker-compose.override.yml):
postgres:
  ports:
    - "5432:5432"   # Exposto ao host para ferramentas locais

# Em produção (docker-compose.prod.yml):
postgres:
  ports: !reset []  # Nenhuma porta exposta — banco acessível apenas internamente
```

Em produção, o banco de dados está acessível somente pelos containers na mesma rede Docker. Não há como conectar diretamente do host sem `docker exec`.

---

## 2. Gerenciamento de Variáveis de Ambiente

### 2.1 Hierarquia de Configuração

A aplicação segue os [12 Fatores App](https://12factor.net/config) — configuração via ambiente, não código:

```
.env (arquivo local, não versionado)
  ↓ carregado pelo Docker Compose
docker-compose.yml (mapeado para os containers)
  ↓ injetado como variáveis de ambiente
Container (Spring Boot lê via @Value, application.properties, etc.)
```

### 2.2 Validação de Variáveis Obrigatórias

O Docker Compose suporta duas sintaxes para variáveis:

```yaml
# Default silencioso (não causa erro se ausente):
DB_NAME: ${DB_NAME:-appdb}

# Erro explícito se não definida (fail-fast):
DB_PASS: ${DB_PASS:?Erro: DB_PASS não definido no .env}
```

`DB_PASS` usa a sintaxe `:?` — o Compose recusa subir se a variável não estiver definida com valor. Isso evita o pior cenário: API subindo com senha em branco ou com o valor literal `<PREENCHER>`.

### 2.3 YAML Anchors para Reúso

Em vez de repetir as mesmas variáveis em múltiplos serviços, o arquivo usa anchors YAML:

```yaml
x-db-credentials: &db-credentials
  SPRING_DATASOURCE_URL:      jdbc:postgresql://postgres:5432/${DB_NAME:-appdb}
  SPRING_DATASOURCE_USERNAME: ${DB_USER:-appuser}
  SPRING_DATASOURCE_PASSWORD: ${DB_PASS:?...}

services:
  api:
    environment:
      <<: *db-credentials    # Expande o anchor aqui
      SERVER_PORT: "8080"
      # ... outras variáveis específicas da API
```

Se a URL do banco mudar, altera-se em um único lugar.

---

## 3. Rede e Comunicação Entre Serviços

### 3.1 DNS Interno do Docker

Quando containers estão na mesma rede Docker, comunicam-se pelo **nome do serviço** como hostname:

```
API acessa o banco via:  jdbc:postgresql://postgres:5432/appdb
                                           ^^^^^^^^
                                   Nome do serviço no docker-compose.yml
                                   Docker resolve para o IP interno
```

Não existe `localhost` entre containers diferentes. Isso é fundamental: quem vem do desenvolvimento local (onde tudo é `localhost`) precisa entender que no Docker, cada serviço tem seu próprio "localhost" interno.

### 3.2 Sub-rede Dedicada

```yaml
networks:
  backend-net:
    ipam:
      config:
        - subnet: 172.20.0.0/24
```

Definir uma sub-rede explícita evita conflitos com outras redes Docker no host e facilita configuração de firewalls e `pg_hba.conf` (que aceita conexões apenas de `172.20.0.0/24`).

---

## 4. Persistência de Dados

### 4.1 Volumes Nomeados vs. Bind Mounts

| Tipo | Usado para | Por quê |
|---|---|---|
| Volume nomeado `backend-postgres-data` | Dados do PostgreSQL | Persistência gerenciada pelo Docker, portátil entre hosts |
| Volume nomeado `backend-api-logs` | Logs da API | Compartilhado entre API e Nginx (para nginx ler logs de acesso) |
| Bind mount `./config:/app/config:ro` | Arquivos de configuração | Editados no host, refletidos no container sem rebuild |

Volumes nomeados sobrevivem a `docker compose down` — os dados do banco são preservados. Apenas `docker compose down -v` os remove.

### 4.2 Proteção dos Dados em Produção

O script `deploy.sh` no comando `clean` exige confirmação explícita antes de chamar `down -v`:

```bash
warn "ATENÇÃO: apaga todos os dados do banco!"
read -r -p "  Confirmar limpeza? [s/N] " confirm
[[ "${confirm,,}" == "s" ]] || exit 0
```

Em produção real, backups automatizados (como o `backup.sh` da entrega anterior) devem rodar antes de qualquer operação de manutenção.

---

## 5. Healthchecks e Dependências

### 5.1 O Problema do "Depends On" Simples

`depends_on` sem condição apenas garante que o container do banco **iniciou** — não que o PostgreSQL está **pronto para aceitar conexões**. A API subiria, tentaria conectar ao banco ainda em inicialização e falharia.

### 5.2 A Solução: `condition: service_healthy`

```yaml
depends_on:
  postgres:
    condition: service_healthy   # Aguarda o healthcheck do banco passar
```

O PostgreSQL tem um healthcheck configurado:
```yaml
healthcheck:
  test: ["CMD-SHELL", "pg_isready -U appuser -d appdb -h localhost"]
  interval: 10s
  retries: 5
  start_period: 30s
```

`pg_isready` retorna 0 apenas quando o servidor está aceitando conexões. A API só é iniciada depois que este comando retornar 0 ao menos uma vez. O `start_period: 30s` evita falsos negativos durante a inicialização.

---

## 6. Nginx como Reverse Proxy

### 6.1 Por Que Adicionar Nginx

Em desenvolvimento, acessar a API diretamente na porta 8080 é conveniente. Em produção, o Nginx agrega:

- **SSL termination**: gerencia certificados TLS; a API recebe HTTP simples internamente.
- **Rate limiting**: 10 req/s por IP com burst de 20 — proteção básica contra abuso.
- **Compressão gzip**: reduz payload de respostas JSON em ~70%.
- **Headers de segurança**: `X-Frame-Options`, `X-Content-Type-Options`, `server_tokens off`.
- **Logging estruturado (JSON)**: facilita ingestão por ferramentas de observabilidade.

### 6.2 Roteamento

```
Request: GET http://servidor/api/produtos
                              ^^^^
                         Nginx identifica o prefixo /api/

Nginx faz proxy pass para:  http://api:8080/produtos
                                     ^^^
                          Hostname Docker DNS interno
```

O `actuator/health` é exposto publicamente (para load balancers verificarem saúde), mas o restante do `/actuator/` só é acessível de dentro da rede `172.20.0.0/24`.

---

## 7. Preparação para Nuvem

O projeto está alinhado com as principais plataformas:

**Imagens prontas para registry:**
```bash
# Nomeia com prefixo do registry
REGISTRY=123456.dkr.ecr.sa-east-1.amazonaws.com/ docker compose build
docker compose push
```

**Compatível com orquestradores:**
- As imagens resultantes funcionam em ECS, EKS, GKE, Cloud Run e qualquer runtime OCI.
- O `docker-compose.yml` pode ser convertido para Kubernetes com `kompose convert`.

**Labels OCI para rastreabilidade:**
```dockerfile
LABEL org.opencontainers.image.version="${APP_VERSION}"
      org.opencontainers.image.revision="${GIT_COMMIT}"
      org.opencontainers.image.created="${BUILD_DATE}"
```
Permite saber exatamente qual commit gerou cada imagem em produção.

---

## 8. Resumo do que foi entregue

| Artefato | Descrição |
|---|---|
| `api/Dockerfile` | Multi-stage, JRE Alpine, usuário não-root, healthcheck, labels OCI |
| `postgres/Dockerfile` | Baseado em postgres:16-alpine, postgresql.conf customizado, pg_hba.conf com scram-sha-256 |
| `postgres/postgresql.conf` | Tuning de performance para 2 GB RAM, logging de queries lentas |
| `postgres/pg_hba.conf` | Autenticação scram-sha-256, acesso restrito à subnet Docker |
| `postgres/initdb/01_extensions.sql` | Schema, extensões, tabelas, índices, triggers |
| `postgres/initdb/02_seed.sh` | Dados de exemplo para desenvolvimento (ativado por variável) |
| `nginx/nginx.conf` | Rate limiting, gzip, headers de segurança, logging JSON |
| `nginx/conf.d/api.conf` | Proxy pass, restrição do actuator, bloqueio de arquivos sensíveis |
| `docker-compose.yml` | Orquestração principal com volumes, redes, healthchecks e profiles |
| `docker-compose.override.yml` | Convenências de desenvolvimento (auto-carregado) |
| `docker-compose.prod.yml` | Hardening de produção (sem portas expostas, restart: always) |
| `.env.example` | Template documentado de todas as variáveis |
| `scripts/deploy.sh` | Utilitário de operações (dev/staging/prod/logs/status/clean) |
| `README.md` | Documentação completa de uso |

---

*Relatório elaborado para demonstrar conceitos de containerização e deploy Cloud Native com Docker.*
