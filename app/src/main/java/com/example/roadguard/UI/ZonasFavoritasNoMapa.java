package com.example.roadguard.UI;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.location.Location;
import android.widget.Toast;

import com.example.roadguard.Model.ZonaFavorita;
import com.tomtom.sdk.location.GeoPoint;
import com.tomtom.sdk.map.display.TomTomMap;
import com.tomtom.sdk.map.display.camera.CameraOptions;
import com.tomtom.sdk.map.display.circle.CircleOptions;
import com.tomtom.sdk.map.display.circle.Radius;

import java.util.ArrayList;
import java.util.List;

/** Lê as zonas escolhidas e mostra os seus círculos no mapa. */
final class ZonasFavoritasNoMapa {
    private final List<ZonaFavorita> zonas = new ArrayList<>();
    private int quantidadeCirculosDesenhados;
    private boolean circulosVisiveis;

    boolean temZonas() {
        return !zonas.isEmpty();
    }

    boolean circulosVisiveis() {
        return circulosVisiveis;
    }

    List<ZonaFavorita> zonas() {
        return zonas;
    }

    void alternarCirculos(TomTomMap mapaTomTom) {
        if (circulosVisiveis) {
            removerCirculos(mapaTomTom);
            circulosVisiveis = false;
        } else {
            desenharCirculos(mapaTomTom);
        }
    }

    // Lê as zonas enviadas pelo ecrã Zonas Favoritas.
    void lerDoIntent(Intent intent, String utilizadorLogado) {
        // Os dados vêm no Intent. Aqui não vai à Firebase.
        // O ecrã anterior envia listas quando foram escolhidas várias zonas.
        double[] latitudes = intent.getDoubleArrayExtra("zonas_latitudes");
        double[] longitudes = intent.getDoubleArrayExtra("zonas_longitudes");
        int[] raios = intent.getIntArrayExtra("zonas_raios");
        String[] nomes = intent.getStringArrayExtra("zonas_nomes");
        if (latitudes != null && longitudes != null && raios != null && nomes != null
                && latitudes.length == longitudes.length && latitudes.length == raios.length
                && latitudes.length == nomes.length) {
            // Em cada volta, lê a latitude, a longitude, o raio e o nome de uma zona.
            for (int indice = 0; indice < latitudes.length; indice++) {
                if (!Double.isFinite(latitudes[indice]) || !Double.isFinite(longitudes[indice])
                        || Math.abs(latitudes[indice]) > 90 || Math.abs(longitudes[indice]) > 180
                        || raios[indice] <= 0) continue;
                zonas.add(new ZonaFavorita("", utilizadorLogado,
                        nomes[indice], "", latitudes[indice], longitudes[indice], raios[indice]));
            }
        }
        if (zonas.isEmpty()) {
            // Se foi escolhida só uma zona, lê os dados dessa zona.
            if (!intent.hasExtra("zona_latitude") || !intent.hasExtra("zona_longitude")) return;
            double latitude = intent.getDoubleExtra("zona_latitude", Double.NaN);
            double longitude = intent.getDoubleExtra("zona_longitude", Double.NaN);
            int raioKm = intent.getIntExtra("zona_raio_km", 0);
            if (!Double.isFinite(latitude) || !Double.isFinite(longitude)
                    || Math.abs(latitude) > 90 || Math.abs(longitude) > 180
                    || raioKm <= 0) return;
            zonas.add(new ZonaFavorita("", utilizadorLogado,
                    intent.getStringExtra("zona_nome"), "", latitude, longitude, raioKm));
        }
    }

    // Desenha as zonas escolhidas e ajusta o mapa para as mostrar.
    void mostrar(Context contexto, TomTomMap mapaTomTom) {
        if (mapaTomTom == null || zonas.isEmpty()) return;
        desenharCirculos(mapaTomTom);
        // Calcula um centro entre as zonas para posicionar o mapa.
        double latitudeMedia = 0;
        double longitudeMedia = 0;
        for (ZonaFavorita zona : zonas) {
            latitudeMedia += zona.latitude;
            longitudeMedia += zona.longitude;
        }
        GeoPoint centro = new GeoPoint(latitudeMedia / zonas.size(), longitudeMedia / zonas.size());
        // Vê quanto espaço é preciso para mostrar todas as zonas.
        double maiorExtensaoKm = 0.5;
        for (ZonaFavorita zona : zonas) {
            float[] distancia = new float[1];
            Location.distanceBetween(centro.getLatitude(), centro.getLongitude(),
                    zona.latitude, zona.longitude, distancia);
            maiorExtensaoKm = Math.max(maiorExtensaoKm,
                    distancia[0] / 1000.0 + zona.raioKm);
        }
        // Afasta o mapa se as zonas ocuparem uma área maior.
        double zoom = Math.max(3.0, Math.min(16.0,
                15.0 - Math.log(maiorExtensaoKm * 2.0) / Math.log(2.0)));
        mapaTomTom.moveCamera(new CameraOptions(centro, zoom, 0.0, 0.0, 45.0));
        String mensagemZonas;
        if (zonas.size() == 1) {
            mensagemZonas = zonas.get(0).nome + " · " + zonas.get(0).raioKm + " km";
        } else {
            mensagemZonas = zonas.size() + " zonas favoritas no mapa";
        }
        Toast.makeText(contexto, mensagemZonas, Toast.LENGTH_SHORT).show();
    }

    // Desenha um círculo azul claro para cada zona.
    private void desenharCirculos(TomTomMap mapaTomTom) {
        if (mapaTomTom == null || zonas.isEmpty()) return;
        removerCirculos(mapaTomTom);
        for (int indice = 0; indice < zonas.size(); indice++) {
            ZonaFavorita zona = zonas.get(indice);
            // Passa o raio de quilómetros para metros.
            Radius raio = new Radius(zona.raioKm * 1000.0, 0, null);
            CircleOptions opcoes = new CircleOptions(
                    new GeoPoint(zona.latitude, zona.longitude), raio,
                    Color.argb(24, 66, 133, 244), Color.argb(95, 66, 133, 244),
                    2.0, false, "zona_favorita_" + indice);
            mapaTomTom.addCircle(opcoes);
        }
        quantidadeCirculosDesenhados = zonas.size();
        circulosVisiveis = true;
    }

    // Esconde os círculos sem apagar as zonas guardadas.
    private void removerCirculos(TomTomMap mapaTomTom) {
        if (mapaTomTom == null) return;
        mapaTomTom.removeCircles("zona_favorita");
        // Remove cada círculo pelo nome que recebeu ao ser criado.
        for (int indice = 0; indice < quantidadeCirculosDesenhados; indice++) {
            mapaTomTom.removeCircles("zona_favorita_" + indice);
        }
        quantidadeCirculosDesenhados = 0;
    }
}
