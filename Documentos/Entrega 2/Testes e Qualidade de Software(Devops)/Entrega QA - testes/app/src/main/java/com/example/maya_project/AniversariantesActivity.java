package com.example.maya_project;

import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Calendar;

/**
 * Tela de Aniversariantes (apenas admin).
 * Filtros: semana, mês, ano.
 */
public class AniversariantesActivity extends AppCompatActivity {

    private LinearLayout listaAniversariantes;
    private TextView txtFiltroAtivo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_aniversariantes);

        listaAniversariantes = findViewById(R.id.listaAniversariantes);
        txtFiltroAtivo = findViewById(R.id.txtFiltroAtivo);

        Button botaoVoltar = findViewById(R.id.botaoVoltar);
        Button botaoFiltroSemana = findViewById(R.id.botaoFiltroSemana);
        Button botaoFiltroMes = findViewById(R.id.botaoFiltroMes);
        Button botaoFiltroAno = findViewById(R.id.botaoFiltroAno);

        botaoVoltar.setOnClickListener(v -> finish());

        botaoFiltroSemana.setOnClickListener(v -> {
            txtFiltroAtivo.setText("Filtro: aniversariantes dos próximos 7 dias");
            mostrarLista(BancoDeDados.aniversariantesDaSemana());
        });

        botaoFiltroMes.setOnClickListener(v -> {
            Calendar hoje = Calendar.getInstance();
            int mesAtual = hoje.get(Calendar.MONTH) + 1;
            txtFiltroAtivo.setText("Filtro: aniversariantes de " + nomeDoMes(mesAtual));
            mostrarLista(BancoDeDados.aniversariantesDoMes(mesAtual));
        });

        botaoFiltroAno.setOnClickListener(v -> {
            txtFiltroAtivo.setText("Filtro: ano todo (em ordem)");
            mostrarLista(BancoDeDados.aniversariantesDoAno());
        });
    }

    private void mostrarLista(ArrayList<Usuario> aniversariantes) {
        listaAniversariantes.removeAllViews();

        if (aniversariantes.isEmpty()) {
            adicionarMensagemVazia();
            return;
        }

        for (Usuario u : aniversariantes) {
            adicionarCard(u);
        }
    }

    private void adicionarMensagemVazia() {
        TextView aviso = new TextView(this);
        aviso.setText("Nenhum paciente encontrado para esse filtro.");
        aviso.setTextColor(ContextCompat.getColor(this, R.color.cinzaTexto));
        aviso.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        aviso.setGravity(Gravity.CENTER);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(20), 0, 0);
        aviso.setLayoutParams(params);

        listaAniversariantes.addView(aviso);
    }

    private void adicionarCard(Usuario u) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.card_fundo);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(0, 0, 0, dp(10));
        card.setLayoutParams(cardParams);

        TextView txtNome = new TextView(this);
        txtNome.setText(u.getNome());
        txtNome.setTextColor(ContextCompat.getColor(this, R.color.azulPetroleo));
        txtNome.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        txtNome.setTypeface(null, android.graphics.Typeface.BOLD);
        card.addView(txtNome);

        String dataAniv = "Aniversário: "
                + (u.getDiaAniversario() < 10 ? "0" + u.getDiaAniversario() : u.getDiaAniversario())
                + " de " + nomeDoMes(u.getMesAniversario());

        TextView txtData = new TextView(this);
        txtData.setText(dataAniv);
        txtData.setTextColor(ContextCompat.getColor(this, R.color.coral));
        txtData.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        txtData.setTypeface(null, android.graphics.Typeface.BOLD);

        LinearLayout.LayoutParams pData = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        pData.setMargins(0, dp(4), 0, 0);
        txtData.setLayoutParams(pData);
        card.addView(txtData);

        TextView txtCelular = new TextView(this);
        txtCelular.setText("Celular: " + u.getCelular());
        txtCelular.setTextColor(ContextCompat.getColor(this, R.color.cinzaTexto));
        txtCelular.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);

        LinearLayout.LayoutParams pCel = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        pCel.setMargins(0, dp(2), 0, 0);
        txtCelular.setLayoutParams(pCel);
        card.addView(txtCelular);

        listaAniversariantes.addView(card);
    }

    private String nomeDoMes(int mes) {
        String[] meses = {
                "janeiro", "fevereiro", "março", "abril",
                "maio", "junho", "julho", "agosto",
                "setembro", "outubro", "novembro", "dezembro"
        };
        if (mes < 1 || mes > 12) return "—";
        return meses[mes - 1];
    }

    private int dp(int valor) {
        float densidade = getResources().getDisplayMetrics().density;
        return Math.round(valor * densidade);
    }
}
