package com.example.roadguard.Service;

import android.os.Handler;
import android.os.Looper;

import com.example.roadguard.API.AlertasApi;
import com.example.roadguard.DTO.AlertaDTO;

import java.util.List;
import java.util.function.Consumer;

/** Atualiza a lista de alertas enquanto o mapa está visível. */
public final class LeituraDeAlertas {
    private final AlertasApi api;
    private final Consumer<List<AlertaDTO>> aoReceber;
    private final Consumer<AlertasApi.Erro> aoFalhar;
    private final Handler fila = new Handler(Looper.getMainLooper());
    private boolean ativa;
    private boolean aLer;
    private int falhasConsecutivas;

    private final Runnable atualizarPeriodicamente = new Runnable() {
        @Override public void run() {
            if (!ativa) return;
            carregar();
            fila.postDelayed(this, 30_000);
        }
    };

    public LeituraDeAlertas(AlertasApi api, Consumer<List<AlertaDTO>> aoReceber,
                           Consumer<AlertasApi.Erro> aoFalhar) {
        this.api = api;
        this.aoReceber = aoReceber;
        this.aoFalhar = aoFalhar;
    }

    public void iniciar() {
        ativa = true;
        fila.removeCallbacks(atualizarPeriodicamente);
        fila.post(atualizarPeriodicamente);
    }

    public void parar() {
        ativa = false;
        fila.removeCallbacksAndMessages(null);
    }

    public void carregar() {
        if (!ativa || aLer) return;
        aLer = true;
        api.listar(new AlertasApi.Resposta<>() {
            @Override public void aoConcluir(List<AlertaDTO> alertas) {
                aLer = false;
                falhasConsecutivas = 0;
                if (ativa) aoReceber.accept(alertas);
            }

            @Override public void aoFalhar(AlertasApi.Erro erro) {
                aLer = false;
                if (!ativa) return;
                falhasConsecutivas++;
                if (falhasConsecutivas == 1) {
                    fila.postDelayed(LeituraDeAlertas.this::carregar, 3_000);
                } else if (falhasConsecutivas == 2) {
                    aoFalhar.accept(erro);
                }
            }
        });
    }
}
