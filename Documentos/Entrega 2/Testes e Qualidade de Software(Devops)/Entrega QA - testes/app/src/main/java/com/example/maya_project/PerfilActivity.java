package com.example.maya_project;

import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Tela de Perfil do paciente.
 * Mostra os dados do usuário que está logado.
 */
public class PerfilActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_perfil);

        Button botaoVoltar = findViewById(R.id.botaoVoltar);
        botaoVoltar.setOnClickListener(v -> finish());

        Usuario u = BancoDeDados.getUsuarioLogado();
        if (u == null) {
            finish();
            return;
        }

        ImageView imgFoto = findViewById(R.id.imgFoto);
        TextView txtNome = findViewById(R.id.txtNome);
        TextView txtEmail = findViewById(R.id.txtEmail);
        TextView txtCpf = findViewById(R.id.txtCpf);
        TextView txtDataNascimento = findViewById(R.id.txtDataNascimento);
        TextView txtNaturalidade = findViewById(R.id.txtNaturalidade);
        TextView txtCelular = findViewById(R.id.txtCelular);
        TextView txtEndereco = findViewById(R.id.txtEndereco);

        txtNome.setText(u.getNome());
        txtEmail.setText(u.getEmail());
        txtCpf.setText(u.getCpf());
        txtDataNascimento.setText(u.getDataNascimento());
        txtNaturalidade.setText(u.getNaturalidade());
        txtCelular.setText(u.getCelular());
        txtEndereco.setText(u.getEndereco());

        if (u.getFotoUri() != null && !u.getFotoUri().isEmpty()) {
            try {
                imgFoto.setImageURI(Uri.parse(u.getFotoUri()));
                imgFoto.setPadding(0, 0, 0, 0);
            } catch (Exception e) {
                // mantém o placeholder
            }
        }
    }
}
