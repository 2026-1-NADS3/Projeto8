# =============================================================================
# api/Dockerfile
# Containerização da API REST (Spring Boot / Java 17)
#
# Estratégia: Multi-stage build
#   Stage 1 — "builder"  : compila o projeto com JDK completo
#   Stage 2 — "runtime"  : executa apenas com JRE mínimo (Alpine)
#
# Benefícios do multi-stage:
#   - Imagem final ~85 MB em vez de ~400 MB (sem JDK, sem Gradle)
#   - Sem código-fonte exposto na imagem de produção
#   - Layers de dependências em cache separadas do código da aplicação
#
# Build:  docker build -t backend-api:1.0 ./api
# Run:    docker run -p 8080:8080 --env-file ../.env backend-api:1.0
# =============================================================================


# ─────────────────────────────────────────────────────────────────────────────
# STAGE 1 — Builder
# Usa JDK completo (eclipse-temurin = distribuição OpenJDK de referência)
# ─────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jdk-alpine AS builder

# ARGs disponíveis apenas durante o build (não persistem na imagem)
ARG APP_VERSION=1.0.0
ARG BUILD_DATE
ARG GIT_COMMIT

LABEL stage="builder"

WORKDIR /workspace

# ── Etapa 1: cache de dependências ──────────────────────────────────────────
# Copia apenas os arquivos de configuração do Gradle ANTES do código-fonte.
# Docker reutiliza este layer enquanto build.gradle não mudar —
# evita baixar todas as dependências a cada alteração de código.
COPY gradlew .
COPY gradle/ gradle/
COPY build.gradle settings.gradle ./

# Torna o wrapper executável e baixa dependências
RUN chmod +x gradlew && \
    ./gradlew dependencies --no-daemon --quiet 2>/dev/null || true

# ── Etapa 2: compilação ──────────────────────────────────────────────────────
# Copia o código-fonte somente após cache de deps
COPY src/ src/

# Compila e gera o JAR executável (bootJar = fat JAR do Spring Boot)
# -x test: pula testes na etapa de build da imagem (testes rodam no CI)
RUN ./gradlew bootJar --no-daemon -x test && \
    # Extrai o JAR em layers separadas para otimizar o cache da imagem runtime
    mkdir -p build/extracted && \
    java -Djarmode=layertools -jar build/libs/*.jar extract \
         --destination build/extracted


# ─────────────────────────────────────────────────────────────────────────────
# STAGE 2 — Runtime
# Apenas JRE Alpine (~85 MB) — sem compilador, sem código-fonte
# ─────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine AS runtime

# ── Metadados OCI (Open Container Initiative) ────────────────────────────────
ARG APP_VERSION=1.0.0
ARG BUILD_DATE
ARG GIT_COMMIT
LABEL org.opencontainers.image.title="backend-api" \
      org.opencontainers.image.version="${APP_VERSION}" \
      org.opencontainers.image.created="${BUILD_DATE}" \
      org.opencontainers.image.revision="${GIT_COMMIT}" \
      org.opencontainers.image.description="API REST Spring Boot — Cloud Native" \
      maintainer="devteam@example.com"

# ── Usuário não-root (segurança) ─────────────────────────────────────────────
# Rodar como root dentro do container é má prática — se houver escape do
# container, o atacante teria privilégios mínimos no host.
RUN addgroup -S appgroup && \
    adduser  -S appuser -G appgroup -h /app -s /sbin/nologin

# ── Dependências do sistema ──────────────────────────────────────────────────
# curl: necessário para o HEALTHCHECK
# tzdata: suporte a timezone (America/Sao_Paulo para logs)
RUN apk add --no-cache \
        curl \
        tzdata && \
    cp /usr/share/zoneinfo/America/Sao_Paulo /etc/localtime && \
    echo "America/Sao_Paulo" > /etc/timezone && \
    apk del tzdata && \
    rm -rf /var/cache/apk/*

# ── Estrutura de diretórios ──────────────────────────────────────────────────
RUN mkdir -p /app/logs /app/config /app/tmp && \
    chown -R appuser:appgroup /app

WORKDIR /app

# ── Copia layers extraídas (cache granular) ──────────────────────────────────
# Ordem importa: layers que mudam menos ficam primeiro (melhor cache)
# 1. Dependências de terceiros (raramente mudam)
# 2. Dependências do Spring Boot (mudam com upgrades)
# 3. Snapshot dependencies (mudam às vezes)
# 4. Código da aplicação (muda frequentemente)
COPY --from=builder --chown=appuser:appgroup /workspace/build/extracted/dependencies/          ./
COPY --from=builder --chown=appuser:appgroup /workspace/build/extracted/spring-boot-loader/   ./
COPY --from=builder --chown=appuser:appgroup /workspace/build/extracted/snapshot-dependencies/ ./
COPY --from=builder --chown=appuser:appgroup /workspace/build/extracted/application/          ./

# ── Variáveis de ambiente com defaults seguros ────────────────────────────────
# Valores de runtime — podem ser sobrescritos via docker-compose.yml ou --env
ENV SERVER_PORT=8080 \
    JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/app/logs/heapdump.hprof" \
    SPRING_PROFILES_ACTIVE=production \
    TZ=America/Sao_Paulo \
    APP_LOG_DIR=/app/logs \
    APP_TMP_DIR=/app/tmp

# ── Porta exposta ─────────────────────────────────────────────────────────────
# EXPOSE é documentação — o mapeamento real ocorre no docker-compose ou -p
EXPOSE ${SERVER_PORT}

# ── Usuário não-root ativo ────────────────────────────────────────────────────
USER appuser

# ── Healthcheck ──────────────────────────────────────────────────────────────
# Docker monitora este endpoint; containers "unhealthy" são reiniciados
# pelo restart policy e removidos do load balancer em produção
HEALTHCHECK \
    --interval=30s \
    --timeout=10s \
    --start-period=90s \
    --retries=3 \
    CMD curl -f http://localhost:${SERVER_PORT}/actuator/health \
        | grep -q '"status":"UP"' || exit 1

# ── Entrypoint ───────────────────────────────────────────────────────────────
# Shell form (sh -c) para expandir variáveis de ambiente em runtime.
# JvmMainClass é o launcher do Spring Boot Layered JAR.
ENTRYPOINT ["sh", "-c", \
    "exec java ${JAVA_OPTS} \
     -Djava.security.egd=file:/dev/./urandom \
     -Dserver.port=${SERVER_PORT} \
     -Dspring.profiles.active=${SPRING_PROFILES_ACTIVE} \
     -Dlogging.file.path=${APP_LOG_DIR} \
     org.springframework.boot.loader.launch.JarLauncher"]
