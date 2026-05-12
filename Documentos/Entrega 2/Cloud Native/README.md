# 🐳 Containerização e Deploy Cloud Native

Projeto de containerização da API REST (Spring Boot) com PostgreSQL, orquestrado via Docker Compose e preparado para deployment em nuvem.

---

## 📁 Estrutura do Projeto

```
cloud-native/
│
├── api/
│   └── Dockerfile                  # Multi-stage: JDK (build) → JRE Alpine (runtime)
│
├── postgres/
│   ├── Dockerfile                  # PostgreSQL customizado com performance tuning
│   ├── postgresql.conf             # Configurações de performance e logging
│   ├── pg_hba.conf                 # Regras de autenticação por host
│   └── initdb/
│       ├── 01_extensions.sql       # Cria extensões, schema e tabelas base
│       └── 02_seed.sh              # Dados de desenvolvimento (opcional)
│
├── nginx/
│   ├── nginx.conf                  # Configuração principal (rate limit, gzip, segurança)
│   └── conf.d/
│       └── api.conf                # Virtual host da API (proxy pass, headers)
│
├── scripts/
│   └── deploy.sh                   # Utilitário de operações (dev/staging/prod)
│
├── config/                         # Arquivos de config montados na API (somente leitura)
├── db/init/                        # Scripts SQL adicionais de inicialização
│
├── docker-compose.yml              # Orquestração principal (todos os ambientes)
├── docker-compose.override.yml     # Sobrescritas para desenvolvimento (auto-carregado)
├── docker-compose.prod.yml         # Sobrescritas para produção
│
├── .env.example                    # Template de variáveis de ambiente
├── .gitignore
└── README.md
```

---

## 🚀 Início Rápido

### 1. Pré-requisitos

| Ferramenta | Versão mínima | Verificar |
|---|---|---|
| Docker Engine | 24.0+ | `docker --version` |
| Docker Compose | v2 (plugin) | `docker compose version` |
| Git | qualquer | `git --version` |

### 2. Setup inicial

```bash
# Clone e entre no diretório
git clone https://github.com/org/backend.git
cd backend/cloud-native

# Crie o arquivo de variáveis de ambiente
cp .env.example .env

# Edite e preencha os valores obrigatórios
nano .env          # ou code .env, vim .env, etc.

# Torne o utilitário executável
chmod +x scripts/deploy.sh
```

### 3. Subir em desenvolvimento

```bash
./scripts/deploy.sh dev
```

Isso sobe:
- ✅ **API REST** → http://localhost:8080
- ✅ **PostgreSQL** → localhost:5432
- ✅ **Adminer** → http://localhost:8888 (gerenciador visual do banco)

### 4. Verificar saúde

```bash
# Status geral dos containers
./scripts/deploy.sh status

# Health check da API (Spring Boot Actuator)
curl http://localhost:8080/actuator/health | python3 -m json.tool

# Logs em tempo real
./scripts/deploy.sh logs api
```

---

## 🗂 Arquivos de Configuração

---

### `api/Dockerfile` — Imagem da API REST

**Estratégia: Multi-stage build**

```
Stage 1 (builder)  →  eclipse-temurin:17-jdk-alpine (~400 MB)
                       ↳ Compila o projeto com Gradle
                       ↳ Extrai layers do JAR Spring Boot

Stage 2 (runtime)  →  eclipse-temurin:17-jre-alpine (~85 MB)
                       ↳ Apenas JRE + código compilado
                       ↳ Sem JDK, sem Gradle, sem código-fonte
```

**Tamanho da imagem final: ~150 MB** (vs ~600 MB sem multi-stage)

**Recursos de segurança:**
- Usuário não-root `appuser` (sem privilégios de sistema)
- Labels OCI com rastreabilidade de versão e commit
- Timezone `America/Sao_Paulo` configurada

**Healthcheck configurado:**
```dockerfile
HEALTHCHECK --interval=30s --timeout=10s --start-period=90s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health | grep '"status":"UP"'
```

**Build manual:**
```bash
docker build \
  --build-arg APP_VERSION=1.0.0 \
  --build-arg GIT_COMMIT=$(git rev-parse --short HEAD) \
  -t backend-api:1.0.0 \
  ./api
```

---

### `postgres/Dockerfile` — Imagem do Banco de Dados

Imagem customizada baseada em `postgres:16-alpine` com:

- **`postgresql.conf`** — tuning de performance (shared_buffers, work_mem, checkpoint)
- **`pg_hba.conf`** — autenticação `scram-sha-256` (mais segura que md5)
- **Scripts de init** — executados uma vez no primeiro boot do volume

**Sequência de inicialização:**
```
Volume vazio detectado
    ↓
01_extensions.sql → cria extensões uuid-ossp, pg_stat_statements, pgcrypto
                    → cria schema "app", tabelas users e products, índices
    ↓
02_seed.sh        → insere dados de exemplo (se POSTGRES_SEED_DATA=true)
```

**Build manual:**
```bash
docker build -t backend-postgres:1.0.0 ./postgres
```

---

### `docker-compose.yml` — Orquestração Principal

Define 4 serviços organizados em perfis (profiles):

| Serviço | Porta | Perfil | Descrição |
|---|---|---|---|
| `api` | 8080 | *(padrão)* | API REST Spring Boot |
| `postgres` | 5432 | *(padrão)* | Banco de dados |
| `nginx` | 80/443 | `proxy` | Reverse proxy |
| `adminer` | 8888 | `tools` | Gerenciador visual do banco |

**Volumes nomeados:**

| Volume | Montado em | Propósito |
|---|---|---|
| `backend-postgres-data` | `/var/lib/postgresql/data` | Dados do banco (persistente) |
| `backend-api-logs` | `/app/logs` | Logs da API |

**Rede interna:**
```
Subnet: 172.20.0.0/24
Driver: bridge

Resolução DNS interna:
  api       → 172.20.0.x
  postgres  → 172.20.0.y
  nginx     → 172.20.0.z
  adminer   → 172.20.0.w
```

**Healthchecks:**
- API: `curl -f http://localhost:8080/actuator/health`
- PostgreSQL: `pg_isready -U appuser -d appdb`
- Nginx: `curl -f http://localhost/nginx-health`

`depends_on` com `condition: service_healthy` garante que a API só sobe **depois** que o banco estiver aceitando conexões.

---

### Variáveis de Ambiente

Todas as configurações são externalizadas via `.env`. Nenhuma credencial está hard-coded nos arquivos.

**Variáveis obrigatórias** (causam erro se não definidas):

| Variável | Descrição |
|---|---|
| `DB_PASS` | Senha do PostgreSQL |

**Variáveis com default** (opcionais):

| Variável | Default | Descrição |
|---|---|---|
| `APP_VERSION` | `1.0.0` | Versão semântica |
| `SPRING_PROFILE` | `development` | Profile Spring Boot |
| `DB_NAME` | `appdb` | Nome do banco |
| `DB_USER` | `appuser` | Usuário do banco |
| `API_PORT` | `8080` | Porta da API no host |
| `POSTGRES_HOST_PORT` | `5432` | Porta do Postgres no host |
| `DDL_AUTO` | `update` | Comportamento do schema Hibernate |
| `JAVA_OPTS` | `-Xms256m -Xmx512m ...` | Opções JVM |
| `TZ` | `America/Sao_Paulo` | Timezone dos containers |
| `POSTGRES_SEED_DATA` | `false` | Inserir dados de exemplo |

---

## 🌍 Ambientes

### Desenvolvimento

```bash
./scripts/deploy.sh dev
# ou
docker compose --profile tools up -d
```

- `docker-compose.yml` + `docker-compose.override.yml` (carregado automaticamente)
- Seed de dados habilitado
- Logs de SQL e debug ativos
- Adminer disponível
- Porta do banco exposta ao host para ferramentas locais (DBeaver, TablePlus)

### Staging

```bash
./scripts/deploy.sh staging
# ou
docker compose --profile proxy up -d
```

- Apenas `docker-compose.yml`
- Nginx como reverse proxy
- Sem Adminer, sem ferramentas de debug

### Produção

```bash
./scripts/deploy.sh prod
# ou
docker compose -f docker-compose.yml -f docker-compose.prod.yml --profile proxy up -d
```

Diferenças em relação ao dev:
- ✅ Porta do banco **não** exposta ao host
- ✅ Porta da API **não** exposta diretamente (acesso via Nginx)
- ✅ Sem seed de dados
- ✅ Logging em JSON para ingestão por ELK/Loki
- ✅ Restart policy `always`
- ✅ `DDL_AUTO=validate` (Hibernate nunca altera schema em produção)

---

## 🛠 Comandos Úteis

```bash
# ── Deploy ────────────────────────────────────────────────────────────────
./scripts/deploy.sh dev           # Desenvolvimento
./scripts/deploy.sh staging       # Staging
./scripts/deploy.sh prod          # Produção (com confirmação)
./scripts/deploy.sh down          # Para tudo
./scripts/deploy.sh build         # Reconstrói imagens

# ── Monitoramento ─────────────────────────────────────────────────────────
./scripts/deploy.sh status        # Status, saúde e recursos
./scripts/deploy.sh logs          # Logs de todos os serviços
./scripts/deploy.sh logs api      # Logs apenas da API
./scripts/deploy.sh logs postgres # Logs do banco

# ── Debug ─────────────────────────────────────────────────────────────────
./scripts/deploy.sh db-shell      # psql interativo no banco
./scripts/deploy.sh api-shell     # Shell no container da API

# ── Manutenção ────────────────────────────────────────────────────────────
./scripts/deploy.sh clean         # Remove tudo (APAGA volumes!)

# ── Docker direto ─────────────────────────────────────────────────────────
docker compose ps                              # Lista containers
docker compose exec api env                    # Variáveis do container
docker compose exec postgres pg_isready        # Verifica banco
docker stats --no-stream                       # Uso de recursos
docker compose logs -f --tail=50 api           # Últimas 50 linhas + follow
```

---

## ☁️ Preparação para Nuvem

O projeto está preparado para deploy em qualquer plataforma Cloud Native:

### AWS (ECS / EKS)
```bash
# Autentica no ECR
aws ecr get-login-password | docker login --username AWS --password-stdin <account>.dkr.ecr.us-east-1.amazonaws.com

# Tag e push das imagens
REGISTRY=<account>.dkr.ecr.us-east-1.amazonaws.com/ APP_VERSION=1.0.0 docker compose build
docker compose push
```

### Google Cloud (Cloud Run / GKE)
```bash
REGISTRY=gcr.io/meu-projeto/ docker compose build
docker compose push
```

### Deploy via pipeline CI/CD (GitHub Actions / GitLab CI)
```yaml
# Exemplo de step de build
- name: Build e push imagens
  env:
    APP_VERSION: ${{ github.sha }}
    BUILD_DATE: ${{ steps.date.outputs.date }}
    GIT_COMMIT: ${{ github.sha }}
  run: |
    docker compose build --parallel
    docker compose push
```

---

## 🔐 Segurança

| Prática | Implementação |
|---|---|
| Sem credenciais no código | Tudo via `.env` (fora do Git) |
| Usuário não-root nos containers | `USER appuser` no Dockerfile |
| Porta do banco não exposta em prod | `docker-compose.prod.yml` remove o port mapping |
| Autenticação forte no Postgres | `scram-sha-256` no `pg_hba.conf` |
| Headers de segurança HTTP | `X-Frame-Options`, `X-Content-Type-Options` no Nginx |
| Rate limiting | 10 req/s por IP no Nginx |
| Imagem mínima (Alpine) | Superfície de ataque reduzida |
| Labels OCI | Rastreabilidade de versão e commit |

---

## 🆘 Troubleshooting

**Container da API em restart loop:**
```bash
docker compose logs api | tail -50
# Verifique conexão com o banco:
docker compose exec api curl -s http://localhost:8080/actuator/health
```

**Banco de dados não aceita conexão:**
```bash
docker compose exec postgres pg_isready -U appuser -d appdb
# Verifique variáveis:
docker compose exec postgres env | grep POSTGRES
```

**Porta já em uso:**
```bash
# Descubra o processo:
sudo ss -tlnp | grep :8080
# Altere no .env:
API_PORT=8090
```

**Limpar tudo e começar do zero:**
```bash
./scripts/deploy.sh clean
# OU manualmente:
docker compose down -v --remove-orphans
docker system prune -f
```

---

## 📝 Licença

MIT — use, modifique e distribua livremente.
