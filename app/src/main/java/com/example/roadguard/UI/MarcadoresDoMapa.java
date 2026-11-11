package com.example.roadguard.UI;

import android.graphics.PointF;

import com.example.roadguard.DTO.AlertaDTO;
import com.example.roadguard.R;
import com.example.roadguard.Service.VerificadorDeAlertas;
import com.tomtom.sdk.location.GeoPoint;
import com.tomtom.sdk.map.display.TomTomMap;
import com.tomtom.sdk.map.display.image.ImageFactory;
import com.tomtom.sdk.map.display.marker.Marker;
import com.tomtom.sdk.map.display.marker.MarkerOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/** Desenha e atualiza os pinos do utilizador e dos alertas. */
final class MarcadoresDoMapa {
    private final Map<String, Marker> marcadoresDeAlertas = new HashMap<>();
    private Marker marcadorDaLocalizacao;

    boolean temAlerta(String id) {
        return marcadoresDeAlertas.containsKey(id);
    }

    void mostrarLocalizacao(TomTomMap mapaTomTom, GeoPoint posicaoAtual) {
        if (marcadorDaLocalizacao == null) {
            // Cria o pino que representa o utilizador.
            MarkerOptions markerOptions = new MarkerOptions(
                    posicaoAtual,
                    ImageFactory.INSTANCE.fromResource(R.drawable.ic_user_location),
                    null,
                    null,
                    new PointF(0.5f, 0.5f),
                    new PointF(0.5f, 0.5f),
                    new PointF(0.5f, 0.5f),
                    "current-location",
                    null,
                    ""
            );
            marcadorDaLocalizacao = mapaTomTom.addMarker(markerOptions);
        } else {
            // Se o pino já existe, move-o para a nova posição.
            marcadorDaLocalizacao.setCoordinate(posicaoAtual);
        }
    }

    // Mostra alertas até 2 km. Fora da viagem, inclui os das zonas visíveis.
    void atualizar(TomTomMap mapaTomTom, Map<String, AlertaDTO> alertasRecebidos,
                   GeoPoint posicaoAtual, boolean viagemIniciada, ZonasFavoritasNoMapa zonasFavoritas) {
        if (mapaTomTom == null) return;

        // Usa uma cópia da lista para poder remover pinos enquanto a percorre.
        for (String id : new ArrayList<>(marcadoresDeAlertas.keySet())) {
            if (!alertasRecebidos.containsKey(id)) removerMarcadorDeAlerta(id);
        }
        Double latitude = null;
        Double longitude = null;
        if (posicaoAtual != null) {
            latitude = posicaoAtual.getLatitude();
            longitude = posicaoAtual.getLongitude();
        }
        for (AlertaDTO alerta : alertasRecebidos.values()) {
            // Pergunta ao VerificadorDeAlertas se este alerta deve aparecer.
            boolean mostrar = VerificadorDeAlertas.deveMostrarNoMapa(alerta, latitude, longitude,
                    viagemIniciada, zonasFavoritas.circulosVisiveis(), zonasFavoritas.zonas());
            if (!mostrar) {
                removerMarcadorDeAlerta(alerta.getId());
            } else {
                mostrarMarcadorDeAlerta(mapaTomTom, alerta);
            }
        }
    }

    // Retira o pino do mapa, mas não apaga o alerta da Firebase.
    private void removerMarcadorDeAlerta(String id) {
        Marker marcador = marcadoresDeAlertas.remove(id);
        if (marcador != null) marcador.remove();
    }

    // Mostra um pino no local do alerta.
    private void mostrarMarcadorDeAlerta(TomTomMap mapaTomTom, AlertaDTO alerta) {
        GeoPoint local = new GeoPoint(alerta.getLatitude(), alerta.getLongitude());
        Marker marcador = marcadoresDeAlertas.get(alerta.getId());
        if (marcador != null) {
            // Se o pino já existe, muda só a sua posição.
            marcador.setCoordinate(local);
            return;
        }
        // Estes valores são necessários para criar um pino na TomTom.
        MarkerOptions opcoes = new MarkerOptions(local,
                ImageFactory.INSTANCE.fromResource(R.drawable.ic_alerta),
                null, null, new PointF(0.5f, 0.5f), new PointF(0.5f, 1.0f),
                new PointF(0.5f, 0.5f), "alerta:" + alerta.getId(), null, "");
        marcadoresDeAlertas.put(alerta.getId(), mapaTomTom.addMarker(opcoes));
    }
}
