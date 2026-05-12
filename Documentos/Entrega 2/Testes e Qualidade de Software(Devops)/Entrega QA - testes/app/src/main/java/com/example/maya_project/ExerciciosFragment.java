package com.example.maya_project;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import java.util.ArrayList;

/**
 * Fragment de exercícios.
 *
 * Consome a API REST pra listar os exercícios prescritos e,
 * quando o paciente marca um como feito, abre um diálogo perguntando
 * o nível de dor (0 a 10). Esse check-in é salvo no SQLite local.
 */
public class ExerciciosFragment extends Fragment {

    private ArrayList<Exercicio> exercicios = new ArrayList<>();

    private TextView txtTotalExercicios;
    private TextView txtTempoTotal;
    private TextView txtConcluidos;

    private View viewRaiz;
    private ApiServico api;
    private BancoLocal bancoLocal;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        viewRaiz = inflater.inflate(R.layout.fragment_exercicios, container, false);

        api = new ApiServico(requireContext());
        bancoLocal = new BancoLocal(requireContext());

        txtTotalExercicios = viewRaiz.findViewById(R.id.txtTotalExercicios);
        txtTempoTotal = viewRaiz.findViewById(R.id.txtTempoTotal);
        txtConcluidos = viewRaiz.findViewById(R.id.txtConcluidos);

        txtTotalExercicios.setText("...");
        txtTempoTotal.setText("...");

        carregarExerciciosDaApi();

        return viewRaiz;
    }

    private void carregarExerciciosDaApi() {
        api.listarExercicios(requireContext(), new ApiServico.CallbackListaExercicios() {
            @Override
            public void aoReceberLista(ArrayList<Exercicio> lista) {
                exercicios = lista;
                preencherCards();
                atualizarResumo();
            }

            @Override
            public void aoFalhar(String mensagemErro) {
                if (getContext() != null) {
                    Toast.makeText(getContext(),
                            "Erro ao carregar exercícios: " + mensagemErro,
                            Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private void preencherCards() {
        if (exercicios.size() < 3) {
            Toast.makeText(requireContext(),
                    "API retornou menos de 3 exercícios.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        preencherCard(0, R.id.titulo1, R.id.img1, R.id.desc1, R.id.freq1, R.id.botaoFeito1);
        preencherCard(1, R.id.titulo2, R.id.img2, R.id.desc2, R.id.freq2, R.id.botaoFeito2);
        preencherCard(2, R.id.titulo3, R.id.img3, R.id.desc3, R.id.freq3, R.id.botaoFeito3);
    }

    private void preencherCard(int posicao, int idTitulo, int idImg,
                               int idDesc, int idFreq, int idBotao) {
        Exercicio ex = exercicios.get(posicao);

        TextView titulo = viewRaiz.findViewById(idTitulo);
        ImageView img = viewRaiz.findViewById(idImg);
        TextView desc = viewRaiz.findViewById(idDesc);
        TextView freq = viewRaiz.findViewById(idFreq);
        Button botao = viewRaiz.findViewById(idBotao);

        titulo.setText(ex.getNome());
        img.setImageResource(ex.getImagem());
        desc.setText(ex.getDescricao());
        freq.setText("Frequência: " + ex.getFrequencia());

        botao.setOnClickListener(v -> abrirDialogoCheckin(posicao, botao));
    }

    /**
     * Abre um diálogo perguntando o nível de dor antes de salvar o check-in.
     * Usa uma lista de opções nativa (mais simples e estável que SeekBar custom).
     */
    private void abrirDialogoCheckin(int posicao, Button botao) {
        Exercicio ex = exercicios.get(posicao);

        if (ex.isFeitoHoje()) {
            Toast.makeText(requireContext(), "Já marcou hoje!", Toast.LENGTH_SHORT).show();
            return;
        }

        // confere se tem usuário logado (precisa do CPF pra salvar)
        Usuario logado = BancoDeDados.getUsuarioLogado(requireContext());
        if (logado == null) {
            Toast.makeText(requireContext(),
                    "Você precisa estar logado pra registrar check-in.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        // lista de níveis de dor (0 a 10 com descrição)
        final String[] niveis = {
                "0 — Sem dor",
                "1 — Quase nada",
                "2 — Muito leve",
                "3 — Leve",
                "4 — Leve a moderada",
                "5 — Moderada",
                "6 — Moderada a forte",
                "7 — Forte",
                "8 — Muito forte",
                "9 — Intensa",
                "10 — Insuportável"
        };

        // guarda a escolha em array de 1 elemento (truque pra acessar dentro do lambda)
        final int[] nivelEscolhido = {0};

        try {
            new AlertDialog.Builder(requireContext())
                    .setTitle("Como está sua dor após este exercício?")
                    .setSingleChoiceItems(niveis, 0, (dialog, which) -> nivelEscolhido[0] = which)
                    .setPositiveButton("Confirmar", (dialog, which) -> {
                        salvarCheckin(ex, nivelEscolhido[0], botao);
                    })
                    .setNegativeButton("Cancelar", null)
                    .show();
        } catch (Exception e) {
            Toast.makeText(requireContext(),
                    "Erro ao abrir diálogo: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }

    /**
     * Salva o check-in no SQLite e atualiza a UI.
     */
    private void salvarCheckin(Exercicio ex, int nivelDor, Button botao) {
        Usuario logado = BancoDeDados.getUsuarioLogado(requireContext());

        if (logado == null) {
            Toast.makeText(requireContext(),
                    "Erro: usuário não está mais logado.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        bancoLocal.salvarCheckin(logado.getCpf(), ex.getNome(), nivelDor);

        ex.marcarComoFeito();
        botao.setText("✓ Concluído");
        botao.setEnabled(false);
        atualizarResumo();

        // 🔔 NOTIFICAÇÃO DE PARABENS
        NotificacaoHelper.enviar(
                requireContext(),
                (int) System.currentTimeMillis(),
                "Check-in registrado! 💪",
                "Você fez \"" + ex.getNome() + "\". Nível de dor: " + nivelDor + "/10. Continue assim!"
        );

        Toast.makeText(requireContext(),
                "Check-in registrado! Dor: " + nivelDor + "/10",
                Toast.LENGTH_LONG).show();
    }

    private void atualizarResumo() {
        int total = exercicios.size();
        int tempoTotal = 0;
        int concluidos = 0;

        for (Exercicio e : exercicios) {
            tempoTotal += e.getTempoEstimadoMin();
            if (e.isFeitoHoje()) {
                concluidos++;
            }
        }

        txtTotalExercicios.setText(String.valueOf(total));
        txtTempoTotal.setText(tempoTotal + " min");
        txtConcluidos.setText(String.valueOf(concluidos));
    }
}