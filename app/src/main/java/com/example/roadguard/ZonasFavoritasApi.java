package com.example.roadguard;

import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Faz os pedidos HTTP usados para guardar, ler e eliminar zonas favoritas na Firebase. */
final class ZonasFavoritasApi {
    private static final String URL_BASE =
            "https://roadguard-25b31-default-rtdb.europe-west1.firebasedatabase.app/";
    // Os pedidos correm em segundo plano para a interface não bloquear.
    private final ExecutorService tarefasDeRede = Executors.newSingleThreadExecutor();
    // As respostas são entregues na thread principal do Android.
    private final Handler principal = new Handler(Looper.getMainLooper());
    private volatile boolean fechado;

    // T é o resultado: uma lista, uma zona criada ou confirmação de eliminação.
    interface Resposta<T> {
        void aoConcluir(T resultado);
        void aoFalhar();
    }

    // GET: lê as zonas guardadas no nó do utilizador com sessão iniciada.
    void listar(String utilizadorId, Resposta<List<ZonaFavorita>> respostaPedido) {
        tarefasDeRede.execute(() -> {
            try {
                String endereco = URL_BASE + "Zonas_Favoritas/" + Uri.encode(utilizadorId) + ".json";
                String texto = pedir(endereco, "GET", null);
                List<ZonaFavorita> zonas = new ArrayList<>();
                if (!"null".equals(texto.trim())) {
                    JSONObject dados = new JSONObject(texto);
                    Iterator<String> ids = dados.keys();
                    while (ids.hasNext()) {
                        String id = ids.next();
                        JSONObject json = dados.optJSONObject(id);
                        ZonaFavorita zona = lerZona(id, utilizadorId, json);
                        if (zona != null) zonas.add(zona);
                    }
                }
                entregar(() -> respostaPedido.aoConcluir(zonas));
            } catch (Exception erro) {
                falhar(erro, respostaPedido);
            }
        });
    }

    // POST: guarda uma zona e recebe da Firebase o id que a identifica.
    void criar(ZonaFavorita zona, Resposta<ZonaFavorita> respostaPedido) {
        tarefasDeRede.execute(() -> {
            try {
                JSONObject json = new JSONObject();
                json.put("utilizadorId", zona.utilizadorId);
                json.put("nome", zona.nome);
                json.put("local", zona.local);
                json.put("latitude", zona.latitude);
                json.put("longitude", zona.longitude);
                json.put("raioKm", zona.raioKm);
                String endereco = URL_BASE + "Zonas_Favoritas/"
                        + Uri.encode(zona.utilizadorId) + ".json";
                String id = new JSONObject(pedir(endereco, "POST", json)).optString("name");
                if (id.isEmpty()) throw new IllegalStateException("A Firebase não devolveu um identificador");
                ZonaFavorita guardada = new ZonaFavorita(id, zona.utilizadorId, zona.nome,
                        zona.local, zona.latitude, zona.longitude, zona.raioKm);
                entregar(() -> respostaPedido.aoConcluir(guardada));
            } catch (Exception erro) {
                falhar(erro, respostaPedido);
            }
        });
    }

    // DELETE: remove a zona selecionada do nó do utilizador.
    void eliminar(String utilizadorId, String zonaId, Resposta<Boolean> respostaPedido) {
        tarefasDeRede.execute(() -> {
            try {
                String endereco = URL_BASE + "Zonas_Favoritas/" + Uri.encode(utilizadorId)
                        + "/" + Uri.encode(zonaId) + ".json";
                pedir(endereco, "DELETE", null);
                entregar(() -> respostaPedido.aoConcluir(true));
            } catch (Exception erro) {
                falhar(erro, respostaPedido);
            }
        });
    }

    // Converte o JSON recebido num objeto e ignora dados incompletos ou inválidos.
    private ZonaFavorita lerZona(String id, String utilizadorId, JSONObject json) {
        if (json == null || !utilizadorId.equals(json.optString("utilizadorId"))) return null;
        double latitude = json.optDouble("latitude", Double.NaN);
        double longitude = json.optDouble("longitude", Double.NaN);
        int raioKm = json.optInt("raioKm", 0);
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude)
                || Math.abs(latitude) > 90 || Math.abs(longitude) > 180
                // Mantém compatibilidade com zonas antigas de 25 e 50 km.
                || !(raioKm == 1 || raioKm == 2 || raioKm == 5 || raioKm == 10
                || raioKm == 25 || raioKm == 50)) return null;
        String nome = json.optString("nome", "").trim();
        String local = json.optString("local", "").trim();
        if (nome.isEmpty() || local.isEmpty()) return null;
        return new ZonaFavorita(id, utilizadorId, nome, local, latitude, longitude, raioKm);
    }

    // Abre uma ligação HTTP e devolve o texto recebido da Firebase.
    private String pedir(String endereco, String metodo, JSONObject corpo) throws Exception {
        HttpURLConnection ligacao = (HttpURLConnection) new URL(endereco).openConnection();
        try {
            ligacao.setRequestMethod(metodo);
            ligacao.setConnectTimeout(15000);
            ligacao.setReadTimeout(15000);
            ligacao.setRequestProperty("Accept", "application/json");
            if (corpo != null) {
                ligacao.setDoOutput(true);
                ligacao.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                try (OutputStream saida = ligacao.getOutputStream()) {
                    saida.write(corpo.toString().getBytes(StandardCharsets.UTF_8));
                }
            }
            int estado = ligacao.getResponseCode();
            if (estado < 200 || estado >= 300) throw new IllegalStateException("HTTP " + estado);
            try (BufferedReader leitor = new BufferedReader(new InputStreamReader(
                    ligacao.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder resposta = new StringBuilder();
                String linha;
                while ((linha = leitor.readLine()) != null) resposta.append(linha);
                return resposta.toString();
            }
        } finally {
            ligacao.disconnect();
        }
    }

    private void falhar(Exception erro, Resposta<?> respostaPedido) {
        Log.e("RoadGuardZonas", "Não foi possível comunicar com a Firebase", erro);
        entregar(respostaPedido::aoFalhar);
    }

    private void entregar(Runnable acao) {
        principal.post(() -> { if (!fechado) acao.run(); });
    }

    // Fecha as tarefas quando o ecrã das zonas é fechado.
    void fechar() {
        fechado = true;
        principal.removeCallbacksAndMessages(null);
        tarefasDeRede.shutdownNow();
    }
}
