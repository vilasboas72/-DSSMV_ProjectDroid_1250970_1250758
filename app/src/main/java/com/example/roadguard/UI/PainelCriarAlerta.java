package com.example.roadguard.UI;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

import com.example.roadguard.Model.CategoriaAlerta;
import com.example.roadguard.R;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;

final class PainelCriarAlerta {
    interface AoGuardar {
        void guardar(CategoriaAlerta categoria, PainelCriarAlerta painel);
    }

    private final BottomSheetDialog dialogo;
    private final MaterialButton confirmar;
    private final View fechar;
    private final TextView resumo;
    private final MaterialButton[] opcoes;
    private CategoriaAlerta categoriaEscolhida;

    PainelCriarAlerta(BottomSheetDialog dialogo, View formulario, AoGuardar aoGuardar) {
        this.dialogo = dialogo;
        confirmar = formulario.findViewById(R.id.confirm_alert);
        fechar = formulario.findViewById(R.id.cancel_alert);
        resumo = formulario.findViewById(R.id.alert_selection_summary);
        opcoes = new MaterialButton[]{
                formulario.findViewById(R.id.category_works),
                formulario.findViewById(R.id.category_road_closed),
                formulario.findViewById(R.id.category_police),
                formulario.findViewById(R.id.category_accident),
                formulario.findViewById(R.id.category_pothole),
                formulario.findViewById(R.id.category_traffic)
        };

        configurarCategorias(formulario.getContext());
        fechar.setOnClickListener(view -> fechar());
        confirmar.setOnClickListener(view -> {
            // AÇÃO DO UTILIZADOR — Guardar alerta
            if (categoriaEscolhida != null) aoGuardar.guardar(categoriaEscolhida, this);
        });
    }

    private void configurarCategorias(Context contexto) {
        // Os botões seguem a mesma ordem das categorias.
        CategoriaAlerta[] categorias = CategoriaAlerta.values();
        for (int i = 0; i < opcoes.length; i++) {
            MaterialButton botao = opcoes[i];
            CategoriaAlerta categoria = categorias[i];
            String descricao = contexto.getString(R.string.alert_selection_summary,
                    contexto.getString(categoria.nome),
                    contexto.getString(CategoriaAlerta.recursoDaGravidade(categoria.gravidade)));
            botao.setCheckable(true);
            botao.setContentDescription(descricao);
            botao.setOnClickListener(view -> {
                // AÇÃO DO UTILIZADOR — Escolher categoria do alerta
                categoriaEscolhida = categoria;
                for (MaterialButton opcao : opcoes) opcao.setChecked(opcao == botao);
                resumo.setText(descricao);
                confirmar.setEnabled(true);
            });
        }
    }

    void mostrarEnvio() {
        // Bloqueia os botões enquanto o pedido está a decorrer.
        dialogo.setCancelable(false);
        dialogo.getBehavior().setDraggable(false);
        confirmar.setEnabled(false);
        confirmar.setText(R.string.alert_saving);
        fechar.setEnabled(false);
        for (MaterialButton opcao : opcoes) opcao.setEnabled(false);
    }

    void mostrarErro(String mensagem) {
        // Permite fechar a caixa e mantém o envio bloqueado para evitar duplicados.
        dialogo.setCancelable(true);
        dialogo.getBehavior().setDraggable(true);
        fechar.setEnabled(true);
        confirmar.setText(R.string.alert_save);
        resumo.setText(mensagem);
    }

    void fechar() {
        dialogo.dismiss();
    }
}
