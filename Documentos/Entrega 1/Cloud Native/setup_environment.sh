#!/usr/bin/env bash
# =============================================================================
# setup_environment.sh
# Automação de instalação de dependências para ambiente de desenvolvimento
# Android + Backend Java/Spring Boot
#
# Uso: sudo ./setup_environment.sh [--skip-android] [--skip-java] [--verbose]
# =============================================================================

set -euo pipefail

# ---------------------------------------------------------------------------
# Variáveis de ambiente e configurações globais
# ---------------------------------------------------------------------------
export JAVA_VERSION="${JAVA_VERSION:-17}"
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-/opt/android-sdk}"
export ANDROID_CMDLINE_VERSION="11076708"
export GRADLE_VERSION="${GRADLE_VERSION:-8.5}"
export LOG_DIR="${LOG_DIR:-/var/log/devsetup}"
export TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
export LOG_FILE="${LOG_DIR}/setup_${TIMESTAMP}.log"

# Cores para output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
BOLD='\033[1m'
RESET='\033[0m'

# Flags de controle
SKIP_ANDROID=false
SKIP_JAVA=false
VERBOSE=false
ERRORS=0

# ---------------------------------------------------------------------------
# Funções utilitárias
# ---------------------------------------------------------------------------
log() {
    local level="$1"; shift
    local message="$*"
    local ts; ts=$(date '+%Y-%m-%d %H:%M:%S')
    echo "[${ts}] [${level}] ${message}" >> "${LOG_FILE}"
    case "${level}" in
        INFO)  echo -e "${GREEN}[✔]${RESET} ${message}" ;;
        WARN)  echo -e "${YELLOW}[⚠]${RESET} ${message}" ;;
        ERROR) echo -e "${RED}[✘]${RESET} ${message}" ;;
        STEP)  echo -e "\n${CYAN}${BOLD}▶ ${message}${RESET}" ;;
    esac
    [[ "${VERBOSE}" == true ]] && echo -e "    ${CYAN}↳ logged to ${LOG_FILE}${RESET}"
}

die() {
    log ERROR "$*"
    exit 1
}

check_root() {
    [[ "${EUID}" -eq 0 ]] || die "Execute como root: sudo $0"
}

check_os() {
    if [[ ! -f /etc/os-release ]]; then
        die "Sistema operacional não reconhecido."
    fi
    # shellcheck source=/dev/null
    source /etc/os-release
    log INFO "Sistema detectado: ${PRETTY_NAME}"
}

command_exists() {
    command -v "$1" &>/dev/null
}

confirm() {
    local prompt="$1"
    read -r -p "${prompt} [s/N] " answer
    [[ "${answer,,}" == "s" ]]
}

# ---------------------------------------------------------------------------
# Parse de argumentos
# ---------------------------------------------------------------------------
parse_args() {
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --skip-android) SKIP_ANDROID=true ;;
            --skip-java)    SKIP_JAVA=true ;;
            --verbose|-v)   VERBOSE=true ;;
            --help|-h)
                echo "Uso: sudo $0 [--skip-android] [--skip-java] [--verbose]"
                exit 0
                ;;
            *) die "Opção desconhecida: $1. Use --help para ajuda." ;;
        esac
        shift
    done
}

# ---------------------------------------------------------------------------
# Preparação do ambiente de log
# ---------------------------------------------------------------------------
init_logging() {
    mkdir -p "${LOG_DIR}"
    chmod 755 "${LOG_DIR}"
    touch "${LOG_FILE}"
    log INFO "Iniciando setup — log em: ${LOG_FILE}"
}

# ---------------------------------------------------------------------------
# Atualização do sistema
# ---------------------------------------------------------------------------
update_system() {
    log STEP "Atualizando pacotes do sistema"
    apt-get update -qq 2>>"${LOG_FILE}" | tee -a "${LOG_FILE}" | grep -E "Hit|Get" | head -5 || true
    apt-get upgrade -y -qq 2>>"${LOG_FILE}"
    apt-get install -y -qq \
        curl wget unzip zip git \
        software-properties-common \
        ca-certificates gnupg lsb-release \
        build-essential jq \
        2>>"${LOG_FILE}"
    log INFO "Sistema atualizado e ferramentas base instaladas"
}

# ---------------------------------------------------------------------------
# Instalação do Docker
# ---------------------------------------------------------------------------
install_docker() {
    log STEP "Verificando Docker"

    if command_exists docker; then
        local docker_ver; docker_ver=$(docker --version | awk '{print $3}' | tr -d ',')
        log INFO "Docker já instalado: v${docker_ver}"
        return 0
    fi

    log INFO "Instalando Docker Engine..."

    # Repositório oficial Docker
    install -m 0755 -d /etc/apt/keyrings
    curl -fsSL https://download.docker.com/linux/ubuntu/gpg \
        | gpg --dearmor -o /etc/apt/keyrings/docker.gpg 2>>"${LOG_FILE}"
    chmod a+r /etc/apt/keyrings/docker.gpg

    echo \
      "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
      https://download.docker.com/linux/ubuntu \
      $(lsb_release -cs) stable" \
      | tee /etc/apt/sources.list.d/docker.list > /dev/null

    apt-get update -qq 2>>"${LOG_FILE}"
    apt-get install -y -qq \
        docker-ce docker-ce-cli containerd.io \
        docker-buildx-plugin docker-compose-plugin \
        2>>"${LOG_FILE}"

    systemctl enable docker 2>>"${LOG_FILE}"
    systemctl start  docker 2>>"${LOG_FILE}"

    # Adiciona usuário atual ao grupo docker (evita sudo)
    if [[ -n "${SUDO_USER:-}" ]]; then
        usermod -aG docker "${SUDO_USER}"
        log INFO "Usuário '${SUDO_USER}' adicionado ao grupo docker"
    fi

    log INFO "Docker instalado: $(docker --version)"
}

# ---------------------------------------------------------------------------
# Instalação do Java (via SDKMAN)
# ---------------------------------------------------------------------------
install_java() {
    [[ "${SKIP_JAVA}" == true ]] && { log WARN "Instalação do Java pulada (--skip-java)"; return 0; }

    log STEP "Instalando Java ${JAVA_VERSION} (Temurin via SDKMAN)"

    local sdkman_init="${SDKMAN_DIR:-/usr/local/sdkman}/bin/sdkman-init.sh"

    if [[ ! -f "${sdkman_init}" ]]; then
        log INFO "Instalando SDKMAN..."
        export SDKMAN_DIR=/usr/local/sdkman
        curl -s "https://get.sdkman.io" | bash 2>>"${LOG_FILE}"
    fi

    # shellcheck source=/dev/null
    source "${sdkman_init}" 2>>"${LOG_FILE}" || true

    # Instala Java Temurin
    sdk install java "${JAVA_VERSION}-tem" <<< "Y" 2>>"${LOG_FILE}" || {
        log WARN "SDKMAN falhou; tentando apt-get..."
        apt-get install -y -qq "openjdk-${JAVA_VERSION}-jdk" 2>>"${LOG_FILE}"
    }

    # Configura JAVA_HOME permanentemente
    local java_home
    java_home=$(dirname "$(dirname "$(readlink -f "$(which java)")")")
    {
        echo "export JAVA_HOME=${java_home}"
        echo 'export PATH=$JAVA_HOME/bin:$PATH'
    } >> /etc/environment

    export JAVA_HOME="${java_home}"
    log INFO "Java instalado: $(java -version 2>&1 | head -1)"
}

# ---------------------------------------------------------------------------
# Instalação do Android SDK
# ---------------------------------------------------------------------------
install_android_sdk() {
    [[ "${SKIP_ANDROID}" == true ]] && { log WARN "Android SDK pulado (--skip-android)"; return 0; }

    log STEP "Instalando Android SDK (Command-line Tools)"

    # Dependências para emulador/SDK
    apt-get install -y -qq \
        lib32stdc++6 lib32z1 libgl1 \
        2>>"${LOG_FILE}"

    mkdir -p "${ANDROID_SDK_ROOT}/cmdline-tools"

    local sdk_zip="/tmp/cmdline-tools.zip"
    local sdk_url="https://dl.google.com/android/repository/commandlinetools-linux-${ANDROID_CMDLINE_VERSION}_latest.zip"

    if [[ ! -d "${ANDROID_SDK_ROOT}/cmdline-tools/latest" ]]; then
        log INFO "Baixando Android Command-line Tools..."
        wget -q "${sdk_url}" -O "${sdk_zip}" 2>>"${LOG_FILE}"
        unzip -q "${sdk_zip}" -d "${ANDROID_SDK_ROOT}/cmdline-tools/" 2>>"${LOG_FILE}"
        mv "${ANDROID_SDK_ROOT}/cmdline-tools/cmdline-tools" \
           "${ANDROID_SDK_ROOT}/cmdline-tools/latest" 2>/dev/null || true
        rm -f "${sdk_zip}"
    fi

    # Variáveis de ambiente Android
    cat >> /etc/environment <<EOF
export ANDROID_SDK_ROOT=${ANDROID_SDK_ROOT}
export ANDROID_HOME=${ANDROID_SDK_ROOT}
export PATH=\$PATH:${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin:${ANDROID_SDK_ROOT}/platform-tools
EOF

    export PATH="${PATH}:${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin:${ANDROID_SDK_ROOT}/platform-tools"

    # Aceitar licenças e instalar plataformas
    log INFO "Aceitando licenças Android SDK..."
    yes | sdkmanager --licenses 2>>"${LOG_FILE}" | grep -c "accepted" \
        | xargs -I{} log INFO "{} licenças aceitas"

    log INFO "Instalando plataformas e build-tools..."
    sdkmanager \
        "platforms;android-34" \
        "build-tools;34.0.0" \
        "platform-tools" \
        2>>"${LOG_FILE}"

    log INFO "Android SDK instalado em: ${ANDROID_SDK_ROOT}"
}

# ---------------------------------------------------------------------------
# Instalação do Gradle
# ---------------------------------------------------------------------------
install_gradle() {
    log STEP "Instalando Gradle ${GRADLE_VERSION}"

    if command_exists gradle; then
        log INFO "Gradle já instalado: $(gradle --version | grep Gradle)"
        return 0
    fi

    local gradle_zip="/tmp/gradle-${GRADLE_VERSION}.zip"
    wget -q "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip" \
        -O "${gradle_zip}" 2>>"${LOG_FILE}"
    unzip -q "${gradle_zip}" -d /opt/ 2>>"${LOG_FILE}"
    ln -sf "/opt/gradle-${GRADLE_VERSION}/bin/gradle" /usr/local/bin/gradle
    rm -f "${gradle_zip}"

    log INFO "Gradle instalado: $(gradle --version | grep Gradle)"
}

# ---------------------------------------------------------------------------
# Verificação final do ambiente
# ---------------------------------------------------------------------------
verify_installation() {
    log STEP "Verificando instalações"

    local checks=(
        "java:java -version 2>&1 | head -1"
        "docker:docker --version"
        "gradle:gradle --version 2>&1 | grep Gradle | head -1"
        "git:git --version"
        "curl:curl --version | head -1"
    )

    printf "\n%-15s %-50s %s\n" "FERRAMENTA" "VERSÃO" "STATUS"
    printf '%0.s─' {1..75}; echo

    for entry in "${checks[@]}"; do
        local tool="${entry%%:*}"
        local cmd="${entry#*:}"
        local version
        if version=$(eval "${cmd}" 2>/dev/null); then
            printf "%-15s %-50s ${GREEN}✔${RESET}\n" "${tool}" "${version:0:50}"
        else
            printf "%-15s %-50s ${RED}✘${RESET}\n" "${tool}" "NÃO ENCONTRADO"
            ((ERRORS++)) || true
        fi
    done
    echo
}

# ---------------------------------------------------------------------------
# Configuração de variáveis no .bashrc do usuário
# ---------------------------------------------------------------------------
configure_user_env() {
    [[ -z "${SUDO_USER:-}" ]] && return 0

    local user_home; user_home=$(getent passwd "${SUDO_USER}" | cut -d: -f6)
    local bashrc="${user_home}/.bashrc"

    log STEP "Configurando ambiente do usuário '${SUDO_USER}'"

    cat >> "${bashrc}" <<'BASHRC'

# ── Adicionado por setup_environment.sh ──────────────────────────────────
source /etc/environment 2>/dev/null || true
# SDKMAN
export SDKMAN_DIR=/usr/local/sdkman
[[ -s "${SDKMAN_DIR}/bin/sdkman-init.sh" ]] && source "${SDKMAN_DIR}/bin/sdkman-init.sh"
# ──────────────────────────────────────────────────────────────────────────
BASHRC

    chown "${SUDO_USER}:${SUDO_USER}" "${bashrc}"
    log INFO "Arquivo .bashrc atualizado"
}

# ---------------------------------------------------------------------------
# Resumo final
# ---------------------------------------------------------------------------
print_summary() {
    echo
    echo -e "${BOLD}╔══════════════════════════════════════════════════════╗${RESET}"
    echo -e "${BOLD}║           SETUP CONCLUÍDO                            ║${RESET}"
    echo -e "${BOLD}╚══════════════════════════════════════════════════════╝${RESET}"
    echo
    echo -e "  📁 Log completo : ${LOG_FILE}"
    echo -e "  ☕ Java         : ${JAVA_VERSION} (Temurin)"
    echo -e "  🐳 Docker       : $(docker --version 2>/dev/null || echo 'verificar')"
    echo -e "  🤖 Android SDK  : ${ANDROID_SDK_ROOT}"
    echo

    if [[ "${ERRORS}" -gt 0 ]]; then
        echo -e "  ${YELLOW}⚠  ${ERRORS} verificação(ões) com falha — revise o log${RESET}"
    else
        echo -e "  ${GREEN}✔  Todas as ferramentas instaladas com sucesso!${RESET}"
    fi

    echo
    echo -e "  ${YELLOW}ℹ  Execute: source ~/.bashrc   (para recarregar o ambiente)${RESET}"
    echo
}

# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------
main() {
    parse_args "$@"
    check_root
    check_os
    init_logging

    update_system
    install_docker
    install_java
    install_android_sdk
    install_gradle
    verify_installation
    configure_user_env
    print_summary
}

main "$@"
