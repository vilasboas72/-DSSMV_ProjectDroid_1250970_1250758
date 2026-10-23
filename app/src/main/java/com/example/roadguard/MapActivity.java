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

    private static final int REQUEST_LOCATION_PERMISSION = 42;
    // No SDK TomTom 1.26.8, o valor 1 representa uma ponta arredondada.
    private static final int PONTA_ARREDONDADA = 1;

    private ActivityMapBinding binding;
    private MapView mapView;
    private TomTomMap tomtomMap;
    private Marker currentLocationMarker;
    private Polyline linhaDaRota;
    private LocationManager locationManager;
    private LocationListener locationListener;
    private GeoPoint currentPosition;
    private boolean hasCenteredOnUserLocation;
    private int topSystemInset;
    private int endSystemInset;
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

    /** Cria o ecrã, lê a chave TomTom e prepara o mapa. */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMapBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        configurarMenuDoMapa();

        String apiKey;
        try {
            ApplicationInfo applicationInfo = getPackageManager().getApplicationInfo(
                    getPackageName(), PackageManager.GET_META_DATA);
            apiKey = applicationInfo.metaData == null
                    ? null
                    : applicationInfo.metaData.getString("com.tomtom.sdk.api.key");
        } catch (PackageManager.NameNotFoundException e) {
            Toast.makeText(this, R.string.map_config_error, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        if (apiKey == null || apiKey.trim().isEmpty()) {
            Toast.makeText(this, R.string.tomtom_key_missing, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        apiTomTom = new TomTomApi(apiKey);
        configurarPesquisaERota();

        // Em Java, o SDK 1.26 exige os valores que o Kotlin fornece por omissão.
        MapOptions mapOptions = new MapOptions(
                apiKey,
                new CameraOptions(new GeoPoint(41.1779, -8.6080), 14.0, 0.0, 0.0, 45.0),
                new Padding(0),
                null,
                StyleMode.MAIN,
                OnlineCachePolicy.Default.INSTANCE,
                false);
        mapView = new MapView(this, mapOptions);
        atualizarPosicaoDaBussola();
        mapView.getCurrentLocationButton().addCurrentLocationButtonClickListener(this::centrarNoUtilizador);
        binding.mapContainer.addView(mapView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        // O MapView precisa de receber os eventos de ciclo de vida da Activity.
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this);
    }

    // Mantém o botão sobre o mapa e afastado da barra de estado e do recorte do ecrã.
    private void configurarMenuDoMapa() {
        int margin = Math.round(16 * getResources().getDisplayMetrics().density);
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (view, windowInsets) -> {
            Insets insets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            topSystemInset = insets.top;
            endSystemInset = ViewCompat.getLayoutDirection(view) == ViewCompat.LAYOUT_DIRECTION_RTL
                    ? insets.left : insets.right;
            FrameLayout.LayoutParams params =
                    (FrameLayout.LayoutParams) binding.btnMapMenu.getLayoutParams();
            params.topMargin = margin + insets.top;
            boolean isRtl = ViewCompat.getLayoutDirection(view) == ViewCompat.LAYOUT_DIRECTION_RTL;
            int margemInicial = margin + (isRtl ? insets.right : insets.left);
            int margemFinalDaPesquisa = margin + converterDpParaPixeis(60) + endSystemInset;
            params.setMarginEnd(margin + (isRtl ? insets.left : insets.right));
            binding.btnMapMenu.setLayoutParams(params);

            FrameLayout.LayoutParams searchParams =
                    (FrameLayout.LayoutParams) binding.getRoot().findViewById(R.id.map_search_bar).getLayoutParams();
            searchParams.topMargin = margin + insets.top;
            searchParams.setMarginStart(margemInicial);
            searchParams.setMarginEnd(margemFinalDaPesquisa);
            binding.getRoot().findViewById(R.id.map_search_bar).setLayoutParams(searchParams);
            FrameLayout.LayoutParams sugestoesParams = (FrameLayout.LayoutParams) binding.searchSuggestions.getLayoutParams();
            sugestoesParams.topMargin = margin + insets.top + converterDpParaPixeis(64);
            sugestoesParams.setMarginStart(margemInicial);
            sugestoesParams.setMarginEnd(margemFinalDaPesquisa);
            binding.searchSuggestions.setLayoutParams(sugestoesParams);
            FrameLayout.LayoutParams painelParams = (FrameLayout.LayoutParams) binding.routePanel.getLayoutParams();
            painelParams.bottomMargin = margin + insets.bottom;
            binding.routePanel.setLayoutParams(painelParams);
            atualizarPosicaoDaBussola();
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(binding.getRoot());
        binding.btnMapMenu.setOnClickListener(view -> {
            PopupMenu menu = new PopupMenu(this, binding.btnMapMenu, Gravity.END);
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
        binding.searchSuggestions.setAdapter(adaptadorDaPesquisa);

        binding.mapSearchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence texto, int inicio, int quantidade, int depois) { }
            @Override public void onTextChanged(CharSequence texto, int inicio, int antes, int quantidade) { }
            @Override public void afterTextChanged(Editable texto) {
                if (aEscolherDestino) return;
                if (pesquisaPendente != null) gestorPesquisa.removeCallbacks(pesquisaPendente);
                int pesquisaAtual = ++numeroDaPesquisa;
                String consulta = texto.toString().trim();
                if (consulta.length() < 2) {
                    binding.searchSuggestions.setVisibility(View.GONE);
                    return;
                }
                pesquisaPendente = () -> pesquisarLocais(consulta, pesquisaAtual);
                gestorPesquisa.postDelayed(pesquisaPendente, 450);
            }
        });

        binding.searchSuggestions.setOnItemClickListener((lista, linha, posicao, id) -> {
            if (posicao >= resultadosDaPesquisa.size()) return;
            destinoSelecionado = resultadosDaPesquisa.get(posicao);
            aEscolherDestino = true;
            binding.mapSearchInput.setText(destinoSelecionado.nome);
            aEscolherDestino = false;
            binding.searchSuggestions.setVisibility(View.GONE);
            esconderTeclado();
            pedirRota();
        });

        binding.btnStartTrip.setOnClickListener(view -> iniciarViagem());
        binding.btnEndTrip.setOnClickListener(view -> terminarViagem());
    }

    private final java.util.List<TomTomApi.Local> resultadosDaPesquisa = new java.util.ArrayList<>();

    // Aguarda o GPS e pede ao TomTom opções próximas do utilizador.
    private void pesquisarLocais(String consulta, int pesquisaAtual) {
        // Se o GPS ainda não respondeu, pesquisa sem forçar a localização do.
        apiTomTom.pesquisarLocais(consulta, currentPosition, new TomTomApi.Callback<>() {
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
                binding.searchSuggestions.setVisibility(locais.isEmpty() ? View.GONE : View.VISIBLE);
                if (locais.isEmpty()) Toast.makeText(MapActivity.this, R.string.search_not_found, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void aoFalhar() {
                if (pesquisaAtual == numeroDaPesquisa) {
                    binding.searchSuggestions.setVisibility(View.GONE);
                    Toast.makeText(MapActivity.this, R.string.search_error, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    // Pede uma rota de carro entre a localização GPS atual e o local escolhido.
    private void pedirRota() {
        if (currentPosition == null || destinoSelecionado == null) {
            Toast.makeText(this, R.string.route_needs_location, Toast.LENGTH_SHORT).show();
            return;
        }
        binding.routePanel.setVisibility(View.VISIBLE);
        binding.routeDestination.setText(destinoSelecionado.nome);
        binding.routeSummary.setText(R.string.route_loading);
        binding.routeGuidance.setText("");
        binding.btnStartTrip.setEnabled(false);
        apiTomTom.calcularRota(currentPosition, destinoSelecionado, new TomTomApi.Callback<>() {
            @Override
            public void aoConcluir(TomTomApi.Rota rota) {
                if (rota.pontos.size() < 2 || tomtomMap == null) {
                    aoFalhar();
                    return;
                }
                rotaPlaneada = rota;
                tomtomMap.removePolylines("rota_planeada");
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
                linhaDaRota = tomtomMap.addPolyline(opcoesDaLinha);
                mostrarResumoDaRota();
                binding.btnStartTrip.setEnabled(true);
                double centroLat = (currentPosition.getLatitude() + destinoSelecionado.latitude) / 2.0;
                double centroLon = (currentPosition.getLongitude() + destinoSelecionado.longitude) / 2.0;
                double zoom = rota.distanciaMetros < 5000 ? 13.0 : rota.distanciaMetros < 20000 ? 11.5 : 9.5;
                tomtomMap.moveCamera(new CameraOptions(new GeoPoint(centroLat, centroLon), zoom, 0.0, 0.0, 45.0));
            }

            @Override
            public void aoFalhar() {
                binding.routePanel.setVisibility(View.GONE);
                Toast.makeText(MapActivity.this, R.string.route_error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void mostrarResumoDaRota() {
        String distancia = rotaPlaneada.distanciaMetros >= 1000
                ? String.format(java.util.Locale.getDefault(), "%.1f km", rotaPlaneada.distanciaMetros / 1000.0)
                : rotaPlaneada.distanciaMetros + " m";
        int minutos = Math.max(1, (rotaPlaneada.duracaoSegundos + 59) / 60);
        binding.routeSummary.setText(getString(R.string.route_summary_format, distancia, String.valueOf(minutos)));
        binding.routeGuidance.setText(R.string.route_preview);
    }

    // Começa o modo de viagem e apresenta a primeira indicação da TomTom.
    private void iniciarViagem() {
        if (rotaPlaneada == null) return;
        viagemIniciada = true;
        indiceAtualDaRota = 0;
        binding.mapSearchBar.setVisibility(View.GONE);
        binding.searchSuggestions.setVisibility(View.GONE);
        binding.btnStartTrip.setVisibility(View.GONE);
        binding.btnEndTrip.setVisibility(View.VISIBLE);
        if (tomtomMap != null && currentPosition != null) {
            tomtomMap.moveCamera(new CameraOptions(currentPosition, 16.0, 0.0, 0.0, 45.0));
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

        binding.routeGuidance.setText(indicacao);
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
        binding.routeSummary.setText(getString(R.string.route_remaining_format, distanciaRestante, String.valueOf(minutosRestantes)));

        for (TomTomApi.Instrucao instrucao : rotaPlaneada.instrucoes) {
            double distanciaAteInstrucao = instrucao.distanciaDesdeInicio - percorridos;
            if (distanciaAteInstrucao > 20) {
                binding.routeGuidance.setText(getString(R.string.route_maneuver_format,
                        formatarDistancia(distanciaAteInstrucao), instrucao.mensagem));
                return;
            }
        }
        binding.routeGuidance.setText(restantes < 60 ? R.string.route_arrival : R.string.route_keep_following);
    }

    private String formatarDistancia(double metros) {
        return metros >= 1000
                ? String.format(java.util.Locale.getDefault(), "%.1f km", metros / 1000.0)
                : Math.round(metros / 10.0) * 10 + " m";
    }
    // Termina a viagem e limpa a rota do mapa.
    private void terminarViagem() {
        viagemIniciada = false;
        if (tomtomMap != null) tomtomMap.removePolylines("rota_planeada");
        linhaDaRota = null;
        rotaPlaneada = null;
        destinoSelecionado = null;
        binding.routePanel.setVisibility(View.GONE);
        binding.btnStartTrip.setVisibility(View.VISIBLE);
        binding.btnEndTrip.setVisibility(View.GONE);
        binding.mapSearchBar.setVisibility(View.VISIBLE);
    }

    private void esconderTeclado() {
        InputMethodManager teclado = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (teclado != null) teclado.hideSoftInputFromWindow(binding.mapSearchInput.getWindowToken(), 0);
    }
    // Coloca a bússola logo abaixo do botão de menu.
    private void atualizarPosicaoDaBussola() {
        if (mapView == null) return;
        int spacing = converterDpParaPixeis(72); // margem + altura do menu + espaço
        mapView.getCompassButton().setMargin(
                new Margin(0, topSystemInset + spacing, endSystemInset + converterDpParaPixeis(16), 0));
    }

    // Converte um valor dp (density-independent pixels) em píxeis no ecrã.
    private int converterDpParaPixeis(int valor) {
        return Math.round(valor * getResources().getDisplayMetrics().density);
    }

    // Centra novamente o mapa na última localização GPS recebida.
    private void centrarNoUtilizador() {
        if (tomtomMap == null || currentPosition == null) {
            Toast.makeText(this, R.string.location_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }
        tomtomMap.moveCamera(new CameraOptions(currentPosition, 16.0, 0.0, 0.0, 45.0));
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
        if (mapView != null) mapView.onStart();
    }

    // Retoma o mapa e volta a pedir localizações se a permissão já foi dada
    @Override
    protected void onResume() {
        super.onResume();
        if (mapView != null) mapView.onResume();
        if (tomtomMap != null && temPermissaoDeLocalizacao()) {
            iniciarAtualizacoesDaLocalizacao();
        }
    }

    // Para as atualizações GPS enquanto a Activity não está em primeiro plano
    @Override
    protected void onPause() {
        pararAtualizacoesDaLocalizacao();
        if (mapView != null) mapView.onPause();
        super.onPause();
    }

    // Informa o mapa de que a Activity deixou de estar visível.
    @Override
    protected void onStop() {
        if (mapView != null) mapView.onStop();
        super.onStop();
    }

    // Guarda o estado do mapa para poder ser restaurado depois.
    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mapView != null) mapView.onSaveInstanceState(outState);
    }

    // Liberta o mapa e termina as atualizações de localização.
    @Override
    protected void onDestroy() {
        pararAtualizacoesDaLocalizacao();
        if (apiTomTom != null) apiTomTom.fechar();
        if (mapView != null) mapView.onDestroy();
        super.onDestroy();
    }

    // É chamado quando o mapa TomTom terminou de carregar.
    @Override
    public void onMapReady(@NonNull TomTomMap map) {
        tomtomMap = map;
        atualizarPosicaoDaBussola();

        // Centro de reserva enquanto a localização do dispositivo é obtida.
        GeoPoint isep = new GeoPoint(41.1779, -8.6080);
        CameraOptions cameraOptions = new CameraOptions(isep, 14.0, 0.0, 0.0, 45.0);
        tomtomMap.moveCamera(cameraOptions);

        pedirLocalizacaoDoUtilizador();
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
                REQUEST_LOCATION_PERMISSION
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
        if (requestCode != REQUEST_LOCATION_PERMISSION) return;

        if (temPermissaoDeLocalizacao()) {
            iniciarAtualizacoesDaLocalizacao();
        } else {
            Toast.makeText(this, R.string.location_permission_hint, Toast.LENGTH_LONG).show();
        }
    }

    // Usa apenas o GPS para obter a posição e começar as atualizações.
    private void iniciarAtualizacoesDaLocalizacao() {
        if (locationListener != null) return;

        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (locationManager == null) {
            Toast.makeText(this, R.string.location_access_error, Toast.LENGTH_LONG).show();
            return;
        }

        try {
            if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                Toast.makeText(this, R.string.gps_disabled, Toast.LENGTH_LONG).show();
                return;
            }

            Location lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (lastKnownLocation != null) mostrarLocalizacaoDoUtilizador(lastKnownLocation);

            // Chama este metodo sempre que o Android recebe uma nova posicao

            locationListener = this::mostrarLocalizacaoDoUtilizador;
            locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1000L,
                    0.0f,
                    locationListener,
                    Looper.getMainLooper());
        } catch (SecurityException e) {
            Toast.makeText(this, R.string.location_permission_denied, Toast.LENGTH_LONG).show();
        }
    }

    // Centra o mapa na primeira posição e move o mesmo marcador nas seguintes.
    private void mostrarLocalizacaoDoUtilizador(@NonNull Location location) {
        if (tomtomMap == null) return;

        currentPosition = new GeoPoint(location.getLatitude(), location.getLongitude());
        if (!hasCenteredOnUserLocation) {
            CameraOptions cameraOptions = new CameraOptions(currentPosition, 16.0, 0.0, 0.0, 45.0);
            tomtomMap.moveCamera(cameraOptions);
            hasCenteredOnUserLocation = true;
        }

        if (currentLocationMarker == null) {
            // Usa apenas os parâmetros obrigatórios e um ícone do próprio Android
            MarkerOptions markerOptions = new MarkerOptions(
                    currentPosition,
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
            currentLocationMarker = tomtomMap.addMarker(markerOptions);
        } else {
            currentLocationMarker.setCoordinate(currentPosition);
        }
        if (viagemIniciada) {
            atualizarOrientacaoDaViagem(location);
            double orientacao = location.hasBearing() && location.getSpeed() > 1.5f ? location.getBearing() : 0.0;
            tomtomMap.moveCamera(new CameraOptions(currentPosition, 16.0, 0.0, orientacao, 45.0));
        }
    }

    // Remove o listener GPS para evitar trabalho e consumo de bateria desnecessários.
    private void pararAtualizacoesDaLocalizacao() {
        if (locationManager != null && locationListener != null) {
            locationManager.removeUpdates(locationListener);
            locationListener = null;
        }
    }
}
