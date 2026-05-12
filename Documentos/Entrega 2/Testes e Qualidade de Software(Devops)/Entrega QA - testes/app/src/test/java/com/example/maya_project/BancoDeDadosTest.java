package com.example.maya_project;

import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import java.util.ArrayList;
import static org.junit.Assert.*;

/**
 * Testes de Integração para a classe BancoDeDados
 * Objetivo: Validar o funcionamento integrado entre BancoDeDados, Usuario e Consulta
 */
public class BancoDeDadosTest {

    @Before
    public void setUp() {
        // Preparação: BancoDeDados já vem com dados pré-carregados no bloco estático
        // Este método pode ser usado para limpeza adicional se necessário
    }

    @After
    public void tearDown() {
        // Limpeza: deslogar usuário após cada teste
        BancoDeDados.deslogar();
    }

    // ============ TESTE DE INTEGRAÇÃO 1 ============
    /**
     * Teste 9: Validar fluxo completo de login
     * Cenário: 1. Fazer login com credenciais corretas
     *          2. Verificar se usuário logado é correto
     *          3. Deslogar
     * Esperado: Login bem-sucedido e usuário logado verificado
     */
    @Test
    public void testFluxoLoginComIntegracao() {
        // Arrange: credenciais do usuário admin padrão
        String emailAdmin = "admin@gmail.com";
        String senhaAdmin = "admin123";
        
        // Act: fazer login
        Usuario usuarioLogado = BancoDeDados.fazerLogin(emailAdmin, senhaAdmin);
        
        // Assert: verificar se o login funcionou
        assertNotNull("Login deve retornar um usuário", usuarioLogado);
        assertEquals("Email deve ser do admin", emailAdmin, usuarioLogado.getEmail());
        assertTrue("Usuário logado deve ser admin", usuarioLogado.isEhAdmin());
        assertEquals("Usuário logado no sistema deve ser o mesmo", 
                usuarioLogado, BancoDeDados.getUsuarioLogado());
    }

    // ============ TESTE DE INTEGRAÇÃO 2 ============
    /**
     * Teste 10: Validar fluxo de busca de usuário e suas consultas
     * Cenário: 1. Buscar usuário por CPF
     *          2. Obter todas as consultas desse usuário
     *          3. Validar dados integrados
     * Esperado: Usuário encontrado e suas consultas recuperadas corretamente
     */
    @Test
    public void testFluxoBuscaUsuarioEConsultas() {
        // Arrange: CPF de um usuário com consultas pré-agendadas
        String cpfPaciente = "123.456.789-00";
        
        // Act: buscar usuário pelo CPF
        Usuario usuario = BancoDeDados.buscarPorCpf(cpfPaciente);
        
        // Assert: verificar se o usuário foi encontrado
        assertNotNull("Usuário com CPF deve ser encontrado", usuario);
        assertEquals("CPF deve corresponder", cpfPaciente, usuario.getCpf());
        assertEquals("Nome deve ser 'Maria Silva'", "Maria Silva", usuario.getNome());
        
        // Act 2: obter consultas do usuário
        ArrayList<Consulta> consultasUsuario = BancoDeDados.consultasDoCpf(cpfPaciente);
        
        // Assert 2: verificar as consultas
        assertNotNull("Lista de consultas não deve ser null", consultasUsuario);
        assertFalse("Usuário deve ter consultas agendadas", consultasUsuario.isEmpty());
        assertTrue("Deve ter pelo menos 2 consultas", consultasUsuario.size() >= 2);
        
        // Validar primeira consulta
        Consulta primeiraConsulta = consultasUsuario.get(0);
        assertEquals("CPF da primeira consulta deve corresponder", 
                cpfPaciente, primeiraConsulta.getCpfPaciente());
        assertEquals("Data da primeira consulta deve ser 12/05/2026", 
                "12/05/2026", primeiraConsulta.getData());
    }

    // ============ TESTE DE INTEGRAÇÃO 3 (BÔNUS) ============
    /**
     * Teste 11: Validar cadastro de novo usuário e consulta
     * Cenário: 1. Cadastrar novo usuário
     *          2. Buscar o usuário cadastrado
     *          3. Agendar uma consulta para esse usuário
     *          4. Recuperar suas consultas
     * Esperado: Usuário cadastrado e consulta agendada corretamente
     */
    @Test
    public void testFluxoCadastroEAgendamento() {
        // Arrange: criar novo usuário
        Usuario novoUsuario = new Usuario(
                "Pedro Santos",
                "pedro@email.com",
                "senha456",
                "20/03/1995",
                "Brasília/DF",
                "(61) 99999-1234",
                "Rua C, 500",
                "789.012.345-66",
                "",
                false
        );
        
        // Act 1: cadastrar novo usuário
        BancoDeDados.cadastrarUsuario(novoUsuario);
        
        // Assert 1: verificar se foi cadastrado
        Usuario usuarioBuscado = BancoDeDados.buscarPorCpf("789.012.345-66");
        assertNotNull("Novo usuário deve ser encontrado", usuarioBuscado);
        assertEquals("Nome deve ser 'Pedro Santos'", "Pedro Santos", usuarioBuscado.getNome());
        
        // Act 2: agendar uma consulta para o novo usuário
        Consulta novaConsulta = new Consulta("789.012.345-66", "20/05/2026", "10:00");
        BancoDeDados.agendarConsulta(novaConsulta);
        
        // Assert 2: recuperar consultas do novo usuário
        ArrayList<Consulta> consultas = BancoDeDados.consultasDoCpf("789.012.345-66");
        assertNotNull("Lista de consultas não deve ser null", consultas);
        assertEquals("Novo usuário deve ter 1 consulta", 1, consultas.size());
        
        Consulta consultaAgendada = consultas.get(0);
        assertEquals("Data da consulta deve ser 20/05/2026", "20/05/2026", consultaAgendada.getData());
        assertEquals("Hora da consulta deve ser 10:00", "10:00", consultaAgendada.getHora());
    }
}
