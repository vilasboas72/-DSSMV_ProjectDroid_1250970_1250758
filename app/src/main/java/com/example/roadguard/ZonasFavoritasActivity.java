package com.example.roadguard;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.SparseBooleanArray;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.tomtom.sdk.location.GeoPoint;

import java.util.ArrayList;
import java.util.List;

/** Mostra e permite criar zonas favoritas do utilizador com sessão iniciada. */
public class ZonasFavoritasActivity extends AppCompatActivity {
    private static final int SEM_SELECAO = 0;
    private static final int SELECIONAR_PARA_ELIMINAR = 1;
    private static final int SELECIONAR_PARA_MOSTRAR = 2;
    private static final int[] RAIOS_DISPONIVEIS = {1, 2, 5, 10};

    private String utilizadorId;
    private GeoPoint posicaoAtual;
    private TomTomApi apiTomTom;
    private ZonasFavoritasApi apiZonas;
    private final List<ZonaFavorita> zonasGuardadas = new ArrayList<>();
    private final Handler gestorPesquisa = new Handler(Looper.getMainLooper());
    private Runnable pesquisaPendente;
    // Número do pedido para ignorar respostas de pesquisas antigas.
    private int numeroDaPesquisa;
    private TextView mensagemLista;
    private ListView listaZonas;
    private View acoesSelecao;
    private MaterialButton botaoConfirmarSelecao;
    private int modoSelecao = SEM_SELECAO;
    private TomTomApi.Local localSelecionado;
    private boolean aEscolherLocal;
    private int eliminacoesPorTerminar;
    private boolean houveErroAoEliminar;

    @Override
    protected void onCreate(@Nullable Bundle estado) {
        super.onCreate(estado);
        if (!lerUtilizadorLogado()) return;

        setContentView(R.layout.activity_zonas_favoritas);
        apiZonas = new ZonasFavoritasApi();
        posicaoAtual = obterPosicaoEnviada();
        apiTomTom = criarApiTomTom();

        configurarLista();
        configurarBotoes();
        carregarZonas();
    }

    // Lê a sessão; sem utilizador, volta ao ecrã de entrada.
    private boolean lerUtilizadorLogado() {
        utilizadorId = getSharedPreferences("RoadGuardPrefs", Context.MODE_PRIVATE)
                .getString("UID_LOGADO", "");
        if (!utilizadorId.isEmpty()) return true;

        startActivity(new Intent(this, MainActivity.class));
        finish();
        return false;
    }

    // Liga os botões às ações do ecrã.
    private void configurarBotoes() {
        findViewById(R.id.btn_voltar_zonas).setOnClickListener(view -> finish());
        findViewById(R.id.btn_criar_zona).setOnClickListener(view -> abrirCriacaoDeZona());
        findViewById(R.id.btn_selecionar_eliminar)
                .setOnClickListener(view -> iniciarSelecao(SELECIONAR_PARA_ELIMINAR));
        findViewById(R.id.btn_selecionar_mapa)
                .setOnClickListener(view -> iniciarSelecao(SELECIONAR_PARA_MOSTRAR));
        findViewById(R.id.btn_selecionar_todas).setOnClickListener(view -> selecionarTodas());
        botaoConfirmarSelecao.setOnClickListener(view -> confirmarSelecao());
    }

    // Abre uma zona ao tocar na linha ou atualiza a seleção com caixas.
    private void configurarLista() {
        mensagemLista = findViewById(R.id.zonas_mensagem);
        listaZonas = findViewById(R.id.lista_zonas);
        acoesSelecao = findViewById(R.id.acoes_selecao_zonas);
        botaoConfirmarSelecao = findViewById(R.id.btn_confirmar_selecao_zonas);
        // Ao tocar numa zona, abre o mapa no local guardado.
        listaZonas.setOnItemClickListener((lista, linha, posicao, id) -> {
            if (modoSelecao != SEM_SELECAO) return;
            if (posicao >= zonasGuardadas.size()) return;
            abrirMapaComZona(zonasGuardadas.get(posicao));
        });
    }

    private void abrirMapaComZona(ZonaFavorita zona) {
        Intent mapa = new Intent(this, MapActivity.class);
        mapa.putExtra("zona_latitude", zona.latitude);
        mapa.putExtra("zona_longitude", zona.longitude);
        mapa.putExtra("zona_raio_km", zona.raioKm);
        mapa.putExtra("zona_nome", zona.nome);
        startActivity(mapa);
        finish();
    }

    // Recebe do mapa a localização atual para priorizar sugestões próximas.
    private GeoPoint obterPosicaoEnviada() {
        Intent dados = getIntent();
        if (!dados.hasExtra("latitude_atual") || !dados.hasExtra("longitude_atual")) return null;
        double latitude = dados.getDoubleExtra("latitude_atual", Double.NaN);
        double longitude = dados.getDoubleExtra("longitude_atual", Double.NaN);
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude)
                || Math.abs(latitude) > 90 || Math.abs(longitude) > 180) return null;
        return new GeoPoint(latitude, longitude);
    }

    // Lê a chave TomTom guardada nas definições Android da aplicação.
    private TomTomApi criarApiTomTom() {
        try {
            ApplicationInfo informacoes = getPackageManager().getApplicationInfo(
                    getPackageName(), PackageManager.GET_META_DATA);
            String chave = informacoes.metaData == null ? null
                    : informacoes.metaData.getString("com.tomtom.sdk.api.key");
            return chave == null || chave.trim().isEmpty() ? null : new TomTomApi(chave);
        } catch (PackageManager.NameNotFoundException erro) {
            return null;
        }
    }

    // GET: carrega as zonas e atualiza a lista apresentada no ecrã.
    private void carregarZonas() {
        mensagemLista.setText(R.string.zones_loading);
        mensagemLista.setVisibility(View.VISIBLE);
        listaZonas.setVisibility(View.GONE);
        apiZonas.listar(utilizadorId, new ZonasFavoritasApi.Resposta<>() {
            @Override public void aoConcluir(List<ZonaFavorita> zonas) {
                zonasGuardadas.clear();
                zonasGuardadas.addAll(zonas);
                boolean vazia = zonas.isEmpty();
                if (vazia) mensagemLista.setText(R.string.zones_empty);
                else mensagemLista.setText("");
                mensagemLista.setVisibility(vazia ? View.VISIBLE : View.GONE);
                listaZonas.setVisibility(vazia ? View.GONE : View.VISIBLE);
                atualizarLista();
            }

            @Override public void aoFalhar() {
                mensagemLista.setText(R.string.zones_load_error);
                mensagemLista.setVisibility(View.VISIBLE);
                listaZonas.setVisibility(View.GONE);
            }
        });
    }

    // Ativa as caixas para eliminar zonas ou mostrá-las juntas no mapa.
    private void iniciarSelecao(int modo) {
        modoSelecao = modoSelecao == modo ? SEM_SELECAO : modo;
        listaZonas.clearChoices();
        atualizarLista();
    }

    // Troca as linhas da lista por linhas com caixas de seleção.
    private void atualizarLista() {
        boolean selecionar = modoSelecao != SEM_SELECAO && !zonasGuardadas.isEmpty();
        int tipoLinha = selecionar ? android.R.layout.simple_list_item_multiple_choice
                : android.R.layout.simple_list_item_1;
        ArrayAdapter<String> adaptador = new ArrayAdapter<>(this, tipoLinha);
        for (ZonaFavorita zona : zonasGuardadas) {
            adaptador.add(getString(R.string.zone_list_item, zona.nome, zona.raioKm, zona.local));
        }
        listaZonas.setChoiceMode(selecionar
                ? ListView.CHOICE_MODE_MULTIPLE : ListView.CHOICE_MODE_NONE);
        listaZonas.setAdapter(adaptador);
        acoesSelecao.setVisibility(selecionar ? View.VISIBLE : View.GONE);
        botaoConfirmarSelecao.setText(modoSelecao == SELECIONAR_PARA_ELIMINAR
                ? R.string.zones_delete_selected : R.string.zones_show_selected);
    }

    // Marca todas as zonas; se já estiverem marcadas, limpa a seleção.
    private void selecionarTodas() {
        boolean todasMarcadas = true;
        for (int indice = 0; indice < zonasGuardadas.size(); indice++) {
            if (!listaZonas.isItemChecked(indice)) {
                todasMarcadas = false;
                break;
            }
        }
        for (int indice = 0; indice < zonasGuardadas.size(); indice++) {
            listaZonas.setItemChecked(indice, !todasMarcadas);
        }
    }

    // Devolve os objetos correspondentes às caixas marcadas.
    private List<ZonaFavorita> zonasSelecionadas() {
        List<ZonaFavorita> selecionadas = new ArrayList<>();
        SparseBooleanArray marcadas = listaZonas.getCheckedItemPositions();
        for (int indice = 0; indice < zonasGuardadas.size(); indice++) {
            if (marcadas.get(indice)) selecionadas.add(zonasGuardadas.get(indice));
        }
        return selecionadas;
    }

    // Executa a ação escolhida depois de verificar que há zonas marcadas.
    private void confirmarSelecao() {
        List<ZonaFavorita> selecionadas = zonasSelecionadas();
        if (selecionadas.isEmpty()) {
            Toast.makeText(this, R.string.zones_select_first, Toast.LENGTH_SHORT).show();
            return;
        }
        if (modoSelecao == SELECIONAR_PARA_MOSTRAR) {
            abrirMapaComZonas(selecionadas);
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.zones_delete_confirm_title)
                .setMessage(R.string.zones_delete_confirm_message)
                .setNegativeButton(R.string.zone_cancel, null)
                .setPositiveButton(R.string.zones_delete, (dialogo, escolha) -> eliminarZonas(selecionadas))
                .show();
    }

    // Envia as coordenadas e os raios ao mapa para desenhar vários círculos.
    private void abrirMapaComZonas(List<ZonaFavorita> zonas) {
        double[] latitudes = new double[zonas.size()];
        double[] longitudes = new double[zonas.size()];
        int[] raios = new int[zonas.size()];
        String[] nomes = new String[zonas.size()];
        for (int indice = 0; indice < zonas.size(); indice++) {
            ZonaFavorita zona = zonas.get(indice);
            latitudes[indice] = zona.latitude;
            longitudes[indice] = zona.longitude;
            raios[indice] = zona.raioKm;
            nomes[indice] = zona.nome;
        }
        Intent mapa = new Intent(this, MapActivity.class);
        mapa.putExtra("zonas_latitudes", latitudes);
        mapa.putExtra("zonas_longitudes", longitudes);
        mapa.putExtra("zonas_raios", raios);
        mapa.putExtra("zonas_nomes", nomes);
        startActivity(mapa);
        finish();
    }

    // DELETE: apaga as zonas escolhidas e atualiza a lista no fim.
    private void eliminarZonas(List<ZonaFavorita> zonas) {
        if (eliminacoesPorTerminar > 0) return;
        botaoConfirmarSelecao.setEnabled(false);
        eliminacoesPorTerminar = zonas.size();
        houveErroAoEliminar = false;
        for (ZonaFavorita zona : zonas) {
            apiZonas.eliminar(utilizadorId, zona.id, new ZonasFavoritasApi.Resposta<>() {
                @Override public void aoConcluir(Boolean resultado) { terminarUmaEliminacao(); }
                @Override public void aoFalhar() {
                    houveErroAoEliminar = true;
                    terminarUmaEliminacao();
                }
            });
        }
    }

    private void terminarUmaEliminacao() {
        eliminacoesPorTerminar--;
        // Só atualiza a lista quando todos os pedidos DELETE terminarem.
        if (eliminacoesPorTerminar > 0 || isFinishing() || isDestroyed()) return;
        modoSelecao = SEM_SELECAO;
        int mensagem = R.string.zones_delete_done;
        if (houveErroAoEliminar) mensagem = R.string.zones_delete_error;
        Toast.makeText(this, mensagem, Toast.LENGTH_SHORT).show();
        carregarZonas();
    }

    // Abre o formulário de pesquisa, nome e raio para criar uma zona.
    private void abrirCriacaoDeZona() {
        if (apiTomTom == null) {
            Toast.makeText(this, R.string.tomtom_key_missing, Toast.LENGTH_LONG).show();
            return;
        }
        View conteudo = getLayoutInflater().inflate(
                R.layout.dialog_criar_zona, new FrameLayout(this), false);
        BottomSheetDialog dialogo = new BottomSheetDialog(this);
        dialogo.setContentView(conteudo);

        EditText pesquisa = conteudo.findViewById(R.id.zone_search_input);
        EditText nome = conteudo.findViewById(R.id.zone_name_input);
        ListView sugestoes = conteudo.findViewById(R.id.zone_search_suggestions);
        Spinner raio = conteudo.findViewById(R.id.zone_radius_spinner);
        MaterialButton guardar = conteudo.findViewById(R.id.btn_guardar_zona);
        MaterialButton cancelar = conteudo.findViewById(R.id.btn_cancelar_zona);
        ArrayAdapter<String> adaptadorSugestoes = new ArrayAdapter<>(
                this, android.R.layout.simple_list_item_1);
        List<TomTomApi.Local> locais = new ArrayList<>();
        sugestoes.setAdapter(adaptadorSugestoes);
        configurarOpcoesDeRaio(raio);
        guardar.setEnabled(false);
        localSelecionado = null;
        aEscolherLocal = false;

        configurarPesquisaDeLocal(pesquisa, sugestoes, guardar, dialogo,
                locais, adaptadorSugestoes);
        configurarAcoesDoFormulario(pesquisa, nome, raio, sugestoes, locais,
                guardar, cancelar, dialogo);
        dialogo.show();
    }

    // Preenche a lista de raios disponíveis no formulário.
    private void configurarOpcoesDeRaio(Spinner raio) {
        ArrayAdapter<CharSequence> adaptador = ArrayAdapter.createFromResource(
                this, R.array.zone_radii, android.R.layout.simple_spinner_item);
        adaptador.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        raio.setAdapter(adaptador);
    }

    // Pesquisa locais quando a pessoa termina de escrever.
    private void configurarPesquisaDeLocal(EditText pesquisa, ListView sugestoes,
                                            MaterialButton guardar,
                                            BottomSheetDialog dialogo, List<TomTomApi.Local> locais,
                                            ArrayAdapter<String> adaptador) {
        pesquisa.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence texto, int inicio, int antes, int depois) { }
            @Override public void onTextChanged(CharSequence texto, int inicio, int antes, int depois) { }

            @Override public void afterTextChanged(Editable texto) {
                if (aEscolherLocal) return;
                localSelecionado = null;
                guardar.setEnabled(false);
                if (pesquisaPendente != null) gestorPesquisa.removeCallbacks(pesquisaPendente);

                int pedidoAtual = ++numeroDaPesquisa;
                String consulta = texto.toString().trim();
                if (consulta.length() < 2) {
                    sugestoes.setVisibility(View.GONE);
                    adaptador.clear();
                    return;
                }

                // Espera 400 ms para evitar um pedido por cada letra escrita.
                pesquisaPendente = () -> pesquisarLocais(
                        consulta, pedidoAtual, sugestoes, dialogo, locais, adaptador);
                gestorPesquisa.postDelayed(pesquisaPendente, 400);
            }
        });
    }

    // GET à TomTom e apresentação das sugestões válidas.
    private void pesquisarLocais(String consulta, int pedido, ListView sugestoes,
                                 BottomSheetDialog dialogo, List<TomTomApi.Local> locais,
                                 ArrayAdapter<String> adaptador) {
        apiTomTom.pesquisarLocais(consulta, posicaoAtual, new TomTomApi.Resposta<>() {
            @Override public void aoConcluir(List<TomTomApi.Local> resultados) {
                if (pedido != numeroDaPesquisa || !dialogo.isShowing()) return;
                locais.clear();
                locais.addAll(resultados);
                adaptador.clear();
                for (TomTomApi.Local local : resultados) {
                    String linha = local.nome;
                    if (!local.endereco.isEmpty() && !local.endereco.equals(local.nome)) {
                        linha += " · " + local.endereco;
                    }
                    adaptador.add(linha);
                }
                adaptador.notifyDataSetChanged();
                sugestoes.setVisibility(resultados.isEmpty() ? View.GONE : View.VISIBLE);
                if (resultados.isEmpty()) {
                    Toast.makeText(ZonasFavoritasActivity.this,
                            R.string.search_not_found, Toast.LENGTH_SHORT).show();
                }
            }

            @Override public void aoFalhar() {
                if (pedido != numeroDaPesquisa || !dialogo.isShowing()) return;
                sugestoes.setVisibility(View.GONE);
                Toast.makeText(ZonasFavoritasActivity.this,
                        R.string.search_error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    // Liga as sugestões, o botão guardar e o botão cancelar.
    private void configurarAcoesDoFormulario(EditText pesquisa, EditText nome, Spinner raio, ListView sugestoes,
                                              List<TomTomApi.Local> locais, MaterialButton guardar,
                                              MaterialButton cancelar, BottomSheetDialog dialogo) {
        sugestoes.setOnItemClickListener((lista, linha, posicao, id) -> {
            if (posicao >= locais.size()) return;
            localSelecionado = locais.get(posicao);
            aEscolherLocal = true;
            pesquisa.setText(localSelecionado.nome);
            nome.setText(localSelecionado.nome);
            aEscolherLocal = false;
            sugestoes.setVisibility(View.GONE);
            guardar.setEnabled(true);
        });
        guardar.setOnClickListener(view -> guardarZona(nome, raio, guardar, dialogo));
        cancelar.setOnClickListener(view -> dialogo.dismiss());
        dialogo.setOnDismissListener(ignorado -> cancelarPesquisaPendente());
    }

    // POST à Firebase com a localização e o raio escolhidos.
    private void guardarZona(EditText nome, Spinner raio, MaterialButton guardar,
                             BottomSheetDialog dialogo) {
        TomTomApi.Local local = localSelecionado;
        String nomeZona = nome.getText().toString().trim();
        if (local == null || nomeZona.isEmpty()) {
            Toast.makeText(this, R.string.zone_choose_location, Toast.LENGTH_SHORT).show();
            return;
        }

        int indiceRaio = Math.max(0, Math.min(
                RAIOS_DISPONIVEIS.length - 1, raio.getSelectedItemPosition()));
        int raioKm = RAIOS_DISPONIVEIS[indiceRaio];
        String morada = local.endereco == null || local.endereco.isEmpty()
                ? local.nome : local.endereco;
        ZonaFavorita zona = new ZonaFavorita("", utilizadorId, nomeZona, morada,
                local.latitude, local.longitude, raioKm);
        guardar.setEnabled(false);

        apiZonas.criar(zona, new ZonasFavoritasApi.Resposta<>() {
            @Override public void aoConcluir(ZonaFavorita guardada) {
                if (isFinishing() || isDestroyed()) return;
                dialogo.dismiss();
                Toast.makeText(ZonasFavoritasActivity.this,
                        R.string.zone_saved, Toast.LENGTH_SHORT).show();
                carregarZonas();
            }

            @Override public void aoFalhar() {
                if (isFinishing() || isDestroyed()) return;
                guardar.setEnabled(true);
                Toast.makeText(ZonasFavoritasActivity.this,
                        R.string.zone_save_error, Toast.LENGTH_LONG).show();
            }
        });
    }

    // Cancela pesquisas já agendadas e ignora respostas antigas.
    private void cancelarPesquisaPendente() {
        if (pesquisaPendente != null) gestorPesquisa.removeCallbacks(pesquisaPendente);
        numeroDaPesquisa++;
    }

    // Liberta pesquisa e ligações quando se sai deste ecrã.
    @Override
    protected void onDestroy() {
        if (pesquisaPendente != null) gestorPesquisa.removeCallbacks(pesquisaPendente);
        gestorPesquisa.removeCallbacksAndMessages(null);
        if (apiZonas != null) apiZonas.fechar();
        if (apiTomTom != null) apiTomTom.fechar();
        super.onDestroy();
    }
}
