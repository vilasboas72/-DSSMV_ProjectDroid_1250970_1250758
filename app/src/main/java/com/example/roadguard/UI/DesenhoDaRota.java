package com.example.roadguard.UI;

import android.graphics.Color;

import com.example.roadguard.API.TomTomApi;
import com.tomtom.sdk.map.display.TomTomMap;
import com.tomtom.sdk.map.display.common.WidthByZoom;
import com.tomtom.sdk.map.display.polyline.Polyline;
import com.tomtom.sdk.map.display.polyline.PolylineOptions;

import java.util.ArrayList;
import java.util.Collections;

final class DesenhoDaRota {
    private static final int PONTA_ARREDONDADA = 1;
    private static final String ID_DA_LINHA = "rota_planeada";
    private Polyline linhaDaRota;

    // Desenha no mapa a rota recebida da TomTom.
    void desenhar(TomTomMap mapaTomTom, TomTomApi.Rota rotaPlaneada) {
        if (mapaTomTom == null || rotaPlaneada == null) return;
        // Apaga a linha anterior antes de desenhar a nova rota.
        mapaTomTom.removePolylines(ID_DA_LINHA);
        PolylineOptions opcoes = new PolylineOptions(
                rotaPlaneada.pontos,
                Color.rgb(26, 115, 232),
                Collections.singletonList(new WidthByZoom(8.0, 15.0)),
                Color.WHITE,
                Collections.singletonList(new WidthByZoom(1.5, 15.0)),
                PONTA_ARREDONDADA,
                PONTA_ARREDONDADA,
                false,
                ID_DA_LINHA,
                null,
                null);
        linhaDaRota = mapaTomTom.addPolyline(opcoes);
    }

    void mostrarPercursoRestante(TomTomApi.Rota rota, int indice) {
        if (linhaDaRota == null) return;
        linhaDaRota.setCoordinates(new ArrayList<>(rota.pontos.subList(indice, rota.pontos.size())));
    }

    void apagar(TomTomMap mapaTomTom) {
        if (mapaTomTom != null) mapaTomTom.removePolylines(ID_DA_LINHA);
        linhaDaRota = null;
    }
}
