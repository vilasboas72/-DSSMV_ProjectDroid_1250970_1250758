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
    interface Confirmar {
        void enviar(AlertaDTO alerta, boolean aindaExiste, AlertasApi.Resposta<Boolean> resposta);
    }

    static final class Aviso {
        final AlertaDTO alerta;
        final double distancia;
        final boolean pedirConfirmacao;
        Aviso(AlertaDTO alerta, double distancia) {
            this(alerta, distancia, false);
        }
        Aviso(AlertaDTO alerta, double distancia, boolean pedirConfirmacao) {
            this.alerta = alerta;
            this.distancia = distancia;
            this.pedirConfirmacao = pedirConfirmacao;
        }
    }

    private final Activity atividade;
    private final View painel;
    private final Handler relogio = new Handler(Looper.getMainLooper());
    private final List<String> idsJaMostrados = new ArrayList<>();
    private final List<String> confirmacoesJaPedidas = new ArrayList<>();
    private final Confirmar confirmar;
    private boolean aEnviar;
    private List<Aviso> disponiveis = new ArrayList<>();
    private Aviso atual;
    private boolean ativo;
    // Avisa o mapa para reposicionar os botões quando a caixa abre ou fecha.
    private final Runnable aoMudarVisibilidade;
    private final Runnable fecharAviso = this::fecharAtual;

    PainelAvisos(Activity atividade, View painel, Runnable aoMudarVisibilidade, Confirmar confirmar) {
        this.atividade = atividade;
        this.painel = painel;
        this.aoMudarVisibilidade = aoMudarVisibilidade;
        this.confirmar = confirmar;
        painel.findViewById(R.id.aviso_fechar).setOnClickListener(vista -> fecharAtual());
        painel.findViewById(R.id.aviso_ainda_existe).setOnClickListener(vista -> responder(true));
        painel.findViewById(R.id.aviso_ja_nao_existe).setOnClickListener(vista -> responder(false));
    }

    void atualizar(List<Aviso> avisos) {
        disponiveis = avisos;
        // A pergunta depois da passagem não deve ficar à espera de outro aviso de aproximação.
        if (atual != null && !atual.pedirConfirmacao && !avisos.isEmpty()
                && avisos.get(0).pedirConfirmacao
                && !confirmacoesJaPedidas.contains(avisos.get(0).alerta.getId())) esconder();
        if (atual != null) {
            for (Aviso aviso : disponiveis) {
                if (aviso.alerta.getId().equals(atual.alerta.getId())
                        && aviso.pedirConfirmacao == atual.pedirConfirmacao) {
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

    void reiniciarViagem() {
        idsJaMostrados.clear();
        confirmacoesJaPedidas.clear();
    }

    boolean foiAvisado(String id) { return idsJaMostrados.contains(id); }

    private void mostrarSeguinte() {
        if (!ativo || atual != null || atividade.isFinishing() || atividade.isDestroyed()) return;
        for (Aviso aviso : disponiveis) {
            String id = aviso.alerta.getId();
            List<String> mostrados = aviso.pedirConfirmacao ? confirmacoesJaPedidas : idsJaMostrados;
            if (mostrados.contains(id)) continue;
            mostrados.add(id);
            atual = aviso;
            aEnviar = false;
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
                atual.pedirConfirmacao ? atividade.getString(R.string.aviso_confirmar_perigo)
                        : atividade.getString(R.string.aviso_na_rota, distancia));
        painel.findViewById(R.id.aviso_confirmacoes).setVisibility(
                atual.pedirConfirmacao ? View.VISIBLE : View.GONE);
        painel.findViewById(R.id.aviso_ainda_existe).setEnabled(!aEnviar);
        painel.findViewById(R.id.aviso_ja_nao_existe).setEnabled(!aEnviar);
        ((TextView) painel.findViewById(R.id.aviso_data)).setText(
                atividade.getString(R.string.aviso_reportado, tempoDesde(atual.alerta.getDataCriacao())));
    }

    private void fecharAtual() { esconder(); mostrarSeguinte(); }

    private void responder(boolean aindaExiste) {
        if (atual == null || !atual.pedirConfirmacao || aEnviar) return;
        Aviso enviado = atual;
        aEnviar = true;
        preencherAviso();
        relogio.removeCallbacks(fecharAviso);
        confirmar.enviar(enviado.alerta, aindaExiste, new AlertasApi.Resposta<Boolean>() {
            @Override public void aoConcluir(Boolean guardado) {
                android.widget.Toast.makeText(atividade, guardado
                        ? R.string.aviso_confirmacao_guardada : R.string.aviso_confirmacao_indisponivel,
                        android.widget.Toast.LENGTH_SHORT).show();
                if (estaAConfirmar(enviado.alerta.getId())) fecharAtual();
            }
            @Override public void aoFalhar(AlertasApi.Erro erro) {
                android.widget.Toast.makeText(atividade, R.string.aviso_confirmacao_erro,
                        android.widget.Toast.LENGTH_LONG).show();
                if (estaAConfirmar(enviado.alerta.getId())) {
                    aEnviar = false;
                    preencherAviso();
                    relogio.postDelayed(fecharAviso, 12000);
                }
            }
        });
    }

    private boolean estaAConfirmar(String id) {
        return atual != null && atual.pedirConfirmacao && atual.alerta.getId().equals(id);
    }

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
