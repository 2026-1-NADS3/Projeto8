package com.example.maya_project;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import java.util.Calendar;

/**
 * Tela de cadastro de paciente (acessível só pelo admin).
 */
public class    CadastroUsuarioActivity extends AppCompatActivity {

    // campos do formulário
    private EditText campoNome;
    private EditText campoEmail;
    private EditText campoSenha;
    private EditText campoDataNascimento;
    private EditText campoNaturalidade;
    private EditText campoCelular;
    private EditText campoEndereco;
    private EditText campoCpf;
    private ImageView imgFoto;

    // guarda o caminho da foto selecionada
    private String fotoEscolhida = "";

    // launcher pra pegar a foto da galeria
    private ActivityResultLauncher<Intent> escolherFoto;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cadastro_usuario);

        campoNome = findViewById(R.id.campoNome);
        campoEmail = findViewById(R.id.campoEmail);
        campoSenha = findViewById(R.id.campoSenha);
        campoDataNascimento = findViewById(R.id.campoDataNascimento);
        campoNaturalidade = findViewById(R.id.campoNaturalidade);
        campoCelular = findViewById(R.id.campoCelular);
        campoEndereco = findViewById(R.id.campoEndereco);
        campoCpf = findViewById(R.id.campoCpf);
        imgFoto = findViewById(R.id.imgFoto);

        Button botaoVoltar = findViewById(R.id.botaoVoltar);
        Button botaoSelecionarFoto = findViewById(R.id.botaoSelecionarFoto);
        Button botaoSalvar = findViewById(R.id.botaoSalvar);

        botaoVoltar.setOnClickListener(v -> finish());

        // ========== ESCOLHA DA FOTO ==========
        escolherFoto = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                resultado -> {
                    if (resultado.getResultCode() == RESULT_OK && resultado.getData() != null) {
                        Uri uriFoto = resultado.getData().getData();
                        if (uriFoto != null) {
                            imgFoto.setImageURI(uriFoto);
                            imgFoto.setPadding(0, 0, 0, 0);
                            fotoEscolhida = uriFoto.toString();
                        }
                    }
                });

        botaoSelecionarFoto.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK,
                    android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            escolherFoto.launch(intent);
        });

        campoDataNascimento.setOnClickListener(v -> abrirSeletorDeData());

        botaoSalvar.setOnClickListener(v -> salvarCadastro());
    }

    private void abrirSeletorDeData() {
        Calendar c = Calendar.getInstance();
        int ano = c.get(Calendar.YEAR);
        int mes = c.get(Calendar.MONTH);
        int dia = c.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog dialog = new DatePickerDialog(
                this,
                (view, anoEsc, mesEsc, diaEsc) -> {
                    String diaStr = (diaEsc < 10) ? "0" + diaEsc : String.valueOf(diaEsc);
                    String mesStr = ((mesEsc + 1) < 10) ? "0" + (mesEsc + 1) : String.valueOf(mesEsc + 1);
                    campoDataNascimento.setText(diaStr + "/" + mesStr + "/" + anoEsc);
                },
                ano, mes, dia
        );
        dialog.show();
    }

    private void salvarCadastro() {
        String nome = campoNome.getText().toString().trim();
        String email = campoEmail.getText().toString().trim();
        String senha = campoSenha.getText().toString().trim();
        String dataNasc = campoDataNascimento.getText().toString().trim();
        String naturalidade = campoNaturalidade.getText().toString().trim();
        String celular = campoCelular.getText().toString().trim();
        String endereco = campoEndereco.getText().toString().trim();
        String cpf = campoCpf.getText().toString().trim();

        if (nome.isEmpty() || email.isEmpty() || senha.isEmpty()
                || dataNasc.isEmpty() || naturalidade.isEmpty()
                || celular.isEmpty() || endereco.isEmpty() || cpf.isEmpty()) {
            Toast.makeText(this, "Preencha todos os campos.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (senha.length() < 6) {
            Toast.makeText(this, "A senha precisa ter no mínimo 6 caracteres.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        if (BancoDeDados.buscarPorCpf(cpf) != null) {
            Toast.makeText(this, "Já existe um paciente com esse CPF.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        Usuario novo = new Usuario(nome, email, senha, dataNasc, naturalidade,
                celular, endereco, cpf, fotoEscolhida, false);
        BancoDeDados.cadastrarUsuario(novo);

        Toast.makeText(this, "Paciente cadastrado com sucesso!", Toast.LENGTH_LONG).show();
        finish();
    }
}
