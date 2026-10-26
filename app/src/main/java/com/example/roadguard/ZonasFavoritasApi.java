package com.example.roadguard;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import java.util.Map;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Faz os pedidos HTTP usados para guardar, ler e eliminar zonas favoritas na Firebase. */
final class ZonasFavoritasApi {
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
                Map<String, JsonElement> dados = ClienteRetrofit.executar(
                        ClienteRetrofit.firebase.listarZonas(utilizadorId));
                List<ZonaFavorita> zonas = new ArrayList<>();
                if (dados != null) {
                    for (String id : dados.keySet()) {
                        ZonaFavorita zona = lerZona(id, utilizadorId, dados.get(id));
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
                JsonObject json = ClienteRetrofit.gson.toJsonTree(zona).getAsJsonObject();
                json.remove("id"); // A chave é criada pelo POST, não pertence ao corpo.
                String id = ClienteRetrofit.executar(ClienteRetrofit.firebase.criarZona(
                        zona.utilizadorId, json))
                        .get("name").getAsString();
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
                ClienteRetrofit.executar(ClienteRetrofit.firebase.eliminarZona(utilizadorId, zonaId));
                entregar(() -> respostaPedido.aoConcluir(true));
            } catch (Exception erro) {
                falhar(erro, respostaPedido);
            }
        });
    }

    // Converte o JSON recebido num objeto e ignora dados incompletos ou inválidos.
    private ZonaFavorita lerZona(String id, String utilizadorId, JsonElement valor) {
        try {
            if (valor == null || !valor.isJsonObject()) return null;
            ZonaFavorita zona = ClienteRetrofit.gson.fromJson(valor, ZonaFavorita.class);
            if (!utilizadorId.equals(zona.utilizadorId)) return null;
            if (!Double.isFinite(zona.latitude) || !Double.isFinite(zona.longitude)
                    || Math.abs(zona.latitude) > 90 || Math.abs(zona.longitude) > 180
                    || !(zona.raioKm == 1 || zona.raioKm == 2 || zona.raioKm == 5
                    || zona.raioKm == 10 || zona.raioKm == 25 || zona.raioKm == 50)
                    || zona.nome == null || zona.nome.trim().isEmpty()
                    || zona.local == null || zona.local.trim().isEmpty()) return null;
            JsonObject json = valor.getAsJsonObject();
            if (!json.has("latitude") || !json.has("longitude")) return null;
            return new ZonaFavorita(id, utilizadorId, zona.nome.trim(), zona.local.trim(),
                    zona.latitude, zona.longitude, zona.raioKm);
        } catch (Exception erro) {
            return null;
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
