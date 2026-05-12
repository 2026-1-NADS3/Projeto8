# =============================================================================
# postgres/Dockerfile
# Imagem customizada do PostgreSQL 16 (Alpine)
#
# Por que customizar em vez de usar a imagem oficial diretamente?
#   - Permite embutir configurações de performance (postgresql.conf)
#   - Inclui extensões extras (uuid-ossp, pg_stat_statements)
#   - Aplica hardening de segurança (remoção de pacotes desnecessários)
#   - Scripts de inicialização versionados junto ao código
#
# Build:  docker build -t backend-postgres:1.0 ./postgres
# A imagem também pode ser usada diretamente pelo docker-compose.yml
# =============================================================================

FROM postgres:16-alpine

# ── Metadados ─────────────────────────────────────────────────────────────────
LABEL org.opencontainers.image.title="backend-postgres" \
      org.opencontainers.image.description="PostgreSQL 16 customizado — Cloud Native" \
      maintainer="devteam@example.com"

# ── ARGs para configurações de build ─────────────────────────────────────────
ARG POSTGRES_LOCALE=pt_BR.UTF-8
ARG TZ=America/Sao_Paulo

# ── Timezone ──────────────────────────────────────────────────────────────────
RUN apk add --no-cache tzdata && \
    cp /usr/share/zoneinfo/${TZ} /etc/localtime && \
    echo "${TZ}" > /etc/timezone && \
    apk del tzdata && \
    rm -rf /var/cache/apk/*

# ── Configuração de performance do PostgreSQL ─────────────────────────────────
# Este arquivo é copiado para dentro do container e sobrescreve os defaults.
# O PostgreSQL carrega configs customizadas de /etc/postgresql/conf.d/
COPY postgresql.conf     /etc/postgresql/postgresql.conf
COPY pg_hba.conf         /etc/postgresql/pg_hba.conf

# ── Scripts de inicialização ──────────────────────────────────────────────────
# Tudo em /docker-entrypoint-initdb.d/ é executado automaticamente
# na PRIMEIRA inicialização do banco (quando o volume está vazio).
# Ordem de execução: alfabética — por isso os nomes começam com números.
COPY initdb/             /docker-entrypoint-initdb.d/

# Permissões corretas para scripts de init
RUN chmod 755 /docker-entrypoint-initdb.d/*.sh 2>/dev/null || true && \
    chmod 644 /docker-entrypoint-initdb.d/*.sql 2>/dev/null || true

# ── Variáveis de ambiente com defaults ────────────────────────────────────────
# Sobrescritas pelo docker-compose.yml via arquivo .env
ENV POSTGRES_DB=appdb \
    POSTGRES_USER=appuser \
    PGDATA=/var/lib/postgresql/data/pgdata \
    TZ=America/Sao_Paulo

# ── Porta exposta ─────────────────────────────────────────────────────────────
EXPOSE 5432

# ── Override do comando para usar config customizada ──────────────────────────
# -c config_file aponta para nossa configuração personalizada
CMD ["postgres", \
     "-c", "config_file=/etc/postgresql/postgresql.conf", \
     "-c", "hba_file=/etc/postgresql/pg_hba.conf"]
