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

/**
 * Tela de consultas agendadas.
 * Paciente vê só as dele; admin vê todas.
 */
public class MinhasConsultasActivity extends AppCompatActivity {

    private LinearLayout listaConsultas;
    private TextView txtTitulo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_minhas_consultas);

        listaConsultas = findViewById(R.id.listaConsultas);
        txtTitulo = findViewById(R.id.txtTitulo);

        Button botaoVoltar = findViewById(R.id.botaoVoltar);
        botaoVoltar.setOnClickListener(v -> finish());

        Usuario logado = BancoDeDados.getUsuarioLogado();
        if (logado == null) {
            finish();
            return;
        }

        ArrayList<Consulta> consultas;
        boolean mostrarNome;

        if (logado.isEhAdmin()) {
            txtTitulo.setText("Todas as Consultas");
            consultas = BancoDeDados.listarTodasConsultas();
            mostrarNome = true;
        } else {
            txtTitulo.setText("Minhas Consultas");
            consultas = BancoDeDados.consultasDoCpf(logado.getCpf());
            mostrarNome = false;
        }

        if (consultas.isEmpty()) {
            adicionarMensagemVazia();
        } else {
            for (Consulta c : consultas) {
                adicionarCardConsulta(c, mostrarNome);
            }
        }
    }

    private void adicionarMensagemVazia() {
        TextView aviso = new TextView(this);
        aviso.setText("Nenhuma consulta agendada por enquanto.");
        aviso.setTextColor(ContextCompat.getColor(this, R.color.cinzaTexto));
        aviso.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        aviso.setGravity(Gravity.CENTER);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(40), 0, 0);
        aviso.setLayoutParams(params);

        listaConsultas.addView(aviso);
    }

    private void adicionarCardConsulta(Consulta consulta, boolean mostrarNome) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.card_fundo);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(0, 0, 0, dp(10));
        card.setLayoutParams(cardParams);

        if (mostrarNome) {
            Usuario paciente = BancoDeDados.buscarPorCpf(consulta.getCpfPaciente());
            String nome = (paciente != null) ? paciente.getNome() : "Paciente";

            TextView txtNome = new TextView(this);
            txtNome.setText(nome);
            txtNome.setTextColor(ContextCompat.getColor(this, R.color.azulPetroleo));
            txtNome.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            txtNome.setTypeface(null, android.graphics.Typeface.BOLD);
            card.addView(txtNome);
        }

        TextView txtData = new TextView(this);
        txtData.setText("Data: " + consulta.getData());
        txtData.setTextColor(ContextCompat.getColor(this, R.color.cinzaTexto));
        txtData.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        if (mostrarNome) {
            LinearLayout.LayoutParams pData = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            pData.setMargins(0, dp(6), 0, 0);
            txtData.setLayoutParams(pData);
        }
        card.addView(txtData);

        TextView txtHora = new TextView(this);
        txtHora.setText("Hora: " + consulta.getHora());
        txtHora.setTextColor(ContextCompat.getColor(this, R.color.marrom));
        txtHora.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        txtHora.setTypeface(null, android.graphics.Typeface.BOLD);
        card.addView(txtHora);

        listaConsultas.addView(card);
    }

    private int dp(int valor) {
        float densidade = getResources().getDisplayMetrics().density;
        return Math.round(valor * densidade);
    }
}
