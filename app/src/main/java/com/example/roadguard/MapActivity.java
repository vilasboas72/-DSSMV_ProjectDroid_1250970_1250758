package com.example.roadguard;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.graphics.PointF;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.content.pm.ApplicationInfo;
import android.widget.FrameLayout;
import android.widget.ArrayAdapter;
import android.widget.Toast;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import android.widget.TextView;
import android.app.Dialog;
import androidx.annotation.NonNull;
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
import com.tomtom.sdk.map.display.ui.Margin;
import com.tomtom.sdk.location.GeoPoint;
import com.tomtom.sdk.map.display.image.ImageFactory;

// Activity que apresenta o mapa TomTom e a posição atual do utilizador.
public class MapActivity extends FragmentActivity implements MapReadyCallback {

    private static final int PEDIDO_PERMISSAO_LOCALIZACAO = 42;
    // No SDK TomTom 1.26.8, o valor 1 representa uma ponta arredondada.
    private static final int PONTA_ARREDONDADA = 1;

    private ActivityMapBinding interfaceMapa;
    private MapView vistaDoMapa;
    private TomTomMap mapaTomTom;
    private Marker marcadorDaLocalizacao;
    private Polyline linhaDaRota;
    private LocationManager gestorDeLocalizacao;
    private LocationListener ouvinteDeLocalizacao;
    private GeoPoint posicaoAtual;
    private boolean mapaCentradoNoUtilizador;
    private int margemSuperiorDoSistema;
    private int margemFinalDoSistema;
    private int margemInferiorDoSistema;
    private Location ultimaLocalizacaoGPS;
    private TomTomApi apiTomTom;
    private TomTomApi.Local destinoSelecionado;
    private TomTomApi.Rota rotaPlaneada;
    private ArrayAdapter<String> adaptadorDaPesquisa;
    private final Handler gestorPesquisa = new Handler(Looper.getMainLooper());
    private Runnable pesquisaPendente;
    private int numeroDaPesquisa;
    private boolean viagemIniciada;
    private boolean aEscolherDestino;
    private int indiceAtualDaRota;
    private AlertasApi apiAlertas;
    private String utilizadorLogado;
    private boolean mapaVisivel;
    private boolean aLerAlertas;
    private boolean erroDeAlertasApresentado;
    private Dialog dialogoDeAlerta;
    private final java.util.Map<String, AlertaDTO> alertasNoMapa = new java.util.HashMap<>();
    private final java.util.List<Marker> marcadoresDeAlertas = new java.util.ArrayList<>();
    private final Handler atualizacaoAlertas = new Handler(Looper.getMainLooper());
    private final Runnable atualizarAlertasPeriodicamente = new Runnable() {
        @Override public void run() {
            if (!mapaVisivel) return;
            carregarAlertas();
            atualizacaoAlertas.postDelayed(this, 30000);
        }
    };

    /** Cria o ecrã, lê a chave TomTom e prepara o mapa. */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        utilizadorLogado = getSharedPreferences("RoadGuardPrefs", Context.MODE_PRIVATE)
                .getString("UID_LOGADO", "");
        if (utilizadorLogado.isEmpty()) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }
        apiAlertas = new AlertasApi();

        interfaceMapa = ActivityMapBinding.inflate(getLayoutInflater());
        setContentView(interfaceMapa.getRoot());
        configurarMenuDoMapa();

        String chaveApi;
        try {
            ApplicationInfo informacoesDaAplicacao = getPackageManager().getApplicationInfo(
                    getPackageName(), PackageManager.GET_META_DATA);
            chaveApi = informacoesDaAplicacao.metaData == null
                    ? null
                    : informacoesDaAplicacao.metaData.getString("com.tomtom.sdk.api.key");
        } catch (PackageManager.NameNotFoundException e) {
            Toast.makeText(this, R.string.map_config_error, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        if (chaveApi == null || chaveApi.trim().isEmpty()) {
            Toast.makeText(this, R.string.tomtom_key_missing, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        apiTomTom = new TomTomApi(chaveApi);
        configurarPesquisaERota();
        interfaceMapa.btnReportAlert.setOnClickListener(view -> abrirCriacaoDeAlerta());
        interfaceMapa.routePanel.addOnLayoutChangeListener((view, left, top, right, bottom,
                oldLeft, oldTop, oldRight, oldBottom) -> atualizarPosicaoDoBotaoAlerta());

        // Em Java, o SDK 1.26 exige os valores que o Kotlin fornece por omissão.
        MapOptions opcoesDoMapa = new MapOptions(
                chaveApi,
                new CameraOptions(new GeoPoint(41.1779, -8.6080), 14.0, 0.0, 0.0, 45.0),
                new Padding(0),
                null,
                StyleMode.MAIN,
                OnlineCachePolicy.Default.INSTANCE,
                false);
        vistaDoMapa = new MapView(this, opcoesDoMapa);
        atualizarPosicaoDaBussola();
        vistaDoMapa.getCurrentLocationButton().addCurrentLocationButtonClickListener(this::centrarNoUtilizador);
        interfaceMapa.mapContainer.addView(vistaDoMapa, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        // O MapView precisa de receber os eventos de ciclo de vida da Activity.
        vistaDoMapa.onCreate(savedInstanceState);
        vistaDoMapa.getMapAsync(this);
    }

    // Mantém o botão sobre o mapa e afastado da barra de estado e do recorte do ecrã.
    private void configurarMenuDoMapa() {
        int margin = Math.round(16 * getResources().getDisplayMetrics().density);
        ViewCompat.setOnApplyWindowInsetsListener(interfaceMapa.getRoot(), (view, windowInsets) -> {
            Insets insets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            margemSuperiorDoSistema = insets.top;
            margemInferiorDoSistema = insets.bottom;
            margemFinalDoSistema = ViewCompat.getLayoutDirection(view) == ViewCompat.LAYOUT_DIRECTION_RTL
                    ? insets.left : insets.right;
            FrameLayout.LayoutParams params =
                    (FrameLayout.LayoutParams) interfaceMapa.btnMapMenu.getLayoutParams();
            params.topMargin = margin + insets.top;
            boolean isRtl = ViewCompat.getLayoutDirection(view) == ViewCompat.LAYOUT_DIRECTION_RTL;
            int margemInicial = margin + (isRtl ? insets.right : insets.left);
            int margemFinalDaPesquisa = margin + converterDpParaPixeis(60) + margemFinalDoSistema;
            params.setMarginEnd(margin + (isRtl ? insets.left : insets.right));
            interfaceMapa.btnMapMenu.setLayoutParams(params);

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
            FrameLayout.LayoutParams painelParams = (FrameLayout.LayoutParams) interfaceMapa.routePanel.getLayoutParams();
            painelParams.bottomMargin = margin + insets.bottom;
            interfaceMapa.routePanel.setLayoutParams(painelParams);
            atualizarPosicaoDoBotaoAlerta();
            atualizarPosicaoDaBussola();
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(interfaceMapa.getRoot());
        interfaceMapa.btnMapMenu.setOnClickListener(view -> {
            PopupMenu menu = new PopupMenu(this, interfaceMapa.btnMapMenu, Gravity.END);
            menu.inflate(R.menu.map_menu);
            menu.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == R.id.action_favorite_zones) {
                    new AlertDialog.Builder(this)
                            .setTitle(R.string.favorite_zones)
                            .setMessage(R.string.favorite_zones_unavailable)
                            .setPositiveButton(R.string.close, null)
                            .show();
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

    // Liga a pesquisa dinâmica e os botões para iniciar ou terminar uma viagem.
    private void configurarPesquisaERota() {
        adaptadorDaPesquisa = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
        interfaceMapa.searchSuggestions.setAdapter(adaptadorDaPesquisa);

        interfaceMapa.mapSearchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence texto, int inicio, int quantidade, int depois) { }
            @Override public void onTextChanged(CharSequence texto, int inicio, int antes, int quantidade) { }
            @Override public void afterTextChanged(Editable texto) {
                if (aEscolherDestino) return;
                if (pesquisaPendente != null) gestorPesquisa.removeCallbacks(pesquisaPendente);
                int pesquisaAtual = ++numeroDaPesquisa;
                String consulta = texto.toString().trim();
                if (consulta.length() < 2) {
                    interfaceMapa.searchSuggestions.setVisibility(View.GONE);
                    return;
                }
                pesquisaPendente = () -> pesquisarLocais(consulta, pesquisaAtual);
                gestorPesquisa.postDelayed(pesquisaPendente, 450);
            }
        });

        interfaceMapa.searchSuggestions.setOnItemClickListener((lista, linha, posicao, id) -> {
            if (posicao >= resultadosDaPesquisa.size()) return;
            destinoSelecionado = resultadosDaPesquisa.get(posicao);
            aEscolherDestino = true;
            interfaceMapa.mapSearchInput.setText(destinoSelecionado.nome);
            aEscolherDestino = false;
            interfaceMapa.searchSuggestions.setVisibility(View.GONE);
            esconderTeclado();
            pedirRota();
        });

        interfaceMapa.btnStartTrip.setOnClickListener(view -> iniciarViagem());
        interfaceMapa.btnEndTrip.setOnClickListener(view -> terminarViagem());
    }

    private final java.util.List<TomTomApi.Local> resultadosDaPesquisa = new java.util.ArrayList<>();

    // Aguarda o GPS e pede ao TomTom opções próximas do utilizador.
    private void pesquisarLocais(String consulta, int pesquisaAtual) {
        // Se o GPS ainda não respondeu, pesquisa sem forçar a localização do.
        apiTomTom.pesquisarLocais(consulta, posicaoAtual, new TomTomApi.Resposta<>() {
            @Override
            public void aoConcluir(java.util.List<TomTomApi.Local> locais) {
                if (pesquisaAtual != numeroDaPesquisa) return;
                resultadosDaPesquisa.clear();
                resultadosDaPesquisa.addAll(locais);
                adaptadorDaPesquisa.clear();
                for (TomTomApi.Local local : locais) {
                    String texto = local.nome;
                    if (!local.endereco.isEmpty() && !local.endereco.equals(local.nome)) texto += " · " + local.endereco;
                    adaptadorDaPesquisa.add(texto);
                }
                adaptadorDaPesquisa.notifyDataSetChanged();
                interfaceMapa.searchSuggestions.setVisibility(locais.isEmpty() ? View.GONE : View.VISIBLE);
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

    // Pede uma rota de carro entre a localização GPS atual e o local escolhido.
    private void pedirRota() {
        if (posicaoAtual == null || destinoSelecionado == null) {
            Toast.makeText(this, R.string.route_needs_location, Toast.LENGTH_SHORT).show();
            return;
        }
        interfaceMapa.routePanel.setVisibility(View.VISIBLE);
        atualizarPosicaoDoBotaoAlerta();
        interfaceMapa.routeDestination.setText(destinoSelecionado.nome);
        interfaceMapa.routeSummary.setText(R.string.route_loading);
        interfaceMapa.routeGuidance.setText("");
        interfaceMapa.btnStartTrip.setEnabled(false);
        apiTomTom.calcularRota(posicaoAtual, destinoSelecionado, new TomTomApi.Resposta<>() {
            @Override
            public void aoConcluir(TomTomApi.Rota rota) {
                if (rota.pontos.size() < 2 || mapaTomTom == null) {
                    aoFalhar();
                    return;
                }
                rotaPlaneada = rota;
                mapaTomTom.removePolylines("rota_planeada");
                PolylineOptions opcoesDaLinha = new PolylineOptions(
                        rota.pontos,
                        Color.rgb(26, 115, 232),
                        java.util.Collections.singletonList(new WidthByZoom(8.0, 15.0)),
                        Color.WHITE,
                        java.util.Collections.singletonList(new WidthByZoom(1.5, 15.0)),
                        PONTA_ARREDONDADA,
                        PONTA_ARREDONDADA,
                        false,
                        "rota_planeada",
                        null,
                        null); // Parâmetro adicional do construtor Kotlin quando usado em Java.
                linhaDaRota = mapaTomTom.addPolyline(opcoesDaLinha);
                mostrarResumoDaRota();
                interfaceMapa.btnStartTrip.setEnabled(true);
                double centroLat = (posicaoAtual.getLatitude() + destinoSelecionado.latitude) / 2.0;
                double centroLon = (posicaoAtual.getLongitude() + destinoSelecionado.longitude) / 2.0;
                double zoom = rota.distanciaMetros < 5000 ? 13.0 : rota.distanciaMetros < 20000 ? 11.5 : 9.5;
                mapaTomTom.moveCamera(new CameraOptions(new GeoPoint(centroLat, centroLon), zoom, 0.0, 0.0, 45.0));
            }

            @Override
            public void aoFalhar() {
                interfaceMapa.routePanel.setVisibility(View.GONE);
                atualizarPosicaoDoBotaoAlerta();
                Toast.makeText(MapActivity.this, R.string.route_error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void mostrarResumoDaRota() {
        String distancia = rotaPlaneada.distanciaMetros >= 1000
                ? String.format(java.util.Locale.getDefault(), "%.1f km", rotaPlaneada.distanciaMetros / 1000.0)
                : rotaPlaneada.distanciaMetros + " m";
        int minutos = Math.max(1, (rotaPlaneada.duracaoSegundos + 59) / 60);
        interfaceMapa.routeSummary.setText(getString(R.string.route_summary_format, distancia, String.valueOf(minutos)));
        interfaceMapa.routeGuidance.setText(R.string.route_preview);
    }

    // Começa o modo de viagem e apresenta a primeira indicação da TomTom.
    private void iniciarViagem() {
        if (rotaPlaneada == null) return;
        viagemIniciada = true;
        indiceAtualDaRota = 0;
        interfaceMapa.mapSearchBar.setVisibility(View.GONE);
        interfaceMapa.searchSuggestions.setVisibility(View.GONE);
        interfaceMapa.btnStartTrip.setVisibility(View.GONE);
        interfaceMapa.btnEndTrip.setVisibility(View.VISIBLE);
        if (mapaTomTom != null && posicaoAtual != null) {
            mapaTomTom.moveCamera(new CameraOptions(posicaoAtual, 16.0, 0.0, 0.0, 45.0));
        }
        String indicacao = getString(R.string.route_trip_started);
        for (TomTomApi.Instrucao instrucao : rotaPlaneada.instrucoes) {
            if (instrucao.distanciaDesdeInicio > 0) {
                String distancia = instrucao.distanciaDesdeInicio >= 1000
                        ? String.format(java.util.Locale.getDefault(), "%.1f km", instrucao.distanciaDesdeInicio / 1000.0)
                        : instrucao.distanciaDesdeInicio + " m";
                indicacao += "\n" + getString(R.string.route_maneuver_format, distancia, instrucao.mensagem);
                break;
            }
        }

        interfaceMapa.routeGuidance.setText(indicacao);
    }

    // Atualiza a distância restante e a próxima manobra conforme o GPS avança na rota.
    private void atualizarOrientacaoDaViagem(Location localizacao) {
        if (rotaPlaneada == null || rotaPlaneada.pontos.isEmpty()) return;
        int inicio = Math.max(0, indiceAtualDaRota - 3);
        int fim = Math.min(rotaPlaneada.pontos.size(), indiceAtualDaRota + 201);
        int pontoMaisProximo = indiceAtualDaRota;
        float menorDistancia = Float.MAX_VALUE;
        float[] distancia = new float[1];

        for (int i = inicio; i < fim; i++) {
            GeoPoint ponto = rotaPlaneada.pontos.get(i);
            Location.distanceBetween(localizacao.getLatitude(), localizacao.getLongitude(),
                    ponto.getLatitude(), ponto.getLongitude(), distancia);
            if (distancia[0] < menorDistancia) {
                menorDistancia = distancia[0];
                pontoMaisProximo = i;
            }
        }

        // Se o GPS se afastou muito do trecho atual, procura a rota toda para se reencontrar.
        if (menorDistancia > 100) {
            for (int i = 0; i < rotaPlaneada.pontos.size(); i++) {
                GeoPoint ponto = rotaPlaneada.pontos.get(i);
                Location.distanceBetween(localizacao.getLatitude(), localizacao.getLongitude(),
                        ponto.getLatitude(), ponto.getLongitude(), distancia);
                if (distancia[0] < menorDistancia) {
                    menorDistancia = distancia[0];
                    pontoMaisProximo = i;
                }
            }
        }
        indiceAtualDaRota = Math.max(indiceAtualDaRota, pontoMaisProximo);
        if (linhaDaRota != null && indiceAtualDaRota < rotaPlaneada.pontos.size() - 1) {
            linhaDaRota.setCoordinates(new java.util.ArrayList<>(
                    rotaPlaneada.pontos.subList(indiceAtualDaRota, rotaPlaneada.pontos.size())));
        }

        double percorridos = rotaPlaneada.distanciaDeCadaPonto.get(indiceAtualDaRota);
        double restantes = Math.max(0, rotaPlaneada.distanciaMetros - percorridos);
        String distanciaRestante = formatarDistancia(restantes);
        int minutosRestantes = rotaPlaneada.distanciaMetros == 0 ? 0
                : (int) Math.ceil(rotaPlaneada.duracaoSegundos * restantes / rotaPlaneada.distanciaMetros / 60.0);
        interfaceMapa.routeSummary.setText(getString(R.string.route_remaining_format, distanciaRestante, String.valueOf(minutosRestantes)));

        for (TomTomApi.Instrucao instrucao : rotaPlaneada.instrucoes) {
            double distanciaAteInstrucao = instrucao.distanciaDesdeInicio - percorridos;
            if (distanciaAteInstrucao > 20) {
                interfaceMapa.routeGuidance.setText(getString(R.string.route_maneuver_format,
                        formatarDistancia(distanciaAteInstrucao), instrucao.mensagem));
                return;
            }
        }
        interfaceMapa.routeGuidance.setText(restantes < 60 ? R.string.route_arrival : R.string.route_keep_following);
    }

    private String formatarDistancia(double metros) {
        return metros >= 1000
                ? String.format(java.util.Locale.getDefault(), "%.1f km", metros / 1000.0)
                : Math.round(metros / 10.0) * 10 + " m";
    }
    // Termina a viagem e limpa a rota do mapa.
    private void terminarViagem() {
        viagemIniciada = false;
        if (mapaTomTom != null) mapaTomTom.removePolylines("rota_planeada");
        linhaDaRota = null;
        rotaPlaneada = null;
        destinoSelecionado = null;
        interfaceMapa.routePanel.setVisibility(View.GONE);
        atualizarPosicaoDoBotaoAlerta();
        interfaceMapa.btnStartTrip.setVisibility(View.VISIBLE);
        interfaceMapa.btnEndTrip.setVisibility(View.GONE);
        interfaceMapa.mapSearchBar.setVisibility(View.VISIBLE);
    }

    private void esconderTeclado() {
        InputMethodManager teclado = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (teclado != null) teclado.hideSoftInputFromWindow(interfaceMapa.mapSearchInput.getWindowToken(), 0);
    }
    // Coloca a bússola logo abaixo do botão de menu.
    private void atualizarPosicaoDaBussola() {
        if (vistaDoMapa == null) return;
        int spacing = converterDpParaPixeis(72); // margem + altura do menu + espaço
        vistaDoMapa.getCompassButton().setMargin(
                new Margin(0, margemSuperiorDoSistema + spacing, margemFinalDoSistema + converterDpParaPixeis(16), 0));
    }

    // Converte um valor dp (density-independent pixels) em píxeis no ecrã.
    private int converterDpParaPixeis(int valor) {
        return Math.round(valor * getResources().getDisplayMetrics().density);
    }

    // Centra novamente o mapa na última localização GPS recebida.
    private void centrarNoUtilizador() {
        if (mapaTomTom == null || posicaoAtual == null) {
            Toast.makeText(this, R.string.location_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }
        mapaTomTom.moveCamera(new CameraOptions(posicaoAtual, 16.0, 0.0, 0.0, 45.0));
    }

    // Limpa a identificação local e a pilha de ecrãs para não voltar ao mapa após sair.
    private void terminarSessao() {
        getSharedPreferences("RoadGuardPrefs", Context.MODE_PRIVATE)
                .edit()
                .remove("UID_LOGADO")
                .remove("EMAIL_LOGADO")
                .apply();
        pararAtualizacoesDaLocalizacao();
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    // Informa o mapa de que a Activity vai ficar visível
    @Override
    protected void onStart() {
        super.onStart();
        mapaVisivel = true;
        if (vistaDoMapa != null) vistaDoMapa.onStart();
        if (mapaTomTom != null) iniciarLeituraDeAlertas();
    }

    // Retoma o mapa e volta a pedir localizações se a permissão já foi dada
    @Override
    protected void onResume() {
        super.onResume();
        if (vistaDoMapa != null) vistaDoMapa.onResume();
        if (mapaTomTom != null && temPermissaoDeLocalizacao()) {
            iniciarAtualizacoesDaLocalizacao();
        }
    }

    // Para as atualizações GPS enquanto a Activity não está em primeiro plano
    @Override
    protected void onPause() {
        pararAtualizacoesDaLocalizacao();
        if (vistaDoMapa != null) vistaDoMapa.onPause();
        super.onPause();
    }

    // Informa o mapa de que a Activity deixou de estar visível.
    @Override
    protected void onStop() {
        mapaVisivel = false;
        atualizacaoAlertas.removeCallbacksAndMessages(null);
        if (vistaDoMapa != null) vistaDoMapa.onStop();
        super.onStop();
    }

    // Guarda o estado do mapa para poder ser restaurado depois.
    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (vistaDoMapa != null) vistaDoMapa.onSaveInstanceState(outState);
    }

    // Liberta o mapa e termina as atualizações de localização.
    @Override
    protected void onDestroy() {
        pararAtualizacoesDaLocalizacao();
        atualizacaoAlertas.removeCallbacksAndMessages(null);
        if (dialogoDeAlerta != null) dialogoDeAlerta.dismiss();
        if (apiAlertas != null) apiAlertas.fechar();
        if (apiTomTom != null) apiTomTom.fechar();
        if (vistaDoMapa != null) vistaDoMapa.onDestroy();
        super.onDestroy();
    }

    // É chamado quando o mapa TomTom terminou de carregar.
    @Override
    public void onMapReady(@NonNull TomTomMap map) {
        mapaTomTom = map;
        // Pede os nomes do mapa em português, independentemente do idioma do telemóvel.
        mapaTomTom.setLanguage(new java.util.Locale("pt", "PT"));
        atualizarPosicaoDaBussola();

        // Centro de reserva enquanto a localização do dispositivo é obtida.
        GeoPoint isep = new GeoPoint(41.1779, -8.6080);
        CameraOptions opcoesDaCamara = new CameraOptions(isep, 14.0, 0.0, 0.0, 45.0);
        mapaTomTom.moveCamera(opcoesDaCamara);

        mapaTomTom.addMarkerClickListener(marcador -> {
            String tag = marcador.getTag();
            if (tag == null || !tag.startsWith("alerta:")) return;
            AlertaDTO alerta = alertasNoMapa.get(tag.substring("alerta:".length()));
            if (alerta != null) mostrarDetalhesDoAlerta(alerta);
        });
        iniciarLeituraDeAlertas();

        pedirLocalizacaoDoUtilizador();
    }

    private void iniciarLeituraDeAlertas() {
        atualizacaoAlertas.removeCallbacks(atualizarAlertasPeriodicamente);
        if (mapaVisivel) atualizacaoAlertas.post(atualizarAlertasPeriodicamente);
    }

    // Um GET obtém os registos; os marcadores anteriores só são substituídos após sucesso.
    private void carregarAlertas() {
        if (aLerAlertas || apiAlertas == null || mapaTomTom == null) return;
        aLerAlertas = true;
        apiAlertas.listar(new AlertasApi.Resposta<java.util.List<AlertaDTO>>() {
            @Override public void aoConcluir(java.util.List<AlertaDTO> alertas) {
                aLerAlertas = false;
                erroDeAlertasApresentado = false;
                if (!mapaVisivel || isFinishing() || isDestroyed()) return;
                for (Marker marcador : marcadoresDeAlertas) marcador.remove();
                marcadoresDeAlertas.clear();
                alertasNoMapa.clear();
                for (AlertaDTO alerta : alertas) {
                    MarkerOptions opcoes = new MarkerOptions(
                            new GeoPoint(alerta.getLatitude(), alerta.getLongitude()),
                            ImageFactory.INSTANCE.fromResource(R.drawable.ic_alerta),
                            null, null, new PointF(0.5f, 0.5f), new PointF(0.5f, 1.0f),
                            new PointF(0.5f, 0.5f), "alerta:" + alerta.getId(), null, "");
                    marcadoresDeAlertas.add(mapaTomTom.addMarker(opcoes));
                    alertasNoMapa.put(alerta.getId(), alerta);
                }
            }
            @Override public void aoFalhar(AlertasApi.Erro erro) {
                aLerAlertas = false;
                if (mapaVisivel && !erroDeAlertasApresentado) {
                    Toast.makeText(MapActivity.this, mensagemErroDeAlertas(erro), Toast.LENGTH_LONG).show();
                    erroDeAlertasApresentado = true;
                }
            }
        });
    }

    // O botão fica acima do painel da rota e afastado da barra de navegação.
    private void atualizarPosicaoDoBotaoAlerta() {
        int margem = converterDpParaPixeis(16);
        FrameLayout.LayoutParams params =
                (FrameLayout.LayoutParams) interfaceMapa.btnReportAlert.getLayoutParams();
        int alturaPainel = interfaceMapa.routePanel.getVisibility() == View.VISIBLE
                ? interfaceMapa.routePanel.getHeight() + margem : 0;
        int margemInferior = margemInferiorDoSistema + margem + alturaPainel;
        int margemFinal = margemFinalDoSistema + margem;
        if (params.bottomMargin != margemInferior || params.getMarginEnd() != margemFinal) {
            params.bottomMargin = margemInferior;
            params.setMarginEnd(margemFinal);
            interfaceMapa.btnReportAlert.setLayoutParams(params);
        }
    }

    private GeoPoint obterPosicaoParaAlerta() {
        // Evita reportar na última posição antiga guardada pelo Android.
        if (!temPermissaoDeLocalizacao() || ultimaLocalizacaoGPS == null
                || gestorDeLocalizacao == null
                || !gestorDeLocalizacao.isProviderEnabled(LocationManager.GPS_PROVIDER)) return null;
        long idade = android.os.SystemClock.elapsedRealtimeNanos()
                - ultimaLocalizacaoGPS.getElapsedRealtimeNanos();
        // O Android mede este tempo em nanossegundos: este limite corresponde a 30 segundos.
        if (idade < 0 || idade > 30_000_000_000L) return null;
        return new GeoPoint(ultimaLocalizacaoGPS.getLatitude(), ultimaLocalizacaoGPS.getLongitude());
    }

    private void abrirCriacaoDeAlerta() {
        if (dialogoDeAlerta != null && dialogoDeAlerta.isShowing()) return;
        if (obterPosicaoParaAlerta() == null) {
            Toast.makeText(this, R.string.alert_needs_gps, Toast.LENGTH_LONG).show();
            pedirLocalizacaoDoUtilizador();
            return;
        }
        esconderTeclado();
        interfaceMapa.searchSuggestions.setVisibility(View.GONE);
        View formulario = getLayoutInflater().inflate(R.layout.dialog_criar_alerta, null);
        BottomSheetDialog dialogo = new BottomSheetDialog(this);
        dialogo.setContentView(formulario);
        dialogoDeAlerta = dialogo;
        MaterialButton confirmar = formulario.findViewById(R.id.confirm_alert);
        View fechar = formulario.findViewById(R.id.cancel_alert);
        TextView resumo = formulario.findViewById(R.id.alert_selection_summary);
        MaterialButton[] opcoes = {
                formulario.findViewById(R.id.category_works),
                formulario.findViewById(R.id.category_road_closed),
                formulario.findViewById(R.id.category_police),
                formulario.findViewById(R.id.category_accident),
                formulario.findViewById(R.id.category_pothole),
                formulario.findViewById(R.id.category_traffic)
        };
        CategoriaAlerta[] categorias = CategoriaAlerta.values();
        CategoriaAlerta[] selecionada = {null};
        fechar.setOnClickListener(view -> dialogo.dismiss());
        for (int i = 0; i < opcoes.length; i++) {
            MaterialButton botao = opcoes[i];
            CategoriaAlerta categoria = categorias[i];
            botao.setCheckable(true);
            botao.setContentDescription(getString(R.string.alert_selection_summary,
                    getString(categoria.nome), nomeDaGravidade(categoria.gravidade)));
            botao.setOnClickListener(view -> {
                selecionada[0] = categoria;
                for (MaterialButton opcao : opcoes) opcao.setChecked(opcao == botao);
                resumo.setText(getString(R.string.alert_selection_summary,
                        getString(categoria.nome), nomeDaGravidade(categoria.gravidade)));
                confirmar.setEnabled(true);
            });
        }
        confirmar.setOnClickListener(view -> {
            // O GPS é capturado ao confirmar; o utilizador apenas escolhe a categoria.
            GeoPoint ponto = obterPosicaoParaAlerta();
            if (ponto == null) {
                Toast.makeText(this, R.string.alert_needs_gps, Toast.LENGTH_LONG).show();
                return;
            }
            CategoriaAlerta categoria = selecionada[0];
            if (categoria == null) return;
            AlertaDTO alerta = new AlertaDTO();
            alerta.setUtilizadorId(utilizadorLogado);
            alerta.setCategoriaId(categoria.id);
            alerta.setGravidade(categoria.gravidade);
            alerta.setLatitude(ponto.getLatitude());
            alerta.setLongitude(ponto.getLongitude());
            alerta.setAtivo(true);
            dialogo.setCancelable(false);
            dialogo.getBehavior().setDraggable(false);
            confirmar.setEnabled(false);
            confirmar.setText(R.string.alert_saving);
            fechar.setEnabled(false);
            for (MaterialButton opcao : opcoes) opcao.setEnabled(false);
            apiAlertas.criar(alerta, new AlertasApi.Resposta<String>() {
                @Override public void aoConcluir(String id) {
                    if (isFinishing() || isDestroyed()) return;
                    dialogo.dismiss();
                    Toast.makeText(MapActivity.this, R.string.alert_created, Toast.LENGTH_SHORT).show();
                    carregarAlertas();
                }
                @Override public void aoFalhar(AlertasApi.Erro erro) {
                    if (isFinishing() || isDestroyed()) return;
                    dialogo.setCancelable(true);
                    dialogo.getBehavior().setDraggable(true);
                    fechar.setEnabled(true);
                    confirmar.setText(R.string.alert_save);
                    // Conservar a mensagem visível para não depender apenas de um Toast.
                    resumo.setText(getString(R.string.alert_post_uncertain, getString(mensagemErroDeAlertas(erro))));
                    carregarAlertas();
                }
            });
        });
        mostrarPainelInferior(dialogo);
    }

    /** Mostra os painéis de reporte e de detalhes com a mesma apresentação. */
    private void mostrarPainelInferior(BottomSheetDialog painel) {
        painel.setOnShowListener(ignorado -> {
            painel.getBehavior().setSkipCollapsed(true);
            painel.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
            View folha = painel.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (folha != null) folha.setBackgroundColor(Color.TRANSPARENT);
        });
        painel.show();
    }

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

    private String nomeDaGravidade(String gravidade) {
        return getString("elevada".equals(gravidade) ? R.string.alert_high
                : "media".equals(gravidade) ? R.string.alert_medium : R.string.alert_low);
    }

    /** Apresenta a ocorrência e consulta o nome do autor a partir do identificador. */
    private void mostrarDetalhesDoAlerta(AlertaDTO alerta) {
        CategoriaAlerta categoria = CategoriaAlerta.procurar(alerta.getCategoriaId());
        if (categoria == null) return;
        View detalhes = getLayoutInflater().inflate(R.layout.dialog_detalhes_alerta, null);
        BottomSheetDialog painel = new BottomSheetDialog(this);
        painel.setContentView(detalhes);
        dialogoDeAlerta = painel;
        ((android.widget.ImageView) detalhes.findViewById(R.id.icone_detalhe_alerta)).setImageResource(categoria.icone);
        ((TextView) detalhes.findViewById(R.id.categoria_detalhe_alerta)).setText(categoria.nome);
        ((TextView) detalhes.findViewById(R.id.gravidade_detalhe_alerta)).setText(nomeDaGravidade(alerta.getGravidade()));
        // Formato português de Portugal, com dia/mês/ano e horas de 24 horas.
        java.text.SimpleDateFormat formato = new java.text.SimpleDateFormat(
                "dd/MM/yyyy 'às' HH:mm", new java.util.Locale("pt", "PT"));
        ((TextView) detalhes.findViewById(R.id.data_detalhe_alerta))
                .setText(formato.format(new java.util.Date(alerta.getDataCriacao())));
        TextView nomeAutor = detalhes.findViewById(R.id.nome_autor_alerta);
        detalhes.findViewById(R.id.fechar_cabecalho_alerta).setOnClickListener(vista -> painel.dismiss());
        detalhes.findViewById(R.id.fechar_detalhe_alerta).setOnClickListener(vista -> painel.dismiss());
        mostrarPainelInferior(painel);
        apiAlertas.obterNomeDoAutor(alerta.getUtilizadorId(), new AlertasApi.Resposta<String>() {
            @Override public void aoConcluir(String nome) {
                if (!painel.isShowing() || isFinishing() || isDestroyed()) return;
                if (nome.isEmpty()) nomeAutor.setText(R.string.alert_author_unavailable);
                else nomeAutor.setText(utilizadorLogado.equals(alerta.getUtilizadorId())
                        ? getString(R.string.alert_author_current_user, nome) : nome);
            }
            @Override public void aoFalhar(AlertasApi.Erro erro) {
                if (painel.isShowing() && !isFinishing() && !isDestroyed()) {
                    nomeAutor.setText(R.string.alert_author_unavailable);
                }
            }
        });
    }

    // Usa a localização se já houver permissão; caso contrário, pede-a ao utilizador.
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
        if (requestCode != PEDIDO_PERMISSAO_LOCALIZACAO) return;

        if (temPermissaoDeLocalizacao()) {
            iniciarAtualizacoesDaLocalizacao();
        } else {
            Toast.makeText(this, R.string.location_permission_hint, Toast.LENGTH_LONG).show();
        }
    }

    // Usa apenas o GPS para obter a posição e começar as atualizações.
    private void iniciarAtualizacoesDaLocalizacao() {
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

            Location lastKnownLocation = gestorDeLocalizacao.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (lastKnownLocation != null) mostrarLocalizacaoDoUtilizador(lastKnownLocation);

            // Chama este metodo sempre que o Android recebe uma nova posicao

            ouvinteDeLocalizacao = this::mostrarLocalizacaoDoUtilizador;
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

    // Centra o mapa na primeira posição e move o mesmo marcador nas seguintes.
    private void mostrarLocalizacaoDoUtilizador(@NonNull Location location) {
        if (mapaTomTom == null) return;

        ultimaLocalizacaoGPS = new Location(location);
        posicaoAtual = new GeoPoint(location.getLatitude(), location.getLongitude());
        if (!mapaCentradoNoUtilizador) {
            CameraOptions opcoesDaCamara = new CameraOptions(posicaoAtual, 16.0, 0.0, 0.0, 45.0);
            mapaTomTom.moveCamera(opcoesDaCamara);
            mapaCentradoNoUtilizador = true;
        }

        if (marcadorDaLocalizacao == null) {
            // Usa apenas os parâmetros obrigatórios e um ícone do próprio Android
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
            marcadorDaLocalizacao.setCoordinate(posicaoAtual);
        }
        if (viagemIniciada) {
            atualizarOrientacaoDaViagem(location);
            double orientacao = location.hasBearing() && location.getSpeed() > 1.5f ? location.getBearing() : 0.0;
            mapaTomTom.moveCamera(new CameraOptions(posicaoAtual, 16.0, 0.0, orientacao, 45.0));
        }
    }

    // Remove o listener GPS para evitar trabalho e consumo de bateria desnecessários.
    private void pararAtualizacoesDaLocalizacao() {
        if (gestorDeLocalizacao != null && ouvinteDeLocalizacao != null) {
            gestorDeLocalizacao.removeUpdates(ouvinteDeLocalizacao);
            ouvinteDeLocalizacao = null;
        }
    }
}
