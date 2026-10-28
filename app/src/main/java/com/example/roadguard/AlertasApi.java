package com.example.roadguard;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import java.net.UnknownHostException;
import java.net.SocketTimeoutException;
import java.io.IOException;
import javax.net.ssl.SSLException;
import com.google.gson.JsonParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Pedidos REST de alertas, executados fora da thread da interface. */
final class AlertasApi {
    // Uma fila de pedidos de rede, executados sem bloquear o ecrã.
    private final ExecutorService tarefasDeRede = Executors.newSingleThreadExecutor();
    private final Handler principal = new Handler(Looper.getMainLooper());
    private volatile boolean fechado;

    enum Erro { DNS, LIGACAO, TEMPO_ESGOTADO, SEM_PERMISSAO, DADOS, SERVIDOR, LIGACAO_SEGURA }

    // T é o tipo de resultado: List<AlertaDTO>, String ou Boolean.
    // A Activity indica o que fazer quando o pedido acaba ou falha.
    interface Resposta<T> {
        void aoConcluir(T resultado);
        void aoFalhar(Erro erro);
    }

    void listar(Resposta<List<AlertaDTO>> respostaPedido) {
        tarefasDeRede.execute(() -> {
            try {
                Map<String, JsonElement> dados = ClienteRetrofit.executar(
                        ClienteRetrofit.firebase.listarAlertas());
                List<AlertaDTO> alertas = new ArrayList<>();
                if (dados != null) {
                    for (String id : dados.keySet()) {
                        AlertaDTO alerta = lerAlerta(id, dados.get(id));
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
                if (categoria == null) throw new JsonParseException("Categoria desconhecida");
                JsonObject json = new JsonObject();
                json.addProperty("utilizadorId", alerta.getUtilizadorId());
                json.addProperty("categoriaId", alerta.getCategoriaId());
                json.addProperty("gravidade", categoria.gravidade);
                json.addProperty("latitude", alerta.getLatitude());
                json.addProperty("longitude", alerta.getLongitude());
                // A hora é atribuída pelo servidor, independentemente do relógio do telefone.
                JsonObject dataServidor = new JsonObject();
                dataServidor.addProperty(".sv", "timestamp");
                json.add("dataCriacao", dataServidor);
                json.addProperty("ativo", true);
                String id = ClienteRetrofit.executar(ClienteRetrofit.firebase.criarAlerta(json)).get("name").getAsString();
                if (id.isEmpty()) throw new IllegalStateException("Identificador em falta");
                entregar(() -> respostaPedido.aoConcluir(id));
            } catch (Exception e) {
                falhar(respostaPedido, e);
            }
        });
    }

    // Remove o perigo da Firebase usando o seu identificador.
    void eliminar(String alertaId, Resposta<Boolean> respostaPedido) {
        tarefasDeRede.execute(() -> {
            try {
                ClienteRetrofit.executar(ClienteRetrofit.firebase.eliminarAlerta(alertaId));
                entregar(() -> respostaPedido.aoConcluir(true));
            } catch (Exception erro) {
                falhar(respostaPedido, erro);
            }
        });
    }

    void confirmar(String id, String utilizador, boolean aindaExiste, Resposta<Boolean> respostaPedido) {
        tarefasDeRede.execute(() -> {
            try {
                AlertaDTO alerta = lerAlerta(id, ClienteRetrofit.executar(ClienteRetrofit.firebase.obterAlerta(id)));
                if (alerta == null || !alerta.isAtivo() || utilizador.isEmpty()
                        || utilizador.equals(alerta.getUtilizadorId()) || alerta.jaConfirmou(utilizador)) {
                    entregar(() -> respostaPedido.aoConcluir(false));
                    return;
                }
                ClienteRetrofit.executar(ClienteRetrofit.firebase.confirmarAlerta(id, utilizador, aindaExiste));
                entregar(() -> respostaPedido.aoConcluir(true));
            } catch (Exception erro) {
                falhar(respostaPedido, erro);
            }
        });
    }

    /** Lê apenas o nome deste utilizador, sem obter emails ou palavras-passe. */
    void obterNomeDoAutor(String idUtilizador, Resposta<String> respostaPedido) {
        tarefasDeRede.execute(() -> {
            try {
                String nome = ClienteRetrofit.executar(ClienteRetrofit.firebase.obterNome(idUtilizador));
                if (nome == null) nome = "";
                String nomeEncontrado = nome.trim();
                entregar(() -> respostaPedido.aoConcluir(nomeEncontrado));
            } catch (Exception causa) {
                falhar(respostaPedido, causa);
            }
        });
    }

    private AlertaDTO lerAlerta(String id, JsonElement valor) {
        try {
            if (valor == null || !valor.isJsonObject()) return null;
            JsonObject json = valor.getAsJsonObject();
            // Rejeita números/booleanos guardados como texto e campos em falta.
            if (!json.getAsJsonPrimitive("latitude").isNumber()
                    || !json.getAsJsonPrimitive("longitude").isNumber()
                    || !json.getAsJsonPrimitive("dataCriacao").isNumber()
                    || !json.getAsJsonPrimitive("ativo").isBoolean()) return null;
            double lat = json.get("latitude").getAsDouble();
            double lon = json.get("longitude").getAsDouble();
            String categoria = json.get("categoriaId").getAsString();
            String gravidade = json.get("gravidade").getAsString();
            if (!Double.isFinite(lat) || !Double.isFinite(lon) || Math.abs(lat) > 90
                    || Math.abs(lon) > 180 || json.get("utilizadorId").getAsString().isEmpty()
                    || CategoriaAlerta.procurar(categoria) == null
                    || !("baixa".equals(gravidade) || "media".equals(gravidade)
                    || "elevada".equals(gravidade))) return null;
            AlertaDTO alerta = ClienteRetrofit.gson.fromJson(valor, AlertaDTO.class);
            alerta.setId(id);
            return alerta;
        } catch (Exception erro) {
            return null; // Um alerta inválido não impede a leitura dos restantes.
        }
    }

    private void falhar(Resposta<?> respostaPedido, Exception causa) {
        // O Android omite a stack trace de UnknownHostException; incluir a classe ajuda a diagnosticar DNS.
        Log.w("RoadGuardAlertas", causa.getClass().getSimpleName() + ": " + causa.getMessage(), causa);
        Erro erro;
        if (causa instanceof UnknownHostException) erro = Erro.DNS;
        else if (causa instanceof SocketTimeoutException) erro = Erro.TEMPO_ESGOTADO;
        else if (causa instanceof SSLException) erro = Erro.LIGACAO_SEGURA;
        else if (causa instanceof retrofit2.HttpException) {
            int estado = ((retrofit2.HttpException) causa).code();
            if (estado == 401 || estado == 403) erro = Erro.SEM_PERMISSAO;
            else if (estado == 400) erro = Erro.DADOS;
            else erro = Erro.SERVIDOR;
        } else if (causa instanceof JsonParseException) erro = Erro.DADOS;
        else if (causa instanceof IOException) erro = Erro.LIGACAO;
        else erro = Erro.SERVIDOR;
        Erro resultado = erro;
        entregar(() -> respostaPedido.aoFalhar(resultado));
    }

    // Handler devolve a resposta à thread principal, onde se pode alterar a interface.
    private void entregar(Runnable acao) {
        principal.post(() -> { if (!fechado) acao.run(); });
    }

    void fechar() {
        fechado = true;
        principal.removeCallbacksAndMessages(null);
        tarefasDeRede.shutdownNow();
    }
}
