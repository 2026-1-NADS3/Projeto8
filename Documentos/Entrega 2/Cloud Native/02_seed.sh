#!/usr/bin/env bash
# =============================================================================
# postgres/initdb/02_seed.sh
# Insere dados de seed apenas em ambiente de desenvolvimento.
# A variável POSTGRES_SEED_DATA controla se o seed deve ser executado.
# =============================================================================

set -euo pipefail

# Só executa seed se a variável estiver habilitada
if [[ "${POSTGRES_SEED_DATA:-false}" != "true" ]]; then
    echo "ℹ  POSTGRES_SEED_DATA != true — seed ignorado (ambiente: ${SPRING_PROFILES_ACTIVE:-unknown})"
    exit 0
fi

echo "▶  Inserindo dados de seed para desenvolvimento..."

psql -v ON_ERROR_STOP=1 --username "${POSTGRES_USER}" --dbname "${POSTGRES_DB}" <<-EOSQL

    -- Usuário admin de exemplo (senha: Admin@123 via bcrypt)
    INSERT INTO app.users (name, email, password_hash) VALUES
        ('Administrador',  'admin@example.com',  crypt('Admin@123',  gen_salt('bf', 12))),
        ('Usuário Dev',    'dev@example.com',    crypt('Dev@123',    gen_salt('bf', 12))),
        ('Usuário Teste',  'teste@example.com',  crypt('Teste@123',  gen_salt('bf', 12)))
    ON CONFLICT (email) DO NOTHING;

    -- Produtos de exemplo
    INSERT INTO app.products (name, description, price, stock) VALUES
        ('Produto Alpha',  'Descrição do produto Alpha',  49.90,  100),
        ('Produto Beta',   'Descrição do produto Beta',   99.90,   50),
        ('Produto Gamma',  'Descrição do produto Gamma', 199.90,   25),
        ('Produto Delta',  'Descrição do produto Delta',  29.90,  200)
    ON CONFLICT DO NOTHING;

EOSQL

echo "✔  Seed concluído: $(psql -tAc "SELECT COUNT(*) FROM app.users" --username "${POSTGRES_USER}" --dbname "${POSTGRES_DB}") usuários, $(psql -tAc "SELECT COUNT(*) FROM app.products" --username "${POSTGRES_USER}" --dbname "${POSTGRES_DB}") produtos inseridos."
