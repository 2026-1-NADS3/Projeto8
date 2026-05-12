package com.example.maya_project;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Testes Unitários para a classe Consulta
 * Objetivo: Validar comportamentos individuais da classe Consulta
 */
public class ConsultaTest {

    private Consulta consulta;

    @Before
    public void setUp() {
        // Preparação: criar uma consulta antes de cada teste
        consulta = new Consulta("123.456.789-00", "15/05/2026", "14:30");
    }

    // ============ TESTE UNITÁRIO 6 ============
    /**
     * Teste 6: Validar getter de CPF do paciente
     * Esperado: Retornar "123.456.789-00"
     */
    @Test
    public void testGetCpfPaciente() {
        String cpfEsperado = "123.456.789-00";
        String cpfObtido = consulta.getCpfPaciente();
        assertEquals("CPF deve ser '123.456.789-00'", cpfEsperado, cpfObtido);
    }

    // ============ TESTE UNITÁRIO 7 ============
    /**
     * Teste 7: Validar getter de data da consulta
     * Esperado: Retornar "15/05/2026"
     */
    @Test
    public void testGetData() {
        String dataEsperada = "15/05/2026";
        String dataObtida = consulta.getData();
        assertEquals("Data deve ser '15/05/2026'", dataEsperada, dataObtida);
    }

    // ============ TESTE UNITÁRIO 8 ============
    /**
     * Teste 8: Validar getter de hora da consulta
     * Esperado: Retornar "14:30"
     */
    @Test
    public void testGetHora() {
        String horaEsperada = "14:30";
        String horaObtida = consulta.getHora();
        assertEquals("Hora deve ser '14:30'", horaEsperada, horaObtida);
    }
}
