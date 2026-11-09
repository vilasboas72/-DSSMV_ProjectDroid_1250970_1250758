package com.example.roadguard.UI;

import com.example.roadguard.R;
import com.example.roadguard.Service.GestorHistorico;
import com.example.roadguard.Service.GestorViagem;
import com.example.roadguard.Service.SessaoUtilizador;
import com.example.roadguard.Service.VerificadorDeAlertas;
import com.example.roadguard.DTO.AlertaDTO;
import com.example.roadguard.Model.CategoriaAlerta;
import com.example.roadguard.Model.ZonaFavorita;
import com.example.roadguard.API.AlertasApi;
import com.example.roadguard.API.TomTomApi;

import android.Manifest;
import android.annotation.SuppressLint;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Locale;
import android.content.Intent;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;
import android.os.Handler;
import android.os.SystemClock;
import android.text.Editable;
import android.text.TextWatcher;
import android.graphics.PointF;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.content.pm.ApplicationInfo;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ArrayAdapter;
import android.widget.Toast;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import android.widget.TextView;
import android.app.Dialog;
import androidx.annotation.NonNull;
import androidx.activity.OnBackPressedCallback;
import android.graphics.Color;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.FragmentActivity;

import com.example.roadguard.databinding.ActivityMapBinding;
import com.tomtom.sdk.map.display.MapOptions;
import com.tomtom.sdk.map.display.TomTomMap;
import com.tomtom.sdk.map.display.camera.CameraOptions;
import com.tomtom.sdk.map.display.camera.CameraPosition;
import com.tomtom.sdk.map.display.circle.CircleOptions;
import com.tomtom.sdk.map.display.circle.Radius;
import com.tomtom.sdk.map.display.common.screen.Padding;
import com.tomtom.sdk.map.display.common.WidthByZoom;
import com.tomtom.sdk.map.display.polyline.PolylineOptions;
import com.tomtom.sdk.map.display.polyline.Polyline;
import com.tomtom.sdk.map.display.map.OnlineCachePolicy;
import com.tomtom.sdk.map.display.style.StyleMode;
import com.tomtom.sdk.map.display.marker.Marker;
import com.tomtom.sdk.map.display.marker.MarkerOptions;
import com.tomtom.sdk.map.display.ui.MapView;
import com.tomtom.sdk.map.display.ui.MapReadyCallback;
import com.tomtom.sdk.location.GeoPoint;
import com.tomtom.sdk.map.display.image.ImageFactory;

// Ecrã que mostra o mapa e a localização do utilizador.
public class MapActivity extends FragmentActivity implements MapReadyCallback {

    private static final int PEDIDO_PERMISSAO_LOCALIZACAO = 42;
    // O valor 1 deixa as pontas da linha da rota arredondadas.
    private static final int PONTA_ARREDONDADA = 1;

    private ActivityMapBinding interfaceMapa;
    private MapView vistaDoMapa;
    private TomTomMap mapaTomTom;
    private Marker marcadorDaLocalizacao;
    private Polyline linhaDaRota;
    // Zona ou zonas escolhidas no ecrã de zonas favoritas.
    private GeoPoint centroZonaFavoritaPendente;
    private boolean circuloZonaFavoritaVisivel;
    // Guarda quantos círculos existem no mapa para os poder esconder.
    private int quantidadeCirculosDesenhados;
    private final List<ZonaFavorita> zonasExibidasNoMapa = new ArrayList<>();
    private PainelAvisos painelAvisos;
    private final List<double[]> pontosDaViagem = new ArrayList<>();
    private LocationManager gestorDeLocalizacao;
    private LocationListener ouvinteDeLocalizacao;
    private GeoPoint posicaoAtual;
    private boolean mapaCentradoNoUtilizador;
    private int margemSuperiorDoSistema;
    private int margemFinalDoSistema;
    private int margemInferiorDoSistema;
    private int margemEsquerdaDoSistema;
    private boolean tecladoVisivel;
    private Location ultimaLocalizacaoGPS;
    private Location localizacaoGPSAnterior;
    private TomTomApi apiTomTom;
    private TomTomApi.Local destinoSelecionado;
    private TomTomApi.Rota rotaPlaneada;
    private ArrayAdapter<String> adaptadorDaPesquisa;
    private ArrayAdapter<TomTomApi.Local> adaptadorHistorico;
    private final List<TomTomApi.Local> historicoPesquisas = new ArrayList<>();
    private View cabecalhoHistorico;
    private boolean historicoVisivel;
    private GestorHistorico gestorHistorico;
    private final GestorViagem gestorViagem = new GestorViagem();
    private final Handler gestorPesquisa = new Handler(Looper.getMainLooper());
    private Runnable pesquisaPendente;
    private int numeroDaPesquisa;
    private boolean viagemIniciada;
    // Fica falso quando o utilizador move o mapa com o dedo.
    private boolean seguirLocalNaViagem = true;
    private boolean aRecalcularRota;
    private long ultimaRequisicaoDeRota;
    // Evita repetir o pedido para o mesmo alerta grave durante a viagem.
    private final Set<String> alertasGravesConsiderados = new HashSet<>();
    private boolean aEscolherDestino;
    private AlertasApi apiAlertas;
    private String utilizadorLogado;
    private boolean mapaVisivel;
    private boolean aLerAlertas;
    private boolean erroDeAlertasApresentado;
    private int falhasConsecutivasDeAlertas;
    private Dialog dialogoDeAlerta;
    private Dialog painelDetalheAlerta;
    // Guarda os alertas recebidos da Firebase pelo seu id.
    private final Map<String, AlertaDTO> alertasRecebidos = new HashMap<>();
    // Guarda os pinos que estão no mapa pelo id do alerta.
    private final Map<String, Marker> marcadoresDeAlertas = new HashMap<>();
    private final Handler atualizacaoAlertas = new Handler(Looper.getMainLooper());
    private final Runnable atualizarAlertasPeriodicamente = new Runnable() {
        @Override public void run() {
            // Volta a procurar alertas enquanto o mapa está aberto.
            if (!mapaVisivel) return;
            carregarAlertas();
            atualizacaoAlertas.postDelayed(this, 30000);
        }
    };

    // ===== ABERTURA DO ECRÃ E PREPARAÇÃO DO MAPA =====
    /** Cria o ecrã, lê a chave TomTom e prepara o mapa. */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Lê o id da conta guardado no login.
        utilizadorLogado = new SessaoUtilizador(this).obterId();
        if (utilizadorLogado.isEmpty()) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }
        // Prepara o histórico da conta e os pedidos dos alertas.
        gestorHistorico = new GestorHistorico(this, utilizadorLogado);
        apiAlertas = new AlertasApi();
        // Lê as zonas enviadas pelo ecrã de favoritos. Ao vir do login, não há zonas.
        lerZonaFavoritaDoIntent(getIntent());

        // Prepara o ecrã e o botão Voltar.
        // Mostra o layout activity_map.xml.
        interfaceMapa = ActivityMapBinding.inflate(getLayoutInflater());
        setContentView(interfaceMapa.getRoot());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            // Se o histórico estiver aberto, o botão Voltar fecha só o histórico.
            public void handleOnBackPressed() {
                if (historicoVisivel) {
                    esconderHistoricoPesquisas();
                    esconderTeclado();
                    interfaceMapa.mapSearchInput.clearFocus();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                    setEnabled(true);
                }
            }
        });
        // Prepara a caixa que mostra os avisos de perigo.
        // Quando a caixa abre ou fecha, muda a posição dos botões.
        // confirmarAlerta envia a resposta Ainda existe ou Já não existe.
        painelAvisos = new PainelAvisos(this, findViewById(R.id.painel_aviso), () ->
        {
            atualizarPosicaoDoBotaoAlerta();
            atualizarPosicaoDoBotaoCentralizar();
        }, this::confirmarAlerta);
        configurarMenuDoMapa();

        // Lê a chave necessária para usar a TomTom.
        String chaveApi;
        try {
            // Vai buscar a chave TomTom ao AndroidManifest.xml.
            ApplicationInfo informacoesDaAplicacao = getPackageManager().getApplicationInfo(
                    getPackageName(), PackageManager.GET_META_DATA);
            if (informacoesDaAplicacao.metaData == null) {
                chaveApi = null;
            } else {
                chaveApi = informacoesDaAplicacao.metaData.getString("com.tomtom.sdk.api.key");
            }
        } catch (PackageManager.NameNotFoundException e) {
            Toast.makeText(this, R.string.map_config_error, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        if (chaveApi == null || chaveApi.trim().isEmpty()) {
            // Se não houver chave, mostra um erro e fecha este ecrã.
            Toast.makeText(this, R.string.tomtom_key_missing, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // Prepara a pesquisa, os botões e as caixas do ecrã.
        apiTomTom = new TomTomApi(chaveApi);
        configurarPesquisaERota();
        // Ao tocar no botão, abre a criação de um alerta.
        interfaceMapa.btnReportAlert.setOnClickListener(view -> abrirCriacaoDeAlerta());
        // As caixas da rota e dos avisos podem mudar de tamanho.
        // Ajusta as posições para não taparem os botões.
        findViewById(R.id.painel_aviso).addOnLayoutChangeListener((v, l, t, r, bottom, ol, ot, or, ob) -> {
            atualizarPosicaoDoAviso();
            atualizarPosicaoDoBotaoAlerta();
            atualizarPosicaoDoBotaoCentralizar();
        });
        interfaceMapa.routePanel.addOnLayoutChangeListener((view, left, top, right, bottom,
                oldLeft, oldTop, oldRight, oldBottom) -> {
            atualizarPosicaoDoAviso();
            atualizarPosicaoDoBotaoAlerta();
            atualizarPosicaoDoBotaoCentralizar();
        });

        // Cria o mapa e prepara os botões de localização e bússola.
        // Estes valores são pedidos pela TomTom quando o mapa é criado em Java.
        MapOptions opcoesDoMapa = new MapOptions(
                chaveApi,
                new CameraOptions(new GeoPoint(41.1779, -8.6080), 14.0, 0.0, 0.0, 45.0),
                new Padding(0),
                null,
                StyleMode.MAIN,
                OnlineCachePolicy.Default.INSTANCE,
                false);
        // Começa com o mapa perto do ISEP enquanto espera pelo GPS.
        vistaDoMapa = new MapView(this, opcoesDoMapa);
        configurarGestosDoMapa();
        // Esconde os botões da TomTom para usar os botões da aplicação.
        ((View) vistaDoMapa.getCompassButton()).setVisibility(View.INVISIBLE);
        ((View) vistaDoMapa.getCurrentLocationButton()).setVisibility(View.INVISIBLE);
        interfaceMapa.btnCenterMap.setImageResource(vistaDoMapa.getCurrentLocationButton().getIcon());
        interfaceMapa.btnCenterMap.setColorFilter(Color.WHITE);
        interfaceMapa.btnCenterMap.setOnClickListener(view -> centrarNoUtilizador());
        interfaceMapa.btnCompass.setOnClickListener(view -> orientarMapaParaNorte());
        atualizarPosicaoDaBussola();
        // Coloca o mapa dentro do espaço reservado no layout.
        interfaceMapa.mapContainer.addView(vistaDoMapa, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        atualizarPosicaoDoBotaoCentralizar();

        // Avisa o componente do mapa de que o ecrã foi criado.
        vistaDoMapa.onCreate(savedInstanceState);
        // Quando o mapa estiver pronto, a TomTom chama onMapReady.
        vistaDoMapa.getMapAsync(this);
    }

    // ===== GESTOS E MENU DO MAPA =====
    // Mover o mapa com o dedo faz a câmara deixar de seguir o utilizador.
    @SuppressLint("ClickableViewAccessibility")
    // Permite explorar o mapa com o dedo durante a viagem.
    private void configurarGestosDoMapa() {
        vistaDoMapa.setOnTouchListener((vista, evento) -> {
            if (viagemIniciada && (evento.getActionMasked() == MotionEvent.ACTION_MOVE
                    || evento.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN)) {
                seguirLocalNaViagem = false;
            }
            return false; // Deixa o TomTom receber o gesto para mover e ampliar o mapa.
        });
    }

    // Ajusta a posição dos botões e prepara as opções do menu.
    private void configurarMenuDoMapa() {
        int margin = Math.round(16 * getResources().getDisplayMetrics().density);
        ViewCompat.setOnApplyWindowInsetsListener(interfaceMapa.getRoot(), (view, windowInsets) -> {
            // Quando o teclado fecha, esconde também o histórico.
            boolean tecladoVisivelAgora = windowInsets.isVisible(WindowInsetsCompat.Type.ime());
            if (tecladoVisivel && !tecladoVisivelAgora && historicoVisivel) {
                esconderHistoricoPesquisas();
                interfaceMapa.mapSearchInput.clearFocus();
            }
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
        // Ao tocar no botão, abre o menu com as opções disponíveis.
        interfaceMapa.btnMapMenu.setOnClickListener(view -> {
            PopupMenu menu = new PopupMenu(this, interfaceMapa.btnMapMenu, Gravity.END);
            menu.inflate(R.menu.map_menu);
            menu.getMenu().findItem(R.id.action_toggle_zone_circle)
                    .setVisible(centroZonaFavoritaPendente != null);
            if (circuloZonaFavoritaVisivel) {
                menu.getMenu().findItem(R.id.action_toggle_zone_circle).setTitle("Ocultar círculo da zona");
            } else {
                menu.getMenu().findItem(R.id.action_toggle_zone_circle).setTitle("Mostrar círculo da zona");
            }
            menu.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == R.id.action_favorite_zones) {
                    // Envia o GPS atual para ajudar a pesquisar zonas próximas.
                    Intent intent = new Intent(this, ZonasFavoritasActivity.class);
                    if (posicaoAtual != null) {
                        intent.putExtra("latitude_atual", posicaoAtual.getLatitude());
                        intent.putExtra("longitude_atual", posicaoAtual.getLongitude());
                    }
                    startActivity(intent);
                    return true;
                }
                if (item.getItemId() == R.id.action_toggle_zone_circle) {
                    // Esconder o círculo não apaga a zona da Firebase.
                    if (mapaTomTom == null || centroZonaFavoritaPendente == null) return false;
                    if (circuloZonaFavoritaVisivel) {
                        removerCirculosDasZonas();
                        circuloZonaFavoritaVisivel = false;
                    } else {
                        desenharCirculosDasZonas(zonasExibidasNoMapa);
                    }
                    atualizarAlertasNoMapa();
                    if (circuloZonaFavoritaVisivel) {
                        item.setTitle("Ocultar círculo da zona");
                    } else {
                        item.setTitle("Mostrar círculo da zona");
                    }
                    return true;
                }
                if (item.getItemId() == R.id.action_logout) {
                    terminarSessao();
                    return true;
                }
                return false;
            });
            menu.show();
        });
    }

    // ===== PESQUISA DE DESTINOS E HISTÓRICO =====
    // Prepara a pesquisa de destinos, o histórico e os botões da viagem.
    private void configurarPesquisaERota() {
        adaptadorDaPesquisa = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
        adaptadorHistorico = new AdaptadorPesquisasRecentes();
        configurarCabecalhoHistorico();
        interfaceMapa.searchSuggestions.setAdapter(adaptadorDaPesquisa);
        interfaceMapa.mapSearchBar.setOnClickListener(view -> {
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
                if (pesquisaPendente != null) gestorPesquisa.removeCallbacks(pesquisaPendente);
                int pesquisaAtual = ++numeroDaPesquisa;
                String consulta = texto.toString().trim();
                if (consulta.length() < 2) {
                    // Se o campo estiver vazio, mostra o histórico. Só pesquisa a partir de duas letras.
                    if (consulta.isEmpty() && interfaceMapa.mapSearchInput.hasFocus()) {
                        mostrarHistoricoPesquisas();
                    } else {
                        esconderHistoricoPesquisas();
                    }
                    return;
                }
                esconderHistoricoPesquisas();
                adaptadorDaPesquisa.clear();
                interfaceMapa.searchSuggestions.setAdapter(adaptadorDaPesquisa);
                pesquisaPendente = () -> pesquisarLocais(consulta, pesquisaAtual);
                // Espera 450 ms depois da escrita antes de pesquisar.
                gestorPesquisa.postDelayed(pesquisaPendente, 450);
            }
        });

        interfaceMapa.btnClearSearch.setOnClickListener(view -> {
            limparPesquisaERota();
            interfaceMapa.mapSearchInput.setText("");
            atualizarPosicaoDoBotaoCentralizar();
        });

        interfaceMapa.searchSuggestions.setOnItemClickListener((lista, linha, posicao, id) -> {
            // Desconta a linha do título para encontrar o local escolhido na lista.
            int indice = posicao - interfaceMapa.searchSuggestions.getHeaderViewsCount();
            if (indice < 0) return;
            if (historicoVisivel) {
                if (indice >= historicoPesquisas.size()) return;
                destinoSelecionado = historicoPesquisas.get(indice);
                gestorHistorico.guardar(destinoSelecionado);
            } else {
                if (indice >= resultadosDaPesquisa.size()) return;
                destinoSelecionado = resultadosDaPesquisa.get(indice);
                gestorHistorico.guardar(destinoSelecionado);
            }
            // Mostra o destino escolhido e pede a rota até ele.
            aEscolherDestino = true;
            interfaceMapa.mapSearchInput.setText(destinoSelecionado.nome);
            aEscolherDestino = false;
            esconderHistoricoPesquisas();
            interfaceMapa.searchSuggestions.setVisibility(View.GONE);
            esconderTeclado();
            pedirRota();
        });

        interfaceMapa.btnStartTrip.setOnClickListener(view -> iniciarViagem());
        interfaceMapa.btnEndTrip.setOnClickListener(view -> terminarViagem());
        interfaceMapa.btnDismissRoute.setOnClickListener(view -> cancelarRotaPlaneada());
        // Evita mostrar o histórico automaticamente ao abrir o mapa.
        interfaceMapa.mapSearchInput.clearFocus();
        esconderHistoricoPesquisas();
    }

    // Cria o título da lista de pesquisas recentes.
    private void configurarCabecalhoHistorico() {
        LinearLayout cabecalho = new LinearLayout(this);
        cabecalho.setGravity(Gravity.CENTER_VERTICAL);
        cabecalho.setPadding(converterDpParaPixeis(16), 0,
                converterDpParaPixeis(8), 0);

        TextView titulo = new TextView(this);
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
            super(MapActivity.this, R.layout.item_pesquisa_recente);
        }

        @NonNull
        @Override
        public View getView(int posicao, View linha, @NonNull android.view.ViewGroup pai) {
            // Se ainda não existir uma linha para mostrar este local, cria-a.
            if (linha == null) {
                linha = getLayoutInflater().inflate(R.layout.item_pesquisa_recente, pai, false);
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
            remover.setContentDescription(getString(R.string.search_remove_recent, local.nome));
            remover.setOnClickListener(view -> removerPesquisaRecente(local));
            return linha;
        }
    }

    // Mostra até três pesquisas recentes da conta atual.
    private void mostrarHistoricoPesquisas() {
        historicoPesquisas.clear();
        historicoPesquisas.addAll(gestorHistorico.ler());
        atualizarAdaptador(adaptadorHistorico, historicoPesquisas);
        if (historicoPesquisas.isEmpty()) {
            esconderHistoricoPesquisas();
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
    private void esconderHistoricoPesquisas() {
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
        if (historico.isEmpty()) esconderHistoricoPesquisas();
    }

    private final List<TomTomApi.Local> resultadosDaPesquisa = new ArrayList<>();

    // Pede à TomTom locais com o nome escrito pelo utilizador.
    private void pesquisarLocais(String consulta, int pesquisaAtual) {
        // A pesquisa também funciona sem saber a posição GPS.
        apiTomTom.pesquisarLocais(consulta, posicaoAtual, new TomTomApi.Resposta<>() {
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
                if (locais.isEmpty()) Toast.makeText(MapActivity.this, R.string.search_not_found, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void aoFalhar() {
                if (pesquisaAtual == numeroDaPesquisa) {
                    interfaceMapa.searchSuggestions.setVisibility(View.GONE);
                    Toast.makeText(MapActivity.this, R.string.search_error, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    // ===== PLANEAMENTO E DESENHO DA ROTA =====
    // Pede uma rota de carro da posição atual até ao destino.
    private void pedirRota() {
        if (posicaoAtual == null || destinoSelecionado == null) {
            Toast.makeText(this, R.string.route_needs_location, Toast.LENGTH_SHORT).show();
            return;
        }
        interfaceMapa.routePanel.setVisibility(View.VISIBLE);
        atualizarPosicaoDoBotaoAlerta();
        interfaceMapa.routeDestination.setText(destinoSelecionado.nome);
        interfaceMapa.routeSummary.setText(R.string.route_loading);
        interfaceMapa.btnStartTrip.setEnabled(false);
        // Guarda o destino para verificar se continua a ser o escolhido quando a resposta chegar.
        TomTomApi.Local destinoPedido = destinoSelecionado;
        apiTomTom.calcularRota(posicaoAtual, destinoPedido, new TomTomApi.Resposta<>() {
            @Override
            public void aoConcluir(TomTomApi.Rota rota) {
                // Se o destino mudou ou a rota foi cancelada, ignora a resposta.
                if (destinoSelecionado != destinoPedido) return;
                if (rota.pontos.size() < 2 || mapaTomTom == null) {
                    aoFalhar();
                    return;
                }
                rotaPlaneada = rota;
                desenharLinhaDaRota();
                mostrarResumoDaRota();
                interfaceMapa.btnStartTrip.setEnabled(true);
                // Ajusta a vista do mapa para mostrar o percurso.
                double centroLat = (posicaoAtual.getLatitude() + destinoPedido.latitude) / 2.0;
                double centroLon = (posicaoAtual.getLongitude() + destinoPedido.longitude) / 2.0;
                double zoom;
                if (rota.distanciaMetros < 5000) {
                    zoom = 13.0;
                } else if (rota.distanciaMetros < 20000) {
                    zoom = 11.5;
                } else {
                    zoom = 9.5;
                }
                mapaTomTom.moveCamera(new CameraOptions(new GeoPoint(centroLat, centroLon), zoom, 0.0, 0.0, 45.0));
            }

            @Override
            public void aoFalhar() {
                if (destinoSelecionado != destinoPedido) return;
                interfaceMapa.routePanel.setVisibility(View.GONE);
                atualizarPosicaoDoBotaoAlerta();
                Toast.makeText(MapActivity.this, R.string.route_error, Toast.LENGTH_LONG).show();
            }
        });
    }

    // Desenha no mapa a rota recebida da TomTom.
    private void desenharLinhaDaRota() {
        if (mapaTomTom == null || rotaPlaneada == null) return;
        // Apaga a linha anterior antes de desenhar a nova rota.
        mapaTomTom.removePolylines("rota_planeada");
        PolylineOptions opcoes = new PolylineOptions(
                rotaPlaneada.pontos,
                Color.rgb(26, 115, 232),
                java.util.Collections.singletonList(new WidthByZoom(8.0, 15.0)),
                Color.WHITE,
                java.util.Collections.singletonList(new WidthByZoom(1.5, 15.0)),
                PONTA_ARREDONDADA,
                PONTA_ARREDONDADA,
                false,
                "rota_planeada",
                null,
                null);
        linhaDaRota = mapaTomTom.addPolyline(opcoes);
    }

    // Apresenta a distância e o tempo estimado da rota.
    private void mostrarResumoDaRota() {
        String distancia;
        if (rotaPlaneada.distanciaMetros >= 1000) {
            distancia = String.format(Locale.getDefault(), "%.1f km", rotaPlaneada.distanciaMetros / 1000.0);
        } else {
            distancia = rotaPlaneada.distanciaMetros + " m";
        }
        // Passa o tempo de segundos para minutos, arredondando para cima.
        int minutos = Math.max(1, (rotaPlaneada.duracaoSegundos + 59) / 60);
        interfaceMapa.routeSummary.setText(
                getString(R.string.route_summary_format, distancia, minutos));
    }

    // Apaga os dados da pesquisa e a rota planeada.
    private void limparPesquisaERota() {
        // Ignora pesquisas antigas e limpa a pesquisa e a rota do ecrã.
        if (pesquisaPendente != null) gestorPesquisa.removeCallbacks(pesquisaPendente);
        numeroDaPesquisa++;
        destinoSelecionado = null;
        rotaPlaneada = null;
        linhaDaRota = null;
        resultadosDaPesquisa.clear();
        adaptadorDaPesquisa.clear();
        interfaceMapa.searchSuggestions.setVisibility(View.GONE);
        interfaceMapa.routePanel.setVisibility(View.GONE);
        if (mapaTomTom != null) mapaTomTom.removePolylines("rota_planeada");
    }

    // Cancela a rota planeada e volta à pesquisa.
    private void cancelarRotaPlaneada() {
        limparPesquisaERota();
        interfaceMapa.navigationBanner.setVisibility(View.GONE);
        interfaceMapa.mapSearchBar.setVisibility(View.VISIBLE);
        aEscolherDestino = true;
        interfaceMapa.mapSearchInput.setText("");
        aEscolherDestino = false;
        esconderHistoricoPesquisas();
        atualizarPosicaoDoBotaoAlerta();
        atualizarPosicaoDoBotaoCentralizar();
    }

    // ===== INÍCIO, ACOMPANHAMENTO E FIM DA VIAGEM =====
    // Começa a viagem pela rota que já está no mapa.
    private void iniciarViagem() {
        if (rotaPlaneada == null) return;
        viagemIniciada = true;
        seguirLocalNaViagem = true;
        aRecalcularRota = false;
        ultimaRequisicaoDeRota = 0;
        alertasGravesConsiderados.clear();
        gestorViagem.reiniciar();
        // Guarda as coordenadas da rota para procurar perigos no caminho.
        pontosDaViagem.clear();
        for (GeoPoint ponto : rotaPlaneada.pontos) {
            pontosDaViagem.add(new double[]{ponto.getLatitude(), ponto.getLongitude()});
        }
        // Limpa os avisos da viagem anterior e mostra os botões da nova viagem.
        painelAvisos.reiniciarViagem();
        interfaceMapa.mapSearchBar.setVisibility(View.GONE);
        interfaceMapa.searchSuggestions.setVisibility(View.GONE);
        interfaceMapa.navigationBanner.setVisibility(View.VISIBLE);
        interfaceMapa.btnDismissRoute.setVisibility(View.GONE);
        interfaceMapa.btnStartTrip.setVisibility(View.GONE);
        interfaceMapa.btnEndTrip.setVisibility(View.VISIBLE);
        interfaceMapa.routePanel.post(this::atualizarPosicaoDoBotaoCentralizar);
        if (mapaTomTom != null && posicaoAtual != null) {
            mapaTomTom.moveCamera(new CameraOptions(posicaoAtual, 16.0, 0.0, 0.0, 45.0));
        }
        String indicacao = getString(R.string.route_trip_started);
        for (TomTomApi.Instrucao instrucao : rotaPlaneada.instrucoes) {
            if (instrucao.distanciaDesdeInicio > 0) {
                String distancia;
                if (instrucao.distanciaDesdeInicio >= 1000) {
                    distancia = String.format(Locale.getDefault(), "%.1f km", instrucao.distanciaDesdeInicio / 1000.0);
                } else {
                    distancia = instrucao.distanciaDesdeInicio + " m";
                }
                indicacao += "\n" + getString(R.string.route_maneuver_format, distancia, instrucao.mensagem);
                break;
            }
        }

        interfaceMapa.navigationInstruction.setText(indicacao);
        atualizarPosicaoDoAviso();
        atualizarAlertasNoMapa();
    }

    // Atualiza o percurso e as indicações com a nova posição GPS.
    private void atualizarOrientacaoDaViagem(Location localizacao) {
        if (rotaPlaneada == null || rotaPlaneada.pontos.isEmpty()) return;
        GestorViagem.Progresso progresso = gestorViagem.atualizar(rotaPlaneada, localizacao);
        // Se o utilizador saiu da rota, tenta calcular outro percurso.
        if (progresso == null) {
            if (!aRecalcularRota) recalcularRota(localizacao);
            return;
        }
        if (linhaDaRota != null) {
            // Retira da linha a parte do percurso que já ficou para trás.
            linhaDaRota.setCoordinates(new ArrayList<>(
                    rotaPlaneada.pontos.subList(progresso.indice, rotaPlaneada.pontos.size())));
        }
        interfaceMapa.routeSummary.setText(getString(R.string.route_remaining_format,
                formatarDistancia(progresso.metrosRestantes), String.valueOf(progresso.minutosRestantes)));
        if (progresso.proximaInstrucao != null) {
            interfaceMapa.navigationInstruction.setText(getString(R.string.route_maneuver_format,
                    formatarDistancia(progresso.distanciaAteInstrucao), progresso.proximaInstrucao.mensagem));
        } else if (progresso.metrosRestantes < 60) {
            interfaceMapa.navigationInstruction.setText(R.string.route_arrival);
        } else {
            interfaceMapa.navigationInstruction.setText(R.string.route_keep_following);
        }
    }

    // Pede outra rota da posição atual até ao mesmo destino.
    private void recalcularRota(Location localizacao) {
        recalcularRota(localizacao, new ArrayList<>(), false);
    }

    // Calcula outra rota e tenta evitar os perigos graves recebidos na lista.
    private boolean recalcularRota(Location localizacao, List<AlertaDTO> alertasEvitados, boolean porAlertaGrave) {
        long agora = SystemClock.elapsedRealtime();
        // Não pede duas rotas ao mesmo tempo. Nos pedidos normais, espera pelo menos 15 segundos entre pedidos.
        if (!viagemIniciada || destinoSelecionado == null || aRecalcularRota || (!porAlertaGrave && agora - ultimaRequisicaoDeRota < 15000)) return false;

        aRecalcularRota = true;
        ultimaRequisicaoDeRota = agora;
        gestorViagem.limparAfastamento();
        TomTomApi.Local destino = destinoSelecionado;
        if (porAlertaGrave) {
            interfaceMapa.navigationInstruction.setText(R.string.route_avoiding_severe_alerts);
        } else {
            interfaceMapa.navigationInstruction.setText(R.string.route_recalculating);
        }

        GeoPoint origem = new GeoPoint(localizacao.getLatitude(), localizacao.getLongitude());
        TomTomApi.Resposta<TomTomApi.Rota> resposta = new TomTomApi.Resposta<>() {
            @Override
            public void aoConcluir(TomTomApi.Rota novaRota) {
                aRecalcularRota = false;
                if (!viagemIniciada || destinoSelecionado != destino) return;
                if (novaRota.pontos.size() < 2 || mapaTomTom == null) {
                    aoFalhar();
                    return;
                }
                // Guarda a nova rota e começa a acompanhar esse percurso.
                rotaPlaneada = novaRota;
                atualizarPontosDaViagem(novaRota);
                alertasGravesConsiderados.clear();
                gestorViagem.reiniciar();
                desenharLinhaDaRota();
                Location posicaoMaisRecente = ultimaLocalizacaoGPS == null
                        ? localizacao : ultimaLocalizacaoGPS;
                atualizarOrientacaoDaViagem(posicaoMaisRecente);
                verificarDesvioPorAlertaGrave();
            }

            @Override
            public void aoFalhar() {
                aRecalcularRota = false;
                if (!viagemIniciada || destinoSelecionado != destino) return;
                if (porAlertaGrave) {
                    interfaceMapa.navigationInstruction.setText(R.string.route_alert_reroute_failed);
                } else {
                    interfaceMapa.navigationInstruction.setText(R.string.route_recalculate_failed);
                }
            }
        };

        // Se a lista de perigos estiver vazia, pede uma rota normal. Se não, tenta evitá-los.
        if (alertasEvitados.isEmpty()) {
            apiTomTom.calcularRota(origem, destino, resposta);
        } else {
            apiTomTom.calcularRotaEvitandoAlertas(
                    origem, destino, pontosDaViagem, alertasEvitados, resposta);
        }
        return true;
    }

    // Guarda as coordenadas da nova rota para verificar os perigos no caminho.
    private void atualizarPontosDaViagem(TomTomApi.Rota rota) {
        pontosDaViagem.clear();
        for (GeoPoint ponto : rota.pontos) {
            pontosDaViagem.add(new double[]{ponto.getLatitude(), ponto.getLongitude()});
        }
    }

    // Procura perigos graves à frente e tenta encontrar um desvio.
    private void verificarDesvioPorAlertaGrave() {
        if (!viagemIniciada || aRecalcularRota || ultimaLocalizacaoGPS == null
                || pontosDaViagem.size() < 2) return;

        List<AlertaDTO> novosAlertasGraves = new ArrayList<>();
        // Procura perigos graves que ainda não tentou evitar.
        for (AlertaDTO alerta : alertasRecebidos.values()) {
            if (!"elevada".equals(alerta.getGravidade())
                    || alertasGravesConsiderados.contains(alerta.getId())) continue;
            double distancia = VerificadorDeAlertas.distanciaAteAlertaMaisAdiante(
                    pontosDaViagem, ultimaLocalizacaoGPS.getLatitude(),
                    ultimaLocalizacaoGPS.getLongitude(), alerta.getLatitude(), alerta.getLongitude());
            if (distancia >= 0) novosAlertasGraves.add(alerta);
        }
        if (novosAlertasGraves.isEmpty()) return;

        // Junta os perigos graves que ainda estão à frente na rota.
        List<AlertaDTO> alertasEvitados = new ArrayList<>();
        for (AlertaDTO alerta : alertasRecebidos.values()) {
            if (!"elevada".equals(alerta.getGravidade())) continue;
            double distancia = VerificadorDeAlertas.distanciaAteAlertaMaisAdiante(
                    pontosDaViagem, ultimaLocalizacaoGPS.getLatitude(),
                    ultimaLocalizacaoGPS.getLongitude(), alerta.getLatitude(), alerta.getLongitude());
            if (distancia >= 0) alertasEvitados.add(alerta);
        }

        // Ordena os perigos do mais próximo para o mais distante.
        alertasEvitados.sort((primeiro, segundo) -> Double.compare(
                VerificadorDeAlertas.distanciaAteAlertaMaisAdiante(pontosDaViagem,
                        ultimaLocalizacaoGPS.getLatitude(), ultimaLocalizacaoGPS.getLongitude(),
                        primeiro.getLatitude(), primeiro.getLongitude()),
                VerificadorDeAlertas.distanciaAteAlertaMaisAdiante(pontosDaViagem,
                        ultimaLocalizacaoGPS.getLatitude(), ultimaLocalizacaoGPS.getLongitude(),
                        segundo.getLatitude(), segundo.getLongitude())));
        if (recalcularRota(ultimaLocalizacaoGPS, alertasEvitados, true)) {
            for (AlertaDTO alerta : novosAlertasGraves) {
                alertasGravesConsiderados.add(alerta.getId());
            }
        }
    }
    // Prepara o texto da distância em metros ou quilómetros.
    private String formatarDistancia(double metros) {
        if (metros >= 1000) {
            return String.format(Locale.getDefault(), "%.1f km", metros / 1000.0);
        } else {
            return Math.round(metros / 10.0) * 10 + " m";
        }
    }
    // Termina a viagem, apaga a rota e volta a mostrar a pesquisa.
    private void terminarViagem() {
        viagemIniciada = false;
        alertasGravesConsiderados.clear();
        aRecalcularRota = false;
        gestorViagem.limparAfastamento();
        seguirLocalNaViagem = false;
        pontosDaViagem.clear();
        atualizarAlertasNoMapa();
        if (mapaTomTom != null) mapaTomTom.removePolylines("rota_planeada");
        linhaDaRota = null;
        rotaPlaneada = null;
        destinoSelecionado = null;
        interfaceMapa.routePanel.setVisibility(View.GONE);
        atualizarPosicaoDoBotaoAlerta();
        interfaceMapa.btnStartTrip.setVisibility(View.VISIBLE);
        interfaceMapa.btnEndTrip.setVisibility(View.GONE);
        interfaceMapa.btnDismissRoute.setVisibility(View.VISIBLE);
        interfaceMapa.mapSearchBar.setVisibility(View.VISIBLE);
        interfaceMapa.navigationBanner.setVisibility(View.GONE);
        atualizarPosicaoDoBotaoCentralizar();
    }

    // Esconde o teclado da pesquisa.
    private void esconderTeclado() {
        InputMethodManager teclado = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (teclado != null) teclado.hideSoftInputFromWindow(interfaceMapa.mapSearchInput.getWindowToken(), 0);
    }
    // Coloca a bússola logo abaixo do botão de menu.
    private void atualizarPosicaoDaBussola() {
        if (interfaceMapa == null) return;
        FrameLayout.LayoutParams parametros =
                (FrameLayout.LayoutParams) interfaceMapa.btnCompass.getLayoutParams();
        parametros.topMargin = margemSuperiorDoSistema + converterDpParaPixeis(72);
        parametros.setMarginEnd(margemFinalDoSistema + converterDpParaPixeis(16));
        interfaceMapa.btnCompass.setLayoutParams(parametros);
    }

    // Ao tocar na bússola, coloca o norte no topo do mapa.
    private void orientarMapaParaNorte() {
        if (mapaTomTom == null) return;
        CameraPosition camara = mapaTomTom.getCameraPosition();
        mapaTomTom.moveCamera(new CameraOptions(
                camara.getPosition(), camara.getZoom(), camara.getTilt(), 0.0, camara.getFieldOfView()));
    }

    // Mantém o botão à esquerda, alinhado ao centro com Reportar perigo.
    private void atualizarPosicaoDoBotaoCentralizar() {
        if (vistaDoMapa == null || interfaceMapa == null) return;
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
        return Math.round(valor * getResources().getDisplayMetrics().density);
    }

    // Centra o mapa na última posição GPS.
    private void centrarNoUtilizador() {
        if (mapaTomTom == null || posicaoAtual == null) {
            Toast.makeText(this, R.string.location_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }
        // Volta a fazer o mapa acompanhar a posição do utilizador.
        seguirLocalNaViagem = true;
        mapaTomTom.moveCamera(new CameraOptions(posicaoAtual, 16.0, 0.0, 0.0, 45.0));
    }

    // ===== SAÍDA DA CONTA =====
    // Termina a sessão e volta ao login.
    private void terminarSessao() {
        // Apaga a sessão do telemóvel, mas mantém a conta na Firebase.
        new SessaoUtilizador(this).limpar();
        pararAtualizacoesDaLocalizacao();
        Intent intent = new Intent(this, MainActivity.class);
        // Impede que o botão Voltar abra de novo o mapa depois de sair.
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    // ===== CICLO DE VIDA DO ECRÃ =====
    // Avisa o mapa de que o ecrã vai aparecer.
    @Override
    protected void onStart() {
        super.onStart();
        if (painelAvisos != null) painelAvisos.retomar();
        mapaVisivel = true;
        if (vistaDoMapa != null) vistaDoMapa.onStart();
        // Ao voltar ao mapa, começa novamente a procurar alertas.
        if (mapaTomTom != null) iniciarLeituraDeAlertas();
    }

    // Ao voltar ao ecrã, pede novamente o GPS se houver permissão.
    @Override
    protected void onResume() {
        super.onResume();
        if (vistaDoMapa != null) vistaDoMapa.onResume();
        if (mapaTomTom != null && temPermissaoDeLocalizacao()) {
            iniciarAtualizacoesDaLocalizacao();
        }
    }

    // Deixa de receber GPS quando o ecrã perde o foco.
    @Override
    protected void onPause() {
        pararAtualizacoesDaLocalizacao();
        if (vistaDoMapa != null) vistaDoMapa.onPause();
        super.onPause();
    }

    // Avisa o mapa de que o ecrã deixou de aparecer.
    @Override
    protected void onStop() {
        // Quando o ecrã fica escondido, para os avisos e as consultas dos alertas.
        mapaVisivel = false;
        if (painelAvisos != null) painelAvisos.parar();
        atualizacaoAlertas.removeCallbacksAndMessages(null);
        if (vistaDoMapa != null) vistaDoMapa.onStop();
        super.onStop();
    }

    // Guarda o estado do mapa caso o Android recrie o ecrã.
    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (vistaDoMapa != null) vistaDoMapa.onSaveInstanceState(outState);
    }

    // Fecha o mapa e para os trabalhos deste ecrã.
    @Override
    protected void onDestroy() {
        // Fecha as caixas e termina os pedidos antes de destruir o ecrã.
        pararAtualizacoesDaLocalizacao();
        atualizacaoAlertas.removeCallbacksAndMessages(null);
        if (dialogoDeAlerta != null) dialogoDeAlerta.dismiss();
        if (painelDetalheAlerta != null) painelDetalheAlerta.dismiss();
        if (painelAvisos != null) painelAvisos.parar();
        if (apiAlertas != null) apiAlertas.fechar();
        if (apiTomTom != null) apiTomTom.fechar();
        if (vistaDoMapa != null) vistaDoMapa.onDestroy();
        super.onDestroy();
    }

    // ===== MAPA PRONTO: CÂMARA, CLIQUES E LOCALIZAÇÃO =====
    // É chamado quando o mapa TomTom terminou de carregar.
    @Override
    public void onMapReady(@NonNull TomTomMap map) {
        // Guarda o mapa pronto para colocar pinos, círculos e a rota.
        mapaTomTom = map;
        // Mostra os nomes do mapa em português.
        mapaTomTom.setLanguage(Locale.forLanguageTag("pt-PT"));
        // Quando o mapa roda, a seta continua a apontar para norte.
        mapaTomTom.addCameraChangeListener(() -> runOnUiThread(() -> {
            if (mapaTomTom == null) return;
            double rotacao = mapaTomTom.getCameraPosition().getRotation();
            interfaceMapa.compassNeedle.setRotation((float) -rotacao);
        }));
        atualizarPosicaoDaBussola();

        if (centroZonaFavoritaPendente != null) {
            // Evita que a primeira posição GPS afaste o mapa da zona escolhida.
            mapaCentradoNoUtilizador = true;
            mostrarZonasFavoritasNoMapa(zonasExibidasNoMapa);
        } else {
            // Mostra o ISEP enquanto não há posição GPS.
            GeoPoint isep = new GeoPoint(41.1779, -8.6080);
            mapaTomTom.moveCamera(new CameraOptions(isep, 14.0, 0.0, 0.0, 45.0));
        }

        // Verifica se o pino tocado é um alerta.
        mapaTomTom.addMarkerClickListener(marcador -> {
            String tag = marcador.getTag();
            if (tag == null || !tag.startsWith("alerta:")) return;
            AlertaDTO alerta = alertasRecebidos.get(tag.substring("alerta:".length()));
            if (alerta != null) mostrarDetalhesDoAlerta(alerta);
        });
        // Com o mapa pronto, começa a procurar alertas e pede o GPS.
        iniciarLeituraDeAlertas();

        pedirLocalizacaoDoUtilizador();
    }

    // ===== ZONAS FAVORITAS NO MAPA =====
    // Lê as zonas enviadas pelo ecrã Zonas Favoritas.
    private void lerZonaFavoritaDoIntent(Intent intent) {
        // Os dados vêm no Intent. Aqui não vai à Firebase.
        // O ecrã anterior envia listas quando foram escolhidas várias zonas.
        double[] latitudes = intent.getDoubleArrayExtra("zonas_latitudes");
        double[] longitudes = intent.getDoubleArrayExtra("zonas_longitudes");
        int[] raios = intent.getIntArrayExtra("zonas_raios");
        String[] nomes = intent.getStringArrayExtra("zonas_nomes");
        if (latitudes != null && longitudes != null && raios != null && nomes != null
                && latitudes.length == longitudes.length && latitudes.length == raios.length
                && latitudes.length == nomes.length) {
            // Em cada volta, lê a latitude, a longitude, o raio e o nome de uma zona.
            for (int indice = 0; indice < latitudes.length; indice++) {
                if (!Double.isFinite(latitudes[indice]) || !Double.isFinite(longitudes[indice])
                        || Math.abs(latitudes[indice]) > 90 || Math.abs(longitudes[indice]) > 180
                        || raios[indice] <= 0) continue;
                zonasExibidasNoMapa.add(new ZonaFavorita("", utilizadorLogado,
                        nomes[indice], "", latitudes[indice], longitudes[indice], raios[indice]));
            }
        }
        if (zonasExibidasNoMapa.isEmpty()) {
            // Se foi escolhida só uma zona, lê os dados dessa zona.
            if (!intent.hasExtra("zona_latitude") || !intent.hasExtra("zona_longitude")) return;
            double latitude = intent.getDoubleExtra("zona_latitude", Double.NaN);
            double longitude = intent.getDoubleExtra("zona_longitude", Double.NaN);
            int raioKm = intent.getIntExtra("zona_raio_km", 0);
            if (!Double.isFinite(latitude) || !Double.isFinite(longitude)
                    || Math.abs(latitude) > 90 || Math.abs(longitude) > 180
                    || raioKm <= 0) return;
            zonasExibidasNoMapa.add(new ZonaFavorita("", utilizadorLogado,
                    intent.getStringExtra("zona_nome"), "", latitude, longitude, raioKm));
        }
        // Guarda o centro da primeira zona para quando o mapa estiver pronto.
        ZonaFavorita primeira = zonasExibidasNoMapa.get(0);
        centroZonaFavoritaPendente = new GeoPoint(primeira.latitude, primeira.longitude);
    }

    // Desenha as zonas escolhidas e ajusta o mapa para as mostrar.
    private void mostrarZonasFavoritasNoMapa(List<ZonaFavorita> zonas) {
        if (mapaTomTom == null || zonas.isEmpty()) return;
        desenharCirculosDasZonas(zonas);
        // Calcula um centro entre as zonas para posicionar o mapa.
        double latitudeMedia = 0;
        double longitudeMedia = 0;
        for (ZonaFavorita zona : zonas) {
            latitudeMedia += zona.latitude;
            longitudeMedia += zona.longitude;
        }
        GeoPoint centro = new GeoPoint(latitudeMedia / zonas.size(), longitudeMedia / zonas.size());
        // Vê quanto espaço é preciso para mostrar todas as zonas.
        double maiorExtensaoKm = 0.5;
        for (ZonaFavorita zona : zonas) {
            float[] distancia = new float[1];
            Location.distanceBetween(centro.getLatitude(), centro.getLongitude(),
                    zona.latitude, zona.longitude, distancia);
            maiorExtensaoKm = Math.max(maiorExtensaoKm,
                    distancia[0] / 1000.0 + zona.raioKm);
        }
        // Afasta o mapa se as zonas ocuparem uma área maior.
        double zoom = Math.max(3.0, Math.min(16.0,
                15.0 - Math.log(maiorExtensaoKm * 2.0) / Math.log(2.0)));
        mapaTomTom.moveCamera(new CameraOptions(centro, zoom, 0.0, 0.0, 45.0));
        String mensagemZonas;
        if (zonas.size() == 1) {
            mensagemZonas = zonas.get(0).nome + " · " + zonas.get(0).raioKm + " km";
        } else {
            mensagemZonas = zonas.size() + " zonas favoritas no mapa";
        }
        Toast.makeText(this, mensagemZonas, Toast.LENGTH_SHORT).show();
    }

    // Desenha um círculo azul claro para cada zona.
    private void desenharCirculosDasZonas(List<ZonaFavorita> zonas) {
        if (mapaTomTom == null || zonas == null || zonas.isEmpty()) return;
        removerCirculosDasZonas();
        for (int indice = 0; indice < zonas.size(); indice++) {
            ZonaFavorita zona = zonas.get(indice);
            // Passa o raio de quilómetros para metros.
            Radius raio = new Radius(zona.raioKm * 1000.0, 0, null);
            CircleOptions opcoes = new CircleOptions(
                    new GeoPoint(zona.latitude, zona.longitude), raio,
                    Color.argb(24, 66, 133, 244), Color.argb(95, 66, 133, 244),
                    2.0, false, "zona_favorita_" + indice);
            mapaTomTom.addCircle(opcoes);
        }
        quantidadeCirculosDesenhados = zonas.size();
        circuloZonaFavoritaVisivel = true;
    }

    // Esconde os círculos sem apagar as zonas guardadas.
    private void removerCirculosDasZonas() {
        if (mapaTomTom == null) return;
        mapaTomTom.removeCircles("zona_favorita");
        // Remove cada círculo pelo nome que recebeu ao ser criado.
        for (int indice = 0; indice < quantidadeCirculosDesenhados; indice++) {
            mapaTomTom.removeCircles("zona_favorita_" + indice);
        }
        quantidadeCirculosDesenhados = 0;
    }

    // ===== AVISOS EM VIAGEM E CONFIRMAÇÃO DOS PERIGOS =====
    // Avisa até 500 m antes do perigo e pede confirmação ao passar por ele.
    private void verificarAvisosDeAlertas() {
        if (painelAvisos == null || !mapaVisivel) return;
        List<PainelAvisos.Aviso> avisos = new ArrayList<>();
        if (viagemIniciada && obterPosicaoParaAlerta() != null) {
            Double latitudeAnterior = null;
            Double longitudeAnterior = null;
            if (localizacaoGPSAnterior != null) {
                long intervalo = ultimaLocalizacaoGPS.getElapsedRealtimeNanos()
                        - localizacaoGPSAnterior.getElapsedRealtimeNanos();
                // Só compara as duas posições se foram recebidas com poucos segundos de diferença.
                if (intervalo > 0 && intervalo <= 5_000_000_000L) {
                    latitudeAnterior = localizacaoGPSAnterior.getLatitude();
                    longitudeAnterior = localizacaoGPSAnterior.getLongitude();
                }
            }
            for (AlertaDTO alerta : alertasRecebidos.values()) {
                if (!marcadoresDeAlertas.containsKey(alerta.getId())) continue;
                double distancia = VerificadorDeAlertas.distanciaAteAlerta(pontosDaViagem,
                        posicaoAtual.getLatitude(), posicaoAtual.getLongitude(),
                        alerta.getLatitude(), alerta.getLongitude());
                // O autor não pode votar. As outras contas só podem votar uma vez.
                // Pede confirmação ao passar por um perigo que já mostrou num aviso.
                boolean pedirConfirmacao = !utilizadorLogado.equals(alerta.getUtilizadorId())
                        && !alerta.jaConfirmou(utilizadorLogado)
                        && (painelAvisos.estaAConfirmar(alerta.getId())
                        || painelAvisos.foiAvisado(alerta.getId())
                            && VerificadorDeAlertas.estaAPassarPeloAlerta(pontosDaViagem,
                            posicaoAtual.getLatitude(), posicaoAtual.getLongitude(),
                            alerta.getLatitude(), alerta.getLongitude(), latitudeAnterior, longitudeAnterior));
                if (pedirConfirmacao) {
                    distancia = 0; // A confirmação tem prioridade sobre os perigos à frente.
                } else if (distancia < 0) {
                    // Uma distância negativa significa que não deve mostrar o aviso.
                    continue;
                }

                // Coloca os avisos mais próximos no início da lista.
                int posicao = 0;
                while (posicao < avisos.size() && avisos.get(posicao).distancia < distancia) {
                    posicao++;
                }
                avisos.add(posicao, new PainelAvisos.Aviso(alerta, distancia, pedirConfirmacao));
            }
        }
        // Envia a lista à caixa de avisos, que escolhe o que mostrar.
        painelAvisos.atualizar(avisos);
    }

    // Envia o voto à Firebase e atualiza os alertas.
    private void confirmarAlerta(AlertaDTO alerta, boolean aindaExiste,
                                AlertasApi.Resposta<Boolean> resposta) {
        apiAlertas.confirmar(alerta.getId(), utilizadorLogado, aindaExiste,
                new AlertasApi.Resposta<>() {
                    @Override public void aoConcluir(Boolean guardado) {
                        if (guardado) {
                            // Guarda também o voto no telemóvel para não perguntar outra vez.
                            AlertaDTO recebido = alertasRecebidos.get(alerta.getId());
                            if (recebido != null) recebido.guardarConfirmacao(utilizadorLogado, aindaExiste);
                        }
                        // Mostra o resultado e volta a ler os alertas da Firebase.
                        resposta.aoConcluir(guardado);
                        atualizarAlertasNoMapa();
                        carregarAlertas();
                    }
                    @Override public void aoFalhar(AlertasApi.Erro erro) {
                        resposta.aoFalhar(erro);
                    }
                });
    }

    // Coloca o aviso acima da caixa com o destino e o tempo.
    private void atualizarPosicaoDoAviso() {
        View painel = findViewById(R.id.painel_aviso);
        if (painel == null || interfaceMapa == null) return;
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

    // Começa a procurar alertas de 30 em 30 segundos.
    private void iniciarLeituraDeAlertas() {
        atualizacaoAlertas.removeCallbacks(atualizarAlertasPeriodicamente);
        if (mapaVisivel) atualizacaoAlertas.post(atualizarAlertasPeriodicamente);
    }

    // Vai buscar os alertas à Firebase e atualiza o mapa se o pedido correr bem.
    private void carregarAlertas() {
        // Se já está a pedir alertas, espera que esse pedido termine.
        if (aLerAlertas || apiAlertas == null || mapaTomTom == null) return;
        aLerAlertas = true;
        apiAlertas.listar(new AlertasApi.Resposta<>() {
            @Override public void aoConcluir(List<AlertaDTO> alertas) {
                aLerAlertas = false;
                erroDeAlertasApresentado = false;
                falhasConsecutivasDeAlertas = 0;
                if (!mapaVisivel || isFinishing() || isDestroyed()) return;
                // O pedido correu bem: troca os alertas antigos pelos recebidos.
                alertasRecebidos.clear();
                for (AlertaDTO alerta : alertas) alertasRecebidos.put(alerta.getId(), alerta);
                // Limpa da lista de desvios os perigos que já não foram recebidos.
                alertasGravesConsiderados.retainAll(alertasRecebidos.keySet());
                atualizarAlertasNoMapa();
            }
            @Override public void aoFalhar(AlertasApi.Erro erro) {
                aLerAlertas = false;
                if (!mapaVisivel) return;
                falhasConsecutivasDeAlertas++;
                if (falhasConsecutivasDeAlertas == 1) {
                    // Na primeira falha, espera 3 segundos e tenta outra vez.
                    atualizacaoAlertas.postDelayed(MapActivity.this::carregarAlertas, 3000);
                } else if (!erroDeAlertasApresentado) {
                    Toast.makeText(MapActivity.this, mensagemErroDeAlertas(erro), Toast.LENGTH_LONG).show();
                    erroDeAlertasApresentado = true;
                }
            }
        });
    }

    // Mostra alertas até 2 km. Fora da viagem, inclui os das zonas visíveis.
    private void atualizarAlertasNoMapa() {
        if (mapaTomTom == null) return;

        // Usa uma cópia da lista para poder remover pinos enquanto a percorre.
        for (String id : new ArrayList<>(marcadoresDeAlertas.keySet())) {
            if (!alertasRecebidos.containsKey(id)) removerMarcadorDeAlerta(id);
        }
        Double latitude = null;
        Double longitude = null;
        if (posicaoAtual != null) {
            latitude = posicaoAtual.getLatitude();
            longitude = posicaoAtual.getLongitude();
        }
        for (AlertaDTO alerta : alertasRecebidos.values()) {
            // Pergunta ao VerificadorDeAlertas se este alerta deve aparecer.
            boolean mostrar = VerificadorDeAlertas.deveMostrarNoMapa(alerta, latitude, longitude,
                    viagemIniciada, circuloZonaFavoritaVisivel, zonasExibidasNoMapa);
            if (!mostrar) {
                removerMarcadorDeAlerta(alerta.getId());
            } else {
                mostrarMarcadorDeAlerta(alerta);
            }
        }
        // Depois de atualizar os pinos, verifica os desvios e os avisos.
        verificarDesvioPorAlertaGrave();
        verificarAvisosDeAlertas();
    }

    // Retira o pino do mapa, mas não apaga o alerta da Firebase.
    private void removerMarcadorDeAlerta(String id) {
        Marker marcador = marcadoresDeAlertas.remove(id);
        if (marcador != null) marcador.remove();
    }

    // Mostra um pino no local do alerta.
    private void mostrarMarcadorDeAlerta(AlertaDTO alerta) {
        GeoPoint local = new GeoPoint(alerta.getLatitude(), alerta.getLongitude());
        Marker marcador = marcadoresDeAlertas.get(alerta.getId());
        if (marcador != null) {
            // Se o pino já existe, muda só a sua posição.
            marcador.setCoordinate(local);
            return;
        }
        // Estes valores são necessários para criar um pino na TomTom.
        MarkerOptions opcoes = new MarkerOptions(local,
                ImageFactory.INSTANCE.fromResource(R.drawable.ic_alerta),
                null, null, new PointF(0.5f, 0.5f), new PointF(0.5f, 1.0f),
                new PointF(0.5f, 0.5f), "alerta:" + alerta.getId(), null, "");
        marcadoresDeAlertas.put(alerta.getId(), mapaTomTom.addMarker(opcoes));
    }

    // ===== BOTÃO DE REPORTE E CRIAÇÃO DE ALERTAS =====
    // Coloca o botão de reporte acima da caixa da rota e da barra do Android.
    private void atualizarPosicaoDoBotaoAlerta() {
        int margem = converterDpParaPixeis(16);
        FrameLayout.LayoutParams params =
                (FrameLayout.LayoutParams) interfaceMapa.btnReportAlert.getLayoutParams();
        int alturaPainel;
        if (interfaceMapa.routePanel.getVisibility() == View.VISIBLE) {
            alturaPainel = interfaceMapa.routePanel.getHeight() + margem;
        } else {
            alturaPainel = 0;
        }
        View aviso = findViewById(R.id.painel_aviso);
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

    // Verifica se há uma posição GPS recente para usar.
    private GeoPoint obterPosicaoParaAlerta() {
        // Não permite reportar se o GPS estiver desligado ou sem posição.
        if (!temPermissaoDeLocalizacao() || ultimaLocalizacaoGPS == null
                || gestorDeLocalizacao == null
                || !gestorDeLocalizacao.isProviderEnabled(LocationManager.GPS_PROVIDER)) return null;
        // Calcula há quanto tempo foi recebida esta posição GPS.
        long idade = android.os.SystemClock.elapsedRealtimeNanos()
                - ultimaLocalizacaoGPS.getElapsedRealtimeNanos();
        // Só aceita uma posição recebida há 30 segundos ou menos.
        if (idade < 0 || idade > 30_000_000_000L) return null;
        return new GeoPoint(ultimaLocalizacaoGPS.getLatitude(), ultimaLocalizacaoGPS.getLongitude());
    }

    // Abre o formulário para reportar um perigo.
    private void abrirCriacaoDeAlerta() {
        if (dialogoDeAlerta != null && dialogoDeAlerta.isShowing()) return;
        if (obterPosicaoParaAlerta() == null) {
            Toast.makeText(this, R.string.alert_needs_gps, Toast.LENGTH_LONG).show();
            pedirLocalizacaoDoUtilizador();
            return;
        }
        esconderTeclado();
        interfaceMapa.searchSuggestions.setVisibility(View.GONE);
        View formulario = getLayoutInflater().inflate(
                R.layout.dialog_criar_alerta, interfaceMapa.getRoot(), false);
        BottomSheetDialog dialogo = new BottomSheetDialog(this);
        dialogo.setContentView(formulario);
        dialogoDeAlerta = dialogo;
        new PainelCriarAlerta(dialogo, formulario, this::guardarAlerta);
        mostrarPainelInferior(dialogo, formulario);
    }

    // Quando o painel entrega a categoria, lê o GPS e envia o alerta.
    private void guardarAlerta(CategoriaAlerta categoria, PainelCriarAlerta painel) {
        GeoPoint ponto = obterPosicaoParaAlerta();
        if (ponto == null) {
            Toast.makeText(this, R.string.alert_needs_gps, Toast.LENGTH_LONG).show();
            return;
        }

        AlertaDTO alerta = new AlertaDTO();
        alerta.setUtilizadorId(utilizadorLogado);
        alerta.setCategoriaId(categoria.id);
        alerta.setGravidade(categoria.gravidade);
        alerta.setLatitude(ponto.getLatitude());
        alerta.setLongitude(ponto.getLongitude());
        alerta.setAtivo(true);

        painel.mostrarEnvio();
        apiAlertas.criar(alerta, new AlertasApi.Resposta<>() {
            @Override public void aoConcluir(String id) {
                if (isFinishing() || isDestroyed()) return;
                painel.fechar();
                Toast.makeText(MapActivity.this, R.string.alert_created, Toast.LENGTH_SHORT).show();
                carregarAlertas();
            }
            @Override public void aoFalhar(AlertasApi.Erro erro) {
                if (isFinishing() || isDestroyed()) return;
                painel.mostrarErro(getString(R.string.alert_post_uncertain,
                        getString(mensagemErroDeAlertas(erro))));
                carregarAlertas();
            }
        });
    }

    // ===== APRESENTAÇÃO DOS PAINÉIS E MENSAGENS =====
    /** Abre as caixas de reporte e de detalhes com o mesmo aspeto. */
    private void mostrarPainelInferior(BottomSheetDialog painel, View conteudo) {
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

    // Escolhe a mensagem de acordo com o erro recebido.
    private int mensagemErroDeAlertas(AlertasApi.Erro erro) {
        switch (erro) {
            case DNS: return R.string.alert_dns_error;
            case LIGACAO: return R.string.alert_connection_error;
            case TEMPO_ESGOTADO: return R.string.alert_timeout_error;
            case SEM_PERMISSAO: return R.string.alert_permission_error;
            case DADOS: return R.string.alert_data_error;
            case LIGACAO_SEGURA: return R.string.alert_secure_connection_error;
            default: return R.string.alert_server_error;
        }
    }

    // ===== DETALHES E ELIMINAÇÃO DE ALERTAS =====
    // Apresenta categoria, gravidade, data e autor do alerta selecionado.
    private void mostrarDetalhesDoAlerta(AlertaDTO alerta) {
        CategoriaAlerta categoria = CategoriaAlerta.procurar(alerta.getCategoriaId());
        if (categoria == null) return;
        View detalhes = getLayoutInflater().inflate(
                R.layout.dialog_detalhes_alerta, interfaceMapa.getRoot(), false);
        BottomSheetDialog painel = new BottomSheetDialog(this);
        painel.setContentView(detalhes);
        painelDetalheAlerta = painel;
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
        boolean eAutor = utilizadorLogado.equals(alerta.getUtilizadorId());
        if (eAutor) {
            eliminar.setVisibility(View.VISIBLE);
        } else {
            eliminar.setVisibility(View.GONE);
        }
        eliminar.setOnClickListener(vista -> confirmarEliminacaoDoAlerta(alerta));
        detalhes.findViewById(R.id.fechar_cabecalho_alerta).setOnClickListener(vista -> painel.dismiss());
        detalhes.findViewById(R.id.fechar_detalhe_alerta).setOnClickListener(vista -> painel.dismiss());
        painel.setOnDismissListener(dialogo -> {
            if (painelDetalheAlerta == painel) painelDetalheAlerta = null;
        });
        mostrarPainelInferior(painel, detalhes);
        // Vai buscar o nome da pessoa que criou o alerta.
        apiAlertas.obterNomeDoAutor(alerta.getUtilizadorId(), new AlertasApi.Resposta<>() {
            @Override public void aoConcluir(String nome) {
                if (!painel.isShowing() || isFinishing() || isDestroyed()) return;
                if (nome.isEmpty()) {
                    nomeAutor.setText(R.string.alert_author_unavailable);
                } else if (eAutor) {
                    nomeAutor.setText(getString(R.string.alert_author_current_user, nome));
                } else {
                    nomeAutor.setText(nome);
                }
            }
            @Override public void aoFalhar(AlertasApi.Erro erro) {
                if (painel.isShowing() && !isFinishing() && !isDestroyed()) {
                    nomeAutor.setText(R.string.alert_author_unavailable);
                }
            }
        });
    }

    // Pergunta ao autor se quer mesmo apagar o alerta.
    private void confirmarEliminacaoDoAlerta(AlertaDTO alerta) {
        if (alerta == null || !utilizadorLogado.equals(alerta.getUtilizadorId())) return;
        new AlertDialog.Builder(this)
                .setTitle(R.string.alert_delete_title)
                .setMessage(R.string.alert_delete_message)
                .setNegativeButton(R.string.alert_cancel, null)
                .setPositiveButton(R.string.alert_delete, (dialogo, escolha) ->
                        apiAlertas.eliminar(alerta.getId(), new AlertasApi.Resposta<>() {
                            @Override public void aoConcluir(Boolean resultado) {
                                if (isFinishing() || isDestroyed()) return;
                                if (painelDetalheAlerta != null) painelDetalheAlerta.dismiss();
                                // O alerta foi apagado: remove-o também da lista e do mapa.
                                alertasRecebidos.remove(alerta.getId());
                                atualizarAlertasNoMapa();
                                carregarAlertas();
                                Toast.makeText(MapActivity.this,
                                        R.string.alert_delete_done, Toast.LENGTH_SHORT).show();
                            }

                            @Override public void aoFalhar(AlertasApi.Erro erro) {
                                if (isFinishing() || isDestroyed()) return;
                                Toast.makeText(MapActivity.this,
                                        R.string.alert_delete_error, Toast.LENGTH_LONG).show();
                            }
                        }))
                .show();
    }

    // ===== PERMISSÃO DE LOCALIZAÇÃO E ATUALIZAÇÕES GPS =====
    // Se houver permissão, usa a localização. Se não, pede permissão.
    private void pedirLocalizacaoDoUtilizador() {
        if (temPermissaoDeLocalizacao()) {
            iniciarAtualizacoesDaLocalizacao();
            return;
        }

        requestPermissions(
                new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION
                },
                PEDIDO_PERMISSAO_LOCALIZACAO
        );
    }

    // Verifica se a app tem permissão para ler a localização do telemóvel.
    private boolean temPermissaoDeLocalizacao() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    // Recebe a resposta do utilizador ao pedido de permissão de localização.
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // Confirma que esta resposta pertence ao pedido de localização.
        if (requestCode != PEDIDO_PERMISSAO_LOCALIZACAO) return;

        if (temPermissaoDeLocalizacao()) {
            iniciarAtualizacoesDaLocalizacao();
        } else {
            Toast.makeText(this, R.string.location_permission_hint, Toast.LENGTH_LONG).show();
        }
    }

    // Começa a receber posições pelo GPS do telemóvel.
    private void iniciarAtualizacoesDaLocalizacao() {
        // Se o GPS já está a enviar posições, não o configura outra vez.
        if (ouvinteDeLocalizacao != null) return;

        gestorDeLocalizacao = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (gestorDeLocalizacao == null) {
            Toast.makeText(this, R.string.location_access_error, Toast.LENGTH_LONG).show();
            return;
        }

        try {
            if (!gestorDeLocalizacao.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                Toast.makeText(this, R.string.gps_disabled, Toast.LENGTH_LONG).show();
                return;
            }

            // Mostra a última posição guardada enquanto espera por uma nova.
            // Antes de criar um alerta, confirma se a posição não é antiga.
            Location lastKnownLocation = gestorDeLocalizacao.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (lastKnownLocation != null) mostrarLocalizacaoDoUtilizador(lastKnownLocation);

            // Quando chega uma posição GPS, chama mostrarLocalizacaoDoUtilizador.
            ouvinteDeLocalizacao = this::mostrarLocalizacaoDoUtilizador;
            // Pede posições com pelo menos 1 segundo de intervalo, mesmo sem movimento.
            gestorDeLocalizacao.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1000L,
                    0.0f,
                    ouvinteDeLocalizacao,
                    Looper.getMainLooper());
        } catch (SecurityException e) {
            Toast.makeText(this, R.string.location_permission_denied, Toast.LENGTH_LONG).show();
        }
    }

    // Mostra a posição GPS e atualiza o pino do utilizador.
    private void mostrarLocalizacaoDoUtilizador(@NonNull Location location) {
        if (mapaTomTom == null) return;

        // Guarda a posição anterior e a nova para saber se passou pelo perigo.
        localizacaoGPSAnterior = ultimaLocalizacaoGPS;
        ultimaLocalizacaoGPS = new Location(location);
        posicaoAtual = new GeoPoint(location.getLatitude(), location.getLongitude());
        if (!mapaCentradoNoUtilizador) {
            // Na primeira posição GPS, centra o mapa no utilizador.
            CameraOptions opcoesDaCamara = new CameraOptions(posicaoAtual, 16.0, 0.0, 0.0, 45.0);
            mapaTomTom.moveCamera(opcoesDaCamara);
            mapaCentradoNoUtilizador = true;
        }

        if (marcadorDaLocalizacao == null) {
            // Cria o pino que representa o utilizador.
            MarkerOptions markerOptions = new MarkerOptions(
                    posicaoAtual,
                    ImageFactory.INSTANCE.fromResource(R.drawable.ic_user_location),
                    null,
                    null,
                    new PointF(0.5f, 0.5f),
                    new PointF(0.5f, 0.5f),
                    new PointF(0.5f, 0.5f),
                    "current-location",
                    null,
                    ""
            );
            marcadorDaLocalizacao = mapaTomTom.addMarker(markerOptions);
        } else {
            // Se o pino já existe, move-o para a nova posição.
            marcadorDaLocalizacao.setCoordinate(posicaoAtual);
        }
        atualizarAlertasNoMapa();
        if (viagemIniciada) {
            // Durante a viagem, atualiza o percurso e faz o mapa seguir o utilizador, se essa opção estiver ativa.
            atualizarOrientacaoDaViagem(location);
            if (seguirLocalNaViagem) {
                double orientacao;
                if (location.hasBearing() && location.getSpeed() > 1.5f) {
                    // Se houver direção e velocidade suficiente, roda o mapa para o sentido do movimento.
                    orientacao = location.getBearing();
                } else {
                    orientacao = 0.0;
                }
                mapaTomTom.moveCamera(new CameraOptions(posicaoAtual, 16.0, 0.0, orientacao, 45.0));
            }
        }
    }

    // Deixa de receber posições GPS para não gastar bateria.
    private void pararAtualizacoesDaLocalizacao() {
        if (gestorDeLocalizacao != null && ouvinteDeLocalizacao != null) {
            gestorDeLocalizacao.removeUpdates(ouvinteDeLocalizacao);
            ouvinteDeLocalizacao = null;
        }
    }
}
