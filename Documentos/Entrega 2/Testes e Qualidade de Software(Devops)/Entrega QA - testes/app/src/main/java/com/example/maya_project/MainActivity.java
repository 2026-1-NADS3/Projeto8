package com.example.maya_project;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Tela principal do app.
 */
public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // inicializa canal de notificação e pede permissão se for Android 13+
        NotificacaoHelper.criarCanal(this);
        NotificacaoHelper.pedirPermissaoSeNecessario(this);

        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.containerSobre, new SobreFragment())
                    .commit();
        }

        Button botaoAgendar = findViewById(R.id.botaoAgendar);
        Button botaoExercicios = findViewById(R.id.botaoExercicios);
        Button botaoPrecos = findViewById(R.id.botaoPrecos);
        Button botaoContato = findViewById(R.id.botaoContato);
        Button botaoLogin = findViewById(R.id.botaoLogin);

        botaoAgendar.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, LoginActivity.class);
            startActivity(intent);
        });

        botaoExercicios.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, ExerciciosActivity.class);
            startActivity(intent);
        });

        botaoPrecos.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, PrecosActivity.class);
            startActivity(intent);
        });

        botaoContato.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, ContatoActivity.class);
            startActivity(intent);
        });

        botaoLogin.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, LoginActivity.class);
            startActivity(intent);
        });
    }
}
