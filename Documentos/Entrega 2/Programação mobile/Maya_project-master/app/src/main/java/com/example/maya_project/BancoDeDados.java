package com.example.maya_project;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Calendar;

/**
 Banco de dados simulado em memória + sessão persistente em SharedPreferences.
 */
public class BancoDeDados {

    // listas em memória (sem mudanças)
    private static ArrayList<Usuario> usuarios = new ArrayList<>();
    private static ArrayList<Consulta> consultas = new ArrayList<>();

    private static Usuario usuarioLogado = null;

    // ====== chaves do SharedPreferences ======
    private static final String PREFS = "maya_sessao";
    private static final String K_NOME = "nome";
    private static final String K_EMAIL = "email";
    private static final String K_SENHA = "senha";
    private static final String K_CPF = "cpf";
    private static final String K_NASC = "nasc";
    private static final String K_NAT = "nat";
    private static final String K_CEL = "cel";
    private static final String K_END = "end";
    private static final String K_ADMIN = "admin";
    private static final String K_LOGADO = "tem_logado";

    static {
        // (mantém os usuários pré-cadastrados de fallback caso a API caia)
        usuarios.add(new Usuario("Maya Yoshiko Yamamoto", "admin@gmail.com", "admin123",
                "10/03/1975", "São Paulo/SP", "(11) 99999-9999",
                "Rua Rio Grande, 141", "000.000.000-00", "", true));
        usuarios.add(new Usuario("Maria Silva", "usuario@gmail.com", "usuario123",
                "15/05/1990", "Curitiba/PR", "(11) 98765-4321",
                "Rua das Flores, 100", "123.456.789-00", "", false));
        consultas.add(new Consulta("123.456.789-00", "12/05/2026", "14:00"));
        consultas.add(new Consulta("123.456.789-00", "19/05/2026", "10:30"));
    }

    // SESSÃO (com persistência)

    /**
     * Salva o usuário logado em memória E no SharedPreferences.
     * Use sempre que tiver Context disponível.
     */
    public static void setUsuarioLogado(Context contexto, Usuario u) {
        usuarioLogado = u;
        if (contexto == null || u == null) return;

        SharedPreferences prefs = contexto.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        prefs.edit()
                .putBoolean(K_LOGADO, true)
                .putString(K_NOME, u.getNome())
                .putString(K_EMAIL, u.getEmail())
                .putString(K_SENHA, u.getSenha())
                .putString(K_CPF, u.getCpf())
                .putString(K_NASC, u.getDataNascimento())
                .putString(K_NAT, u.getNaturalidade())
                .putString(K_CEL, u.getCelular())
                .putString(K_END, u.getEndereco())
                .putBoolean(K_ADMIN, u.isEhAdmin())
                .apply();
    }

    /**
     Pega o usuário logado. Se a variável em memória for null,
     tenta restaurar do SharedPreferences.
     */
    public static Usuario getUsuarioLogado(Context contexto) {
        if (usuarioLogado != null) return usuarioLogado;
        if (contexto == null) return null;

        SharedPreferences prefs = contexto.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!prefs.getBoolean(K_LOGADO, false)) return null;

        usuarioLogado = new Usuario(
                prefs.getString(K_NOME, ""),
                prefs.getString(K_EMAIL, ""),
                prefs.getString(K_SENHA, ""),
                prefs.getString(K_NASC, ""),
                prefs.getString(K_NAT, ""),
                prefs.getString(K_CEL, ""),
                prefs.getString(K_END, ""),
                prefs.getString(K_CPF, ""),
                "",
                prefs.getBoolean(K_ADMIN, false)
        );
        return usuarioLogado;
    }

    /**
     Versão SEM context (compatibilidade — só usa memória, pode retornar null).
     */
    public static Usuario getUsuarioLogado() {
        return usuarioLogado;
    }

    /**
     Desloga: limpa memória E SharedPreferences.
     */
    public static void deslogar(Context contexto) {
        usuarioLogado = null;
        if (contexto != null) {
            SharedPreferences prefs = contexto.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            prefs.edit().clear().apply();
        }
    }

    /**
     Versão SEM context (compat — só limpa memória).
     */
    public static void deslogar() {
        usuarioLogado = null;
    }

    /**
     Compat: chamada antiga sem context (só memória, sem persistir).
     */
    public static void setUsuarioLogado(Usuario u) {
        usuarioLogado = u;
    }

    // RESTO (sem mudanças)

    public static Usuario fazerLogin(String email, String senha) {
        for (Usuario u : usuarios) {
            if (u.getEmail().equals(email) && u.getSenha().equals(senha)) {
                usuarioLogado = u;
                return u;
            }
        }
        return null;
    }

    public static void cadastrarUsuario(Usuario novo) {
        usuarios.add(novo);
    }

    public static Usuario buscarPorCpf(String cpf) {
        for (Usuario u : usuarios) {
            if (u.getCpf() != null && u.getCpf().equals(cpf)) return u;
        }
        return null;
    }

    public static ArrayList<Usuario> listarPacientes() {
        ArrayList<Usuario> r = new ArrayList<>();
        for (Usuario u : usuarios) {
            if (!u.isEhAdmin()) r.add(u);
        }
        return r;
    }

    public static void agendarConsulta(Consulta nova) { consultas.add(nova); }

    public static ArrayList<Consulta> consultasDoCpf(String cpf) {
        ArrayList<Consulta> r = new ArrayList<>();
        for (Consulta c : consultas) {
            if (c.getCpfPaciente().equals(cpf)) r.add(c);
        }
        return r;
    }

    public static ArrayList<Consulta> listarTodasConsultas() { return consultas; }

    public static ArrayList<Usuario> aniversariantesDoMes(int mes) {
        ArrayList<Usuario> r = new ArrayList<>();
        for (Usuario u : listarPacientes()) {
            if (u.getMesAniversario() == mes) r.add(u);
        }
        return r;
    }

    public static ArrayList<Usuario> aniversariantesDaSemana() {
        ArrayList<Usuario> r = new ArrayList<>();
        Calendar hoje = Calendar.getInstance();
        for (Usuario u : listarPacientes()) {
            int diaAniv = u.getDiaAniversario();
            int mesAniv = u.getMesAniversario();
            for (int i = 0; i < 7; i++) {
                Calendar dia = (Calendar) hoje.clone();
                dia.add(Calendar.DAY_OF_MONTH, i);
                int d = dia.get(Calendar.DAY_OF_MONTH);
                int m = dia.get(Calendar.MONTH) + 1;
                if (d == diaAniv && m == mesAniv) { r.add(u); break; }
            }
        }
        return r;
    }

    public static ArrayList<Usuario> aniversariantesDoAno() {
        ArrayList<Usuario> lista = listarPacientes();
        for (int i = 0; i < lista.size() - 1; i++) {
            for (int j = 0; j < lista.size() - 1 - i; j++) {
                Usuario a = lista.get(j);
                Usuario b = lista.get(j + 1);
                boolean trocar = false;
                if (a.getMesAniversario() > b.getMesAniversario()) trocar = true;
                else if (a.getMesAniversario() == b.getMesAniversario()
                        && a.getDiaAniversario() > b.getDiaAniversario()) trocar = true;
                if (trocar) { lista.set(j, b); lista.set(j + 1, a); }
            }
        }
        return lista;
    }
}