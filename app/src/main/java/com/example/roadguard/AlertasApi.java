package com.example.roadguard;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.net.Uri;
import org.json.JSONObject;
import org.json.JSONTokener;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.UnknownHostException;
import java.net.SocketTimeoutException;
import java.io.IOException;
import javax.net.ssl.SSLException;
import org.json.JSONException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Pedidos REST de alertas, executados fora da thread da interface. */
final class AlertasApi {
    private static final String URL_BASE =
            "https://roadguard-25b31-default-rtdb.europe-west1.firebasedatabase.app/";
    private static final String URL_ALERTAS = URL_BASE + "Alerta.json";
    private final ExecutorService tarefasDeRede = Executors.newSingleThreadExecutor();
    private final Handler principal = new Handler(Looper.getMainLooper());
    private volatile boolean fechado;
    // Evita repetir o pedido quando se abrem vários alertas do mesmo autor.
    private final Map<String, String> nomesDosAutores = new HashMap<>();

    enum Erro { DNS, LIGACAO, TEMPO_ESGOTADO, SEM_PERMISSAO, DADOS, SERVIDOR, LIGACAO_SEGURA }

    private static final class ErroHttp extends IOException {
        final int estado;
        ErroHttp(int estado) { super("HTTP " + estado); this.estado = estado; }
    }

    interface Resposta<T> {
        void aoConcluir(T resultado);
        void aoFalhar(Erro erro);
    }

    void listar(Resposta<List<AlertaDTO>> respostaPedido) {
        tarefasDeRede.execute(() -> {
            try {
                String resposta = pedir(URL_ALERTAS, "GET", null);
                List<AlertaDTO> alertas = new ArrayList<>();
                // Um nó que ainda não existe é devolvido como JSON null.
                if (!"null".equals(resposta.trim())) {
                    JSONObject dados = new JSONObject(resposta);
                    Iterator<String> chaves = dados.keys();
                    while (chaves.hasNext()) {
                        String id = chaves.next();
                        JSONObject json = dados.optJSONObject(id);
                        AlertaDTO alerta = lerAlerta(id, json);
                        if (alerta != null && alerta.isAtivo()) alertas.add(alerta);
                    }
                }
                entregar(() -> respostaPedido.aoConcluir(alertas));
            } catch (Exception e) {
                falhar(respostaPedido, e);
            }
        });
    }

    void criar(AlertaDTO alerta, Resposta<String> respostaPedido) {
        tarefasDeRede.execute(() -> {
            try {
                CategoriaAlerta categoria = CategoriaAlerta.procurar(alerta.getCategoriaId());
                if (categoria == null) throw new JSONException("Categoria desconhecida");
                JSONObject json = new JSONObject();
                json.put("utilizadorId", alerta.getUtilizadorId());
                json.put("categoriaId", alerta.getCategoriaId());
                json.put("gravidade", categoria.gravidade);
                json.put("latitude", alerta.getLatitude());
                json.put("longitude", alerta.getLongitude());
                // A hora é atribuída pelo servidor, independentemente do relógio do telefone.
                json.put("dataCriacao", new JSONObject().put(".sv", "timestamp"));
                json.put("ativo", true);
                String id = new JSONObject(pedir(URL_ALERTAS, "POST", json)).getString("name");
                if (id.isEmpty()) throw new IllegalStateException("Identificador em falta");
                entregar(() -> respostaPedido.aoConcluir(id));
            } catch (Exception e) {
                falhar(respostaPedido, e);
            }
        });
    }

    /** Lê apenas o nome deste utilizador, sem obter emails ou palavras-passe. */
    void obterNomeDoAutor(String idUtilizador, Resposta<String> respostaPedido) {
        tarefasDeRede.execute(() -> {
            try {
                String nome = nomesDosAutores.get(idUtilizador);
                if (nome == null) {
                    String endereco = URL_BASE + "Utilizador/" + Uri.encode(idUtilizador) + "/nome.json";
                    Object valor = new JSONTokener(pedir(endereco, "GET", null)).nextValue();
                    nome = valor instanceof String ? ((String) valor).trim() : "";
                    if (!nome.isEmpty()) nomesDosAutores.put(idUtilizador, nome);
                }
                String nomeEncontrado = nome;
                entregar(() -> respostaPedido.aoConcluir(nomeEncontrado));
            } catch (Exception causa) {
                falhar(respostaPedido, causa);
            }
        });
    }

    private AlertaDTO lerAlerta(String id, JSONObject json) {
        if (json == null || !(json.opt("latitude") instanceof Number)
                || !(json.opt("longitude") instanceof Number)
                || !(json.opt("dataCriacao") instanceof Number)
                || !(json.opt("ativo") instanceof Boolean)) return null;
        double lat = json.optDouble("latitude", Double.NaN);
        double lon = json.optDouble("longitude", Double.NaN);
        String categoria = json.optString("categoriaId");
        String gravidade = json.optString("gravidade");
        if (!Double.isFinite(lat) || !Double.isFinite(lon) || Math.abs(lat) > 90
                || Math.abs(lon) > 180 || json.optString("utilizadorId").isEmpty()
                || CategoriaAlerta.procurar(categoria) == null
                || !("baixa".equals(gravidade) || "media".equals(gravidade) || "elevada".equals(gravidade))) return null;
        AlertaDTO alerta = new AlertaDTO();
        alerta.setId(id);
        alerta.setUtilizadorId(json.optString("utilizadorId"));
        alerta.setCategoriaId(categoria);
        alerta.setGravidade(gravidade);
        alerta.setLatitude(lat);
        alerta.setLongitude(lon);
        alerta.setDataCriacao(json.optLong("dataCriacao"));
        alerta.setAtivo(json.optBoolean("ativo"));
        return alerta;
    }

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
            if (estado < 200 || estado >= 300) throw new ErroHttp(estado);
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

    private void falhar(Resposta<?> respostaPedido, Exception causa) {
        // O Android omite a stack trace de UnknownHostException; incluir a classe ajuda a diagnosticar DNS.
        Log.w("RoadGuardAlertas", causa.getClass().getSimpleName() + ": " + causa.getMessage(), causa);
        Erro erro;
        if (causa instanceof UnknownHostException) erro = Erro.DNS;
        else if (causa instanceof SocketTimeoutException) erro = Erro.TEMPO_ESGOTADO;
        else if (causa instanceof SSLException) erro = Erro.LIGACAO_SEGURA;
        else if (causa instanceof ErroHttp) {
            int estado = ((ErroHttp) causa).estado;
            erro = estado == 401 || estado == 403 ? Erro.SEM_PERMISSAO
                    : estado == 400 ? Erro.DADOS : Erro.SERVIDOR;
        } else if (causa instanceof JSONException) erro = Erro.DADOS;
        else if (causa instanceof IOException) erro = Erro.LIGACAO;
        else erro = Erro.SERVIDOR;
        Erro resultado = erro;
        entregar(() -> respostaPedido.aoFalhar(resultado));
    }

    private void entregar(Runnable acao) {
        principal.post(() -> { if (!fechado) acao.run(); });
    }

    void fechar() {
        fechado = true;
        principal.removeCallbacksAndMessages(null);
        tarefasDeRede.shutdownNow();
    }
}
