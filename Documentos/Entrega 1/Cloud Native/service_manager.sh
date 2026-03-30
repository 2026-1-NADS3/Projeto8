#!/usr/bin/env bash
# =============================================================================
# service_manager.sh
# Gerenciamento de processos do backend: iniciar, parar, status, restart
# automático em caso de falha (watchdog).
#
# Uso:
#   ./service_manager.sh start   [api|worker|all]
#   ./service_manager.sh stop    [api|worker|all]
#   ./service_manager.sh restart [api|worker|all]
#   ./service_manager.sh status  [api|worker|all]
#   ./service_manager.sh logs    [api|worker]
#   ./service_manager.sh watchdog               # loop de monitoramento
#
# Variáveis:
#   APP_DIR, JAVA_OPTS, API_PORT, WORKER_ENABLED
# =============================================================================

set -euo pipefail

# ---------------------------------------------------------------------------
# Variáveis de ambiente
# ---------------------------------------------------------------------------
export APP_DIR="${APP_DIR:-/opt/app}"
export LOG_DIR="${LOG_DIR:-/var/log/backend}"
export PID_DIR="${PID_DIR:-/var/run/backend}"
export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk-amd64}"
export JAVA_OPTS="${JAVA_OPTS:--Xms256m -Xmx1g -XX:+UseG1GC}"

# Configurações do serviço API
export API_JAR="${APP_DIR}/api/app.jar"
export API_PORT="${API_PORT:-8080}"
export API_PID_FILE="${PID_DIR}/api.pid"
export API_LOG="${LOG_DIR}/api.log"
export API_STARTUP_TIMEOUT="${API_STARTUP_TIMEOUT:-60}"  # segundos

# Configurações do worker (processamento assíncrono)
export WORKER_JAR="${APP_DIR}/worker/worker.jar"
export WORKER_PID_FILE="${PID_DIR}/worker.pid"
export WORKER_LOG="${LOG_DIR}/worker.log"
export WORKER_ENABLED="${WORKER_ENABLED:-true}"

# Docker Compose (se aplicável)
export COMPOSE_FILE="${APP_DIR}/docker-compose.yml"
export USE_DOCKER="${USE_DOCKER:-false}"

# Watchdog
export WATCHDOG_INTERVAL="${WATCHDOG_INTERVAL:-30}"       # segundos entre checks
export WATCHDOG_MAX_RESTARTS="${WATCHDOG_MAX_RESTARTS:-5}"
export WATCHDOG_LOG="${LOG_DIR}/watchdog.log"

# Cores
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
CYAN='\033[0;36m'; BOLD='\033[1m'; RESET='\033[0m'

# ---------------------------------------------------------------------------
# Utilitários
# ---------------------------------------------------------------------------
log() {
    local level="$1"; shift
    local ts; ts=$(date '+%Y-%m-%d %H:%M:%S')
    case "${level}" in
        INFO)  echo -e "${GREEN}[✔]${RESET} $*" ;;
        WARN)  echo -e "${YELLOW}[⚠]${RESET} $*" ;;
        ERROR) echo -e "${RED}[✘]${RESET} $*" ;;
        STEP)  echo -e "\n${CYAN}${BOLD}▶ $*${RESET}" ;;
    esac
    # Loga também no arquivo de watchdog se disponível
    [[ -f "${WATCHDOG_LOG}" ]] && echo "[${ts}] [${level}] $*" >> "${WATCHDOG_LOG}" || true
}

die() { log ERROR "$*"; exit 1; }

ensure_dirs() {
    mkdir -p "${LOG_DIR}" "${PID_DIR}"
    chmod 755 "${LOG_DIR}" "${PID_DIR}"
    touch "${WATCHDOG_LOG}"
}

# ---------------------------------------------------------------------------
# Funções de PID e status
# ---------------------------------------------------------------------------
get_pid() {
    local pid_file="$1"
    [[ -f "${pid_file}" ]] || return 1
    local pid; pid=$(cat "${pid_file}" 2>/dev/null || echo "")
    [[ -n "${pid}" ]] || return 1
    echo "${pid}"
}

is_running() {
    local pid_file="$1"
    local pid; pid=$(get_pid "${pid_file}") || return 1
    kill -0 "${pid}" 2>/dev/null
}

wait_for_port() {
    local port="$1"
    local timeout="$2"
    local elapsed=0

    while (( elapsed < timeout )); do
        if timeout 1 bash -c "echo >/dev/tcp/localhost/${port}" 2>/dev/null; then
            return 0
        fi
        sleep 2
        (( elapsed += 2 )) || true
        echo -n "."
    done
    return 1
}

# ---------------------------------------------------------------------------
# Inicialização de serviços
# ---------------------------------------------------------------------------
start_api() {
    log STEP "Iniciando API backend"

    if is_running "${API_PID_FILE}"; then
        local pid; pid=$(get_pid "${API_PID_FILE}")
        log WARN "API já está rodando (PID: ${pid})"
        return 0
    fi

    if [[ "${USE_DOCKER}" == "true" ]]; then
        start_docker_service "api"
        return 0
    fi

    [[ -f "${API_JAR}" ]] || die "JAR não encontrado: ${API_JAR}"

    log INFO "Iniciando API na porta ${API_PORT}..."

    # Inicia o processo em background, redireciona stdout/stderr para log
    nohup "${JAVA_HOME}/bin/java" \
        ${JAVA_OPTS} \
        -Dserver.port="${API_PORT}" \
        -Dspring.profiles.active="${SPRING_PROFILE:-development}" \
        -Dapp.log.dir="${LOG_DIR}" \
        -jar "${API_JAR}" \
        >> "${API_LOG}" 2>&1 &

    local pid=$!
    echo "${pid}" > "${API_PID_FILE}"

    log INFO "Processo iniciado — PID: ${pid}"
    log INFO "Aguardando porta ${API_PORT} (timeout: ${API_STARTUP_TIMEOUT}s)..."

    if wait_for_port "${API_PORT}" "${API_STARTUP_TIMEOUT}"; then
        echo
        log INFO "API disponível em http://localhost:${API_PORT}"
        log INFO "Health check: $(curl -sf "http://localhost:${API_PORT}/actuator/health" 2>/dev/null || echo "endpoint não disponível")"
    else
        echo
        log ERROR "Timeout: API não respondeu na porta ${API_PORT}"
        log ERROR "Verifique: tail -50 ${API_LOG}"
        stop_api
        return 1
    fi
}

start_worker() {
    [[ "${WORKER_ENABLED}" == "true" ]] || { log WARN "Worker desabilitado (WORKER_ENABLED=false)"; return 0; }

    log STEP "Iniciando Worker"

    if is_running "${WORKER_PID_FILE}"; then
        local pid; pid=$(get_pid "${WORKER_PID_FILE}")
        log WARN "Worker já está rodando (PID: ${pid})"
        return 0
    fi

    if [[ "${USE_DOCKER}" == "true" ]]; then
        start_docker_service "worker"
        return 0
    fi

    [[ -f "${WORKER_JAR}" ]] || { log WARN "Worker JAR não encontrado: ${WORKER_JAR}"; return 0; }

    nohup "${JAVA_HOME}/bin/java" \
        ${JAVA_OPTS} \
        -Dspring.profiles.active="${SPRING_PROFILE:-development}" \
        -jar "${WORKER_JAR}" \
        >> "${WORKER_LOG}" 2>&1 &

    local pid=$!
    echo "${pid}" > "${WORKER_PID_FILE}"
    log INFO "Worker iniciado — PID: ${pid}"
}

start_docker_service() {
    local service="${1:-}"
    [[ -f "${COMPOSE_FILE}" ]] || die "docker-compose.yml não encontrado: ${COMPOSE_FILE}"

    log INFO "Iniciando via Docker Compose: ${service:-todos os serviços}"
    if [[ -n "${service}" ]]; then
        docker compose -f "${COMPOSE_FILE}" up -d "${service}" 2>&1 | tee -a "${LOG_DIR}/docker.log"
    else
        docker compose -f "${COMPOSE_FILE}" up -d 2>&1 | tee -a "${LOG_DIR}/docker.log"
    fi
}

# ---------------------------------------------------------------------------
# Parada de serviços
# ---------------------------------------------------------------------------
stop_service() {
    local name="$1"
    local pid_file="$2"

    log STEP "Parando ${name}"

    if ! is_running "${pid_file}"; then
        log WARN "${name} não está rodando"
        rm -f "${pid_file}"
        return 0
    fi

    local pid; pid=$(get_pid "${pid_file}")
    log INFO "Enviando SIGTERM ao PID ${pid}..."
    kill -TERM "${pid}" 2>/dev/null || true

    # Aguarda graceful shutdown (até 15 segundos)
    local elapsed=0
    while (( elapsed < 15 )); do
        if ! kill -0 "${pid}" 2>/dev/null; then
            log INFO "${name} parou graciosamente"
            rm -f "${pid_file}"
            return 0
        fi
        sleep 1
        ((elapsed++)) || true
        echo -n "."
    done
    echo

    # Força encerramento
    log WARN "SIGTERM ignorado — enviando SIGKILL..."
    kill -KILL "${pid}" 2>/dev/null || true
    sleep 1
    rm -f "${pid_file}"
    log INFO "${name} encerrado forçadamente"
}

stop_api()    { stop_service "API" "${API_PID_FILE}"; }
stop_worker() { stop_service "Worker" "${WORKER_PID_FILE}"; }

stop_docker() {
    [[ -f "${COMPOSE_FILE}" ]] || return 0
    log STEP "Parando containers Docker"
    docker compose -f "${COMPOSE_FILE}" down 2>&1 | tee -a "${LOG_DIR}/docker.log"
}

# ---------------------------------------------------------------------------
# Status dos serviços
# ---------------------------------------------------------------------------
show_status() {
    echo
    echo -e "${BOLD}${CYAN}╔══════════════════════════════════════════════════════╗${RESET}"
    echo -e "${BOLD}${CYAN}║  STATUS DOS SERVIÇOS — $(date '+%H:%M:%S')                   ║${RESET}"
    echo -e "${BOLD}${CYAN}╚══════════════════════════════════════════════════════╝${RESET}"
    echo

    # API
    printf "  %-20s" "API Backend"
    if is_running "${API_PID_FILE}"; then
        local pid; pid=$(get_pid "${API_PID_FILE}")
        local mem; mem=$(ps -o rss= -p "${pid}" 2>/dev/null | awk '{printf "%.1f MB", $1/1024}' || echo "N/A")
        local cpu; cpu=$(ps -o %cpu= -p "${pid}" 2>/dev/null | tr -d ' ' || echo "N/A")
        local uptime_s; uptime_s=$(ps -o etimes= -p "${pid}" 2>/dev/null | tr -d ' ' || echo "0")
        local uptime_h=$(( uptime_s / 3600 ))
        local uptime_m=$(( (uptime_s % 3600) / 60 ))
        echo -e "${GREEN}● RODANDO${RESET}  PID:${pid}  CPU:${cpu}%  MEM:${mem}  UP:${uptime_h}h${uptime_m}m"

        # Health check HTTP
        local health
        health=$(curl -sf --max-time 3 "http://localhost:${API_PORT}/actuator/health" 2>/dev/null \
            | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('status','?'))" 2>/dev/null \
            || echo "no-endpoint")
        echo -e "    Health: ${health}"
    else
        echo -e "${RED}● PARADO${RESET}"
    fi

    # Worker
    printf "  %-20s" "Worker"
    if is_running "${WORKER_PID_FILE}"; then
        local pid; pid=$(get_pid "${WORKER_PID_FILE}")
        echo -e "${GREEN}● RODANDO${RESET}  PID:${pid}"
    else
        echo -e "${RED}● PARADO${RESET}"
    fi

    # Docker
    if [[ "${USE_DOCKER}" == "true" ]] && command -v docker &>/dev/null; then
        echo
        echo -e "  ${BOLD}Containers Docker:${RESET}"
        docker ps --format "  %-20s %-15s %-10s %s" \
            --filter "label=com.docker.compose.project" 2>/dev/null \
            | head -10 || echo "  (nenhum container em execução)"
    fi

    # Logs recentes
    echo
    echo -e "  ${BOLD}Últimas linhas do log da API:${RESET}"
    tail -5 "${API_LOG}" 2>/dev/null | sed 's/^/    /' || echo "    (log vazio)"
    echo
}

# ---------------------------------------------------------------------------
# Visualização de logs
# ---------------------------------------------------------------------------
show_logs() {
    local service="${1:-api}"
    local lines="${2:-50}"
    local log_file

    case "${service}" in
        api)      log_file="${API_LOG}" ;;
        worker)   log_file="${WORKER_LOG}" ;;
        watchdog) log_file="${WATCHDOG_LOG}" ;;
        docker)   log_file="${LOG_DIR}/docker.log" ;;
        *) die "Serviço desconhecido: ${service}" ;;
    esac

    [[ -f "${log_file}" ]] || die "Log não encontrado: ${log_file}"

    echo -e "${BOLD}${CYAN}=== ${log_file} (últimas ${lines} linhas) ===${RESET}"
    tail -n "${lines}" "${log_file}" | grep --color=auto -E \
        "ERROR|WARN|FATAL|Exception|error|warn" \
        --color=always || tail -n "${lines}" "${log_file}"
}

# ---------------------------------------------------------------------------
# Watchdog — restart automático em caso de falha
# ---------------------------------------------------------------------------
run_watchdog() {
    log STEP "Watchdog iniciado (intervalo: ${WATCHDOG_INTERVAL}s)"
    log INFO "Pressione Ctrl+C para interromper"

    local restart_count_api=0
    local restart_count_worker=0
    local ts

    while true; do
        ts=$(date '+%Y-%m-%d %H:%M:%S')

        # Verifica API
        if ! is_running "${API_PID_FILE}"; then
            echo "[${ts}] [WATCHDOG] API parada — reiniciando..." | tee -a "${WATCHDOG_LOG}"

            if (( restart_count_api < WATCHDOG_MAX_RESTARTS )); then
                start_api >> "${WATCHDOG_LOG}" 2>&1 && \
                    echo "[${ts}] [WATCHDOG] API reiniciada (tentativa $((++restart_count_api)))" \
                    | tee -a "${WATCHDOG_LOG}"
            else
                echo "[${ts}] [WATCHDOG] CRÍTICO: API falhou ${WATCHDOG_MAX_RESTARTS}x — interrompendo watchdog" \
                    | tee -a "${WATCHDOG_LOG}"
                exit 1
            fi
        else
            restart_count_api=0  # Reset contador se estiver saudável
        fi

        # Verifica Worker
        if [[ "${WORKER_ENABLED}" == "true" ]] && ! is_running "${WORKER_PID_FILE}"; then
            echo "[${ts}] [WATCHDOG] Worker parado — reiniciando..." | tee -a "${WATCHDOG_LOG}"

            if (( restart_count_worker < WATCHDOG_MAX_RESTARTS )); then
                start_worker >> "${WATCHDOG_LOG}" 2>&1 && \
                    echo "[${ts}] [WATCHDOG] Worker reiniciado (tentativa $((++restart_count_worker)))" \
                    | tee -a "${WATCHDOG_LOG}"
            fi
        else
            restart_count_worker=0
        fi

        # Exibe status resumido a cada verificação
        echo -e "[${ts}] [WATCHDOG] OK — API:$(is_running "${API_PID_FILE}" && echo UP || echo DOWN) | Worker:$(is_running "${WORKER_PID_FILE}" && echo UP || echo DOWN)"

        sleep "${WATCHDOG_INTERVAL}"
    done
}

# ---------------------------------------------------------------------------
# Parse de argumentos e dispatch
# ---------------------------------------------------------------------------
usage() {
    cat <<EOF

${BOLD}service_manager.sh${RESET} — Gerenciador de serviços backend

  ${CYAN}./service_manager.sh <comando> [serviço]${RESET}

Comandos:
  start   [api|worker|all]    Inicia serviço(s)
  stop    [api|worker|all]    Para serviço(s)
  restart [api|worker|all]    Reinicia serviço(s)
  status                       Exibe status geral
  logs    [api|worker|watchdog] Exibe logs
  watchdog                     Inicia watchdog de restart automático

Variáveis de ambiente:
  APP_DIR, JAVA_HOME, JAVA_OPTS, API_PORT
  USE_DOCKER, WORKER_ENABLED, WATCHDOG_INTERVAL
EOF
    exit 0
}

main() {
    [[ $# -eq 0 ]] && usage

    ensure_dirs

    local command="$1"
    local target="${2:-all}"

    case "${command}" in
        start)
            case "${target}" in
                api)    start_api ;;
                worker) start_worker ;;
                all)    start_api; start_worker ;;
                *)      die "Serviço desconhecido: ${target}" ;;
            esac
            ;;
        stop)
            case "${target}" in
                api)    stop_api ;;
                worker) stop_worker ;;
                all)    stop_api; stop_worker; [[ "${USE_DOCKER}" == "true" ]] && stop_docker ;;
                *)      die "Serviço desconhecido: ${target}" ;;
            esac
            ;;
        restart)
            case "${target}" in
                api)    stop_api;    sleep 2; start_api ;;
                worker) stop_worker; sleep 2; start_worker ;;
                all)    stop_api; stop_worker; sleep 2; start_api; start_worker ;;
                *)      die "Serviço desconhecido: ${target}" ;;
            esac
            ;;
        status)   show_status ;;
        logs)     show_logs "${target}" "${3:-50}" ;;
        watchdog) run_watchdog ;;
        --help|-h) usage ;;
        *) die "Comando desconhecido: ${command}. Use --help para ajuda." ;;
    esac
}

main "$@"
