package com.example.roadguard.Service;

import android.content.Context;
import android.content.SharedPreferences;
import com.example.roadguard.API.TomTomApi;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Mantém as três pesquisas mais recentes de cada conta no telemóvel. */
public final class GestorHistorico {
    private final SharedPreferences preferencias;
    private final String chave;

    public GestorHistorico(Context contexto, String utilizador) {
        preferencias = contexto.getSharedPreferences("RoadGuardPrefs", Context.MODE_PRIVATE);
        chave = "historico_pesquisas_" + utilizador;
    }

    // Lê os locais da conta; um histórico inválido não impede pesquisar.
    public List<TomTomApi.Local> ler() {
        List<TomTomApi.Local> historico = new ArrayList<>();
        String dados = preferencias
                .getString(chave, "[]");
        try {
            JSONArray json = new JSONArray(dados);
            for (int i = 0; i < json.length() && historico.size() < 3; i++) {
                JSONObject local = json.getJSONObject(i);
                double latitude = local.optDouble("latitude", Double.NaN);
                double longitude = local.optDouble("longitude", Double.NaN);
                if (!Double.isFinite(latitude) || !Double.isFinite(longitude)) continue;
                historico.add(new TomTomApi.Local(local.optString("nome"),
                        local.optString("endereco"), latitude, longitude));
            }
        } catch (JSONException ignorado) {
            // Um histórico inválido é ignorado; a pesquisa normal continua disponível.
        }
        return historico;
    }

    // Coloca o local em primeiro lugar, sem duplicados e com um máximo de três.
    public void guardar(TomTomApi.Local novoLocal) {
        List<TomTomApi.Local> historico = ler();
        historico.removeIf(local -> local.nome.equalsIgnoreCase(novoLocal.nome)
                && Math.abs(local.latitude - novoLocal.latitude) < 0.0001
                && Math.abs(local.longitude - novoLocal.longitude) < 0.0001);
        historico.add(0, novoLocal);
        if (historico.size() > 3) historico = new ArrayList<>(historico.subList(0, 3));

        guardarLista(historico);
    }

    // Apaga apenas o local escolhido e devolve a lista atualizada.
    public List<TomTomApi.Local> remover(TomTomApi.Local removido) {
        List<TomTomApi.Local> historico = ler();
        historico.removeIf(local -> local.nome.equalsIgnoreCase(removido.nome)
                && Math.abs(local.latitude - removido.latitude) < 0.0001
                && Math.abs(local.longitude - removido.longitude) < 0.0001);
        guardarLista(historico);
        return historico;
    }

    // Grava a lista em JSON usando a mesma chave da versão anterior.
    private void guardarLista(List<TomTomApi.Local> historico) {
        JSONArray json = new JSONArray();
        for (TomTomApi.Local local : historico) {
            JSONObject item = new JSONObject();
            try {
                item.put("nome", local.nome);
                item.put("endereco", local.endereco);
                item.put("latitude", local.latitude);
                item.put("longitude", local.longitude);
                json.put(item);
            } catch (JSONException ignorado) {
                // Ignora só o item que não pôde ser convertido em JSON.
            }
        }
        preferencias.edit()
                .putString(chave, json.toString()).apply();
    }
}
