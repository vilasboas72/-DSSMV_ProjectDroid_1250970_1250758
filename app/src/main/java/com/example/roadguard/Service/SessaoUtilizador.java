package com.example.roadguard.Service;

import android.content.Context;
import android.content.SharedPreferences;

/** Guarda e consulta a conta ligada na memória privada da aplicação. */
public final class SessaoUtilizador {
    private final SharedPreferences preferencias;

    public SessaoUtilizador(Context contexto) {
        preferencias = contexto.getSharedPreferences("RoadGuardPrefs", Context.MODE_PRIVATE);
    }

    public boolean temSessao() {
        return preferencias.contains("UID_LOGADO");
    }

    public String obterId() {
        return preferencias.getString("UID_LOGADO", "");
    }

    public String obterEmail() {
        return preferencias.getString("EMAIL_LOGADO", "a sua conta");
    }

    public void guardar(String id, String email) {
        preferencias.edit().putString("UID_LOGADO", id).putString("EMAIL_LOGADO", email).apply();
    }

    public void limpar() {
        preferencias.edit().remove("UID_LOGADO").remove("EMAIL_LOGADO").apply();
    }
}
