package com.example.maya_project;

/**
 * Representa um exercício do plano do paciente.
 */
public class Exercicio {

    private String nome;
    private String descricao;
    private int series;
    private int repeticoes;
    private int tempoEstimadoMin;
    private String frequencia;
    private int imagem; // id do drawable
    private boolean feitoHoje;

    public Exercicio(String nome, String descricao, int series, int repeticoes,
                     int tempoEstimadoMin, String frequencia, int imagem) {
        this.nome = nome;
        this.descricao = descricao;
        this.series = series;
        this.repeticoes = repeticoes;
        this.tempoEstimadoMin = tempoEstimadoMin;
        this.frequencia = frequencia;
        this.imagem = imagem;
        this.feitoHoje = false;
    }

    public String getNome() { return nome; }
    public String getDescricao() { return descricao; }
    public int getSeries() { return series; }
    public int getRepeticoes() { return repeticoes; }
    public int getTempoEstimadoMin() { return tempoEstimadoMin; }
    public String getFrequencia() { return frequencia; }
    public int getImagem() { return imagem; }
    public boolean isFeitoHoje() { return feitoHoje; }

    public void marcarComoFeito() {
        this.feitoHoje = true;
    }
}
