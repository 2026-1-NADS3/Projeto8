package com.example.maya_project;

/**
 * Representa um usuário do app (paciente ou administrador).
 *
 * O admin é só um Usuario com a flag "ehAdmin" como true.
 * Os pacientes ficam com ehAdmin = false e preenchem todos os campos.
 */
public class Usuario {

    private String nome;
    private String email;
    private String senha;
    private String dataNascimento; // formato "dd/MM/yyyy"
    private String naturalidade;
    private String celular;
    private String endereco;
    private String cpf;
    private String fotoUri; // caminho da foto (vazio se ainda não tiver foto)
    private boolean ehAdmin;

    // construtor com todos os campos
    public Usuario(String nome, String email, String senha, String dataNascimento,
                   String naturalidade, String celular, String endereco,
                   String cpf, String fotoUri, boolean ehAdmin) {
        this.nome = nome;
        this.email = email;
        this.senha = senha;
        this.dataNascimento = dataNascimento;
        this.naturalidade = naturalidade;
        this.celular = celular;
        this.endereco = endereco;
        this.cpf = cpf;
        this.fotoUri = fotoUri;
        this.ehAdmin = ehAdmin;
    }

    // getters
    public String getNome() { return nome; }
    public String getEmail() { return email; }
    public String getSenha() { return senha; }
    public String getDataNascimento() { return dataNascimento; }
    public String getNaturalidade() { return naturalidade; }
    public String getCelular() { return celular; }
    public String getEndereco() { return endereco; }
    public String getCpf() { return cpf; }
    public String getFotoUri() { return fotoUri; }
    public boolean isEhAdmin() { return ehAdmin; }

    // setters
    public void setFotoUri(String fotoUri) { this.fotoUri = fotoUri; }

    /**
     * Retorna o dia do aniversário (1 a 31).
     */
    public int getDiaAniversario() {
        if (dataNascimento == null || dataNascimento.length() < 10) return 0;
        return Integer.parseInt(dataNascimento.substring(0, 2));
    }

    /**
     * Retorna o mês do aniversário (1 a 12).
     */
    public int getMesAniversario() {
        if (dataNascimento == null || dataNascimento.length() < 10) return 0;
        return Integer.parseInt(dataNascimento.substring(3, 5));
    }
}
