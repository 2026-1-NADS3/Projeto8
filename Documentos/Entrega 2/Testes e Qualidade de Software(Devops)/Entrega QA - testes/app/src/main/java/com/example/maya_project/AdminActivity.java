package com.example.maya_project;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Painel do administrador da clínica.
 */
public class AdminActivity extends AppCompatActivity {

    public static final String MODO_ADMIN = "modo_admin";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin);

        // saudação personalizada com o nome da Maya
        Usuario logado = BancoDeDados.getUsuarioLogado();
        TextView txtBoasVindas = findViewById(R.id.txtBoasVindas);
        if (logado != null) {
            String[] partes = logado.getNome().split(" ");
            txtBoasVindas.setText("Olá, " + partes[0] + "!");
        }

        Button botaoCadastrar = findViewById(R.id.botaoCadastrar);
        Button botaoAgendar = findViewById(R.id.botaoAgendar);
        Button botaoConsultas = findViewById(R.id.botaoConsultas);
        Button botaoAniversariantes = findViewById(R.id.botaoAniversariantes);
        Button botaoSair = findViewById(R.id.botaoSair);

        botaoCadastrar.setOnClickListener(v -> {
            Intent intent = new Intent(this, CadastroUsuarioActivity.class);
            startActivity(intent);
        });

        botaoAgendar.setOnClickListener(v -> {
            Intent intent = new Intent(this, AgendarConsultaActivity.class);
            intent.putExtra(MODO_ADMIN, true);
            startActivity(intent);
        });

        botaoConsultas.setOnClickListener(v -> {
            Intent intent = new Intent(this, MinhasConsultasActivity.class);
            startActivity(intent);
        });

        botaoAniversariantes.setOnClickListener(v -> {
            Intent intent = new Intent(this, AniversariantesActivity.class);
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
