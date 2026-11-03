package com.example.roadguard.Service;

import com.example.roadguard.DTO.UtilizadorDTO;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

/** Verifica a comparação das contas sem fazer pedidos à Firebase. */
public class GestorAutenticacaoTest {
    private Map<String, UtilizadorDTO> contas() {
        Map<String, UtilizadorDTO> contas = new LinkedHashMap<>();
        contas.put("registo_incompleto", null);
        UtilizadorDTO utilizador = new UtilizadorDTO();
        utilizador.setEmail("ana@isep.pt");
        utilizador.setPassword("segredo");
        contas.put("id_ana", utilizador);
        return contas;
    }

    @Test public void encontraOIdDaContaMesmoComUmRegistoNulo() {
        GestorAutenticacao.ResultadoLogin resultado = GestorAutenticacao.verificarCredenciais(
                contas(), "ana@isep.pt", "segredo");
        assertEquals(GestorAutenticacao.EstadoLogin.SUCESSO, resultado.estado);
        assertEquals("id_ana", resultado.id);
    }

    @Test public void passwordErradaNaoIdentificaAConta() {
        GestorAutenticacao.ResultadoLogin resultado = GestorAutenticacao.verificarCredenciais(
                contas(), "ana@isep.pt", "outra");
        assertEquals(GestorAutenticacao.EstadoLogin.DADOS_INVALIDOS, resultado.estado);
        assertEquals("", resultado.id);
    }

    @Test public void emailInexistenteNaoIdentificaAConta() {
        assertEquals(GestorAutenticacao.EstadoLogin.DADOS_INVALIDOS,
                GestorAutenticacao.verificarCredenciais(contas(), "outro@isep.pt", "segredo").estado);
    }

    @Test public void distingueBaseVaziaDeCredenciaisInvalidas() {
        assertEquals(GestorAutenticacao.EstadoLogin.SEM_UTILIZADORES,
                GestorAutenticacao.verificarCredenciais(null, "a", "b").estado);
        assertEquals(GestorAutenticacao.EstadoLogin.SEM_UTILIZADORES,
                GestorAutenticacao.verificarCredenciais(new LinkedHashMap<>(), "a", "b").estado);
    }
}
