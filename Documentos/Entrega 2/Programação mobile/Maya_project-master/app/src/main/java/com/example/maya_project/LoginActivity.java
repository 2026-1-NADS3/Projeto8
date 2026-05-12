package com.example.maya_project;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Tela de login do app.
 *
 * Agora consome a API REST: envia email/senha pra API,
 * que devolve os dados do usuário se as credenciais baterem.
 */
public class LoginActivity extends AppCompatActivity {

    private ApiServico api;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // cria a instância da API (passa o contexto pro Volley)
        api = new ApiServico(this);

        EditText campoEmail = findViewById(R.id.campoEmail);
        EditText campoSenha = findViewById(R.id.campoSenha);
        Button botaoEntrar = findViewById(R.id.botaoEntrar);
        TextView txtEsqueciSenha = findViewById(R.id.txtEsqueciSenha);

        botaoEntrar.setOnClickListener(v -> {
            String email = campoEmail.getText().toString().trim();
            String senha = campoSenha.getText().toString().trim();

            if (email.isEmpty() || senha.isEmpty()) {
                Toast.makeText(this, "Preencha e-mail e senha.", Toast.LENGTH_SHORT).show();
                return;
            }

            // desabilita o botão pra evitar cliques múltiplos durante a requisição
            botaoEntrar.setEnabled(false);
            botaoEntrar.setText("Verificando...");

            // chama a API
            api.fazerLogin(email, senha, new ApiServico.CallbackUsuario() {
                @Override
                public void aoReceberUsuario(Usuario usuario) {
                    // login deu certo!
                    // guarda no BancoDeDados (em memória) pra outras telas usarem
                    BancoDeDados.setUsuarioLogado(LoginActivity.this, usuario);

                    Toast.makeText(LoginActivity.this,
                            "Bem-vindo(a), " + usuario.getNome() + "!",
                            Toast.LENGTH_SHORT).show();

                    Intent intent;
                    if (usuario.isEhAdmin()) {
                        intent = new Intent(LoginActivity.this, AdminActivity.class);
                    } else {
                        intent = new Intent(LoginActivity.this, UsuarioActivity.class);
                    }
                    startActivity(intent);
                    finish();
                }

                @Override
                public void aoFalhar(String mensagemErro) {
                    // reativa o botão
                    botaoEntrar.setEnabled(true);
                    botaoEntrar.setText("Entrar");
                    Toast.makeText(LoginActivity.this, mensagemErro, Toast.LENGTH_SHORT).show();
                }
            });
        });

        txtEsqueciSenha.setOnClickListener(v -> {
            Toast.makeText(this, "Entre em contato com a clínica pra recuperar sua senha.",
                    Toast.LENGTH_LONG).show();
        });
    }
}