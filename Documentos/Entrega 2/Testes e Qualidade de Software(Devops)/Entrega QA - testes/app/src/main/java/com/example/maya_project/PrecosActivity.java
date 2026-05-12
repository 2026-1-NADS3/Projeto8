package com.example.maya_project;

import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Tela de Preços.
 */
public class PrecosActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_precos);

        Button botaoVoltar = findViewById(R.id.botaoVoltar);
        botaoVoltar.setOnClickListener(v -> finish());
    }
}
