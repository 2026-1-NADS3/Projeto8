package com.example.maya_project;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Tela de Contato.
 */
public class ContatoActivity extends AppCompatActivity {

    private final String telefone = "11999999999";
    private final String email = "contato@mayayamamoto.com.br";
    private final String linkMapa = "https://share.google/DdSIEXCovoyRumY8Y";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contato);

        Button botaoVoltar = findViewById(R.id.botaoVoltar);
        botaoVoltar.setOnClickListener(v -> finish());

        Button botaoLigar = findViewById(R.id.botaoLigar);
        Button botaoWhatsapp = findViewById(R.id.botaoWhatsapp);
        Button botaoEmail = findViewById(R.id.botaoEmail);
        Button botaoMapa = findViewById(R.id.botaoMapa);

        botaoLigar.setOnClickListener(v -> {
            try {
                Intent intent = new Intent(Intent.ACTION_DIAL);
                intent.setData(Uri.parse("tel:" + telefone));
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(this, "Não foi possível abrir o discador.", Toast.LENGTH_SHORT).show();
            }
        });

        botaoWhatsapp.setOnClickListener(v -> {
            try {
                String url = "https://wa.me/55" + telefone;
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse(url));
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(this, "Não foi possível abrir o WhatsApp.", Toast.LENGTH_SHORT).show();
            }
        });

        botaoEmail.setOnClickListener(v -> {
            try {
                Intent intent = new Intent(Intent.ACTION_SENDTO);
                intent.setData(Uri.parse("mailto:" + email));
                intent.putExtra(Intent.EXTRA_SUBJECT, "Contato pelo App");
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(this, "Não foi possível abrir o app de e-mail.", Toast.LENGTH_SHORT).show();
            }
        });

        botaoMapa.setOnClickListener(v -> {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse(linkMapa));
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(this, "Não foi possível abrir o mapa.", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
