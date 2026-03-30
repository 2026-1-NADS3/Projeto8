# =============================================================================
# Dockerfile — API REST (Spring Boot)
# Multi-stage build: builder + runtime mínimo
# =============================================================================

# ── Stage 1: Build ────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jdk-alpine AS builder

ARG APP_PORT=8080
ENV APP_PORT=${APP_PORT}

WORKDIR /workspace

# Cache de dependências (Gradle)
COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon -q 2>/dev/null || true

# Copia código-fonte e compila
COPY src src
RUN ./gradlew bootJar --no-daemon -q && \
    mkdir -p build/dependency && \
    cd build/dependency && \
    jar -xf ../libs/*.jar

# ── Stage 2: Runtime ──────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine AS runtime

# Metadados
LABEL maintainer="devteam@example.com" \
      app.name="backend-api" \
      app.version="${APP_VERSION:-dev}"

# Usuário não-root para segurança
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Diretórios da aplicação
RUN mkdir -p /app/logs /app/config && \
    chown -R appuser:appgroup /app

WORKDIR /app

# Copia layers extraídos (otimiza cache Docker)
ARG DEPENDENCY=/workspace/build/dependency
COPY --from=builder --chown=appuser:appgroup ${DEPENDENCY}/BOOT-INF/lib     ./lib
COPY --from=builder --chown=appuser:appgroup ${DEPENDENCY}/META-INF          ./META-INF
COPY --from=builder --chown=appuser:appgroup ${DEPENDENCY}/BOOT-INF/classes  .

# Instala curl (para healthcheck) e remove cache
RUN apk add --no-cache curl && \
    rm -rf /var/cache/apk/*

USER appuser

# Expõe porta da aplicação
EXPOSE ${APP_PORT}

# Ponto de entrada com opções JVM configuráveis via ENV
ENTRYPOINT ["sh", "-c", \
  "exec java ${JAVA_OPTS} \
   -cp .:lib/* \
   -Dserver.port=${APP_PORT} \
   -Dapp.log.dir=/app/logs \
   com.example.ApiApplication"]
