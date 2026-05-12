package com.example.maya_project;

/**
 * Representa uma consulta agendada.
 * Por simplicidade guardamos apenas o CPF do paciente e a data/hora.
 * Quando precisamos do nome, buscamos no BancoDeDados pelo CPF.
 */
public class Consulta {

    private String cpfPaciente;
    private String data; // "dd/MM/yyyy"
    private String hora; // "HH:mm"

    public Consulta(String cpfPaciente, String data, String hora) {
        this.cpfPaciente = cpfPaciente;
        this.data = data;
        this.hora = hora;
    }

    public String getCpfPaciente() { return cpfPaciente; }
    public String getData() { return data; }
    public String getHora() { return hora; }
}
