package com.example.roadguard;

import org.junit.Test;
import com.google.gson.Gson;
import static org.junit.Assert.*;

public class ConfirmacoesDeAlertasTest {
    private AlertaDTO alerta() {
        AlertaDTO alerta = new AlertaDTO();
        alerta.setUtilizadorId("autor");
        alerta.setAtivo(true);
        return alerta;
    }

    @Test public void precisaDeTresContasDiferentesParaRetirarOAlerta() {
        AlertaDTO alerta = alerta();
        alerta.guardarConfirmacao("ana", false);
        alerta.guardarConfirmacao("ana", false);
        alerta.guardarConfirmacao("rui", false);
        assertTrue(alerta.isAtivo());
        alerta.guardarConfirmacao("joao", false);
        assertFalse(alerta.isAtivo());
    }

    @Test public void autorEConfirmacoesPositivasNaoContamComoRemocoes() {
        AlertaDTO alerta = alerta();
        alerta.guardarConfirmacao("autor", false);
        alerta.guardarConfirmacao("ana", false);
        alerta.guardarConfirmacao("rui", false);
        alerta.guardarConfirmacao("joao", true);
        assertTrue(alerta.isAtivo());
        assertTrue(alerta.jaConfirmou("joao"));
    }

    @Test public void empateOuMaioriaDeAindaExisteMantemOAlerta() {
        AlertaDTO alerta = alerta();
        for (int i = 0; i < 3; i++) {
            alerta.guardarConfirmacao("nao" + i, false);
            alerta.guardarConfirmacao("sim" + i, true);
        }
        assertTrue(alerta.isAtivo());
    }

    @Test public void dadosAntigosSemConfirmacoesContinuamValidos() {
        AlertaDTO alerta = new Gson().fromJson("{\"ativo\":true}", AlertaDTO.class);
        assertTrue(alerta.isAtivo());
        assertFalse(alerta.jaConfirmou("ana"));
        alerta.setAtivo(false);
        assertFalse(alerta.isAtivo());
    }
}
