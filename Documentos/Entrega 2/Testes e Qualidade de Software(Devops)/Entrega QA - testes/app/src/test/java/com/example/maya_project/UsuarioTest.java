package com.example.maya_project;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Testes Unitários para a classe Usuario
 * Objetivo: Validar comportamentos individuais da classe Usuario
 */
public class UsuarioTest {

    private Usuario usuario;

    @Before
    public void setUp() {
        // Preparação: criar um usuário antes de cada teste
        usuario = new Usuario(
                "João Silva",
                "joao@email.com",
                "senha123",
                "15/05/1990",
                "São Paulo/SP",
                "(11) 98765-4321",
                "Rua das Flores, 100",
                "123.456.789-00",
                "",
                false
        );
    }

    // ============ TESTE UNITÁRIO 1 ============
    /**
     * Teste 1: Validar extração correta do dia do aniversário
     * Esperado: Retornar 15 para a data "15/05/1990"
     */
    @Test
    public void testGetDiaAniversario() {
        int diaEsperado = 15;
        int diaObtido = usuario.getDiaAniversario();
        assertEquals("O dia do aniversário deve ser 15", diaEsperado, diaObtido);
    }

    // ============ TESTE UNITÁRIO 2 ============
    /**
     * Teste 2: Validar extração correta do mês do aniversário
     * Esperado: Retornar 5 para a data "15/05/1990"
     */
    @Test
    public void testGetMesAniversario() {
        int mesEsperado = 5;
        int mesObtido = usuario.getMesAniversario();
        assertEquals("O mês do aniversário deve ser 5 (maio)", mesEsperado, mesObtido);
    }

    // ============ TESTE UNITÁRIO 3 ============
    /**
     * Teste 3: Validar se usuário é admin ou não
     * Esperado: O usuário criado não deve ser admin (false)
     */
    @Test
    public void testIsNotAdmin() {
        assertFalse("O usuário criado não deve ser admin", usuario.isEhAdmin());
    }

    // ============ TESTE UNITÁRIO 4 ============
    /**
     * Teste 4: Validar getters básicos do usuário
     * Esperado: Recuperar corretamente nome, email e CPF
     */
    @Test
    public void testGettersBasicos() {
        assertEquals("Nome deve ser 'João Silva'", "João Silva", usuario.getNome());
        assertEquals("Email deve ser 'joao@email.com'", "joao@email.com", usuario.getEmail());
        assertEquals("CPF deve ser '123.456.789-00'", "123.456.789-00", usuario.getCpf());
    }

    // ============ TESTE UNITÁRIO 5 (BÔNUS) ============
    /**
     * Teste 5: Validar setter de foto
     * Esperado: Poder atualizar a foto do usuário
     */
    @Test
    public void testSetFotoUri() {
        String caminhoFoto = "/storage/foto_usuario.jpg";
        usuario.setFotoUri(caminhoFoto);
        assertEquals("Foto URI deve ser atualizada", caminhoFoto, usuario.getFotoUri());
    }
}
