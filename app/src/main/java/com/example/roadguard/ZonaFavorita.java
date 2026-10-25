package com.example.roadguard;

/** Guarda os dados de uma zona favorita lida ou enviada para a Firebase. */
final class ZonaFavorita {
    // O id identifica esta zona dentro da base de dados.
    final String id;
    // O utilizadorId separa as zonas de cada conta.
    final String utilizadorId;
    final String nome;
    final String local;
    final double latitude;
    final double longitude;
    // O raio é guardado em quilómetros e convertido para metros ao desenhar o círculo.
    final int raioKm;

    ZonaFavorita(String id, String utilizadorId, String nome, String local,
                 double latitude, double longitude, int raioKm) {
        this.id = id;
        this.utilizadorId = utilizadorId;
        this.nome = nome;
        this.local = local;
        this.latitude = latitude;
        this.longitude = longitude;
        this.raioKm = raioKm;
    }
}
