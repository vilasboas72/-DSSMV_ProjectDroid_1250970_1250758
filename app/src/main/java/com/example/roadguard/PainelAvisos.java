package com.example.roadguard;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/** Mostra um aviso de cada vez, sem bloquear o mapa nem abrir uma janela inteira. */
final class PainelAvisos {
    static final class Aviso {
        final AlertaDTO alerta;
        final double distancia;
        Aviso(AlertaDTO alerta, double distancia) {
            this.alerta = alerta;
            this.distancia = distancia;
        }
    }

    private final Activity atividade;
    private final View painel;
    private final Handler relogio = new Handler(Looper.getMainLooper());
    private final List<String> idsJaMostrados = new ArrayList<>();
    private List<Aviso> disponiveis = new ArrayList<>();
    private Aviso atual;
    private boolean ativo;
    // Avisa o mapa para reposicionar os botões quando a caixa abre ou fecha.
    private final Runnable aoMudarVisibilidade;
    private final Runnable fecharAviso = this::fecharAtual;

    PainelAvisos(Activity atividade, View painel, Runnable aoMudarVisibilidade) {
        this.atividade = atividade;
        this.painel = painel;
        this.aoMudarVisibilidade = aoMudarVisibilidade;
        painel.findViewById(R.id.aviso_fechar).setOnClickListener(vista -> fecharAtual());
    }

    void atualizar(List<Aviso> avisos) {
        disponiveis = avisos;
        if (atual != null) {
            for (Aviso aviso : disponiveis) {
                if (aviso.alerta.getId().equals(atual.alerta.getId())) {
                    atual = aviso;
                    preencherAviso();
                    return;
                }
            }
            // Por exemplo: terminou a viagem, o alerta foi apagado ou ficou para trás.
            esconder();
        }
        mostrarSeguinte();
    }

    void retomar() { ativo = true; mostrarSeguinte(); }

    void parar() {
        ativo = false;
        esconder();
        disponiveis.clear();
    }

    void reiniciarViagem() { idsJaMostrados.clear(); }

    private void mostrarSeguinte() {
        if (!ativo || atual != null || atividade.isFinishing() || atividade.isDestroyed()) return;
        for (Aviso aviso : disponiveis) {
            String id = aviso.alerta.getId();
            if (idsJaMostrados.contains(id)) continue;
            idsJaMostrados.add(id);
            atual = aviso;
            preencherAviso();
            painel.setVisibility(View.VISIBLE);
            painel.post(aoMudarVisibilidade);
            // Handler agenda o fecho para daqui a 12 segundos, sem bloquear o ecrã.
            relogio.postDelayed(fecharAviso, 12000);
            return;
        }
        painel.setVisibility(View.GONE);
    }

    private void preencherAviso() {
        CategoriaAlerta categoria = CategoriaAlerta.procurar(atual.alerta.getCategoriaId());
        if (categoria == null) return;
        ((ImageView) painel.findViewById(R.id.aviso_icone)).setImageResource(categoria.icone);
        ((TextView) painel.findViewById(R.id.aviso_titulo)).setText(categoria.nome);
        String distancia = Math.round(atual.distancia / 10.0) * 10 + " m";
        ((TextView) painel.findViewById(R.id.aviso_contexto)).setText(
                atividade.getString(R.string.aviso_na_rota, distancia));
        ((TextView) painel.findViewById(R.id.aviso_data)).setText(
                atividade.getString(R.string.aviso_reportado, tempoDesde(atual.alerta.getDataCriacao())));
    }

    private void fecharAtual() { esconder(); mostrarSeguinte(); }

    private void esconder() {
        relogio.removeCallbacksAndMessages(null);
        atual = null;
        painel.setVisibility(View.GONE);
        painel.post(aoMudarVisibilidade);
    }

    private String tempoDesde(long data) {
        long minutos = Math.max(0, (System.currentTimeMillis() - data) / 60000);
        if (minutos == 0) return atividade.getString(R.string.alert_age_now);
        if (minutos < 60) return atividade.getResources().getQuantityString(
                R.plurals.alert_age_minutes, (int) minutos, minutos);
        long horas = minutos / 60;
        if (horas < 24) return atividade.getResources().getQuantityString(
                R.plurals.alert_age_hours, (int) horas, horas);
        long dias = horas / 24;
        return atividade.getResources().getQuantityString(R.plurals.alert_age_days, (int) dias, dias);
    }
}
