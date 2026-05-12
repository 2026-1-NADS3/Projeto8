package com.example.maya_project;

import android.content.Context;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

/**
  Classe que centraliza todas as chamadas para a API REST.

  Em vez de cada Activity fazer requisições direto, elas pedem
  pra essa classe (que cuida do Volley, montagem de URL, parse do JSON).

  Como o Volley trabalha de forma assíncrona (a resposta chega depois),
  usamos interfaces de "callback" pra avisar quem chamou quando os
  dados chegam (ou quando dá erro).
 */
public class ApiServico {

    // URL base da API no mockapi.io
    private static final String URL_BASE = "https://6a024e8c0d92f63dd25388f4.mockapi.io/";

    private RequestQueue filaRequisicoes;

    // construtor: precisa do Context da Activity pra criar a fila
    public ApiServico(Context contexto) {
        filaRequisicoes = Volley.newRequestQueue(contexto);
    }


    // INTERFACES DE CALLBACK

    public interface CallbackUsuario {
        void aoReceberUsuario(Usuario usuario);
        void aoFalhar(String mensagemErro);
    }

    public interface CallbackListaUsuarios {
        void aoReceberLista(ArrayList<Usuario> lista);
        void aoFalhar(String mensagemErro);
    }

    public interface CallbackListaExercicios {
        void aoReceberLista(ArrayList<Exercicio> lista);
        void aoFalhar(String mensagemErro);
    }

    // USUÁRIOS

    /**
     Faz login: busca todos os usuários e procura quem bate com email/senha.
     */
    public void fazerLogin(String email, String senha, CallbackUsuario callback) {
        String url = URL_BASE + "usuarios";

        JsonArrayRequest requisicao = new JsonArrayRequest(
                Request.Method.GET,
                url,
                null,
                resposta -> {
                    try {
                        // percorre todos os usuários procurando o que bate
                        for (int i = 0; i < resposta.length(); i++) {
                            JSONObject obj = resposta.getJSONObject(i);
                            String emailApi = obj.optString("email");
                            String senhaApi = obj.optString("senha");

                            if (emailApi.equals(email) && senhaApi.equals(senha)) {
                                Usuario u = jsonParaUsuario(obj);
                                callback.aoReceberUsuario(u);
                                return;
                            }
                        }
                        // se chegou aqui, não achou
                        callback.aoFalhar("E-mail ou senha incorretos.");
                    } catch (JSONException e) {
                        callback.aoFalhar("Erro ao ler resposta da API.");
                    }
                },
                erro -> callback.aoFalhar("Falha de conexão: " + erro.getMessage())
        );

        filaRequisicoes.add(requisicao);
    }

    /**
     Busca um usuário pelo CPF.
     */
    public void buscarPorCpf(String cpf, CallbackUsuario callback) {
        String url = URL_BASE + "usuarios";

        JsonArrayRequest requisicao = new JsonArrayRequest(
                Request.Method.GET,
                url,
                null,
                resposta -> {
                    try {
                        for (int i = 0; i < resposta.length(); i++) {
                            JSONObject obj = resposta.getJSONObject(i);
                            String cpfApi = obj.optString("cpf");
                            if (cpfApi.equals(cpf)) {
                                callback.aoReceberUsuario(jsonParaUsuario(obj));
                                return;
                            }
                        }
                        callback.aoFalhar("Paciente não encontrado.");
                    } catch (JSONException e) {
                        callback.aoFalhar("Erro ao ler resposta.");
                    }
                },
                erro -> callback.aoFalhar("Falha de conexão.")
        );

        filaRequisicoes.add(requisicao);
    }

    /**
     Lista todos os pacientes (filtra fora os admins).
     */
    public void listarPacientes(CallbackListaUsuarios callback) {
        String url = URL_BASE + "usuarios";

        JsonArrayRequest requisicao = new JsonArrayRequest(
                Request.Method.GET,
                url,
                null,
                resposta -> {
                    try {
                        ArrayList<Usuario> pacientes = new ArrayList<>();
                        for (int i = 0; i < resposta.length(); i++) {
                            JSONObject obj = resposta.getJSONObject(i);
                            Usuario u = jsonParaUsuario(obj);
                            if (!u.isEhAdmin()) {
                                pacientes.add(u);
                            }
                        }
                        callback.aoReceberLista(pacientes);
                    } catch (JSONException e) {
                        callback.aoFalhar("Erro ao ler resposta.");
                    }
                },
                erro -> callback.aoFalhar("Falha de conexão.")
        );

        filaRequisicoes.add(requisicao);
    }

    /**
     Cadastra um novo usuário na API (POST).
     */
    public void cadastrarUsuario(Usuario novo, CallbackUsuario callback) {
        String url = URL_BASE + "usuarios";

        try {
            JSONObject body = new JSONObject();
            body.put("nome", novo.getNome());
            body.put("email", novo.getEmail());
            body.put("senha", novo.getSenha());
            body.put("cpf", novo.getCpf());
            body.put("dataNascimento", novo.getDataNascimento());
            body.put("naturalidade", novo.getNaturalidade());
            body.put("celular", novo.getCelular());
            body.put("endereco", novo.getEndereco());
            body.put("ehAdmin", novo.isEhAdmin());

            JsonObjectRequest requisicao = new JsonObjectRequest(
                    Request.Method.POST,
                    url,
                    body,
                    resposta -> {
                        try {
                            callback.aoReceberUsuario(jsonParaUsuario(resposta));
                        } catch (JSONException e) {
                            callback.aoFalhar("Erro ao ler resposta.");
                        }
                    },
                    erro -> callback.aoFalhar("Falha ao cadastrar.")
            );

            filaRequisicoes.add(requisicao);
        } catch (JSONException e) {
            callback.aoFalhar("Erro ao montar requisição.");
        }
    }

    // EXERCÍCIOS

    /**
     Lista todos os exercícios prescritos pela clínica.
     */
    public void listarExercicios(Context contexto, CallbackListaExercicios callback) {
        String url = URL_BASE + "exercicios";

        JsonArrayRequest requisicao = new JsonArrayRequest(
                Request.Method.GET,
                url,
                null,
                resposta -> {
                    try {
                        ArrayList<Exercicio> lista = new ArrayList<>();
                        for (int i = 0; i < resposta.length(); i++) {
                            JSONObject obj = resposta.getJSONObject(i);

                            // converte o nome da imagem (string) em ID do drawable
                            String imagemNome = obj.optString("imagemNome", "exercicio_coluna");
                            int idImagem = contexto.getResources().getIdentifier(
                                    imagemNome, "drawable", contexto.getPackageName());
                            // fallback: se não achar, usa exercicio_coluna
                            if (idImagem == 0) {
                                idImagem = contexto.getResources().getIdentifier(
                                        "exercicio_coluna", "drawable", contexto.getPackageName());
                            }

                            Exercicio e = new Exercicio(
                                    obj.optString("nome"),
                                    obj.optString("descricao"),
                                    obj.optInt("series", 3),
                                    obj.optInt("repeticoes", 10),
                                    obj.optInt("tempoEstimado", 5),
                                    obj.optString("frequencia"),
                                    idImagem
                            );
                            lista.add(e);
                        }
                        callback.aoReceberLista(lista);
                    } catch (JSONException e) {
                        callback.aoFalhar("Erro ao ler resposta.");
                    }
                },
                erro -> callback.aoFalhar("Falha de conexão.")
        );

        filaRequisicoes.add(requisicao);
    }

    // HELPER: converte JSON em Usuario

    private Usuario jsonParaUsuario(JSONObject obj) throws JSONException {
        return new Usuario(
                obj.optString("nome"),
                obj.optString("email"),
                obj.optString("senha"),
                obj.optString("dataNascimento"),
                obj.optString("naturalidade"),
                obj.optString("celular"),
                obj.optString("endereco"),
                obj.optString("cpf"),
                "", // foto fica vazia (não vamos enviar fotos pra API)
                obj.optBoolean("ehAdmin", false)
        );
    }
}