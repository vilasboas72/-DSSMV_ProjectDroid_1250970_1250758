package com.example.roadguard.UI;

import android.app.Activity;
import android.app.AlertDialog;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.example.roadguard.API.AlertasApi;
import com.example.roadguard.DTO.AlertaDTO;
import com.example.roadguard.Model.CategoriaAlerta;
import com.example.roadguard.R;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.Locale;

/** Mostra os detalhes de um alerta e pede confirmação antes de o apagar. */
final class PainelDetalhesAlerta {
    private final Activity atividade;
    private final ViewGroup raiz;
    private final AlertasApi apiAlertas;
    private BottomSheetDialog dialogoAberto;

    PainelDetalhesAlerta(Activity atividade, ViewGroup raiz, AlertasApi apiAlertas) {
        this.atividade = atividade;
        this.raiz = raiz;
        this.apiAlertas = apiAlertas;
    }

    // Apresenta categoria, gravidade, data e autor do alerta selecionado.
    void mostrar(AlertaDTO alerta, boolean eAutor, Runnable aoEliminar) {
        CategoriaAlerta categoria = CategoriaAlerta.procurar(alerta.getCategoriaId());
        if (categoria == null) return;
        View detalhes = atividade.getLayoutInflater().inflate(
                R.layout.dialog_detalhes_alerta, raiz, false);
        BottomSheetDialog painel = new BottomSheetDialog(atividade);
        painel.setContentView(detalhes);
        dialogoAberto = painel;
        ((android.widget.ImageView) detalhes.findViewById(R.id.icone_detalhe_alerta))
                .setImageResource(categoria.icone);
        ((TextView) detalhes.findViewById(R.id.categoria_detalhe_alerta)).setText(categoria.nome);
        ((TextView) detalhes.findViewById(R.id.gravidade_detalhe_alerta))
                .setText(CategoriaAlerta.recursoDaGravidade(alerta.getGravidade()));
        java.text.SimpleDateFormat formato = new java.text.SimpleDateFormat(
                "dd/MM/yyyy 'às' HH:mm", Locale.forLanguageTag("pt-PT"));
        ((TextView) detalhes.findViewById(R.id.data_detalhe_alerta))
                .setText(formato.format(new java.util.Date(alerta.getDataCriacao())));

        TextView nomeAutor = detalhes.findViewById(R.id.nome_autor_alerta);
        View eliminar = detalhes.findViewById(R.id.eliminar_detalhe_alerta);
        // Só quem criou o alerta pode ver o botão Eliminar.
        if (eAutor) {
            eliminar.setVisibility(View.VISIBLE);
        } else {
            eliminar.setVisibility(View.GONE);
        }
        eliminar.setOnClickListener(vista -> {
            // AÇÃO DO UTILIZADOR — Eliminar alerta
            confirmarEliminacao(aoEliminar);
        });
        detalhes.findViewById(R.id.fechar_cabecalho_alerta).setOnClickListener(vista -> painel.dismiss());
        detalhes.findViewById(R.id.fechar_detalhe_alerta).setOnClickListener(vista -> painel.dismiss());
        painel.setOnDismissListener(dialogo -> {
            if (dialogoAberto == painel) dialogoAberto = null;
        });
        PainelInferior.mostrar(painel, detalhes);
        // Vai buscar o nome da pessoa que criou o alerta.
        apiAlertas.obterNomeDoAutor(alerta.getUtilizadorId(), new AlertasApi.Resposta<>() {
            @Override public void aoConcluir(String nome) {
                if (!painel.isShowing() || atividade.isFinishing() || atividade.isDestroyed()) return;
                if (nome.isEmpty()) {
                    nomeAutor.setText(R.string.alert_author_unavailable);
                } else if (eAutor) {
                    nomeAutor.setText(atividade.getString(R.string.alert_author_current_user, nome));
                } else {
                    nomeAutor.setText(nome);
                }
            }
            @Override public void aoFalhar(AlertasApi.Erro erro) {
                if (painel.isShowing() && !atividade.isFinishing() && !atividade.isDestroyed()) {
                    nomeAutor.setText(R.string.alert_author_unavailable);
                }
            }
        });
    }

    private void confirmarEliminacao(Runnable aoEliminar) {
        new AlertDialog.Builder(atividade)
                .setTitle(R.string.alert_delete_title)
                .setMessage(R.string.alert_delete_message)
                .setNegativeButton(R.string.alert_cancel, null)
                .setPositiveButton(R.string.alert_delete, (dialogo, escolha) -> {
                    // AÇÃO DO UTILIZADOR — Confirmar eliminação do alerta
                    aoEliminar.run();
                })
                .show();
    }

    void fechar() {
        if (dialogoAberto != null) dialogoAberto.dismiss();
    }
}
