package com.example.roadguard.UI;

import android.graphics.Color;
import android.view.View;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

final class PainelInferior {
    private PainelInferior() {}

    /** Abre as caixas de reporte e de detalhes com o mesmo aspeto. */
    static void mostrar(BottomSheetDialog painel, View conteudo) {
        painel.setOnShowListener(ignorado -> {
            painel.getBehavior().setSkipCollapsed(true);
            painel.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
            // Deixa transparente o fundo por trás da nossa caixa.
            if (conteudo.getParent() instanceof View) {
                ((View) conteudo.getParent()).setBackgroundColor(Color.TRANSPARENT);
            }
        });
        painel.show();
    }
}
