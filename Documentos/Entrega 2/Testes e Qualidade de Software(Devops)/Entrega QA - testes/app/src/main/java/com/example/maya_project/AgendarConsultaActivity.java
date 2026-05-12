package com.example.maya_project;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Calendar;

/**
 * Tela de Agendar Consulta.
 */
public class AgendarConsultaActivity extends AppCompatActivity {

    private LinearLayout blocoBuscarPaciente;
    private LinearLayout blocoPaciente;
    private LinearLayout blocoDataHora;

    private EditText campoCpf;
    private EditText campoData;
    private EditText campoHora;
    private TextView txtNomePaciente;
    private TextView txtCpfPaciente;

    // paciente que vai receber a consulta
    private Usuario pacienteSelecionado;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_agendar_consulta);

        blocoBuscarPaciente = findViewById(R.id.blocoBuscarPaciente);
        blocoPaciente = findViewById(R.id.blocoPaciente);
        blocoDataHora = findViewById(R.id.blocoDataHora);

        campoCpf = findViewById(R.id.campoCpf);
        campoData = findViewById(R.id.campoData);
        campoHora = findViewById(R.id.campoHora);
        txtNomePaciente = findViewById(R.id.txtNomePaciente);
        txtCpfPaciente = findViewById(R.id.txtCpfPaciente);

        Button botaoVoltar = findViewById(R.id.botaoVoltar);
        Button botaoBuscar = findViewById(R.id.botaoBuscar);
        Button botaoConfirmar = findViewById(R.id.botaoConfirmar);

        botaoVoltar.setOnClickListener(v -> finish());

        boolean ehAdmin = getIntent().getBooleanExtra(AdminActivity.MODO_ADMIN, false);

        if (ehAdmin) {
            blocoBuscarPaciente.setVisibility(View.VISIBLE);
            blocoPaciente.setVisibility(View.GONE);
            blocoDataHora.setVisibility(View.GONE);
        } else {
            pacienteSelecionado = BancoDeDados.getUsuarioLogado();
            if (pacienteSelecionado == null) {
                Toast.makeText(this, "Faça login primeiro.", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            blocoBuscarPaciente.setVisibility(View.GONE);
            mostrarPaciente(pacienteSelecionado);
        }

        botaoBuscar.setOnClickListener(v -> buscarPaciente());

        campoData.setOnClickListener(v -> abrirSeletorData());
        campoHora.setOnClickListener(v -> abrirSeletorHora());

        botaoConfirmar.setOnClickListener(v -> confirmarAgendamento());
    }

    private void buscarPaciente() {
        String cpf = campoCpf.getText().toString().trim();
        if (cpf.isEmpty()) {
            Toast.makeText(this, "Digite o CPF do paciente.", Toast.LENGTH_SHORT).show();
            return;
        }

        Usuario achado = BancoDeDados.buscarPorCpf(cpf);
        if (achado == null) {
            Toast.makeText(this, "Paciente não encontrado.", Toast.LENGTH_SHORT).show();
            return;
        }

        pacienteSelecionado = achado;
        mostrarPaciente(achado);
        Toast.makeText(this, "Paciente encontrado!", Toast.LENGTH_SHORT).show();
    }

    private void mostrarPaciente(Usuario u) {
        txtNomePaciente.setText(u.getNome());
        txtCpfPaciente.setText("CPF: " + u.getCpf());
        blocoPaciente.setVisibility(View.VISIBLE);
        blocoDataHora.setVisibility(View.VISIBLE);
    }

    private void abrirSeletorData() {
        Calendar c = Calendar.getInstance();
        int ano = c.get(Calendar.YEAR);
        int mes = c.get(Calendar.MONTH);
        int dia = c.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog dialog = new DatePickerDialog(
                this,
                (view, anoEsc, mesEsc, diaEsc) -> {
                    String diaStr = (diaEsc < 10) ? "0" + diaEsc : String.valueOf(diaEsc);
                    String mesStr = ((mesEsc + 1) < 10) ? "0" + (mesEsc + 1) : String.valueOf(mesEsc + 1);
                    campoData.setText(diaStr + "/" + mesStr + "/" + anoEsc);
                },
                ano, mes, dia
        );
        dialog.getDatePicker().setMinDate(System.currentTimeMillis() - 1000);
        dialog.show();
    }

    private void abrirSeletorHora() {
        Calendar c = Calendar.getInstance();
        int hora = c.get(Calendar.HOUR_OF_DAY);
        int min = c.get(Calendar.MINUTE);

        TimePickerDialog dialog = new TimePickerDialog(
                this,
                (view, horaEsc, minEsc) -> {
                    String horaStr = (horaEsc < 10) ? "0" + horaEsc : String.valueOf(horaEsc);
                    String minStr = (minEsc < 10) ? "0" + minEsc : String.valueOf(minEsc);
                    campoHora.setText(horaStr + ":" + minStr);
                },
                hora, min, true
        );
        dialog.show();
    }

    private void confirmarAgendamento() {
        if (pacienteSelecionado == null) {
            Toast.makeText(this, "Identifique o paciente primeiro.", Toast.LENGTH_SHORT).show();
            return;
        }

        String data = campoData.getText().toString().trim();
        String hora = campoHora.getText().toString().trim();

        if (data.isEmpty() || hora.isEmpty()) {
            Toast.makeText(this, "Escolha a data e a hora da consulta.", Toast.LENGTH_SHORT).show();
            return;
        }

        Consulta nova = new Consulta(pacienteSelecionado.getCpf(), data, hora);
        BancoDeDados.agendarConsulta(nova);

        Toast.makeText(this,
                "Consulta agendada para " + data + " às " + hora,
                Toast.LENGTH_LONG).show();
        // NOTIFICAÇÃO DE LEMBRETE
        NotificacaoHelper.enviar(
                this,
                (int) System.currentTimeMillis(), // ID único (pra cada notificação ser separada)
                "Consulta agendada ✅",
                "Sua consulta com a Maya foi marcada para " + data + " às " + hora
                        + ". Lembre-se de comparecer!"
        );
        finish();
    }
}
