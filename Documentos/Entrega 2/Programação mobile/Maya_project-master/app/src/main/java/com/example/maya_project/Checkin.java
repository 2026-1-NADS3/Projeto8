package com.example.maya_project;

/**
 * Representa um check-in de exercício feito pelo paciente.
 * Inclui o nível de dor sentido na hora.
 */
public class Checkin {

    private int id;
    private String cpfPaciente;
    private String nomeExercicio;
    private int nivelDor; // 0 a 10
    private String data; // "dd/MM/yyyy HH:mm"

    public Checkin(int id, String cpfPaciente, String nomeExercicio,
                   int nivelDor, String data) {
        this.id = id;
        this.cpfPaciente = cpfPaciente;
        this.nomeExercicio = nomeExercicio;
        this.nivelDor = nivelDor;
        this.data = data;
    }

    public int getId() { return id; }
    public String getCpfPaciente() { return cpfPaciente; }
    public String getNomeExercicio() { return nomeExercicio; }
    public int getNivelDor() { return nivelDor; }
    public String getData() { return data; }
}