package com.example.roadguard;

/** Representa uma zona guardada por um utilizador. */
final class ZonaFavorita {
    final String id;
    final String utilizadorId;
    final String nome;
    final String local;
    final double latitude;
    final double longitude;
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