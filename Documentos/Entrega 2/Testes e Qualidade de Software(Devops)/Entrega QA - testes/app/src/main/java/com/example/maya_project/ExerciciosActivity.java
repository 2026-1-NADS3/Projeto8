package com.example.maya_project;

import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Tela de exercícios — carrega o ExerciciosFragment.
 */
public class ExerciciosActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_exercicios);

        Button botaoVoltar = findViewById(R.id.botaoVoltar);
        botaoVoltar.setOnClickListener(v -> finish());

        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.containerExercicios, new ExerciciosFragment())
                    .commit();
        }
    }
}
