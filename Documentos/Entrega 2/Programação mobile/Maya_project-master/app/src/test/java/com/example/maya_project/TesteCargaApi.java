package com.example.maya_project;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;

/**
 * Teste de carga do servidor (API REST).
 *
 * Dispara um número configurável de requisições contra a API
 * (mockapi.io neste projeto) e mede:
 *   - tempo médio de resposta
 *   - tempo máximo e mínimo
 *   - taxa de sucesso (status 2xx)
 *   - requisições por segundo (throughput)
 *
 * Para executar:
 *   Botão direito na classe → Run 'TesteCargaApi'
 *
 * O relatório completo é impresso no console e também salvo
 * em docs/RELATORIO_TESTE_CARGA.md (manualmente).
 */
public class TesteCargaApi {

    // URL base da API (a mesma usada no app)
    private static final String URL_BASE = "https://6a024e8c0d92f63dd25388f4.mockapi.io/";

    // quantidade de requisições por endpoint
    private static final int TOTAL_REQUISICOES = 50;

    // pausa entre requisições (ms) — pra não estourar rate limit do mockapi
    private static final int PAUSA_ENTRE_REQUISICOES_MS = 100;

    @Test
    public void testCargaEndpointUsuarios() throws Exception {
        executarTesteCarga("usuarios", TOTAL_REQUISICOES);
    }

    @Test
    public void testCargaEndpointExercicios() throws Exception {
        executarTesteCarga("exercicios", TOTAL_REQUISICOES);
    }

    /**
     * Executa o teste de carga para um endpoint específico.
     */
    private void executarTesteCarga(String endpoint, int totalRequisicoes) throws Exception {
        System.out.println("\n========================================");
        System.out.println("TESTE DE CARGA: GET /" + endpoint);
        System.out.println("Total de requisições: " + totalRequisicoes);
        System.out.println("========================================\n");

        ArrayList<Long> tempos = new ArrayList<>();
        int sucessos = 0;
        int falhas = 0;

        long inicioTotal = System.currentTimeMillis();

        for (int i = 1; i <= totalRequisicoes; i++) {
            long inicio = System.currentTimeMillis();
            boolean sucesso = fazerRequisicao(URL_BASE + endpoint);
            long fim = System.currentTimeMillis();

            long duracao = fim - inicio;
            tempos.add(duracao);

            if (sucesso) sucessos++;
            else falhas++;

            // imprime progresso a cada 10 requisições
            if (i % 10 == 0) {
                System.out.println("Requisições concluídas: " + i + "/" + totalRequisicoes);
            }

            Thread.sleep(PAUSA_ENTRE_REQUISICOES_MS);
        }

        long fimTotal = System.currentTimeMillis();
        long duracaoTotalMs = fimTotal - inicioTotal;

        // calcula estatísticas
        long tempoMin = Collections.min(tempos);
        long tempoMax = Collections.max(tempos);
        double tempoMedio = tempos.stream().mapToLong(Long::longValue).average().orElse(0);
        double throughput = (totalRequisicoes * 1000.0) / duracaoTotalMs;
        double taxaSucesso = (sucessos * 100.0) / totalRequisicoes;

        // imprime relatório
        System.out.println("\n=== RESULTADO ===");
        System.out.println("Endpoint testado:       GET /" + endpoint);
        System.out.println("Total de requisições:   " + totalRequisicoes);
        System.out.println("Sucessos (HTTP 2xx):    " + sucessos);
        System.out.println("Falhas:                 " + falhas);
        System.out.println("Taxa de sucesso:        " + String.format("%.1f%%", taxaSucesso));
        System.out.println("Tempo mínimo:           " + tempoMin + " ms");
        System.out.println("Tempo máximo:           " + tempoMax + " ms");
        System.out.println("Tempo médio:            " + String.format("%.2f ms", tempoMedio));
        System.out.println("Duração total do teste: " + (duracaoTotalMs / 1000.0) + " s");
        System.out.println("Throughput:             " + String.format("%.2f req/s", throughput));
        System.out.println("========================================\n");
    }

    /**
     * Faz uma requisição GET e retorna true se status 2xx.
     */
    private boolean fazerRequisicao(String urlStr) {
        HttpURLConnection conexao = null;
        try {
            URL url = new URL(urlStr);
            conexao = (HttpURLConnection) url.openConnection();
            conexao.setRequestMethod("GET");
            conexao.setConnectTimeout(10000); // 10s
            conexao.setReadTimeout(10000);

            int status = conexao.getResponseCode();

            // consome o body pra liberar a conexão
            BufferedReader leitor = new BufferedReader(
                    new InputStreamReader(conexao.getInputStream()));
            while (leitor.readLine() != null) { /* descarta */ }
            leitor.close();

            return status >= 200 && status < 300;
        } catch (Exception e) {
            return false;
        } finally {
            if (conexao != null) conexao.disconnect();
        }
    }
}