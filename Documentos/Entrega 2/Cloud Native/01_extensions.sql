-- =============================================================================
-- postgres/initdb/01_extensions.sql
-- Executado automaticamente na primeira inicialização do banco.
-- Cria extensões necessárias e configura o schema base da aplicação.
-- =============================================================================

-- ── Extensões ─────────────────────────────────────────────────────────────────
-- uuid-ossp: geração de UUIDs v4 (usado como PK nas entidades)
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- pg_stat_statements: rastreamento de queries para análise de performance
CREATE EXTENSION IF NOT EXISTS pg_stat_statements;

-- pgcrypto: funções de hash e criptografia (bcrypt para senhas)
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ── Schema da aplicação ───────────────────────────────────────────────────────
-- Isola as tabelas da aplicação do schema public (boa prática)
CREATE SCHEMA IF NOT EXISTS app AUTHORIZATION appuser;

-- Garante que o appuser possa criar objetos no schema app
GRANT ALL PRIVILEGES ON SCHEMA app TO appuser;
ALTER USER appuser SET search_path TO app, public;

-- ── Tabela de exemplo: usuários ───────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS app.users (
    id            UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name          VARCHAR(255) NOT NULL,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    active        BOOLEAN NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ── Tabela de exemplo: produtos ───────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS app.products (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    price       NUMERIC(10, 2) NOT NULL CHECK (price >= 0),
    stock       INTEGER NOT NULL DEFAULT 0 CHECK (stock >= 0),
    active      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ── Índices ───────────────────────────────────────────────────────────────────
CREATE INDEX IF NOT EXISTS idx_users_email   ON app.users (email);
CREATE INDEX IF NOT EXISTS idx_users_active  ON app.users (active) WHERE active = TRUE;
CREATE INDEX IF NOT EXISTS idx_products_name ON app.products (name);

-- ── Trigger: updated_at automático ───────────────────────────────────────────
CREATE OR REPLACE FUNCTION app.set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE TRIGGER trg_users_updated_at
    BEFORE UPDATE ON app.users
    FOR EACH ROW EXECUTE FUNCTION app.set_updated_at();

CREATE OR REPLACE TRIGGER trg_products_updated_at
    BEFORE UPDATE ON app.products
    FOR EACH ROW EXECUTE FUNCTION app.set_updated_at();

\echo '✔ Schema app, extensões e tabelas base criados com sucesso.'
