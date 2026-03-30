#!/usr/bin/env bash
# =============================================================================
# monitor.sh
# Monitoramento de métricas do sistema: CPU, memória, disco, processos
# e geração de relatórios em JSON + texto.
#
# Uso:
#   ./monitor.sh                   # snapshot único
#   ./monitor.sh --watch 30        # loop a cada 30 segundos
#   ./monitor.sh --report          # relatório HTML
#   ./monitor.sh --alert           # habilita alertas via log
#
# Cron sugerido (a cada 5 min):
#   */5 * * * * /opt/scripts/monitor.sh --alert >> /var/log/monitor_cron.log 2>&1
# =============================================================================

set -euo pipefail

# ---------------------------------------------------------------------------
# Variáveis de ambiente
# ---------------------------------------------------------------------------
export MONITOR_LOG_DIR="${MONITOR_LOG_DIR:-/var/log/app-monitor}"
export METRICS_FILE="${MONITOR_LOG_DIR}/metrics_$(date +%Y%m%d).jsonl"
export REPORT_FILE="${MONITOR_LOG_DIR}/report_$(date +%Y%m%d_%H%M%S).html"
export ALERT_LOG="${MONITOR_LOG_DIR}/alerts.log"

# Thresholds para alertas
ALERT_CPU_THRESHOLD="${ALERT_CPU_THRESHOLD:-85}"
ALERT_MEM_THRESHOLD="${ALERT_MEM_THRESHOLD:-90}"
ALERT_DISK_THRESHOLD="${ALERT_DISK_THRESHOLD:-80}"

# Cores
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
CYAN='\033[0;36m'; BOLD='\033[1m'; RESET='\033[0m'

# Flags
WATCH_MODE=false
WATCH_INTERVAL=60
REPORT_MODE=false
ALERT_MODE=false

# ---------------------------------------------------------------------------
# Funções utilitárias
# ---------------------------------------------------------------------------
log_alert() {
    local ts; ts=$(date '+%Y-%m-%d %H:%M:%S')
    echo "[${ts}] ALERTA: $*" | tee -a "${ALERT_LOG}"
}

ensure_dirs() {
    mkdir -p "${MONITOR_LOG_DIR}"
    chmod 755 "${MONITOR_LOG_DIR}"
}

# ---------------------------------------------------------------------------
# Coleta de métricas
# ---------------------------------------------------------------------------

# CPU — média de uso nos últimos 1s via /proc/stat
collect_cpu() {
    # Duas leituras para calcular delta real
    local cpu1 cpu2
    cpu1=$(grep '^cpu ' /proc/stat)
    sleep 1
    cpu2=$(grep '^cpu ' /proc/stat)

    local idle1 total1 idle2 total2
    read -r _ user1 nice1 sys1 idle1 iowait1 irq1 softirq1 <<< "${cpu1}"
    read -r _ user2 nice2 sys2 idle2 iowait2 irq2 softirq2 <<< "${cpu2}"

    total1=$(( user1 + nice1 + sys1 + idle1 + iowait1 + irq1 + softirq1 ))
    total2=$(( user2 + nice2 + sys2 + idle2 + iowait2 + irq2 + softirq2 ))

    local delta_total=$(( total2 - total1 ))
    local delta_idle=$(( idle2 - idle1 ))

    if [[ "${delta_total}" -eq 0 ]]; then
        echo "0.0"
    else
        echo "scale=1; 100 * (${delta_total} - ${delta_idle}) / ${delta_total}" | bc
    fi
}

# Memória — valores em MB
collect_memory() {
    local mem_total mem_avail mem_free mem_buffers mem_cached
    mem_total=$(grep MemTotal /proc/meminfo | awk '{print $2}')
    mem_avail=$(grep MemAvailable /proc/meminfo | awk '{print $2}')
    mem_free=$(grep '^MemFree' /proc/meminfo | awk '{print $2}')
    mem_buffers=$(grep Buffers /proc/meminfo | awk '{print $2}')
    mem_cached=$(grep '^Cached' /proc/meminfo | awk '{print $2}')

    local mem_used=$(( mem_total - mem_avail ))
    local mem_pct
    mem_pct=$(echo "scale=1; 100 * ${mem_used} / ${mem_total}" | bc)

    echo "${mem_total} ${mem_used} ${mem_avail} ${mem_pct}"
}

# Disco — uso por ponto de montagem
collect_disk() {
    df -BM --output=target,size,used,avail,pcent 2>/dev/null \
        | grep -v "^Filesystem\|tmpfs\|udev\|/boot/efi" \
        | head -10
}

# Processos mais pesados
collect_top_processes() {
    ps aux --sort=-%cpu \
        | awk 'NR>1 {printf "%s\t%s\t%s\t%s\n", $1, $11, $3, $4}' \
        | head -5
}

# Informações de rede
collect_network() {
    # Bytes recebidos/transmitidos na interface principal
    local iface
    iface=$(ip route | grep '^default' | awk '{print $5}' | head -1)
    [[ -z "${iface}" ]] && { echo "N/A N/A ${iface:-unknown}"; return; }

    local rx tx
    rx=$(cat "/sys/class/net/${iface}/statistics/rx_bytes" 2>/dev/null || echo 0)
    tx=$(cat "/sys/class/net/${iface}/statistics/tx_bytes" 2>/dev/null || echo 0)

    # Converte para MB
    local rx_mb tx_mb
    rx_mb=$(echo "scale=2; ${rx} / 1048576" | bc)
    tx_mb=$(echo "scale=2; ${tx} / 1048576" | bc)

    echo "${rx_mb} ${tx_mb} ${iface}"
}

# Uptime e load average
collect_uptime() {
    uptime | awk '{
        for(i=1;i<=NF;i++) if($i=="load") { print $(i+2), $(i+3), $(i+4); break }
    }' | tr -d ','
}

# Contagem de containers Docker (se disponível)
collect_docker() {
    if command -v docker &>/dev/null && docker info &>/dev/null 2>&1; then
        local running stopped
        running=$(docker ps -q | wc -l)
        stopped=$(docker ps -aq | wc -l)
        echo "${running} $((stopped - running))"
    else
        echo "N/A N/A"
    fi
}

# ---------------------------------------------------------------------------
# Geração de snapshot JSON (JSONL — uma linha por coleta)
# ---------------------------------------------------------------------------
generate_json_snapshot() {
    local ts; ts=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
    local hostname; hostname=$(hostname -f 2>/dev/null || hostname)

    # CPU
    local cpu_usage; cpu_usage=$(collect_cpu)

    # Memória
    local mem_data; mem_data=$(collect_memory)
    local mem_total mem_used mem_avail mem_pct
    read -r mem_total mem_used mem_avail mem_pct <<< "${mem_data}"

    # Disco (primeira partição principal)
    local disk_line; disk_line=$(collect_disk | head -1)
    local disk_mount disk_size disk_used disk_avail disk_pct
    read -r disk_mount disk_size disk_used disk_avail disk_pct <<< "${disk_line}"
    disk_pct="${disk_pct//%/}"

    # Rede
    local net_data; net_data=$(collect_network)
    local net_rx net_tx net_iface
    read -r net_rx net_tx net_iface <<< "${net_data}"

    # Uptime / load
    local load_data; load_data=$(collect_uptime)
    local load1 load5 load15
    read -r load1 load5 load15 <<< "${load_data}"

    # Docker
    local docker_data; docker_data=$(collect_docker)
    local docker_running docker_stopped
    read -r docker_running docker_stopped <<< "${docker_data}"

    # Processos
    local proc_count; proc_count=$(ps aux | wc -l)

    # Monta JSON
    cat <<JSON
{"timestamp":"${ts}","host":"${hostname}","cpu":{"usage_pct":${cpu_usage}},"memory":{"total_kb":${mem_total},"used_kb":${mem_used},"available_kb":${mem_avail},"usage_pct":${mem_pct}},"disk":{"mount":"${disk_mount}","size":"${disk_size}","used":"${disk_used}","available":"${disk_avail}","usage_pct":${disk_pct:-0}},"network":{"interface":"${net_iface}","rx_mb":${net_rx:-0},"tx_mb":${net_tx:-0}},"load_avg":{"1m":"${load1:-0}","5m":"${load5:-0}","15m":"${load15:-0}"},"docker":{"running":${docker_running:-0},"stopped":${docker_stopped:-0}},"processes":{"total":${proc_count}}}
JSON
}

# ---------------------------------------------------------------------------
# Exibição no terminal (snapshot formatado)
# ---------------------------------------------------------------------------
display_snapshot() {
    local cpu_usage; cpu_usage=$(collect_cpu)
    local mem_data; mem_data=$(collect_memory)
    local mem_total mem_used mem_avail mem_pct
    read -r mem_total mem_used mem_avail mem_pct <<< "${mem_data}"
    local mem_total_mb=$(( mem_total / 1024 ))
    local mem_used_mb=$(( mem_used / 1024 ))

    clear
    echo -e "${BOLD}${CYAN}"
    echo "╔══════════════════════════════════════════════════════════╗"
    printf "║  🖥  Monitor — %-42s ║\n" "$(hostname) · $(date '+%H:%M:%S')"
    echo "╚══════════════════════════════════════════════════════════╝"
    echo -e "${RESET}"

    # CPU
    local cpu_bar; cpu_bar=$(printf_bar "${cpu_usage%.*}" 40)
    local cpu_color="${GREEN}"
    (( ${cpu_usage%.*} >= ALERT_CPU_THRESHOLD )) && cpu_color="${RED}"
    echo -e "  ${BOLD}CPU${RESET}  ${cpu_color}${cpu_bar}${RESET} ${cpu_usage}%"

    # Memória
    local mem_bar; mem_bar=$(printf_bar "${mem_pct%.*}" 40)
    local mem_color="${GREEN}"
    (( ${mem_pct%.*} >= ALERT_MEM_THRESHOLD )) && mem_color="${RED}"
    echo -e "  ${BOLD}MEM${RESET}  ${mem_color}${mem_bar}${RESET} ${mem_pct}% (${mem_used_mb}/${mem_total_mb} MB)"

    # Disco
    echo
    echo -e "  ${BOLD}DISCO${RESET}"
    while IFS= read -r line; do
        local mount size used avail pct
        read -r mount size used avail pct <<< "${line}"
        local pct_num="${pct//%/}"
        local disk_bar; disk_bar=$(printf_bar "${pct_num}" 35)
        local dcolor="${GREEN}"
        (( pct_num >= ALERT_DISK_THRESHOLD )) && dcolor="${YELLOW}"
        (( pct_num >= 95 )) && dcolor="${RED}"
        printf "    %-20s %s%s%s %s (livre: %s)\n" \
            "${mount}" "${dcolor}" "${disk_bar}" "${RESET}" "${pct}" "${avail}"
    done < <(collect_disk)

    # Top processos
    echo
    echo -e "  ${BOLD}TOP PROCESSOS (CPU)${RESET}"
    printf "    %-15s %-35s %7s %7s\n" "USUÁRIO" "PROCESSO" "CPU%" "MEM%"
    printf '    %.0s─' {1..60}; echo
    while IFS=$'\t' read -r user cmd cpu mem; do
        printf "    %-15s %-35s %7s %7s\n" "${user:0:15}" "${cmd:0:35}" "${cpu}" "${mem}"
    done < <(collect_top_processes)

    # Rede e uptime
    echo
    local net_data; net_data=$(collect_network)
    read -r net_rx net_tx net_iface <<< "${net_data}"
    echo -e "  ${BOLD}REDE${RESET}  Interface: ${net_iface} | RX: ${net_rx} MB | TX: ${net_tx} MB"

    local load_data; load_data=$(collect_uptime)
    echo -e "  ${BOLD}LOAD${RESET}  ${load_data}"

    # Docker
    local docker_data; docker_data=$(collect_docker)
    read -r docker_running docker_stopped <<< "${docker_data}"
    echo -e "  ${BOLD}DOCKER${RESET} Rodando: ${docker_running} | Parados: ${docker_stopped}"

    echo
    echo -e "  ${CYAN}Métricas salvas em: ${METRICS_FILE}${RESET}"
}

# Barra de progresso ASCII
printf_bar() {
    local pct="$1"
    local width="$2"
    local filled=$(( pct * width / 100 ))
    local empty=$(( width - filled ))
    printf '['; printf '█%.0s' $(seq 1 "${filled}") 2>/dev/null || true
    printf '░%.0s' $(seq 1 "${empty}") 2>/dev/null || true
    printf ']'
}

# ---------------------------------------------------------------------------
# Verificação de alertas
# ---------------------------------------------------------------------------
check_alerts() {
    local cpu_usage; cpu_usage=$(collect_cpu)
    local mem_data; mem_data=$(collect_memory)
    local mem_pct; mem_pct=$(echo "${mem_data}" | awk '{print $4}')

    # Remove decimal para comparação inteira
    local cpu_int="${cpu_usage%.*}"
    local mem_int="${mem_pct%.*}"

    [[ "${cpu_int}" -ge "${ALERT_CPU_THRESHOLD}" ]] && \
        log_alert "CPU em ${cpu_usage}% (threshold: ${ALERT_CPU_THRESHOLD}%)"

    [[ "${mem_int}" -ge "${ALERT_MEM_THRESHOLD}" ]] && \
        log_alert "Memória em ${mem_pct}% (threshold: ${ALERT_MEM_THRESHOLD}%)"

    while IFS= read -r line; do
        local pct; pct=$(echo "${line}" | awk '{print $5}' | tr -d '%')
        local mount; mount=$(echo "${line}" | awk '{print $1}')
        [[ "${pct}" -ge "${ALERT_DISK_THRESHOLD}" ]] && \
            log_alert "Disco ${mount} em ${pct}% (threshold: ${ALERT_DISK_THRESHOLD}%)"
    done < <(collect_disk)
}

# ---------------------------------------------------------------------------
# Relatório HTML
# ---------------------------------------------------------------------------
generate_html_report() {
    log INFO "Gerando relatório HTML: ${REPORT_FILE}"

    local snapshot; snapshot=$(generate_json_snapshot)
    local cpu_usage; cpu_usage=$(echo "${snapshot}" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d['cpu']['usage_pct'])" 2>/dev/null || echo "N/A")
    local mem_pct;   mem_pct=$(echo "${snapshot}" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d['memory']['usage_pct'])" 2>/dev/null || echo "N/A")

    cat > "${REPORT_FILE}" <<HTML
<!DOCTYPE html>
<html lang="pt-BR">
<head>
<meta charset="UTF-8">
<title>Monitor — $(hostname) — $(date)</title>
<style>
  body{font-family:monospace;background:#0d1117;color:#c9d1d9;margin:0;padding:2rem}
  h1{color:#58a6ff} table{border-collapse:collapse;width:100%}
  th,td{border:1px solid #30363d;padding:.5rem 1rem;text-align:left}
  th{background:#161b22;color:#58a6ff}
  .ok{color:#3fb950} .warn{color:#d29922} .crit{color:#f85149}
  pre{background:#161b22;padding:1rem;border-radius:6px;overflow:auto}
</style>
</head>
<body>
<h1>🖥 Relatório de Monitoramento</h1>
<p>Host: <strong>$(hostname -f)</strong> &nbsp;|&nbsp; Gerado em: <strong>$(date)</strong></p>
<h2>Resumo</h2>
<table>
  <tr><th>Métrica</th><th>Valor</th><th>Status</th></tr>
  <tr><td>CPU</td><td>${cpu_usage}%</td><td class="ok">OK</td></tr>
  <tr><td>Memória</td><td>${mem_pct}%</td><td class="ok">OK</td></tr>
</table>
<h2>Métricas Brutas (JSON)</h2>
<pre>${snapshot}</pre>
<h2>Histórico de Alertas</h2>
<pre>$(tail -50 "${ALERT_LOG}" 2>/dev/null || echo "Nenhum alerta registrado.")</pre>
</body>
</html>
HTML

    echo -e "${GREEN}✔ Relatório gerado: ${REPORT_FILE}${RESET}"
}

# ---------------------------------------------------------------------------
# Parse de argumentos
# ---------------------------------------------------------------------------
parse_args() {
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --watch)    WATCH_MODE=true; [[ "${2:-}" =~ ^[0-9]+$ ]] && { WATCH_INTERVAL="$2"; shift; } ;;
            --report)   REPORT_MODE=true ;;
            --alert)    ALERT_MODE=true ;;
            --help|-h)
                echo "Uso: $0 [--watch <seg>] [--report] [--alert]"
                exit 0 ;;
            *) echo "Opção desconhecida: $1"; exit 1 ;;
        esac
        shift
    done
}

log() { echo -e "${GREEN}[✔]${RESET} $*"; }

# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------
main() {
    parse_args "$@"
    ensure_dirs

    if [[ "${REPORT_MODE}" == true ]]; then
        generate_html_report
        exit 0
    fi

    if [[ "${WATCH_MODE}" == true ]]; then
        echo -e "${BOLD}${CYAN}Monitor em loop — intervalo: ${WATCH_INTERVAL}s (Ctrl+C para sair)${RESET}"
        while true; do
            # Salva JSON
            generate_json_snapshot >> "${METRICS_FILE}"
            display_snapshot
            [[ "${ALERT_MODE}" == true ]] && check_alerts
            sleep "${WATCH_INTERVAL}"
        done
    else
        # Snapshot único
        generate_json_snapshot >> "${METRICS_FILE}"
        display_snapshot
        [[ "${ALERT_MODE}" == true ]] && check_alerts
    fi
}

main "$@"
