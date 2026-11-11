package com.example.roadguard.UI;

import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.roadguard.R;
import com.example.roadguard.databinding.ActivityMapBinding;

/** Ajusta as margens do ecrã e mantém os botões fora dos painéis. */
final class LayoutDoMapa {
    private final ActivityMapBinding interfaceMapa;
    private int margemSuperiorDoSistema;
    private int margemFinalDoSistema;
    private int margemInferiorDoSistema;
    private int margemEsquerdaDoSistema;
    private boolean tecladoVisivel;

    LayoutDoMapa(ActivityMapBinding interfaceMapa) {
        this.interfaceMapa = interfaceMapa;
    }

    void configurar(Runnable aoFecharTeclado) {
        int margin = Math.round(16 * interfaceMapa.getRoot().getResources().getDisplayMetrics().density);
        ViewCompat.setOnApplyWindowInsetsListener(interfaceMapa.getRoot(), (view, windowInsets) -> {
            // Quando o teclado fecha, esconde também o histórico.
            boolean tecladoVisivelAgora = windowInsets.isVisible(WindowInsetsCompat.Type.ime());
            if (tecladoVisivel && !tecladoVisivelAgora) aoFecharTeclado.run();
            tecladoVisivel = tecladoVisivelAgora;
            // Vê quanto espaço ocupam as barras do Android e a câmara do telemóvel.
            Insets insets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            margemSuperiorDoSistema = insets.top;
            margemInferiorDoSistema = insets.bottom;
            margemEsquerdaDoSistema = insets.left;
            // Escolhe a margem de acordo com o lado de escrita do idioma.
            if (ViewCompat.getLayoutDirection(view) == ViewCompat.LAYOUT_DIRECTION_RTL) {
                margemFinalDoSistema = insets.left;
            } else {
                margemFinalDoSistema = insets.right;
            }
            FrameLayout.LayoutParams params =
                    (FrameLayout.LayoutParams) interfaceMapa.btnMapMenu.getLayoutParams();
            params.topMargin = margin + insets.top;
            boolean isRtl = ViewCompat.getLayoutDirection(view) == ViewCompat.LAYOUT_DIRECTION_RTL;
            int margemInicial;
            if (isRtl) {
                margemInicial = margin + insets.right;
            } else {
                margemInicial = margin + insets.left;
            }
            int margemFinalDaPesquisa = margin + converterDpParaPixeis(60) + margemFinalDoSistema;
            if (isRtl) {
                params.setMarginEnd(margin + insets.left);
            } else {
                params.setMarginEnd(margin + insets.right);
            }
            interfaceMapa.btnMapMenu.setLayoutParams(params);

            // Reserva espaço à direita da pesquisa para o botão do menu.
            FrameLayout.LayoutParams searchParams =
                    (FrameLayout.LayoutParams) interfaceMapa.getRoot().findViewById(R.id.map_search_bar).getLayoutParams();
            searchParams.topMargin = margin + insets.top;
            searchParams.setMarginStart(margemInicial);
            searchParams.setMarginEnd(margemFinalDaPesquisa);
            interfaceMapa.getRoot().findViewById(R.id.map_search_bar).setLayoutParams(searchParams);
            FrameLayout.LayoutParams sugestoesParams = (FrameLayout.LayoutParams) interfaceMapa.searchSuggestions.getLayoutParams();
            sugestoesParams.topMargin = margin + insets.top + converterDpParaPixeis(64);
            sugestoesParams.setMarginStart(margemInicial);
            sugestoesParams.setMarginEnd(margemFinalDaPesquisa);
            interfaceMapa.searchSuggestions.setLayoutParams(sugestoesParams);
            FrameLayout.LayoutParams navigationParams = (FrameLayout.LayoutParams)
                    interfaceMapa.navigationBanner.getLayoutParams();
            navigationParams.topMargin = margin + insets.top;
            navigationParams.setMarginStart(margemInicial);
            navigationParams.setMarginEnd(margemFinalDaPesquisa);
            interfaceMapa.navigationBanner.setLayoutParams(navigationParams);
            FrameLayout.LayoutParams painelParams = (FrameLayout.LayoutParams) interfaceMapa.routePanel.getLayoutParams();
            painelParams.bottomMargin = margin + insets.bottom;
            interfaceMapa.routePanel.setLayoutParams(painelParams);
            atualizarPosicaoDoBotaoAlerta();
            atualizarPosicaoDoBotaoCentralizar();
            atualizarPosicaoDaBussola();
            atualizarPosicaoDoAviso();
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(interfaceMapa.getRoot());
        interfaceMapa.getRoot().findViewById(R.id.painel_aviso)
                .addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> atualizarPaineis());
        interfaceMapa.routePanel
                .addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> atualizarPaineis());
    }

    private void atualizarPaineis() {
        atualizarPosicaoDoAviso();
        atualizarPosicaoDoBotaoAlerta();
        atualizarPosicaoDoBotaoCentralizar();
    }

    // Coloca a bússola logo abaixo do botão de menu.
    void atualizarPosicaoDaBussola() {
        FrameLayout.LayoutParams parametros =
                (FrameLayout.LayoutParams) interfaceMapa.btnCompass.getLayoutParams();
        parametros.topMargin = margemSuperiorDoSistema + converterDpParaPixeis(72);
        parametros.setMarginEnd(margemFinalDoSistema + converterDpParaPixeis(16));
        interfaceMapa.btnCompass.setLayoutParams(parametros);
    }

    // Mantém o botão à esquerda, alinhado ao centro com Reportar perigo.
    void atualizarPosicaoDoBotaoCentralizar() {
        FrameLayout.LayoutParams parametros =
                (FrameLayout.LayoutParams) interfaceMapa.btnCenterMap.getLayoutParams();
        FrameLayout.LayoutParams parametrosAlerta =
                (FrameLayout.LayoutParams) interfaceMapa.btnReportAlert.getLayoutParams();
        parametros.gravity = Gravity.BOTTOM | Gravity.START;
        parametros.leftMargin = margemEsquerdaDoSistema + converterDpParaPixeis(16);
        parametros.rightMargin = 0;
        int alturaAlerta = Math.max(interfaceMapa.btnReportAlert.getHeight(), converterDpParaPixeis(64));
        int alturaBotao = Math.max(interfaceMapa.btnCenterMap.getHeight(), converterDpParaPixeis(48));
        parametros.bottomMargin = parametrosAlerta.bottomMargin + (alturaAlerta - alturaBotao) / 2;
        interfaceMapa.btnCenterMap.setLayoutParams(parametros);
    }

    // Passa uma medida do layout para píxeis, de acordo com o ecrã do telemóvel.
    private int converterDpParaPixeis(int valor) {
        return Math.round(valor * interfaceMapa.getRoot().getResources().getDisplayMetrics().density);
    }

    // Coloca o aviso acima da caixa com o destino e o tempo.
    void atualizarPosicaoDoAviso() {
        View painel = interfaceMapa.getRoot().findViewById(R.id.painel_aviso);
        if (painel == null) return;
        int alturaRota;
        if (interfaceMapa.routePanel.getVisibility() == View.VISIBLE) {
            alturaRota = interfaceMapa.routePanel.getHeight();
        } else {
            alturaRota = 0;
        }
        FrameLayout.LayoutParams parametros = (FrameLayout.LayoutParams) painel.getLayoutParams();
        int margem = margemInferiorDoSistema + converterDpParaPixeis(24) + alturaRota;
        if (parametros.bottomMargin != margem) {
            parametros.bottomMargin = margem;
            painel.setLayoutParams(parametros);
        }
    }

    // Coloca o botão de reporte acima da caixa da rota e da barra do Android.
    void atualizarPosicaoDoBotaoAlerta() {
        int margem = converterDpParaPixeis(16);
        FrameLayout.LayoutParams params =
                (FrameLayout.LayoutParams) interfaceMapa.btnReportAlert.getLayoutParams();
        int alturaPainel;
        if (interfaceMapa.routePanel.getVisibility() == View.VISIBLE) {
            alturaPainel = interfaceMapa.routePanel.getHeight() + margem;
        } else {
            alturaPainel = 0;
        }
        View aviso = interfaceMapa.getRoot().findViewById(R.id.painel_aviso);
        int alturaAviso;
        if (aviso != null && aviso.getVisibility() == View.VISIBLE) {
            alturaAviso = aviso.getHeight() + converterDpParaPixeis(16);
        } else {
            alturaAviso = 0;
        }
        int margemInferior = margemInferiorDoSistema + margem + alturaPainel + alturaAviso;
        int margemFinal = margemFinalDoSistema + margem;
        if (params.bottomMargin != margemInferior || params.getMarginEnd() != margemFinal) {
            params.bottomMargin = margemInferior;
            params.setMarginEnd(margemFinal);
            interfaceMapa.btnReportAlert.setLayoutParams(params);
        }
    }
}
