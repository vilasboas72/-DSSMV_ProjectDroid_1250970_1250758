package com.example.roadguard;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
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
import android.util.SparseBooleanArray;

/** Mostra e permite criar zonas favoritas do utilizador com sessão iniciada. */
public class ZonasFavoritasActivity extends AppCompatActivity {
    private String utilizadorId;
    private GeoPoint posicaoAtual;
    private TomTomApi apiTomTom;
    private ZonasFavoritasApi apiZonas;
    private ArrayAdapter<String> adaptadorZonas;
    private final List<ZonaFavorita> zonasGuardadas = new ArrayList<>();
    private final Handler gestorPesquisa = new Handler(Looper.getMainLooper());
    private Runnable pesquisaPendente;
    private int numeroDaPesquisa;
    private TextView mensagemLista;
    private ListView listaZonas;
    private View acoesSelecao;
    private MaterialButton botaoConfirmarSelecao;
    private int modoSelecao;

    @Override
    protected void onCreate(@Nullable Bundle estado) {
        super.onCreate(estado);
        utilizadorId = getSharedPreferences("RoadGuardPrefs", Context.MODE_PRIVATE)
                .getString("UID_LOGADO", "");
        if (utilizadorId.isEmpty()) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_zonas_favoritas);
        apiZonas = new ZonasFavoritasApi();
        posicaoAtual = obterPosicaoEnviada();
        apiTomTom = criarApiTomTom();

        findViewById(R.id.btn_voltar_zonas).setOnClickListener(view -> finish());
        findViewById(R.id.btn_criar_zona).setOnClickListener(view -> abrirCriacaoDeZona());
        mensagemLista = findViewById(R.id.zonas_mensagem);
        listaZonas = findViewById(R.id.lista_zonas);
        adaptadorZonas = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
        listaZonas.setAdapter(adaptadorZonas);
        acoesSelecao = findViewById(R.id.acoes_selecao_zonas);
        botaoConfirmarSelecao = findViewById(R.id.btn_confirmar_selecao_zonas);
        findViewById(R.id.btn_selecionar_eliminar).setOnClickListener(view -> iniciarSelecao(1));
        findViewById(R.id.btn_selecionar_mapa).setOnClickListener(view -> iniciarSelecao(2));
        findViewById(R.id.btn_selecionar_todas).setOnClickListener(view -> selecionarTodas());
        botaoConfirmarSelecao.setOnClickListener(view -> confirmarSelecao());
        // Ao tocar numa zona, abre o mapa no local guardado.
        listaZonas.setOnItemClickListener((lista, linha, posicao, id) -> {
            if (modoSelecao != 0) return;
            if (posicao >= zonasGuardadas.size()) return;
            ZonaFavorita zona = zonasGuardadas.get(posicao);
            Intent mapa = new Intent(this, MapActivity.class);
            mapa.putExtra("zona_latitude", zona.latitude);
            mapa.putExtra("zona_longitude", zona.longitude);
            mapa.putExtra("zona_raio_km", zona.raioKm);
            mapa.putExtra("zona_nome", zona.nome);
            startActivity(mapa);
            finish();
        });
        carregarZonas();
    }

    private GeoPoint obterPosicaoEnviada() {
        Intent dados = getIntent();
        if (!dados.hasExtra("latitude_atual") || !dados.hasExtra("longitude_atual")) return null;
        double latitude = dados.getDoubleExtra("latitude_atual", Double.NaN);
        double longitude = dados.getDoubleExtra("longitude_atual", Double.NaN);
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude)
                || Math.abs(latitude) > 90 || Math.abs(longitude) > 180) return null;
        return new GeoPoint(latitude, longitude);
    }

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

    private void carregarZonas() {
        mensagemLista.setText(R.string.zones_loading);
        mensagemLista.setVisibility(View.VISIBLE);
        listaZonas.setVisibility(View.GONE);
        apiZonas.listar(utilizadorId, new ZonasFavoritasApi.Resposta<>() {
            @Override public void aoConcluir(List<ZonaFavorita> zonas) {
                zonasGuardadas.clear();
                zonasGuardadas.addAll(zonas);
                adaptadorZonas.clear();
                for (ZonaFavorita zona : zonas) {
                    adaptadorZonas.add(getString(R.string.zone_list_item,
                            zona.nome, zona.raioKm, zona.local));
                }
                adaptadorZonas.notifyDataSetChanged();
                boolean vazia = zonas.isEmpty();
                if (vazia) {
                    mensagemLista.setText(R.string.zones_empty);
                } else {
                    mensagemLista.setText("");
                }
                mensagemLista.setVisibility(vazia ? View.VISIBLE : View.GONE);
                listaZonas.setVisibility(vazia ? View.GONE : View.VISIBLE);
                atualizarModoSelecao();
            }

            @Override public void aoFalhar() {
                mensagemLista.setText(R.string.zones_load_error);
                mensagemLista.setVisibility(View.VISIBLE);
                listaZonas.setVisibility(View.GONE);
            }
        });
    }

    private void iniciarSelecao(int modo) {
        modoSelecao = modoSelecao == modo ? 0 : modo;
        listaZonas.clearChoices();
        atualizarModoSelecao();
    }

    private void atualizarModoSelecao() {
        boolean selecionar = modoSelecao != 0 && !zonasGuardadas.isEmpty();
        listaZonas.setChoiceMode(selecionar ? ListView.CHOICE_MODE_MULTIPLE : ListView.CHOICE_MODE_NONE);
        listaZonas.setAdapter(null);
        adaptadorZonas = new ArrayAdapter<>(this,
                selecionar ? android.R.layout.simple_list_item_multiple_choice
                        : android.R.layout.simple_list_item_1);
        for (ZonaFavorita zona : zonasGuardadas) {
            adaptadorZonas.add(getString(R.string.zone_list_item, zona.nome, zona.raioKm, zona.local));
        }
        listaZonas.setAdapter(adaptadorZonas);
        acoesSelecao.setVisibility(selecionar ? View.VISIBLE : View.GONE);
        botaoConfirmarSelecao.setText(modoSelecao == 1
                ? R.string.zones_delete_selected : R.string.zones_show_selected);
    }

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

    private List<ZonaFavorita> zonasSelecionadas() {
        List<ZonaFavorita> selecionadas = new ArrayList<>();
        SparseBooleanArray marcadas = listaZonas.getCheckedItemPositions();
        for (int indice = 0; indice < zonasGuardadas.size(); indice++) {
            if (marcadas.get(indice)) selecionadas.add(zonasGuardadas.get(indice));
        }
        return selecionadas;
    }

    private void confirmarSelecao() {
        List<ZonaFavorita> selecionadas = zonasSelecionadas();
        if (selecionadas.isEmpty()) {
            Toast.makeText(this, R.string.zones_select_first, Toast.LENGTH_SHORT).show();
            return;
        }
        if (modoSelecao == 2) {
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

    private void eliminarZonas(List<ZonaFavorita> zonas) {
        botaoConfirmarSelecao.setEnabled(false);
        int[] restantes = {zonas.size()};
        boolean[] houveErro = {false};
        for (ZonaFavorita zona : zonas) {
            apiZonas.eliminar(utilizadorId, zona.id, new ZonasFavoritasApi.Resposta<>() {
                @Override public void aoConcluir(Boolean resultado) { terminarUmaEliminacao(); }
                @Override public void aoFalhar() {
                    houveErro[0] = true;
                    terminarUmaEliminacao();
                }
                private void terminarUmaEliminacao() {
                    if (--restantes[0] != 0 || isFinishing() || isDestroyed()) return;
                    modoSelecao = 0;
                    Toast.makeText(ZonasFavoritasActivity.this,
                            houveErro[0] ? R.string.zones_delete_error : R.string.zones_delete_done,
                            Toast.LENGTH_SHORT).show();
                    carregarZonas();
                }
            });
        }
    }

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
        ArrayAdapter<CharSequence> adaptadorRaios = ArrayAdapter.createFromResource(
                this, R.array.zone_radii, android.R.layout.simple_spinner_item);
        adaptadorRaios.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        raio.setAdapter(adaptadorRaios);
        guardar.setEnabled(false);
        final TomTomApi.Local[] localSelecionado = {null};
        final boolean[] aEscolherLocal = {false};

        pesquisa.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence texto, int inicio, int antes, int depois) { }
            @Override public void onTextChanged(CharSequence texto, int inicio, int antes, int depois) { }
            @Override public void afterTextChanged(Editable texto) {
                if (aEscolherLocal[0]) return;
                localSelecionado[0] = null;
                guardar.setEnabled(false);
                if (pesquisaPendente != null) gestorPesquisa.removeCallbacks(pesquisaPendente);
                int pedidoAtual = ++numeroDaPesquisa;
                String consulta = texto.toString().trim();
                if (consulta.length() < 2) {
                    sugestoes.setVisibility(View.GONE);
                    adaptadorSugestoes.clear();
                    return;
                }
                pesquisaPendente = () -> apiTomTom.pesquisarLocais(consulta, posicaoAtual,
                        new TomTomApi.Resposta<>() {
                            @Override public void aoConcluir(List<TomTomApi.Local> resultados) {
                                if (pedidoAtual != numeroDaPesquisa || !dialogo.isShowing()) return;
                                locais.clear();
                                locais.addAll(resultados);
                                adaptadorSugestoes.clear();
                                for (TomTomApi.Local local : resultados) {
                                    String linha = local.nome;
                                    if (!local.endereco.isEmpty() && !local.endereco.equals(local.nome)) {
                                        linha += " · " + local.endereco;
                                    }
                                    adaptadorSugestoes.add(linha);
                                }
                                adaptadorSugestoes.notifyDataSetChanged();
                                sugestoes.setVisibility(resultados.isEmpty() ? View.GONE : View.VISIBLE);
                                if (resultados.isEmpty()) {
                                    Toast.makeText(ZonasFavoritasActivity.this,
                                            R.string.search_not_found, Toast.LENGTH_SHORT).show();
                                }
                            }
                            @Override public void aoFalhar() {
                                if (pedidoAtual == numeroDaPesquisa && dialogo.isShowing()) {
                                    sugestoes.setVisibility(View.GONE);
                                    Toast.makeText(ZonasFavoritasActivity.this,
                                            R.string.search_error, Toast.LENGTH_SHORT).show();
                                }
                            }
                        });
                gestorPesquisa.postDelayed(pesquisaPendente, 400);
            }
        });

        sugestoes.setOnItemClickListener((lista, linha, posicao, id) -> {
            if (posicao >= locais.size()) return;
            localSelecionado[0] = locais.get(posicao);
            aEscolherLocal[0] = true;
            pesquisa.setText(localSelecionado[0].nome);
            nome.setText(localSelecionado[0].nome);
            aEscolherLocal[0] = false;
            sugestoes.setVisibility(View.GONE);
            guardar.setEnabled(true);
        });
        guardar.setOnClickListener(view -> {
            TomTomApi.Local local = localSelecionado[0];
            String nomeZona = nome.getText().toString().trim();
            if (local == null || nomeZona.isEmpty()) {
                Toast.makeText(this, R.string.zone_choose_location, Toast.LENGTH_SHORT).show();
                return;
            }
            int[] raios = {1, 2, 5, 10};
            int raioKm = raios[Math.max(0, raio.getSelectedItemPosition())];
            String morada = local.endereco == null || local.endereco.isEmpty() ? local.nome : local.endereco;
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
        });
        cancelar.setOnClickListener(view -> {
            if (pesquisaPendente != null) gestorPesquisa.removeCallbacks(pesquisaPendente);
            numeroDaPesquisa++;
            dialogo.dismiss();
        });
        dialogo.setOnDismissListener(ignored -> {
            if (pesquisaPendente != null) gestorPesquisa.removeCallbacks(pesquisaPendente);
            numeroDaPesquisa++;
        });
        dialogo.show();
    }

    @Override
    protected void onDestroy() {
        if (pesquisaPendente != null) gestorPesquisa.removeCallbacks(pesquisaPendente);
        gestorPesquisa.removeCallbacksAndMessages(null);
        if (apiZonas != null) apiZonas.fechar();
        if (apiTomTom != null) apiTomTom.fechar();
        super.onDestroy();
    }
}
