package com.example.roadguard.Service;

import com.example.roadguard.API.TomTomApi;
import org.junit.Test;
import static org.junit.Assert.*;

/** Verifica os resultados da viagem independentemente do ecrã e do GPS. */
public class GestorViagemTest {
    private TomTomApi.Rota rota() {
        TomTomApi.Rota rota = new TomTomApi.Rota();
        rota.distanciaMetros = 1000;
        rota.duracaoSegundos = 600;
        rota.distanciaDeCadaPonto.add(0.0);
        rota.distanciaDeCadaPonto.add(500.0);
        rota.distanciaDeCadaPonto.add(1000.0);
        rota.instrucoes.add(new TomTomApi.Instrucao("Virar à direita", 520));
        rota.instrucoes.add(new TomTomApi.Instrucao("Seguir em frente", 800));
        return rota;
    }

    @Test public void calculaDistanciaETempoQueFaltam() {
        GestorViagem.Progresso progresso = GestorViagem.calcularProgresso(rota(), 1);
        assertEquals(500, progresso.metrosRestantes, 0.001);
        assertEquals(5, progresso.minutosRestantes);
        assertEquals(1, progresso.indice);
    }

    @Test public void ignoraManobraQueJaEstaNaMargemDeVinteMetros() {
        GestorViagem.Progresso progresso = GestorViagem.calcularProgresso(rota(), 1);
        assertEquals("Seguir em frente", progresso.proximaInstrucao.mensagem);
        assertEquals(300, progresso.distanciaAteInstrucao, 0.001);
    }

    @Test public void noDestinoNaoRestamTempoNemManobras() {
        GestorViagem.Progresso progresso = GestorViagem.calcularProgresso(rota(), 2);
        assertEquals(0, progresso.metrosRestantes, 0.001);
        assertEquals(0, progresso.minutosRestantes);
        assertNull(progresso.proximaInstrucao);
    }

    @Test public void rotaSemDistanciaNaoDividePorZero() {
        TomTomApi.Rota rota = new TomTomApi.Rota();
        rota.distanciaDeCadaPonto.add(0.0);
        GestorViagem.Progresso progresso = GestorViagem.calcularProgresso(rota, 0);
        assertEquals(0, progresso.minutosRestantes);
        assertEquals(0, progresso.metrosRestantes, 0.001);
    }
}
