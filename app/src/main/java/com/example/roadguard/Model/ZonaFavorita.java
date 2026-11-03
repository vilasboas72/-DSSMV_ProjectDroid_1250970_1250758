package com.example.roadguard.Model;

/** Guarda os dados de uma zona favorita lida ou enviada para a Firebase. */
public final class ZonaFavorita {
    // O id identifica esta zona dentro da base de dados.
    public final String id;
    // O utilizadorId separa as zonas de cada conta.
    public final String utilizadorId;
    public final String nome;
    public final String local;
    public final double latitude;
    public final double longitude;
    // O raio é guardado em quilómetros e convertido para metros ao desenhar o círculo.
    public final int raioKm;

    public ZonaFavorita(String id, String utilizadorId, String nome, String local,
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
