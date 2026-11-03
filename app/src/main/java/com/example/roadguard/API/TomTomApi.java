package com.example.roadguard.API;

import com.example.roadguard.Service.VerificadorDeAlertas;
import com.example.roadguard.DTO.AlertaDTO;

import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import com.tomtom.sdk.location.GeoPoint;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Faz as pesquisas de locais e pede rotas à API HTTP da TomTom. */
public final class TomTomApi {
    public interface Resposta<T> {
        void aoConcluir(T resultado);
        void aoFalhar();
    }

    public static final class Local {
        public final String nome;
        public final String endereco;
        public final double latitude;
        public final double longitude;

        public Local(String nome, String endereco, double latitude, double longitude) {
            this.nome = nome;
            this.endereco = endereco;
            this.latitude = latitude;
            this.longitude = longitude;
        }
    }

    public static final class Rota {
        public final List<GeoPoint> pontos = new ArrayList<>();
        public final List<Instrucao> instrucoes = new ArrayList<>();
        public final List<Double> distanciaDeCadaPonto = new ArrayList<>();
        public int distanciaMetros;
        public int duracaoSegundos;
    }

    public static final class Instrucao {
        public final String mensagem;
        public final int distanciaDesdeInicio;

        public Instrucao(String mensagem, int distanciaDesdeInicio) {
            this.mensagem = mensagem;
            this.distanciaDesdeInicio = distanciaDesdeInicio;
        }
    }

    private final String chaveApi;
    private final ExecutorService tarefasDeRede = Executors.newSingleThreadExecutor();
    private final Handler gestorPrincipal = new Handler(Looper.getMainLooper());

    public TomTomApi(String chaveApi) {
        this.chaveApi = chaveApi;
    }

    // Procura sugestões, dando prioridade à posição atual quando já está disponível.
    public void pesquisarLocais(String texto, GeoPoint posicao, Resposta<List<Local>> respostaPedido) {
        tarefasDeRede.execute(() -> {
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
                        String endereco;
                        if (morada == null) {
                            endereco = "";
                        } else {
                            endereco = morada.optString("freeformAddress", "");
                        }
                        JSONObject pontoInteresse = item.optJSONObject("poi");
                        String nome;
                        if (pontoInteresse == null) {
                            nome = endereco;
                        } else {
                            nome = pontoInteresse.optString("name", endereco);
                        }
                        if (nome.isEmpty()) nome = endereco;
                        locais.add(new Local(nome, endereco,
                                ponto.optDouble("lat"), ponto.optDouble("lon")));
                    }
                }
                entregar(respostaPedido, locais);
            } catch (Exception e) {
                entregarFalha(respostaPedido);
            }
        });
    }

    // Pede à mesma API TomTom uma rota normal.
    public void calcularRota(GeoPoint origem, Local destino, Resposta<Rota> respostaPedido) {
        tarefasDeRede.execute(() -> {
            try {
                entregar(respostaPedido, pedirRota(origem, destino, new ArrayList<>()));
            } catch (Exception erro) {
                entregarFalha(respostaPedido);
            }
        });
    }

    // Recalcula desde a posição atual, passando por pontos ao lado dos perigos graves.
    public void calcularRotaEvitandoAlertas(GeoPoint origem, Local destino,
            List<double[]> rotaAtual, List<AlertaDTO> alertas, Resposta<Rota> respostaPedido) {
        tarefasDeRede.execute(() -> {
            try {
                Rota rotaPelaEsquerda = pedirRota(origem, destino,
                        criarPontosDeDesvio(rotaAtual, alertas, 1));
                if (rotaTemDistanciaSegura(rotaPelaEsquerda, alertas)) {
                    entregar(respostaPedido, rotaPelaEsquerda);
                    return;
                }
            } catch (Exception ignorado) {
                // Tenta o outro lado do perigo antes de desistir.
            }

            try {
                Rota rotaPelaDireita = pedirRota(origem, destino,
                        criarPontosDeDesvio(rotaAtual, alertas, -1));
                if (rotaTemDistanciaSegura(rotaPelaDireita, alertas)) {
                    entregar(respostaPedido, rotaPelaDireita);
                    return;
                }
            } catch (Exception ignorado) {
                // A falha final é apresentada à Activity abaixo.
            }
            // Se a TomTom não conseguir afastar a rota dos perigos, não a substituímos.
            entregarFalha(respostaPedido);
        });
    }

    // Faz o pedido HTTP GET e transforma a resposta JSON numa rota que o mapa entende.
    private Rota pedirRota(GeoPoint origem, Local destino, List<GeoPoint> pontosDePassagem)
            throws Exception {
        List<GeoPoint> locais = new ArrayList<>();
        locais.add(origem);
        locais.addAll(pontosDePassagem);
        locais.add(new GeoPoint(destino.latitude, destino.longitude));

        StringBuilder coordenadas = new StringBuilder();
        for (GeoPoint local : locais) {
            if (coordenadas.length() > 0) coordenadas.append(':');
            coordenadas.append(String.format(Locale.US, "%f,%f",
                    local.getLatitude(), local.getLongitude()));
        }
        String url = "https://api.tomtom.com/routing/1/calculateRoute/" + coordenadas
                + "/json?key=" + Uri.encode(chaveApi)
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
        double distanciaAcumulada = 0;
        JSONArray trechos = rotaJson.optJSONArray("legs");
        if (trechos != null) {
            for (int i = 0; i < trechos.length(); i++) {
                JSONArray pontosDoTrecho = trechos.getJSONObject(i).optJSONArray("points");
                if (pontosDoTrecho == null) continue;
                for (int j = 0; j < pontosDoTrecho.length(); j++) {
                    JSONObject ponto = pontosDoTrecho.getJSONObject(j);
                    GeoPoint pontoAtual = new GeoPoint(
                            ponto.getDouble("latitude"), ponto.getDouble("longitude"));
                    if (pontoAnterior != null) {
                        distanciaAcumulada += medirDistancia(pontoAnterior, pontoAtual);
                    }
                    rota.pontos.add(pontoAtual);
                    rota.distanciaDeCadaPonto.add(distanciaAcumulada);
                    pontoAnterior = pontoAtual;
                }
            }
        }

        JSONObject orientacoes = rotaJson.optJSONObject("guidance");
        JSONArray listaOrientacoes = orientacoes == null
                ? null : orientacoes.optJSONArray("instructions");
        if (listaOrientacoes != null) {
            for (int i = 0; i < listaOrientacoes.length(); i++) {
                JSONObject item = listaOrientacoes.getJSONObject(i);
                String mensagem = item.optString("message", "");
                if (!mensagem.isEmpty()) {
                    rota.instrucoes.add(new Instrucao(
                            mensagem, item.optInt("routeOffsetInMeters")));
                }
            }
        }
        return rota;
    }

    // Cria dois pontos de passagem do mesmo lado de cada alerta.
    private List<GeoPoint> criarPontosDeDesvio(List<double[]> rota, List<AlertaDTO> alertas,
                                                double lado) {
        List<GeoPoint> pontos = new ArrayList<>();
        for (AlertaDTO alerta : alertas) {
            double[] projecao = projetarAlertaNaRota(rota,
                    alerta.getLatitude(), alerta.getLongitude());
            if (projecao == null) continue;
            double tangenteX = projecao[2];
            double tangenteY = projecao[3];
            double normalX = -tangenteY;
            for (double frente : new double[]{-1, 1}) {
                double x = projecao[0] + tangenteX * frente * 120 + normalX * lado * 180;
                double y = projecao[1] + tangenteY * frente * 120 + tangenteX * lado * 180;
                double latitude = alerta.getLatitude() + y / 111320.0;
                double escalaLongitude = 111320.0
                        * Math.cos(Math.toRadians(alerta.getLatitude()));
                double longitude = alerta.getLongitude() + x / escalaLongitude;
                pontos.add(new GeoPoint(latitude, longitude));
            }
        }
        return pontos;
    }

    // Encontra o ponto da rota mais próximo do alerta e a direção da estrada nesse local.
    private double[] projetarAlertaNaRota(List<double[]> rota, double latitude, double longitude) {
        if (rota == null || rota.size() < 2) return null;
        double escalaY = 111320.0;
        double escalaX = escalaY * Math.cos(Math.toRadians(latitude));
        double menorDistancia = Double.MAX_VALUE;
        double[] melhorResultado = null;
        for (int i = 1; i < rota.size(); i++) {
            double[] inicio = rota.get(i - 1);
            double[] fim = rota.get(i);
            double inicioX = (inicio[1] - longitude) * escalaX;
            double inicioY = (inicio[0] - latitude) * escalaY;
            double fimX = (fim[1] - longitude) * escalaX;
            double fimY = (fim[0] - latitude) * escalaY;
            double variacaoX = fimX - inicioX;
            double variacaoY = fimY - inicioY;
            double tamanho = variacaoX * variacaoX + variacaoY * variacaoY;
            if (tamanho == 0) continue;
            double fracao = -(inicioX * variacaoX + inicioY * variacaoY) / tamanho;
            fracao = Math.max(0, Math.min(1, fracao));
            double pontoX = inicioX + fracao * variacaoX;
            double pontoY = inicioY + fracao * variacaoY;
            double distancia = Math.hypot(pontoX, pontoY);
            if (distancia < menorDistancia) {
                double comprimento = Math.sqrt(tamanho);
                melhorResultado = new double[]{pontoX, pontoY,
                        variacaoX / comprimento, variacaoY / comprimento};
                menorDistancia = distancia;
            }
        }
        return melhorResultado;
    }

    // Só aceita um desvio se a nova linha ficar afastada de todos os alertas.
    private boolean rotaTemDistanciaSegura(Rota rota, List<AlertaDTO> alertas) {
        if (rota.pontos.size() < 2) return false;
        List<double[]> pontos = new ArrayList<>();
        for (GeoPoint ponto : rota.pontos) {
            pontos.add(new double[]{ponto.getLatitude(), ponto.getLongitude()});
        }
        for (AlertaDTO alerta : alertas) {
            if (VerificadorDeAlertas.distanciaLateralAteRota(pontos,
                    alerta.getLatitude(), alerta.getLongitude()) < 100) return false;
        }
        return true;
    }
    private double medirDistancia(GeoPoint pontoA, GeoPoint pontoB) {
        return VerificadorDeAlertas.medirDistancia(pontoA.getLatitude(), pontoA.getLongitude(),
                pontoB.getLatitude(), pontoB.getLongitude());
    }

    public void fechar() {
        tarefasDeRede.shutdownNow();
    }

    private JSONObject pedirJson(String endereco) throws Exception {
        com.google.gson.JsonObject resposta = ClienteRetrofit.executar(
                ClienteRetrofit.tomTom.obter(endereco));
        if (resposta == null) throw new IllegalStateException("Resposta vazia da TomTom");
        return new JSONObject(resposta.toString());
    }

    private <T> void entregar(Resposta<T> respostaPedido, T resultado) {
        gestorPrincipal.post(() -> respostaPedido.aoConcluir(resultado));
    }

    private void entregarFalha(Resposta<?> respostaPedido) {
        gestorPrincipal.post(respostaPedido::aoFalhar);
    }
}
