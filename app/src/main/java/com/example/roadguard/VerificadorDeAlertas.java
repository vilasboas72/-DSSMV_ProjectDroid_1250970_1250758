package com.example.roadguard;

import java.util.List;

/** Cálculos de proximidade, separados do ecrã para serem fáceis de testar. */
final class VerificadorDeAlertas {
    static final double DISTANCIA_AVISO = 500;
    private static final double RAIO_TERRA = 6371000;
    private static final double TOLERANCIA_GPS = 75;
    private static final double TOLERANCIA_ALERTA = 40;
    private static final double TOLERANCIA_PASSAGEM = 20;

    static final double RAIO_VISIBILIDADE = 2000;

    // Sem GPS, só as zonas favoritas visíveis podem dar origem a marcadores.
    static boolean deveMostrarNoMapa(AlertaDTO alerta, Double latitude, Double longitude,
                                    boolean emViagem, boolean zonasVisiveis,
                                    List<ZonaFavorita> zonas) {
        if (!alerta.isAtivo()) return false;

        // 1. Um alerta até 2 km é visível, com ou sem viagem.
        if (latitude != null && longitude != null) {
            double distanciaAoUtilizador = medirDistancia(latitude, longitude,
                    alerta.getLatitude(), alerta.getLongitude());
            if (distanciaAoUtilizador <= RAIO_VISIBILIDADE + 0.001) return true;
        }

        // 2. A exceção das zonas favoritas só se aplica fora da viagem.
        if (emViagem || !zonasVisiveis) return false;
        for (ZonaFavorita zona : zonas) {
            double distanciaAoCentro = medirDistancia(zona.latitude, zona.longitude,
                    alerta.getLatitude(), alerta.getLongitude());
            double raioEmMetros = zona.raioKm * 1000.0;
            if (distanciaAoCentro <= raioEmMetros + 0.001) return true;
        }
        return false;
    }

    static double medirDistancia(double lat1, double lon1, double lat2, double lon2) {
        // Fórmula de Haversine: calcula metros em linha reta entre duas coordenadas.
        double diferencaLatitude = Math.toRadians(lat2 - lat1);
        double diferencaLongitude = Math.toRadians(lon2 - lon1);
        double senoLatitude = Math.sin(diferencaLatitude / 2);
        double senoLongitude = Math.sin(diferencaLongitude / 2);
        double a = senoLatitude * senoLatitude
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * senoLongitude * senoLongitude;
        return RAIO_TERRA * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(Math.max(0, 1 - a)));
    }

    // Nomes explícitos em vez de usar [0] e [1] para os resultados dos cálculos.
    private static class PosicaoNaRota {
        double metrosDesdeInicio;
        double distanciaLateral;

        PosicaoNaRota(double metrosDesdeInicio, double distanciaLateral) {
            this.metrosDesdeInicio = metrosDesdeInicio;
            this.distanciaLateral = distanciaLateral;
        }
    }

    // Devolve a distância pela rota até ao perigo; -1 significa que não deve avisar.
    static double distanciaAteAlerta(List<double[]> rota, double latitude, double longitude,
                                     double latitudeAlerta, double longitudeAlerta) {
        if (rota.size() < 2) return -1;
        PosicaoNaRota utilizador = projetarNaRota(rota, latitude, longitude);
        PosicaoNaRota alerta = projetarNaRota(rota, latitudeAlerta, longitudeAlerta);
        double distancia = alerta.metrosDesdeInicio - utilizador.metrosDesdeInicio;

        if (utilizador.distanciaLateral > TOLERANCIA_GPS) return -1;
        if (alerta.distanciaLateral > TOLERANCIA_ALERTA) return -1;
        if (distancia < 0) return -1; // O utilizador já passou pelo perigo.
        if (distancia > DISTANCIA_AVISO) return -1; // Ainda está a mais de 500 m.
        return distancia;
    }

    // Pede junto ao ponto, com 20 m de tolerância para a imprecisão do GPS.
    static boolean estaAPassarPeloAlerta(List<double[]> rota, double latitude, double longitude,
                                        double latitudeAlerta, double longitudeAlerta,
                                        Double latitudeAnterior, Double longitudeAnterior) {
        if (rota.size() < 2) return false;
        PosicaoNaRota utilizador = projetarNaRota(rota, latitude, longitude);
        PosicaoNaRota alerta = projetarNaRota(rota, latitudeAlerta, longitudeAlerta);
        if (utilizador.distanciaLateral > TOLERANCIA_GPS
                || alerta.distanciaLateral > TOLERANCIA_ALERTA) return false;
        double distancia = alerta.metrosDesdeInicio - utilizador.metrosDesdeInicio;
        if (Math.abs(distancia) <= TOLERANCIA_PASSAGEM) return true;

        // Uma leitura pode chegar antes e a seguinte depois, sem medir exatamente o ponto.
        if (latitudeAnterior == null || longitudeAnterior == null) return false;
        PosicaoNaRota anterior = projetarNaRota(rota, latitudeAnterior, longitudeAnterior);
        double avancou = utilizador.metrosDesdeInicio - anterior.metrosDesdeInicio;
        return anterior.distanciaLateral <= TOLERANCIA_GPS
                && anterior.metrosDesdeInicio < alerta.metrosDesdeInicio
                && distancia < 0 && avancou > 0 && avancou <= 100;
    }

    // Procura o segmento da estrada mais próximo da coordenada.
    // Cada double[] da lista contém {latitude, longitude}.
    private static PosicaoNaRota projetarNaRota(List<double[]> rota, double latitude, double longitude) {
        double metrosPercorridos = 0;
        double posicaoMaisProxima = 0;
        double menorDistanciaLateral = Double.MAX_VALUE;
        for (int i = 1; i < rota.size(); i++) {
            double[] inicio = rota.get(i - 1);
            double[] fim = rota.get(i);
            double metrosPorRadianoLongitude = RAIO_TERRA * Math.cos(Math.toRadians(latitude));
            // Coordenadas em metros, tendo a posição a verificar como origem.
            double xInicio = Math.toRadians(inicio[1] - longitude) * metrosPorRadianoLongitude;
            double yInicio = Math.toRadians(inicio[0] - latitude) * RAIO_TERRA;
            double xFim = Math.toRadians(fim[1] - longitude) * metrosPorRadianoLongitude;
            double yFim = Math.toRadians(fim[0] - latitude) * RAIO_TERRA;
            double variacaoX = xFim - xInicio;
            double variacaoY = yFim - yInicio;
            double comprimentoAoQuadrado = variacaoX * variacaoX + variacaoY * variacaoY;
            // fracao = 0 no início do segmento e 1 no fim.
            double fracao = 0;
            if (comprimentoAoQuadrado > 0) {
                fracao = -(xInicio * variacaoX + yInicio * variacaoY) / comprimentoAoQuadrado;
                if (fracao < 0) fracao = 0;
                if (fracao > 1) fracao = 1;
            }
            double distancia = Math.hypot(xInicio + fracao * variacaoX, yInicio + fracao * variacaoY);
            double comprimento = medirDistancia(inicio[0], inicio[1], fim[0], fim[1]);
            if (distancia < menorDistanciaLateral) {
                menorDistanciaLateral = distancia;
                posicaoMaisProxima = metrosPercorridos + fracao * comprimento;
            }
            metrosPercorridos += comprimento;
        }
        return new PosicaoNaRota(posicaoMaisProxima, menorDistanciaLateral);
    }
}
