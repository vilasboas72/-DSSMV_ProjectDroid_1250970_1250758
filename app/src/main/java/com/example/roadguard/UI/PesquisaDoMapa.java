package com.example.roadguard.UI;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.example.roadguard.API.TomTomApi;
import com.example.roadguard.R;
import com.example.roadguard.Service.GestorHistorico;
import com.example.roadguard.databinding.ActivityMapBinding;
import com.tomtom.sdk.location.GeoPoint;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Trata da barra de pesquisa, do histórico e da seleção de um destino. */
final class PesquisaDoMapa {
    private final Activity atividade;
    private final ActivityMapBinding interfaceMapa;
    private final TomTomApi apiTomTom;
    private final GestorHistorico gestorHistorico;
    private final Supplier<GeoPoint> obterPosicao;
    private final Consumer<TomTomApi.Local> aoEscolherDestino;
    private final Runnable aoLimparRota;
    private final Handler gestorPesquisa = new Handler(Looper.getMainLooper());
    private final List<TomTomApi.Local> historicoPesquisas = new ArrayList<>();
    private final List<TomTomApi.Local> resultadosDaPesquisa = new ArrayList<>();
    private ArrayAdapter<String> adaptadorDaPesquisa;
    private ArrayAdapter<TomTomApi.Local> adaptadorHistorico;
    private View cabecalhoHistorico;
    private boolean historicoVisivel;
    private boolean aEscolherDestino;
    private Runnable pesquisaPendente;
    private int numeroDaPesquisa;

    PesquisaDoMapa(Activity atividade, ActivityMapBinding interfaceMapa, TomTomApi apiTomTom,
                   GestorHistorico gestorHistorico, Supplier<GeoPoint> obterPosicao,
                   Consumer<TomTomApi.Local> aoEscolherDestino, Runnable aoLimparRota) {
        this.atividade = atividade;
        this.interfaceMapa = interfaceMapa;
        this.apiTomTom = apiTomTom;
        this.gestorHistorico = gestorHistorico;
        this.obterPosicao = obterPosicao;
        this.aoEscolherDestino = aoEscolherDestino;
        this.aoLimparRota = aoLimparRota;
        configurar();
    }

    private void configurar() {
        adaptadorDaPesquisa = new ArrayAdapter<>(atividade, android.R.layout.simple_list_item_1);
        adaptadorHistorico = new AdaptadorPesquisasRecentes();
        configurarCabecalhoHistorico();
        interfaceMapa.searchSuggestions.setAdapter(adaptadorDaPesquisa);
        interfaceMapa.mapSearchBar.setOnClickListener(view -> {
            // AÇÃO DO UTILIZADOR — Tocar na barra de pesquisa
            interfaceMapa.mapSearchInput.requestFocus();
            if (interfaceMapa.mapSearchInput.getText().length() == 0) {
                mostrarHistoricoPesquisas();
            }
        });
        interfaceMapa.mapSearchInput.setOnFocusChangeListener((view, focado) -> {
            if (focado && interfaceMapa.mapSearchInput.getText().length() == 0) {
                mostrarHistoricoPesquisas();
            }
        });
        interfaceMapa.mapSearchInput.setOnClickListener(view -> {
            if (interfaceMapa.mapSearchInput.getText().length() == 0) {
                mostrarHistoricoPesquisas();
            }
        });

        interfaceMapa.mapSearchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence texto, int inicio, int quantidade, int depois) { }
            @Override public void onTextChanged(CharSequence texto, int inicio, int antes, int quantidade) {
                if (texto.length() > 0) {
                    interfaceMapa.btnClearSearch.setVisibility(View.VISIBLE);
                } else {
                    interfaceMapa.btnClearSearch.setVisibility(View.GONE);
                }
            }
            @Override public void afterTextChanged(Editable texto) {
                // Se foi a aplicação a preencher o destino, não pesquisa outra vez.
                if (aEscolherDestino) return;
                // Cancela a pesquisa que estava à espera e dá um número à nova pesquisa.
                cancelarPesquisaPendente();
                int pesquisaAtual = numeroDaPesquisa;
                String consulta = texto.toString().trim();
                if (consulta.length() < 2) {
                    // Se o campo estiver vazio, mostra o histórico. Só pesquisa a partir de duas letras.
                    if (consulta.isEmpty() && interfaceMapa.mapSearchInput.hasFocus()) {
                        mostrarHistoricoPesquisas();
                    } else {
                        esconderHistorico();
                    }
                    return;
                }
                esconderHistorico();
                adaptadorDaPesquisa.clear();
                interfaceMapa.searchSuggestions.setAdapter(adaptadorDaPesquisa);
                pesquisaPendente = () -> pesquisarLocais(consulta, pesquisaAtual);
                // Espera 450 ms depois da escrita antes de pesquisar.
                gestorPesquisa.postDelayed(pesquisaPendente, 450);
            }
        });

        interfaceMapa.btnClearSearch.setOnClickListener(view -> {
            // AÇÃO DO UTILIZADOR — Limpar pesquisa
            limparResultados();
            aoLimparRota.run();
            interfaceMapa.mapSearchInput.setText("");
        });

        interfaceMapa.searchSuggestions.setOnItemClickListener((lista, linha, posicao, id) -> {
            // AÇÃO DO UTILIZADOR — Escolher destino
            int indice = posicao - interfaceMapa.searchSuggestions.getHeaderViewsCount();
            List<TomTomApi.Local> locais = resultadosDaPesquisa;
            if (historicoVisivel) locais = historicoPesquisas;
            if (indice < 0 || indice >= locais.size()) return;

            TomTomApi.Local destino = locais.get(indice);
            gestorHistorico.guardar(destino);
            preencherCampo(destino.nome);
            esconderHistorico();
            esconderTeclado();
            aoEscolherDestino.accept(destino);
        });

        // Evita mostrar o histórico automaticamente ao abrir o mapa.
        interfaceMapa.mapSearchInput.clearFocus();
        esconderHistorico();
    }

    // Cria o título da lista de pesquisas recentes.
    private void configurarCabecalhoHistorico() {
        LinearLayout cabecalho = new LinearLayout(atividade);
        cabecalho.setGravity(Gravity.CENTER_VERTICAL);
        cabecalho.setPadding(converterDpParaPixeis(16), 0,
                converterDpParaPixeis(8), 0);

        TextView titulo = new TextView(atividade);
        titulo.setText(R.string.search_history_title);
        titulo.setTextColor(Color.rgb(95, 99, 104));
        titulo.setTextSize(14);
        cabecalho.addView(titulo, new LinearLayout.LayoutParams(0,
                converterDpParaPixeis(20), 1));

        cabecalhoHistorico = cabecalho;
        cabecalhoHistorico.setVisibility(View.GONE);
        interfaceMapa.searchSuggestions.addHeaderView(cabecalhoHistorico, null, false);
    }

    // Mostra cada pesquisa recente com nome, morada e botão para remover.
    private final class AdaptadorPesquisasRecentes extends ArrayAdapter<TomTomApi.Local> {
        AdaptadorPesquisasRecentes() {
            super(atividade, R.layout.item_pesquisa_recente);
        }

        @NonNull
        @Override
        public View getView(int posicao, View linha, @NonNull android.view.ViewGroup pai) {
            // Se ainda não existir uma linha para mostrar este local, cria-a.
            if (linha == null) {
                linha = atividade.getLayoutInflater().inflate(R.layout.item_pesquisa_recente, pai, false);
            }
            TomTomApi.Local local = getItem(posicao);
            if (local == null) return linha;

            TextView nome = linha.findViewById(R.id.recent_search_name);
            TextView morada = linha.findViewById(R.id.recent_search_address);
            TextView remover = linha.findViewById(R.id.btn_remove_recent_search);
            nome.setText(local.nome);
            morada.setText(local.endereco);
            if (local.endereco.isEmpty()) {
                morada.setVisibility(View.GONE);
            } else {
                morada.setVisibility(View.VISIBLE);
            }
            remover.setContentDescription(atividade.getString(R.string.search_remove_recent, local.nome));
            remover.setOnClickListener(view -> {
                // AÇÃO DO UTILIZADOR — Remover pesquisa recente
                removerPesquisaRecente(local);
            });
            return linha;
        }
    }

    // Mostra até três pesquisas recentes da conta atual.
    private void mostrarHistoricoPesquisas() {
        historicoPesquisas.clear();
        historicoPesquisas.addAll(gestorHistorico.ler());
        atualizarAdaptador(adaptadorHistorico, historicoPesquisas);
        if (historicoPesquisas.isEmpty()) {
            esconderHistorico();
            return;
        }
        historicoVisivel = true;
        cabecalhoHistorico.setVisibility(View.VISIBLE);
        interfaceMapa.searchSuggestions.setAdapter(adaptadorHistorico);
        ajustarAlturaListaPesquisas(historicoPesquisas.size(), true);
        interfaceMapa.searchSuggestions.setVisibility(View.VISIBLE);
    }

    // Atualiza a lista com os novos itens.
    private <T> void atualizarAdaptador(ArrayAdapter<T> adaptador, List<T> itens) {
        adaptador.clear();
        for (T item : itens) adaptador.add(item);
        adaptador.notifyDataSetChanged();
    }

    // Ajusta a altura da lista ao número de resultados.
    private void ajustarAlturaListaPesquisas(int quantidade, boolean mostrarCabecalho) {
        int altura;
        if (mostrarCabecalho) {
            altura = 20 + quantidade * 64;
        } else {
            altura = quantidade * 56;
        }
        android.view.ViewGroup.LayoutParams parametros = interfaceMapa.searchSuggestions.getLayoutParams();
        parametros.height = converterDpParaPixeis(Math.min(altura, 240));
        interfaceMapa.searchSuggestions.setLayoutParams(parametros);
    }

    // Fecha o histórico de pesquisas.
    void esconderHistorico() {
        historicoVisivel = false;
        cabecalhoHistorico.setVisibility(View.GONE);
        interfaceMapa.searchSuggestions.setVisibility(View.GONE);
    }

    // Apaga uma pesquisa guardada no telemóvel e atualiza a lista.
    private void removerPesquisaRecente(TomTomApi.Local removido) {
        List<TomTomApi.Local> historico = gestorHistorico.remover(removido);
        historicoPesquisas.clear();
        historicoPesquisas.addAll(historico);
        atualizarAdaptador(adaptadorHistorico, historico);
        ajustarAlturaListaPesquisas(historico.size(), true);
        if (historico.isEmpty()) esconderHistorico();
    }

    // Pede à TomTom locais com o nome escrito pelo utilizador.
    private void pesquisarLocais(String consulta, int pesquisaAtual) {
        // A pesquisa também funciona sem saber a posição GPS.
        apiTomTom.pesquisarLocais(consulta, obterPosicao.get(), new TomTomApi.Resposta<>() {
            @Override
            public void aoConcluir(List<TomTomApi.Local> locais) {
                // Se já existe uma pesquisa mais recente, ignora esta resposta.
                if (pesquisaAtual != numeroDaPesquisa) return;
                resultadosDaPesquisa.clear();
                resultadosDaPesquisa.addAll(locais);
                // Prepara o nome e a morada de cada resultado para mostrar na lista.
                List<String> opcoes = new ArrayList<>();
                for (TomTomApi.Local local : locais) {
                    String texto = local.nome;
                    if (!local.endereco.isEmpty() && !local.endereco.equals(local.nome)) texto += " · " + local.endereco;
                    opcoes.add(texto);
                }
                atualizarAdaptador(adaptadorDaPesquisa, opcoes);
                ajustarAlturaListaPesquisas(locais.size(), false);
                if (locais.isEmpty()) {
                    interfaceMapa.searchSuggestions.setVisibility(View.GONE);
                } else {
                    interfaceMapa.searchSuggestions.setVisibility(View.VISIBLE);
                }
                if (locais.isEmpty()) Toast.makeText(atividade, R.string.search_not_found, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void aoFalhar() {
                if (pesquisaAtual == numeroDaPesquisa) {
                    interfaceMapa.searchSuggestions.setVisibility(View.GONE);
                    Toast.makeText(atividade, R.string.search_error, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    boolean historicoVisivel() {
        return historicoVisivel;
    }

    void fecharHistorico() {
        esconderHistorico();
        esconderTeclado();
        interfaceMapa.mapSearchInput.clearFocus();
    }

    void reporCampo() {
        limparResultados();
        preencherCampo("");
        esconderHistorico();
    }

    private void preencherCampo(String texto) {
        aEscolherDestino = true;
        interfaceMapa.mapSearchInput.setText(texto);
        aEscolherDestino = false;
    }

    private void limparResultados() {
        cancelarPesquisaPendente();
        resultadosDaPesquisa.clear();
        adaptadorDaPesquisa.clear();
        interfaceMapa.searchSuggestions.setVisibility(View.GONE);
    }

    void cancelarPesquisaPendente() {
        if (pesquisaPendente != null) gestorPesquisa.removeCallbacks(pesquisaPendente);
        pesquisaPendente = null;
        // Uma resposta de uma pesquisa anterior também deixa de ser válida.
        numeroDaPesquisa++;
    }

    private int converterDpParaPixeis(int valor) {
        return Math.round(valor * atividade.getResources().getDisplayMetrics().density);
    }

    // Esconde o teclado da pesquisa.
    void esconderTeclado() {
        InputMethodManager teclado = (InputMethodManager) atividade.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (teclado != null) teclado.hideSoftInputFromWindow(interfaceMapa.mapSearchInput.getWindowToken(), 0);
    }
}
