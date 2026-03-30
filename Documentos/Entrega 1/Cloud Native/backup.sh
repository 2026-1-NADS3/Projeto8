#!/usr/bin/env bash
# =============================================================================
# backup.sh
# Backup automatizado de código-fonte (Git) e banco de dados de desenvolvimento
#
# Uso:
#   ./backup.sh                        # backup completo
#   ./backup.sh --source-only          # apenas código-fonte
#   ./backup.sh --db-only              # apenas banco de dados
#   ./backup.sh --restore <arquivo>    # restaurar backup
#   ./backup.sh --list                 # listar backups existentes
#
# Variáveis de ambiente necessárias (ou definidas abaixo):
#   BACKUP_DIR, SOURCE_DIR, DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASS
#
# Cron sugerido (todo dia às 02:00):
#   0 2 * * * /opt/scripts/backup.sh >> /var/log/backup_cron.log 2>&1
# =============================================================================

set -euo pipefail

# ---------------------------------------------------------------------------
# Variáveis de ambiente — sobrescreva via export antes de chamar o script
# ---------------------------------------------------------------------------
export BACKUP_DIR="${BACKUP_DIR:-/var/backups/devapp}"
export SOURCE_DIR="${SOURCE_DIR:-/opt/app/src}"
export BACKUP_RETENTION_DAYS="${BACKUP_RETENTION_DAYS:-7}"
export TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
export LOG_FILE="${BACKUP_DIR}/backup_${TIMESTAMP}.log"

# Banco de dados (PostgreSQL por padrão; suporta MySQL/SQLite)
export DB_TYPE="${DB_TYPE:-postgres}"          # postgres | mysql | sqlite
export DB_HOST="${DB_HOST:-localhost}"
export DB_PORT="${DB_PORT:-5432}"
export DB_NAME="${DB_NAME:-appdb}"
export DB_USER="${DB_USER:-appuser}"
export DB_PASS="${DB_PASS:-}"                  # Use .pgpass ou arquivo de credenciais

# Configurações de compressão e criptografia
export COMPRESS="${COMPRESS:-true}"
export ENCRYPT="${ENCRYPT:-false}"
export GPG_KEY_ID="${GPG_KEY_ID:-}"

# Notificação (webhook Slack/Discord — opcional)
export NOTIFY_WEBHOOK="${NOTIFY_WEBHOOK:-}"

# Cores
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
CYAN='\033[0;36m'; BOLD='\033[1m'; RESET='\033[0m'

# Contadores
BACKUP_SUCCESS=0
BACKUP_FAILED=0
BACKUP_FILES=()

# Flags
SOURCE_ONLY=false
DB_ONLY=false
RESTORE_FILE=""
LIST_MODE=false

# ---------------------------------------------------------------------------
# Utilitários
# ---------------------------------------------------------------------------
log() {
    local level="$1"; shift
    local ts; ts=$(date '+%Y-%m-%d %H:%M:%S')
    local msg="[${ts}] [${level}] $*"
    echo "${msg}" >> "${LOG_FILE}"
    case "${level}" in
        INFO)  echo -e "${GREEN}[✔]${RESET} $*" ;;
        WARN)  echo -e "${YELLOW}[⚠]${RESET} $*" ;;
        ERROR) echo -e "${RED}[✘]${RESET} $*" ;;
        STEP)  echo -e "\n${CYAN}${BOLD}▶ $*${RESET}" ;;
    esac
}

die() { log ERROR "$*"; exit 1; }

ensure_dirs() {
    local dirs=("${BACKUP_DIR}" "${BACKUP_DIR}/source" "${BACKUP_DIR}/database" "${BACKUP_DIR}/logs")
    for d in "${dirs[@]}"; do
        mkdir -p "${d}"
        chmod 750 "${d}"
    done
    mv "${LOG_FILE}" "${BACKUP_DIR}/logs/" 2>/dev/null || true
    LOG_FILE="${BACKUP_DIR}/logs/backup_${TIMESTAMP}.log"
    touch "${LOG_FILE}"
}

human_size() {
    local bytes="$1"
    if (( bytes < 1024 )); then echo "${bytes} B"
    elif (( bytes < 1048576 )); then echo "$(( bytes / 1024 )) KB"
    elif (( bytes < 1073741824 )); then echo "$(( bytes / 1048576 )) MB"
    else echo "$(( bytes / 1073741824 )) GB"
    fi
}

# ---------------------------------------------------------------------------
# Verificação de dependências
# ---------------------------------------------------------------------------
check_deps() {
    local missing=()
    local deps=(tar gzip find date)

    [[ "${DB_TYPE}" == "postgres" ]] && deps+=(pg_dump)
    [[ "${DB_TYPE}" == "mysql" ]]    && deps+=(mysqldump)
    [[ "${ENCRYPT}" == "true" ]]     && deps+=(gpg)

    for cmd in "${deps[@]}"; do
        command -v "${cmd}" &>/dev/null || missing+=("${cmd}")
    done

    if [[ "${#missing[@]}" -gt 0 ]]; then
        log WARN "Ferramentas não encontradas: ${missing[*]}"
        log WARN "Instale com: apt-get install ${missing[*]}"
    fi
}

# ---------------------------------------------------------------------------
# Backup do código-fonte (Git + tar)
# ---------------------------------------------------------------------------
backup_source() {
    log STEP "Backup do código-fonte: ${SOURCE_DIR}"

    [[ -d "${SOURCE_DIR}" ]] || { log WARN "Diretório não encontrado: ${SOURCE_DIR}"; return 1; }

    local backup_name="source_${TIMESTAMP}.tar.gz"
    local backup_path="${BACKUP_DIR}/source/${backup_name}"

    # Se for repositório Git, salva também o log de commits
    if git -C "${SOURCE_DIR}" rev-parse --is-inside-work-tree &>/dev/null 2>&1; then
        local git_branch; git_branch=$(git -C "${SOURCE_DIR}" branch --show-current 2>/dev/null || echo "unknown")
        local git_hash; git_hash=$(git -C "${SOURCE_DIR}" rev-parse --short HEAD 2>/dev/null || echo "unknown")
        log INFO "Git branch: ${git_branch} | commit: ${git_hash}"

        # Exporta log de commits (últimos 100)
        git -C "${SOURCE_DIR}" log --oneline -100 \
            > "${BACKUP_DIR}/source/git_log_${TIMESTAMP}.txt" 2>>"${LOG_FILE}" || true

        backup_name="source_${git_branch}_${git_hash}_${TIMESTAMP}.tar.gz"
        backup_path="${BACKUP_DIR}/source/${backup_name}"
    fi

    log INFO "Compactando para: ${backup_path}"

    # tar com exclusões comuns (node_modules, .gradle, build)
    tar \
        --exclude="${SOURCE_DIR}/.git" \
        --exclude="${SOURCE_DIR}/node_modules" \
        --exclude="${SOURCE_DIR}/.gradle" \
        --exclude="${SOURCE_DIR}/build" \
        --exclude="${SOURCE_DIR}/.android" \
        --exclude="*.class" \
        --exclude="*.apk" \
        -czf "${backup_path}" \
        -C "$(dirname "${SOURCE_DIR}")" \
        "$(basename "${SOURCE_DIR}")" \
        2>>"${LOG_FILE}"

    local size; size=$(stat -c%s "${backup_path}" 2>/dev/null || echo 0)
    log INFO "Backup de código criado: ${backup_name} ($(human_size "${size}"))"

    # Criptografia opcional
    if [[ "${ENCRYPT}" == "true" ]] && [[ -n "${GPG_KEY_ID}" ]]; then
        gpg --recipient "${GPG_KEY_ID}" --encrypt "${backup_path}" 2>>"${LOG_FILE}"
        rm -f "${backup_path}"
        backup_path="${backup_path}.gpg"
        log INFO "Arquivo criptografado: ${backup_path}"
    fi

    BACKUP_FILES+=("${backup_path}")
    ((BACKUP_SUCCESS++)) || true
}

# ---------------------------------------------------------------------------
# Backup do banco de dados
# ---------------------------------------------------------------------------
backup_database() {
    log STEP "Backup do banco de dados (${DB_TYPE}): ${DB_NAME}"

    local dump_file="${BACKUP_DIR}/database/db_${DB_NAME}_${TIMESTAMP}.sql"
    local final_file

    case "${DB_TYPE}" in
        postgres)
            backup_postgres "${dump_file}"
            ;;
        mysql)
            backup_mysql "${dump_file}"
            ;;
        sqlite)
            backup_sqlite "${dump_file}"
            ;;
        *)
            log WARN "DB_TYPE não suportado: ${DB_TYPE}"
            return 1
            ;;
    esac

    # Comprime o dump SQL
    if [[ "${COMPRESS}" == "true" ]] && [[ -f "${dump_file}" ]]; then
        gzip -9 "${dump_file}"
        final_file="${dump_file}.gz"
        local size; size=$(stat -c%s "${final_file}" 2>/dev/null || echo 0)
        log INFO "Dump comprimido: $(basename "${final_file}") ($(human_size "${size}"))"
    else
        final_file="${dump_file}"
    fi

    BACKUP_FILES+=("${final_file}")
    ((BACKUP_SUCCESS++)) || true
}

backup_postgres() {
    local out_file="$1"
    log INFO "pg_dump → ${out_file}"

    # Usa arquivo .pgpass se existir; caso contrário, PGPASSWORD
    PGPASSWORD="${DB_PASS}" pg_dump \
        --host="${DB_HOST}" \
        --port="${DB_PORT}" \
        --username="${DB_USER}" \
        --dbname="${DB_NAME}" \
        --format=plain \
        --no-password \
        --verbose \
        --file="${out_file}" \
        2>>"${LOG_FILE}" || { log ERROR "pg_dump falhou"; ((BACKUP_FAILED++)) || true; return 1; }

    log INFO "PostgreSQL dump concluído: $(wc -l < "${out_file}") linhas"
}

backup_mysql() {
    local out_file="$1"
    log INFO "mysqldump → ${out_file}"

    mysqldump \
        --host="${DB_HOST}" \
        --port="${DB_PORT}" \
        --user="${DB_USER}" \
        --password="${DB_PASS}" \
        --single-transaction \
        --routines \
        --triggers \
        "${DB_NAME}" \
        > "${out_file}" \
        2>>"${LOG_FILE}" || { log ERROR "mysqldump falhou"; ((BACKUP_FAILED++)) || true; return 1; }

    log INFO "MySQL dump concluído"
}

backup_sqlite() {
    local out_file="$1"
    local sqlite_path="${SOURCE_DIR}/${DB_NAME}.db"

    [[ -f "${sqlite_path}" ]] || sqlite_path="${DB_NAME}"
    [[ -f "${sqlite_path}" ]] || { log ERROR "SQLite não encontrado: ${sqlite_path}"; return 1; }

    log INFO "SQLite backup: ${sqlite_path} → ${out_file}"
    cp "${sqlite_path}" "${out_file}"
    log INFO "SQLite backup concluído"
}

# ---------------------------------------------------------------------------
# Limpeza de backups antigos (retenção)
# ---------------------------------------------------------------------------
cleanup_old_backups() {
    log STEP "Limpeza de backups com mais de ${BACKUP_RETENTION_DAYS} dias"

    local count=0
    while IFS= read -r old_file; do
        log INFO "Removendo: $(basename "${old_file}")"
        rm -f "${old_file}"
        ((count++)) || true
    done < <(find "${BACKUP_DIR}" -type f \
        \( -name "*.tar.gz" -o -name "*.sql.gz" -o -name "*.sql" -o -name "*.gpg" \) \
        -mtime +"${BACKUP_RETENTION_DAYS}" 2>/dev/null)

    log INFO "${count} arquivo(s) antigo(s) removido(s)"
}

# ---------------------------------------------------------------------------
# Listagem de backups
# ---------------------------------------------------------------------------
list_backups() {
    echo -e "\n${BOLD}${CYAN}Backups disponíveis em: ${BACKUP_DIR}${RESET}\n"
    printf "%-60s %-12s %s\n" "ARQUIVO" "TAMANHO" "DATA"
    printf '%0.s─' {1..90}; echo

    find "${BACKUP_DIR}" -type f \
        \( -name "*.tar.gz" -o -name "*.sql.gz" -o -name "*.gpg" -o -name "*.sql" \) \
        -printf "%f\t%s\t%TY-%Tm-%Td %TH:%TM\n" 2>/dev/null \
        | sort -k3 -r \
        | while IFS=$'\t' read -r name size date; do
            printf "%-60s %-12s %s\n" "${name:0:60}" "$(human_size "${size}")" "${date}"
        done

    echo
    local total; total=$(du -sh "${BACKUP_DIR}" 2>/dev/null | cut -f1)
    echo -e "Total em disco: ${BOLD}${total}${RESET}"
}

# ---------------------------------------------------------------------------
# Restauração de backup
# ---------------------------------------------------------------------------
restore_backup() {
    local file="$1"
    [[ -f "${file}" ]] || die "Arquivo não encontrado: ${file}"

    log STEP "Restaurando: ${file}"

    case "${file}" in
        *.tar.gz)
            local dest="${RESTORE_DEST:-/tmp/restore_${TIMESTAMP}}"
            mkdir -p "${dest}"
            tar -xzf "${file}" -C "${dest}" 2>>"${LOG_FILE}"
            log INFO "Código restaurado em: ${dest}"
            ;;
        *.sql.gz)
            log WARN "Para restaurar o banco, execute:"
            echo "  gunzip -c '${file}' | psql -h ${DB_HOST} -U ${DB_USER} ${DB_NAME}"
            ;;
        *.sql)
            log WARN "Para restaurar o banco, execute:"
            echo "  psql -h ${DB_HOST} -U ${DB_USER} ${DB_NAME} < '${file}'"
            ;;
        *)
            log WARN "Tipo de arquivo desconhecido: ${file}"
            ;;
    esac
}

# ---------------------------------------------------------------------------
# Notificação via webhook (Slack/Discord)
# ---------------------------------------------------------------------------
send_notification() {
    [[ -z "${NOTIFY_WEBHOOK}" ]] && return 0

    local status="$1"
    local details="$2"
    local color; [[ "${status}" == "success" ]] && color="good" || color="danger"

    local payload
    payload=$(cat <<JSON
{
  "attachments": [{
    "color": "${color}",
    "title": "Backup ${status} — $(hostname)",
    "text": "${details}",
    "footer": "$(date)"
  }]
}
JSON
)

    curl -s -X POST "${NOTIFY_WEBHOOK}" \
        -H 'Content-Type: application/json' \
        -d "${payload}" \
        >>"${LOG_FILE}" 2>&1 || true

    log INFO "Notificação enviada"
}

# ---------------------------------------------------------------------------
# Checksum de integridade
# ---------------------------------------------------------------------------
generate_checksums() {
    log STEP "Gerando checksums SHA-256"
    local checksum_file="${BACKUP_DIR}/checksums_${TIMESTAMP}.sha256"

    for f in "${BACKUP_FILES[@]}"; do
        sha256sum "${f}" >> "${checksum_file}" 2>>"${LOG_FILE}" || true
    done

    log INFO "Checksums em: ${checksum_file}"
}

# ---------------------------------------------------------------------------
# Parse de argumentos
# ---------------------------------------------------------------------------
parse_args() {
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --source-only)   SOURCE_ONLY=true ;;
            --db-only)       DB_ONLY=true ;;
            --restore)       [[ -n "${2:-}" ]] || die "--restore requer caminho do arquivo"; RESTORE_FILE="$2"; shift ;;
            --list|-l)       LIST_MODE=true ;;
            --help|-h)
                echo "Uso: $0 [--source-only] [--db-only] [--restore <arquivo>] [--list]"
                exit 0 ;;
            *) die "Opção desconhecida: $1" ;;
        esac
        shift
    done
}

# ---------------------------------------------------------------------------
# Resumo
# ---------------------------------------------------------------------------
print_summary() {
    local end_ts; end_ts=$(date '+%Y-%m-%d %H:%M:%S')
    echo
    echo -e "${BOLD}╔══════════════════════════════════════════════════════╗${RESET}"
    echo -e "${BOLD}║  BACKUP CONCLUÍDO — ${end_ts}  ║${RESET}"
    echo -e "${BOLD}╚══════════════════════════════════════════════════════╝${RESET}"
    echo
    echo -e "  ✔ Bem-sucedidos : ${GREEN}${BACKUP_SUCCESS}${RESET}"
    echo -e "  ✘ Falhados      : ${RED}${BACKUP_FAILED}${RESET}"
    echo
    for f in "${BACKUP_FILES[@]}"; do
        local size; size=$(stat -c%s "${f}" 2>/dev/null || echo 0)
        echo -e "  📦 $(basename "${f}") — $(human_size "${size}")"
    done
    echo
    echo -e "  📋 Log: ${LOG_FILE}"
    echo
}

# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------
main() {
    parse_args "$@"
    ensure_dirs
    check_deps

    if [[ "${LIST_MODE}" == true ]]; then
        list_backups
        exit 0
    fi

    if [[ -n "${RESTORE_FILE}" ]]; then
        restore_backup "${RESTORE_FILE}"
        exit 0
    fi

    log INFO "Iniciando backup — $(date)"
    log INFO "Retenção: ${BACKUP_RETENTION_DAYS} dias"

    [[ "${DB_ONLY}" == false ]]     && backup_source
    [[ "${SOURCE_ONLY}" == false ]] && backup_database
    cleanup_old_backups
    generate_checksums
    print_summary

    if [[ "${BACKUP_FAILED}" -eq 0 ]]; then
        send_notification "success" "${BACKUP_SUCCESS} backup(s) concluído(s)"
    else
        send_notification "failure" "${BACKUP_FAILED} backup(s) falharam"
        exit 1
    fi
}

main "$@"
