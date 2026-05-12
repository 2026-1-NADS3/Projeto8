package com.example.maya_project;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Painel do paciente.
 */
public class UsuarioActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_usuario);

        // saudação personalizada
        Usuario logado = BancoDeDados.getUsuarioLogado();
        TextView txtBoasVindas = findViewById(R.id.txtBoasVindas);
        if (logado != null) {
            String[] partes = logado.getNome().split(" ");
            txtBoasVindas.setText("Olá, " + partes[0] + "!");
        }

        Button botaoAgendar = findViewById(R.id.botaoAgendar);
        Button botaoMinhasConsultas = findViewById(R.id.botaoMinhasConsultas);
        Button botaoPerfil = findViewById(R.id.botaoPerfil);
        Button botaoHistorico = findViewById(R.id.botaoHistorico);
        Button botaoSair = findViewById(R.id.botaoSair);

        botaoAgendar.setOnClickListener(v -> {
            Intent intent = new Intent(this, AgendarConsultaActivity.class);
            intent.putExtra(AdminActivity.MODO_ADMIN, false);
            startActivity(intent);
        });

        botaoMinhasConsultas.setOnClickListener(v -> {
            Intent intent = new Intent(this, MinhasConsultasActivity.class);
            startActivity(intent);
        });

        botaoPerfil.setOnClickListener(v -> {
            Intent intent = new Intent(this, PerfilActivity.class);
            startActivity(intent);
        });

        botaoHistorico.setOnClickListener(v -> {
            Intent intent = new Intent(this, HistoricoActivity.class);
            startActivity(intent);
        });

        botaoSair.setOnClickListener(v -> {
            BancoDeDados.deslogar(this);
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });
    }
}
