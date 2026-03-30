# 🛠 Infraestrutura e Automação — Backend Cloud Native

Scripts Shell/Bash para automação do ciclo completo de desenvolvimento e deploy de uma aplicação backend (API REST Java/Spring Boot + Android).

---

## 📁 Estrutura do Projeto

```
scripts/
├── setup_environment.sh      # Script 1 — Setup do ambiente
├── monitor.sh                # Script 2 — Monitoramento do sistema
├── backup.sh                 # Script 3 — Backup de código e banco
├── service_manager.sh        # Script 4 — Gerenciamento de processos
├── docker-compose.yml        # Infraestrutura containerizada
├── api/
│   └── Dockerfile            # Build multi-stage da API REST
├── .env.example              # Variáveis de ambiente (template)
└── README.md                 # Este documento
```

---

## ⚙️ Pré-requisitos

| Ferramenta | Versão mínima | Necessário para |
|---|---|---|
| Bash | 5.0+ | Todos os scripts |
| Ubuntu/Debian | 20.04+ | `setup_environment.sh` |
| Docker Engine | 24+ | Docker Compose |
| Docker Compose | v2 plugin | `docker-compose.yml` |
| curl, bc, jq | qualquer | `monitor.sh` |

---

## 🚀 Início Rápido

```bash
# 1. Clone o repositório
git clone https://github.com/org/backend.git /opt/app/src
cd /opt/app/src/scripts

# 2. Copie e ajuste as variáveis de ambiente
cp .env.example .env
nano .env

# 3. Torne os scripts executáveis
chmod +x *.sh

# 4. Configure o ambiente (Java, Docker, Android SDK)
sudo ./setup_environment.sh

# 5. Suba os containers
docker compose up -d

# 6. Monitore o sistema
./monitor.sh --watch 30 --alert

# 7. Configure backups automáticos (cron)
(crontab -l 2>/dev/null; echo "0 2 * * * /opt/app/src/scripts/backup.sh") | crontab -
```

---

## 📜 Scripts

---

### 1. `setup_environment.sh` — Setup do Ambiente

Automatiza a instalação completa das dependências de desenvolvimento.

#### O que instala
- **Docker Engine** + Docker Compose Plugin (repositório oficial)
- **Java 17 Temurin** via SDKMAN
- **Android SDK** (Command-line Tools, platforms, build-tools)
- **Gradle 8.5**
- Ferramentas base: `git`, `curl`, `wget`, `unzip`, `build-essential`

#### Uso

```bash
sudo ./setup_environment.sh                    # instalação completa
sudo ./setup_environment.sh --skip-android     # sem Android SDK
sudo ./setup_environment.sh --skip-java        # sem Java
sudo ./setup_environment.sh --verbose          # saída detalhada
sudo ./setup_environment.sh --help
```

#### Variáveis de ambiente

| Variável | Padrão | Descrição |
|---|---|---|
| `JAVA_VERSION` | `17` | Versão do Java a instalar |
| `ANDROID_SDK_ROOT` | `/opt/android-sdk` | Diretório do SDK Android |
| `GRADLE_VERSION` | `8.5` | Versão do Gradle |
| `LOG_DIR` | `/var/log/devsetup` | Diretório de logs |

#### Conceitos demonstrados

| Conceito | Onde | Exemplo |
|---|---|---|
| Variáveis de ambiente | Cabeçalho | `export JAVA_VERSION="${JAVA_VERSION:-17}"` |
| Redirecionamento | Logs | `apt-get install ... 2>>"${LOG_FILE}"` |
| Pipes | Verificação | `docker --version \| awk '{print $3}'` |
| Permissões | Diretórios | `chmod 755 "${LOG_DIR}"` |
| Subshells | Leitura de arquivos | `source /etc/os-release` |

#### Log gerado

```
/var/log/devsetup/setup_20240315_143022.log
```

---

### 2. `monitor.sh` — Monitoramento do Sistema

Coleta métricas em tempo real e gera logs estruturados (JSONL) e relatórios HTML.

#### Métricas coletadas

- **CPU**: uso percentual calculado via `/proc/stat` (delta real entre duas leituras)
- **Memória**: total, usada, disponível (via `/proc/meminfo`)
- **Disco**: uso por ponto de montagem (via `df`)
- **Rede**: bytes RX/TX na interface principal (`/sys/class/net/`)
- **Load average**: 1m, 5m, 15m (via `uptime`)
- **Processos**: top 5 por CPU (via `ps aux`)
- **Docker**: containers rodando/parados

#### Uso

```bash
./monitor.sh                       # snapshot único no terminal
./monitor.sh --watch 30            # loop a cada 30 segundos
./monitor.sh --watch 10 --alert    # loop + alertas em log
./monitor.sh --report              # gera relatório HTML
./monitor.sh --help
```

#### Variáveis de ambiente

| Variável | Padrão | Descrição |
|---|---|---|
| `MONITOR_LOG_DIR` | `/var/log/app-monitor` | Diretório de métricas |
| `ALERT_CPU_THRESHOLD` | `85` | % CPU para alerta |
| `ALERT_MEM_THRESHOLD` | `90` | % memória para alerta |
| `ALERT_DISK_THRESHOLD` | `80` | % disco para alerta |

#### Formato de saída JSONL (uma linha por coleta)

```json
{
  "timestamp": "2024-03-15T14:30:22Z",
  "host": "prod-server-01",
  "cpu": { "usage_pct": 23.5 },
  "memory": { "total_kb": 8192000, "used_kb": 4096000, "usage_pct": 50.0 },
  "disk": { "mount": "/", "size": "100G", "used": "45G", "usage_pct": 45 },
  "network": { "interface": "eth0", "rx_mb": 1234.56, "tx_mb": 567.89 },
  "load_avg": { "1m": "0.45", "5m": "0.52", "15m": "0.48" },
  "docker": { "running": 4, "stopped": 1 }
}
```

#### Cron job (coleta a cada 5 minutos)

```cron
*/5 * * * * /opt/scripts/monitor.sh --alert >> /var/log/monitor_cron.log 2>&1
```

#### Conceitos demonstrados

| Conceito | Onde |
|---|---|
| Pipes | `ps aux \| awk ... \| head -5` |
| Redirecionamento | `>> "${METRICS_FILE}"` |
| Here-strings | Geração de JSON |
| Subshell / Process substitution | `< <(collect_disk)` |
| Aritmética Bash | `$(( delta_total - delta_idle ))` |

---

### 3. `backup.sh` — Backup Automatizado

Realiza backup do código-fonte (tar.gz) e banco de dados (pg_dump/mysqldump/SQLite) com retenção configurável.

#### Funcionalidades

- Backup comprimido do código-fonte (exclui `node_modules`, `build`, `.gradle`)
- Salva log de commits Git com branch e hash no nome do arquivo
- Dump do banco de dados (PostgreSQL, MySQL ou SQLite)
- Compressão gzip com nível 9
- Criptografia GPG opcional
- Limpeza automática por política de retenção
- Checksums SHA-256 de integridade
- Notificação via webhook (Slack/Discord)
- Restauração de backups

#### Uso

```bash
./backup.sh                              # backup completo (código + banco)
./backup.sh --source-only               # apenas código-fonte
./backup.sh --db-only                   # apenas banco de dados
./backup.sh --list                      # lista backups existentes
./backup.sh --restore /path/backup.tar.gz   # restaura um backup
./backup.sh --help
```

#### Variáveis de ambiente

| Variável | Padrão | Descrição |
|---|---|---|
| `BACKUP_DIR` | `/var/backups/devapp` | Destino dos backups |
| `SOURCE_DIR` | `/opt/app/src` | Código-fonte |
| `BACKUP_RETENTION_DAYS` | `7` | Dias de retenção |
| `DB_TYPE` | `postgres` | `postgres`, `mysql`, `sqlite` |
| `DB_HOST` | `localhost` | Host do banco |
| `DB_NAME` | `appdb` | Nome do banco |
| `DB_USER` | `appuser` | Usuário do banco |
| `DB_PASS` | _(vazio)_ | Senha (prefira `.pgpass`) |
| `COMPRESS` | `true` | Comprime dumps |
| `ENCRYPT` | `false` | Criptografa com GPG |
| `GPG_KEY_ID` | _(vazio)_ | ID da chave GPG |
| `NOTIFY_WEBHOOK` | _(vazio)_ | URL de webhook |

#### Estrutura de diretórios criada

```
/var/backups/devapp/
├── source/
│   ├── source_main_abc1234_20240315_020000.tar.gz
│   └── git_log_20240315_020000.txt
├── database/
│   └── db_appdb_20240315_020000.sql.gz
├── logs/
│   └── backup_20240315_020000.log
└── checksums_20240315_020000.sha256
```

#### Cron job (diário às 02:00)

```cron
0 2 * * * /opt/scripts/backup.sh >> /var/log/backup_cron.log 2>&1
```

#### Conceitos demonstrados

| Conceito | Onde |
|---|---|
| Variáveis com default | `${BACKUP_DIR:-/var/backups}` |
| Redirecionamento de stderr | `2>>"${LOG_FILE}"` |
| Pipes em while | `while IFS= read -r line; do ... done < <(find ...)` |
| Subprocessos | `nohup`, `background (&)` |
| Cron | Documentado no cabeçalho |

---

### 4. `service_manager.sh` — Gerenciamento de Processos

Controla o ciclo de vida dos serviços backend (API e Worker) com suporte a Docker e watchdog de restart automático.

#### Uso

```bash
# Iniciar serviços
./service_manager.sh start api
./service_manager.sh start worker
./service_manager.sh start all

# Parar serviços
./service_manager.sh stop api
./service_manager.sh stop all

# Reiniciar
./service_manager.sh restart api

# Status detalhado (PID, CPU, MEM, uptime, health check)
./service_manager.sh status

# Ver logs com destaque de erros
./service_manager.sh logs api 100
./service_manager.sh logs watchdog

# Watchdog — reinício automático em caso de falha
./service_manager.sh watchdog
```

#### Variáveis de ambiente

| Variável | Padrão | Descrição |
|---|---|---|
| `APP_DIR` | `/opt/app` | Raiz da aplicação |
| `API_PORT` | `8080` | Porta da API |
| `JAVA_HOME` | `/usr/lib/jvm/java-17-...` | Diretório do JDK |
| `JAVA_OPTS` | `-Xms256m -Xmx1g ...` | Opções JVM |
| `WORKER_ENABLED` | `true` | Habilita worker |
| `USE_DOCKER` | `false` | Usa Docker Compose |
| `WATCHDOG_INTERVAL` | `30` | Segundos entre checks |
| `WATCHDOG_MAX_RESTARTS` | `5` | Máximo de restarts |

#### Como o watchdog funciona

```
Loop (a cada WATCHDOG_INTERVAL segundos):
  ├── Verifica se API está rodando (kill -0 <PID>)
  │   └── Se parada → start_api()
  │       └── Se restart_count > MAX → aborta com exit 1
  └── Verifica se Worker está rodando
      └── Se parado → start_worker()
```

#### Cron para watchdog (a cada 2 minutos)

```cron
*/2 * * * * /opt/scripts/service_manager.sh watchdog >> /var/log/watchdog_cron.log 2>&1
```

#### Conceitos demonstrados

| Conceito | Onde |
|---|---|
| `nohup` + `&` | Processo em background |
| PID files | `/var/run/backend/*.pid` |
| `kill -0` | Verifica se processo existe sem matar |
| Sinais (`SIGTERM`/`SIGKILL`) | Graceful shutdown |
| `/dev/tcp` | Verificação de porta TCP sem netcat |
| Redirecionamento `>>` | Logs append |

---

## 🐳 Docker Compose

O `docker-compose.yml` define toda a infraestrutura containerizada:

| Serviço | Imagem | Porta | Perfil |
|---|---|---|---|
| `api` | `backend-api:latest` | 8080 | padrão |
| `postgres` | `postgres:16-alpine` | 5432 | padrão |
| `redis` | `redis:7-alpine` | 6379 | padrão |
| `worker` | `backend-worker:latest` | — | `full` |
| `nginx` | `nginx:1.25-alpine` | 80/443 | `full` |
| `adminer` | `adminer:4` | 8888 | `dev` |

### Comandos úteis

```bash
# Desenvolvimento (API + banco + Redis)
docker compose up -d

# Stack completa (+ worker + nginx)
docker compose --profile full up -d

# Stack de dev (+ adminer para gerenciar banco)
docker compose --profile dev up -d

# Ver logs de todos os serviços
docker compose logs -f

# Ver apenas logs da API
docker compose logs -f api

# Rebuild da imagem da API
docker compose build --no-cache api

# Inspecionar saúde dos containers
docker compose ps

# Parar tudo
docker compose down

# Parar e remover volumes (CUIDADO: apaga dados!)
docker compose down -v
```

---

## 🕐 Cron Jobs Recomendados

Configure com `crontab -e`:

```cron
# Monitoramento a cada 5 minutos
*/5 * * * * /opt/scripts/monitor.sh --alert >> /var/log/monitor_cron.log 2>&1

# Backup diário às 02:00
0 2 * * * /opt/scripts/backup.sh >> /var/log/backup_cron.log 2>&1

# Watchdog a cada 2 minutos
*/2 * * * * /opt/scripts/service_manager.sh watchdog >> /var/log/watchdog.log 2>&1

# Relatório HTML semanal (domingo às 08:00)
0 8 * * 0 /opt/scripts/monitor.sh --report >> /var/log/monitor_report.log 2>&1
```

---

## 🔐 Permissões

```bash
# Scripts executáveis apenas pelo owner e grupo
chmod 750 *.sh

# Diretórios de log acessíveis pelo usuário da aplicação
chown -R appuser:appgroup /var/log/backend /var/run/backend

# .env com credenciais: apenas owner pode ler
chmod 600 .env
```

---

## 📊 Conceitos Shell Demonstrados

| Conceito | Scripts |
|---|---|
| Variáveis de ambiente e defaults | Todos |
| Pipes (`\|`) | `monitor.sh`, `backup.sh` |
| Redirecionamento (`>`, `>>`, `2>`) | Todos |
| Process substitution (`< <()`) | `monitor.sh` |
| Here-strings e here-docs | `monitor.sh`, `setup_environment.sh` |
| Sinais e tratamento de processos | `service_manager.sh` |
| `set -euo pipefail` | Todos |
| Funções e escopo local | Todos |
| Arrays | `backup.sh`, `setup_environment.sh` |
| Aritmética (`$(( ))`) | `monitor.sh` |
| Cron jobs | Documentação e cabeçalhos |
| Permissões (`chmod`, `chown`) | `setup_environment.sh`, `backup.sh` |
| `nohup` e background | `service_manager.sh` |
| PID files | `service_manager.sh` |
| Compressão e arquivamento | `backup.sh` |

---

## 🆘 Troubleshooting

**Script não executa:**
```bash
chmod +x script.sh
bash -x script.sh  # modo debug
```

**Erro de permissão em diretórios de log:**
```bash
sudo mkdir -p /var/log/backend /var/run/backend
sudo chown $(whoami):$(whoami) /var/log/backend /var/run/backend
```

**API não sobe (timeout):**
```bash
tail -100 /var/log/backend/api.log | grep -i error
```

**Docker Compose: imagem não encontrada:**
```bash
docker compose build api
docker compose up -d
```

---

## 📝 Licença

MIT — use, modifique e distribua livremente.
