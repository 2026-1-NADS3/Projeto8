package com.example.maya_project;

import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;

/**
 * Tela de Histórico do paciente.
 * Mostra indicadores, gráfico de barras com a evolução da dor
 * e lista detalhada de check-ins.
 */
public class HistoricoActivity extends AppCompatActivity {

    private LinearLayout listaCheckins;
    private LinearLayout containerGrafico;
    private TextView txtTotal;
    private TextView txtSemana;
    private TextView txtMediaDor;

    private BancoLocal bancoLocal;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_historico);

        bancoLocal = new BancoLocal(this);

        listaCheckins = findViewById(R.id.listaCheckins);
        containerGrafico = findViewById(R.id.containerGrafico);
        txtTotal = findViewById(R.id.txtTotal);
        txtSemana = findViewById(R.id.txtSemana);
        txtMediaDor = findViewById(R.id.txtMediaDor);

        Button botaoVoltar = findViewById(R.id.botaoVoltar);
        botaoVoltar.setOnClickListener(v -> finish());

        carregarDados();
    }

    private void carregarDados() {
        Usuario logado = BancoDeDados.getUsuarioLogado(this);
        if (logado == null) {
            finish();
            return;
        }

        String cpf = logado.getCpf();

        // ====== Indicadores ======
        int total = bancoLocal.contarCheckins(cpf);
        int semana = bancoLocal.contarUltimaSemana(cpf);
        double mediaDor = bancoLocal.mediaDor(cpf);

        txtTotal.setText(String.valueOf(total));
        txtSemana.setText(String.valueOf(semana));

        if (total > 0) {
            txtMediaDor.setText(String.format(Locale.getDefault(), "Média: %.1f / 10", mediaDor));
        } else {
            txtMediaDor.setText("Faça seu primeiro check-in!");
        }

        // ====== Lista de check-ins ======
        ArrayList<Checkin> checkins = bancoLocal.listarCheckins(cpf);

        // ====== Gráfico de barras ======
        // (lista vem do mais novo pro mais antigo; inverte pra mostrar tempo da
        // esquerda pra direita)
        ArrayList<Checkin> paraGrafico = new ArrayList<>();
        int max = Math.min(checkins.size(), 10); // últimos 10
        for (int i = 0; i < max; i++) {
            paraGrafico.add(checkins.get(i));
        }
        Collections.reverse(paraGrafico);
        montarGrafico(paraGrafico);

        // ====== Lista ======
        if (checkins.isEmpty()) {
            adicionarMensagemVazia();
        } else {
            for (Checkin c : checkins) {
                adicionarCardCheckin(c);
            }
        }
    }

    /**
     * Monta um gráfico de barras dentro do containerGrafico.
     * Cada barra representa um check-in, com altura proporcional ao nível de dor
     * e cor variando do azul claro (pouca dor) ao coral (muita dor).
     */
    private void montarGrafico(ArrayList<Checkin> checkins) {
        containerGrafico.removeAllViews();

        if (checkins.isEmpty()) {
            TextView vazio = new TextView(this);
            vazio.setText("Faça check-ins para ver sua evolução aqui");
            vazio.setTextColor(ContextCompat.getColor(this, R.color.cinzaTexto));
            vazio.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            vazio.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams pv = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
            vazio.setLayoutParams(pv);
            containerGrafico.addView(vazio);
            return;
        }

        // altura máxima da barra em pixels (deixamos uma margem em cima pro número)
        int alturaMax = dp(140);

        for (Checkin c : checkins) {
            // container vertical pra cada coluna do gráfico: número em cima, barra embaixo
            LinearLayout coluna = new LinearLayout(this);
            coluna.setOrientation(LinearLayout.VERTICAL);
            coluna.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);

            LinearLayout.LayoutParams colunaParams = new LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    1f); // weight = 1, distribui igualmente
            colunaParams.setMargins(dp(2), 0, dp(2), 0);
            coluna.setLayoutParams(colunaParams);

            // número da dor (em cima da barra)
            TextView txtValor = new TextView(this);
            txtValor.setText(String.valueOf(c.getNivelDor()));
            txtValor.setTextColor(ContextCompat.getColor(this, R.color.cinzaTexto));
            txtValor.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
            txtValor.setGravity(Gravity.CENTER);
            txtValor.setTypeface(null, android.graphics.Typeface.BOLD);
            coluna.addView(txtValor);

            // a barra em si (View colorida)
            View barra = new View(this);

            // altura proporcional à dor (mínimo de 4dp pra ficar visível mesmo com 0)
            int alturaBarra = dp(4) + (int) ((c.getNivelDor() / 10.0) * alturaMax);

            // cor varia conforme o nível: claro → médio → coral
            int corBarra;
            if (c.getNivelDor() <= 3) {
                corBarra = ContextCompat.getColor(this, R.color.azulClaro);
            } else if (c.getNivelDor() <= 6) {
                corBarra = ContextCompat.getColor(this, R.color.azulPetroleo);
            } else {
                corBarra = ContextCompat.getColor(this, R.color.coral);
            }
            barra.setBackgroundColor(corBarra);

            LinearLayout.LayoutParams barraParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    alturaBarra);
            barra.setLayoutParams(barraParams);
            coluna.addView(barra);

            containerGrafico.addView(coluna);
        }
    }

    private void adicionarMensagemVazia() {
        TextView aviso = new TextView(this);
        aviso.setText("Você ainda não fez nenhum check-in.\nFaça exercícios e registre sua evolução!");
        aviso.setTextColor(ContextCompat.getColor(this, R.color.cinzaTexto));
        aviso.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        aviso.setGravity(Gravity.CENTER);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(20), 0, 0);
        aviso.setLayoutParams(params);

        listaCheckins.addView(aviso);
    }

    private void adicionarCardCheckin(Checkin c) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.card_fundo);
        card.setPadding(dp(16), dp(12), dp(16), dp(12));

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(0, 0, 0, dp(8));
        card.setLayoutParams(cardParams);

        TextView txtNome = new TextView(this);
        txtNome.setText(c.getNomeExercicio());
        txtNome.setTextColor(ContextCompat.getColor(this, R.color.azulPetroleo));
        txtNome.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        txtNome.setTypeface(null, android.graphics.Typeface.BOLD);
        card.addView(txtNome);

        TextView txtData = new TextView(this);
        txtData.setText("📅 " + c.getData());
        txtData.setTextColor(ContextCompat.getColor(this, R.color.cinzaTexto));
        txtData.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        LinearLayout.LayoutParams pd = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        pd.setMargins(0, dp(2), 0, 0);
        txtData.setLayoutParams(pd);
        card.addView(txtData);

        TextView txtDor = new TextView(this);
        txtDor.setText("Dor: " + c.getNivelDor() + "/10");
        txtDor.setTextColor(ContextCompat.getColor(this, R.color.coral));
        txtDor.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        txtDor.setTypeface(null, android.graphics.Typeface.BOLD);
        card.addView(txtDor);

        listaCheckins.addView(card);
    }

    private int dp(int valor) {
        float densidade = getResources().getDisplayMetrics().density;
        return Math.round(valor * densidade);
    }
}