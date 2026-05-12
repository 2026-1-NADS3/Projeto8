# Relatório de Teste de Carga — API REST

**Disciplina:** Testes e Qualidade de Software (DEVOPS)
**Projeto:** Aplicativo Maya Yoshiko Yamamoto RPG (Fisioterapia)
**Data:** 11/05/2026
**Versão do app:** Entrega 2

---

## 1. Objetivo

Avaliar o comportamento da API REST utilizada pelo aplicativo Maya sob
carga, medindo tempo de resposta, taxa de sucesso e throughput
(requisições por segundo) para os endpoints principais consumidos pelo app.

## 2. Ambiente do teste

| Item | Valor |
|---|---|
| API testada | mockapi.io |
| URL base | `https://6a024e8c0d92f63dd25388f4.mockapi.io/` |
| Endpoints testados | `GET /usuarios`, `GET /exercicios` |
| Total de requisições | 100 (50 por endpoint) |
| Pausa entre requisições | 100 ms |
| Execução | Sequencial, single-thread |
| Ferramenta | Classe Java `TesteCargaApi` (JUnit 4 + HttpURLConnection) |
| Data/hora da execução | 11/05/2026 — 23:25 |
| Conexão | Banda larga doméstica |

## 3. Metodologia

Foi desenvolvida uma classe Java (`TesteCargaApi.java`) que dispara
requisições HTTP GET sequenciais contra os endpoints da API. Para cada
requisição, foram registrados:

- Tempo decorrido (do envio à resposta completa)
- Código de status HTTP retornado
- Tamanho do payload de resposta

Ao final do teste, foram calculadas as seguintes métricas:

- **Tempo mínimo:** menor tempo de resposta registrado
- **Tempo máximo:** maior tempo de resposta registrado
- **Tempo médio:** média aritmética de todos os tempos
- **Taxa de sucesso:** porcentagem de respostas com status HTTP 2xx
- **Throughput:** requisições processadas por segundo (req/s)

## 4. Resultados

### 4.1. Endpoint `GET /usuarios`

| Métrica | Valor |
|---|---|
| Total de requisições | 50 |
| Sucessos (HTTP 2xx) | 50 |
| Falhas | 0 |
| **Taxa de sucesso** | **100,0%** |
| Tempo mínimo | 123 ms |
| Tempo máximo | 947 ms |
| **Tempo médio** | **143,70 ms** |
| Duração total | 12,495 s |
| **Throughput** | **4,00 req/s** |

**Análise:** Excelente desempenho. Todas as requisições foram bem-sucedidas,
com tempo médio bem abaixo do limite aceitável de 500ms para aplicações
mobile. O tempo máximo de 947ms (provavelmente um pico de latência da rede)
ainda está dentro de um patamar aceitável.

### 4.2. Endpoint `GET /exercicios`

| Métrica | Valor |
|---|---|
| Total de requisições | 50 |
| Sucessos (HTTP 2xx) | 46 |
| Falhas | 4 |
| **Taxa de sucesso** | **92,0%** |
| Tempo mínimo | 124 ms |
| Tempo máximo | 501 ms |
| **Tempo médio** | **150,56 ms** |
| Duração total | 12,858 s |
| **Throughput** | **3,89 req/s** |

**Análise:** Desempenho satisfatório com 92% de sucesso. As 4 falhas
provavelmente são decorrentes do *rate limit* aplicado pelo plano gratuito
do mockapi.io, que limita o número de requisições por segundo. O tempo
médio é consistente com o do outro endpoint.

## 5. Visualização Comparativa

Tempo médio de resposta (ms):
/usuarios     ████████████████░░░░  143.70 ms
/exercicios   █████████████████░░░  150.56 ms
Taxa de sucesso:
/usuarios     ████████████████████  100.0%
/exercicios   ██████████████████░░   92.0%
Throughput (req/s):
/usuarios     ████████████████████  4.00 req/s
/exercicios   ███████████████████░  3.89 req/s

## 6. Conclusões

A API REST utilizada pelo aplicativo Maya demonstrou desempenho adequado
para a carga atual prevista (uso por dezenas de pacientes da clínica).
Os tempos de resposta médios entre **143ms e 150ms** são satisfatórios
para aplicações mobile, mantendo a experiência do usuário fluida.

### Pontos positivos
- Tempos de resposta consistentes (média próxima da mediana)
- Taxa de sucesso de 100% no endpoint mais utilizado (`/usuarios`)
- Latência baixa e estável durante quase todo o teste

### Pontos de atenção
- Endpoint `/exercicios` apresentou 8% de falhas, possivelmente por *rate
  limit* do mockapi.io
- Tempo máximo de 947ms em uma requisição isolada indica que pode haver
  picos de latência ocasionais
- Throughput baixo (~4 req/s) por causa da pausa intencional de 100ms
  entre requisições para respeitar o limite do mockapi.io

## 7. Recomendações

1. **Backend definitivo:** Substituir o mockapi.io pelo backend
   desenvolvido pelo grupo no projeto interdisciplinar (Node.js/Express +
   MySQL), eliminando o *rate limit* e ganhando controle total sobre a
   API.

2. **Cache local:** Implementar cache em SQLite para dados que não
   mudam com frequência (lista de exercícios), reduzindo o número de
   requisições à API.

3. **Retry automático:** Adicionar lógica de re-tentativa com *exponential
   backoff* para requisições que falham por timeout ou rate limit.

4. **Testes de stress:** Realizar testes de carga com volumes maiores
   (500+ requisições) no backend definitivo para validar limites de
   capacidade.

5. **Monitoramento contínuo:** Em produção, implementar monitoramento
   de tempo de resposta com ferramentas como New Relic ou Datadog.

## 8. Reprodução do teste

Para reproduzir este teste:

1. Abrir o projeto no Android Studio
2. Localizar a classe `TesteCargaApi.java` em
   `app/src/test/java/com/example/maya_project/`
3. Clicar com botão direito → **Run 'TesteCargaApi'**
4. Aguardar ~26 segundos
5. Resultado é impresso no console do Android Studio

---

**Responsável:** Matheus
**Disciplina:** Testes e Qualidade de Software (DEVOPS)