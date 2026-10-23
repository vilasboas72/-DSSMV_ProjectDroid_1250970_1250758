package com.example.roadguard;

import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import com.tomtom.sdk.location.GeoPoint;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Faz as pesquisas de locais e pede rotas à API HTTP da TomTom. */
final class TomTomApi {
    interface Callback<T> {
        void aoConcluir(T resultado);
        void aoFalhar();
    }

    static final class Local {
        final String nome;
        final String endereco;
        final double latitude;
        final double longitude;

        Local(String nome, String endereco, double latitude, double longitude) {
            this.nome = nome;
            this.endereco = endereco;
            this.latitude = latitude;
            this.longitude = longitude;
        }
    }

    static final class Rota {
        final List<GeoPoint> pontos = new ArrayList<>();
        final List<Instrucao> instrucoes = new ArrayList<>();
        final List<Double> distanciaDeCadaPonto = new ArrayList<>();
        int distanciaMetros;
        int duracaoSegundos;
    }

    static final class Instrucao {
        final String mensagem;
        final int distanciaDesdeInicio;

        Instrucao(String mensagem, int distanciaDesdeInicio) {
            this.mensagem = mensagem;
            this.distanciaDesdeInicio = distanciaDesdeInicio;
        }
    }

    private final String chaveApi;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler gestorPrincipal = new Handler(Looper.getMainLooper());

    TomTomApi(String chaveApi) {
        this.chaveApi = chaveApi;
    }

    // Procura sugestões, dando prioridade à posição atual quando já está disponível.
    void pesquisarLocais(String texto, GeoPoint posicao, Callback<List<Local>> callback) {
        executor.execute(() -> {
            try {
                String url = "https://api.tomtom.com/search/2/search/" + Uri.encode(texto)
                        + ".json?key=" + Uri.encode(chaveApi)
                        + "&typeahead=true&limit=6&language=pt-PT";
                // Sem countrySet=PT, a API também pode devolver locais nos EUA ou noutros países.
                if (posicao != null) {
                    url += "&geobias=point:" + posicao.getLatitude() + "," + posicao.getLongitude();
                }
                JSONObject resposta = pedirJson(url);
                JSONArray resultados = resposta.optJSONArray("results");
                List<Local> locais = new ArrayList<>();
                if (resultados != null) {
                    for (int i = 0; i < resultados.length(); i++) {
                        JSONObject item = resultados.getJSONObject(i);
                        JSONObject ponto = item.optJSONObject("position");
                        if (ponto == null) continue;

                        JSONObject morada = item.optJSONObject("address");
                        String endereco = morada == null ? "" : morada.optString("freeformAddress", "");
                        JSONObject pontoInteresse = item.optJSONObject("poi");
                        String nome = pontoInteresse == null ? endereco : pontoInteresse.optString("name", endereco);
                        if (nome.isEmpty()) nome = endereco;
                        locais.add(new Local(nome, endereco,
                                ponto.optDouble("lat"), ponto.optDouble("lon")));
                    }
                }
                entregar(callback, locais);
            } catch (Exception e) {
                entregarFalha(callback);
            }
        });
    }

    // Calcula uma rota de carro e pede instruções em português.
    void calcularRota(GeoPoint origem, Local destino, Callback<Rota> callback) {
        executor.execute(() -> {
            try {
                String pontos = String.format(Locale.US, "%f,%f:%f,%f",
                        origem.getLatitude(), origem.getLongitude(), destino.latitude, destino.longitude);
                String url = "https://api.tomtom.com/routing/1/calculateRoute/" + pontos + "/json?key="
                        + Uri.encode(chaveApi)
                        + "&travelMode=car&routeType=fastest&traffic=true&instructionsType=text"
                        + "&language=pt-PT&computeTravelTimeFor=all";
                JSONObject resposta = pedirJson(url);
                JSONArray rotas = resposta.optJSONArray("routes");
                if (rotas == null || rotas.length() == 0) throw new IllegalStateException("Sem rota");

                JSONObject rotaJson = rotas.getJSONObject(0);
                JSONObject resumo = rotaJson.getJSONObject("summary");
                Rota rota = new Rota();
                rota.distanciaMetros = resumo.optInt("lengthInMeters");
                rota.duracaoSegundos = resumo.optInt("travelTimeInSeconds");

                GeoPoint pontoAnterior = null;
                double distanciaAcumulada = 0.0;
                JSONArray trechos = rotaJson.optJSONArray("legs");
                if (trechos != null) {
                    for (int i = 0; i < trechos.length(); i++) {
                        JSONArray pontosDoTrecho = trechos.getJSONObject(i).optJSONArray("points");
                        if (pontosDoTrecho == null) continue;
                        for (int j = 0; j < pontosDoTrecho.length(); j++) {
                            JSONObject ponto = pontosDoTrecho.getJSONObject(j);
                            GeoPoint pontoAtual = new GeoPoint(ponto.getDouble("latitude"), ponto.getDouble("longitude"));
                            if (pontoAnterior != null) distanciaAcumulada += medirDistancia(pontoAnterior, pontoAtual);
                            rota.pontos.add(pontoAtual);
                            rota.distanciaDeCadaPonto.add(distanciaAcumulada);
                            pontoAnterior = pontoAtual;
                        }
                    }
                }

                JSONObject orientacoes = rotaJson.optJSONObject("guidance");
                JSONArray listaOrientacoes = orientacoes == null ? null : orientacoes.optJSONArray("instructions");
                if (listaOrientacoes != null) {
                    for (int i = 0; i < listaOrientacoes.length(); i++) {
                        JSONObject item = listaOrientacoes.getJSONObject(i);
                        String mensagem = item.optString("message", "");
                        if (!mensagem.isEmpty()) {
                            rota.instrucoes.add(new Instrucao(mensagem, item.optInt("routeOffsetInMeters")));
                        }
                    }
                }
                entregar(callback, rota);
            } catch (Exception e) {
                entregarFalha(callback);
            }
        });
    }

    private double medirDistancia(GeoPoint pontoA, GeoPoint pontoB) {
        double lat1 = Math.toRadians(pontoA.getLatitude());
        double lat2 = Math.toRadians(pontoB.getLatitude());
        double dLat = lat2 - lat1;
        double dLon = Math.toRadians(pontoB.getLongitude() - pontoA.getLongitude());
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 6371000 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    void fechar() {
        executor.shutdownNow();
    }

    private JSONObject pedirJson(String endereco) throws Exception {
        HttpURLConnection ligacao = (HttpURLConnection) new URL(endereco).openConnection();
        ligacao.setRequestMethod("GET");
        ligacao.setConnectTimeout(10000);
        ligacao.setReadTimeout(15000);
        try (BufferedReader leitor = new BufferedReader(new InputStreamReader(ligacao.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder texto = new StringBuilder();
            String linha;
            while ((linha = leitor.readLine()) != null) texto.append(linha);
            return new JSONObject(texto.toString());
        } finally {
            ligacao.disconnect();
        }
    }

    private <T> void entregar(Callback<T> callback, T resultado) {
        gestorPrincipal.post(() -> callback.aoConcluir(resultado));
    }

    private void entregarFalha(Callback<?> callback) {
        gestorPrincipal.post(callback::aoFalhar);
    }
}
