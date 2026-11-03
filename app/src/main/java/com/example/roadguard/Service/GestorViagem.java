package com.example.roadguard.Service;

import android.location.Location;
import com.example.roadguard.API.TomTomApi;
import com.tomtom.sdk.location.GeoPoint;

/** Calcula o progresso e deteta o afastamento do percurso, sem alterar o ecrã. */
public final class GestorViagem {
    private int indiceAtualDaRota;
    private int posicoesForaDaRota;

    public static final class Progresso {
        public int indice;
        public double metrosRestantes;
        public int minutosRestantes;
        public TomTomApi.Instrucao proximaInstrucao;
        public double distanciaAteInstrucao;
    }

    public void reiniciar() {
        indiceAtualDaRota = 0;
        posicoesForaDaRota = 0;
    }

    public void limparAfastamento() {
        posicoesForaDaRota = 0;
    }

    // Null indica duas leituras afastadas: a Activity deve tentar recalcular a rota.
    public Progresso atualizar(TomTomApi.Rota rotaPlaneada, Location localizacao) {
        // Procura também atrás do índice atual, para acompanhar quem volta pelo caminho.
        int inicio = Math.max(0, indiceAtualDaRota - 200);
        int fim = Math.min(rotaPlaneada.pontos.size(), indiceAtualDaRota + 201);
        int pontoMaisProximo = indiceAtualDaRota;
        float menorDistancia = Float.MAX_VALUE;
        float[] distancia = new float[1];

        for (int i = inicio; i < fim; i++) {
            GeoPoint ponto = rotaPlaneada.pontos.get(i);
            Location.distanceBetween(localizacao.getLatitude(), localizacao.getLongitude(),
                    ponto.getLatitude(), ponto.getLongitude(), distancia);
            if (distancia[0] < menorDistancia) {
                menorDistancia = distancia[0];
                pontoMaisProximo = i;
            }
        }

        // Se o GPS se afastou muito do trecho atual, procura a rota toda para se reencontrar.
        if (menorDistancia > 100) {
            for (int i = 0; i < rotaPlaneada.pontos.size(); i++) {
                GeoPoint ponto = rotaPlaneada.pontos.get(i);
                Location.distanceBetween(localizacao.getLatitude(), localizacao.getLongitude(),
                        ponto.getLatitude(), ponto.getLongitude(), distancia);
                if (distancia[0] < menorDistancia) {
                    menorDistancia = distancia[0];
                    pontoMaisProximo = i;
                }
            }
        }

        // Se nos afastarmos da estrada em duas leituras GPS, pede uma nova rota.
        if (menorDistancia > 60) posicoesForaDaRota++;
        else posicoesForaDaRota = 0;
        if (posicoesForaDaRota >= 2) return null;

        // O índice pode avançar ou recuar conforme a posição real do utilizador.
        indiceAtualDaRota = pontoMaisProximo;
        return calcularProgresso(rotaPlaneada, indiceAtualDaRota);
    }

    // Calcula a distância, o tempo proporcional e a próxima manobra a partir de um ponto.
    public static Progresso calcularProgresso(TomTomApi.Rota rota, int indice) {
        Progresso progresso = new Progresso();
        progresso.indice = indice;
        double percorridos = rota.distanciaDeCadaPonto.get(indice);
        progresso.metrosRestantes = Math.max(0, rota.distanciaMetros - percorridos);
        if (rota.distanciaMetros == 0) {
            progresso.minutosRestantes = 0;
        } else {
            progresso.minutosRestantes = (int) Math.ceil(
                    rota.duracaoSegundos * progresso.metrosRestantes / rota.distanciaMetros / 60.0);
        }
        for (TomTomApi.Instrucao instrucao : rota.instrucoes) {
            double distancia = instrucao.distanciaDesdeInicio - percorridos;
            if (distancia > 20) {
                progresso.proximaInstrucao = instrucao;
                progresso.distanciaAteInstrucao = distancia;
                break;
            }
        }
        return progresso;
    }
}
