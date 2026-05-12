#!/usr/bin/env bash
# =============================================================================
# scripts/deploy.sh
# Utilitário de operações Docker Compose para o ciclo de desenvolvimento
#
# Uso:
#   ./scripts/deploy.sh dev          # Sobe ambiente de desenvolvimento completo
#   ./scripts/deploy.sh staging      # Sobe ambiente de staging (com Nginx)
#   ./scripts/deploy.sh prod         # Sobe ambiente de produção
#   ./scripts/deploy.sh down         # Para todos os containers
#   ./scripts/deploy.sh build        # Reconstrói as imagens
#   ./scripts/deploy.sh logs [svc]   # Exibe logs (ou de serviço específico)
#   ./scripts/deploy.sh status       # Status e saúde dos containers
#   ./scripts/deploy.sh clean        # Remove containers, redes e volumes
#   ./scripts/deploy.sh db-shell     # Acessa psql dentro do container
#   ./scripts/deploy.sh api-shell    # Shell dentro do container da API
# =============================================================================

set -euo pipefail

# Cores
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
CYAN='\033[0;36m'; BOLD='\033[1m'; RESET='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "${SCRIPT_DIR}")"

log()  { echo -e "${GREEN}[✔]${RESET} $*"; }
warn() { echo -e "${YELLOW}[⚠]${RESET} $*"; }
die()  { echo -e "${RED}[✘]${RESET} $*"; exit 1; }
step() { echo -e "\n${CYAN}${BOLD}▶ $*${RESET}"; }

# Verifica se o .env existe
check_env() {
    if [[ ! -f "${PROJECT_DIR}/.env" ]]; then
        warn "Arquivo .env não encontrado!"
        echo "  Execute: cp .env.example .env && nano .env"
        exit 1
    fi
    # Verifica variáveis obrigatórias
    local required_vars=("DB_PASS" "DB_NAME" "DB_USER")
    local missing=()
    for var in "${required_vars[@]}"; do
        grep -q "^${var}=.\+" "${PROJECT_DIR}/.env" 2>/dev/null || missing+=("${var}")
    done
    if [[ "${#missing[@]}" -gt 0 ]]; then
        die "Variáveis obrigatórias não definidas no .env: ${missing[*]}"
    fi
}

# Composição de arquivos por ambiente
compose_files() {
    local env="${1:-dev}"
    case "${env}" in
        dev|development)
            echo "-f docker-compose.yml -f docker-compose.override.yml"
            ;;
        staging)
            echo "-f docker-compose.yml"
            ;;
        prod|production)
            echo "-f docker-compose.yml -f docker-compose.prod.yml"
            ;;
        *)
            die "Ambiente desconhecido: ${env}"
            ;;
    esac
}

# Adiciona informações de build ao .env temporariamente
inject_build_info() {
    export BUILD_DATE; BUILD_DATE=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
    export GIT_COMMIT; GIT_COMMIT=$(git -C "${PROJECT_DIR}" rev-parse --short HEAD 2>/dev/null || echo "unknown")
    log "Build: ${GIT_COMMIT} @ ${BUILD_DATE}"
}

cmd_dev() {
    step "Subindo ambiente de desenvolvimento"
    inject_build_info
    docker compose \
        $(compose_files dev) \
        --profile tools \
        up -d --build
    echo
    log "API:      http://localhost:${API_PORT:-8080}"
    log "Adminer:  http://localhost:${ADMINER_PORT:-8888}  (server: postgres)"
    log "Logs:     ./scripts/deploy.sh logs"
}

cmd_staging() {
    step "Subindo ambiente de staging (com Nginx)"
    inject_build_info
    docker compose \
        $(compose_files staging) \
        --profile proxy \
        up -d --build
    echo
    log "API (via Nginx): http://localhost:${HTTP_PORT:-80}/api/"
    log "Health: http://localhost:${HTTP_PORT:-80}/actuator/health"
}

cmd_prod() {
    step "Subindo ambiente de produção"
    warn "Certifique-se de que o .env contém as credenciais de produção!"
    read -r -p "  Confirmar deploy em produção? [s/N] " confirm
    [[ "${confirm,,}" == "s" ]] || { warn "Deploy cancelado."; exit 0; }

    inject_build_info
    docker compose \
        $(compose_files prod) \
        --profile proxy \
        up -d --build
    log "Deploy de produção concluído."
}

cmd_down() {
    step "Parando todos os containers"
    docker compose \
        -f docker-compose.yml \
        -f docker-compose.override.yml \
        -f docker-compose.prod.yml \
        down 2>/dev/null || docker compose down
    log "Containers parados."
}

cmd_build() {
    step "Reconstruindo imagens"
    inject_build_info
    docker compose build --no-cache --parallel
    log "Build concluído."
    docker images | grep "backend-" || true
}

cmd_logs() {
    local service="${1:-}"
    if [[ -n "${service}" ]]; then
        docker compose logs -f --tail=100 "${service}"
    else
        docker compose logs -f --tail=50
    fi
}

cmd_status() {
    step "Status dos containers"
    docker compose ps --format "table {{.Name}}\t{{.Status}}\t{{.Ports}}"
    echo
    step "Saúde dos serviços"

    local services=("backend-api" "backend-postgres" "backend-nginx")
    for ctr in "${services[@]}"; do
        if docker inspect "${ctr}" &>/dev/null 2>&1; then
            local health; health=$(docker inspect --format='{{.State.Health.Status}}' "${ctr}" 2>/dev/null || echo "no-healthcheck")
            local status; status=$(docker inspect --format='{{.State.Status}}' "${ctr}" 2>/dev/null || echo "not-found")
            printf "  %-25s status:%-12s health:%s\n" "${ctr}" "${status}" "${health}"
        fi
    done

    echo
    step "Uso de recursos"
    docker stats --no-stream --format "table {{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}\t{{.NetIO}}" \
        2>/dev/null | grep "backend-" || true
}

cmd_clean() {
    step "Limpeza completa (containers + redes + volumes)"
    warn "ATENÇÃO: apaga todos os dados do banco!"
    read -r -p "  Confirmar limpeza? [s/N] " confirm
    [[ "${confirm,,}" == "s" ]] || { warn "Cancelado."; exit 0; }

    docker compose down -v --remove-orphans
    docker image rm backend-api:latest backend-postgres:latest 2>/dev/null || true
    log "Limpeza concluída."
}

cmd_db_shell() {
    step "Abrindo shell PostgreSQL"
    docker exec -it backend-postgres \
        psql -U "${DB_USER:-appuser}" -d "${DB_NAME:-appdb}"
}

cmd_api_shell() {
    step "Abrindo shell no container da API"
    docker exec -it backend-api sh
}

usage() {
    cat <<EOF

${BOLD}deploy.sh${RESET} — Utilitário de operações Docker Compose

  ${CYAN}./scripts/deploy.sh <comando>${RESET}

Comandos:
  ${GREEN}dev${RESET}              Desenvolvimento (API + banco + Adminer)
  ${GREEN}staging${RESET}          Staging (API + banco + Nginx)
  ${GREEN}prod${RESET}             Produção (com confirmação)
  ${GREEN}down${RESET}             Para todos os containers
  ${GREEN}build${RESET}            Reconstrói imagens (--no-cache)
  ${GREEN}logs [serviço]${RESET}   Exibe logs em tempo real
  ${GREEN}status${RESET}           Status, saúde e uso de recursos
  ${GREEN}clean${RESET}            Remove tudo, incluindo volumes (CUIDADO!)
  ${GREEN}db-shell${RESET}         psql interativo no container do banco
  ${GREEN}api-shell${RESET}        Shell sh no container da API

EOF
}

main() {
    cd "${PROJECT_DIR}"
    [[ $# -eq 0 ]] && { usage; exit 0; }

    check_env

    case "$1" in
        dev)        cmd_dev ;;
        staging)    cmd_staging ;;
        prod)       cmd_prod ;;
        down)       cmd_down ;;
        build)      cmd_build ;;
        logs)       cmd_logs "${2:-}" ;;
        status)     cmd_status ;;
        clean)      cmd_clean ;;
        db-shell)   cmd_db_shell ;;
        api-shell)  cmd_api_shell ;;
        --help|-h)  usage ;;
        *) die "Comando desconhecido: $1. Use --help para ajuda." ;;
    esac
}

main "$@"
