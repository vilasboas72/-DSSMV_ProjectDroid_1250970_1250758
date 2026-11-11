package com.example.roadguard.UI;

import com.example.roadguard.R;
import com.example.roadguard.Service.GestorHistorico;
import com.example.roadguard.Service.GestorLocalizacao;
import com.example.roadguard.Service.LeituraDeAlertas;
import com.example.roadguard.Service.SessaoUtilizador;
import com.example.roadguard.Service.VerificadorDeAlertas;
import com.example.roadguard.DTO.AlertaDTO;
import com.example.roadguard.Model.CategoriaAlerta;
import com.example.roadguard.API.AlertasApi;
import com.example.roadguard.API.TomTomApi;

import android.Manifest;
import android.annotation.SuppressLint;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Locale;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.content.pm.ApplicationInfo;
import android.widget.FrameLayout;
import android.widget.Toast;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import android.app.Dialog;
import androidx.annotation.NonNull;
import androidx.activity.OnBackPressedCallback;
import android.graphics.Color;
import androidx.appcompat.widget.PopupMenu;
import androidx.fragment.app.FragmentActivity;

import com.example.roadguard.databinding.ActivityMapBinding;
import com.tomtom.sdk.map.display.MapOptions;
import com.tomtom.sdk.map.display.TomTomMap;
import com.tomtom.sdk.map.display.camera.CameraOptions;
import com.tomtom.sdk.map.display.camera.CameraPosition;
import com.tomtom.sdk.map.display.common.screen.Padding;
import com.tomtom.sdk.map.display.map.OnlineCachePolicy;
import com.tomtom.sdk.map.display.style.StyleMode;
import com.tomtom.sdk.map.display.ui.MapView;
import com.tomtom.sdk.map.display.ui.MapReadyCallback;
import com.tomtom.sdk.location.GeoPoint;

// Ecrã que mostra o mapa e a localização do utilizador.
public class MapActivity extends FragmentActivity implements MapReadyCallback {

    private static final int PEDIDO_PERMISSAO_LOCALIZACAO = 42;

    private ActivityMapBinding interfaceMapa;
    private MapView vistaDoMapa;
    private TomTomMap mapaTomTom;
    private final MarcadoresDoMapa marcadoresDoMapa = new MarcadoresDoMapa();
    // Zona ou zonas escolhidas no ecrã de zonas favoritas.
    private final ZonasFavoritasNoMapa zonasFavoritas = new ZonasFavoritasNoMapa();
    private PainelAvisos painelAvisos;
    private GestorLocalizacao gestorLocalizacao;
    private GeoPoint posicaoAtual;
    private boolean mapaCentradoNoUtilizador;
    private LayoutDoMapa layoutDoMapa;
    private Location ultimaLocalizacaoGPS;
    private Location localizacaoGPSAnterior;
    private TomTomApi apiTomTom;
    private PesquisaDoMapa pesquisaDoMapa;
    private ViagemNoMapa viagem;
    private AlertasApi apiAlertas;
    private String utilizadorLogado;
    private boolean mapaVisivel;
    private LeituraDeAlertas leituraDeAlertas;
    private Dialog dialogoDeAlerta;
    private PainelDetalhesAlerta painelDetalhesAlerta;
    // Guarda os alertas recebidos da Firebase pelo seu id.
    private final Map<String, AlertaDTO> alertasRecebidos = new HashMap<>();
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
        // Prepara os pedidos dos alertas e a localização.
        apiAlertas = new AlertasApi();
        leituraDeAlertas = new LeituraDeAlertas(apiAlertas, this::receberAlertas,
                erro -> Toast.makeText(this, mensagemErroDeAlertas(erro), Toast.LENGTH_LONG).show());
        gestorLocalizacao = new GestorLocalizacao(this);
        // Lê as zonas enviadas pelo ecrã de favoritos. Ao vir do login, não há zonas.
        zonasFavoritas.lerDoIntent(getIntent(), utilizadorLogado);

        // Prepara o ecrã e o botão Voltar.
        // Mostra o layout activity_map.xml.
        interfaceMapa = ActivityMapBinding.inflate(getLayoutInflater());
        setContentView(interfaceMapa.getRoot());
        layoutDoMapa = new LayoutDoMapa(interfaceMapa);
        painelDetalhesAlerta = new PainelDetalhesAlerta(this, interfaceMapa.getRoot(), apiAlertas);
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            // Se o histórico estiver aberto, o botão Voltar fecha só o histórico.
            public void handleOnBackPressed() {
                if (pesquisaDoMapa != null && pesquisaDoMapa.historicoVisivel()) {
                    pesquisaDoMapa.fecharHistorico();
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
            layoutDoMapa.atualizarPosicaoDoBotaoAlerta();
            layoutDoMapa.atualizarPosicaoDoBotaoCentralizar();
        }, this::confirmarAlerta);
        configurarMenuDoMapa();
        layoutDoMapa.configurar(() -> {
            if (pesquisaDoMapa != null && pesquisaDoMapa.historicoVisivel()) {
                pesquisaDoMapa.esconderHistorico();
                interfaceMapa.mapSearchInput.clearFocus();
            }
        });

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
        pesquisaDoMapa = new PesquisaDoMapa(this, interfaceMapa, apiTomTom,
                new GestorHistorico(this, utilizadorLogado), () -> posicaoAtual,
                local -> viagem.selecionarDestino(local), () -> {
                    viagem.limparRotaPlaneada();
                    layoutDoMapa.atualizarPosicaoDoBotaoCentralizar();
                });
        viagem = new ViagemNoMapa(this, interfaceMapa, apiTomTom, layoutDoMapa, pesquisaDoMapa,
                painelAvisos, alertasRecebidos.values(), this::atualizarAlertasNoMapa);
        // Ao tocar no botão, abre a criação de um alerta.
        interfaceMapa.btnReportAlert.setOnClickListener(view -> {
            // AÇÃO DO UTILIZADOR — Reportar perigo
            abrirCriacaoDeAlerta();
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
        layoutDoMapa.atualizarPosicaoDaBussola();
        // Coloca o mapa dentro do espaço reservado no layout.
        interfaceMapa.mapContainer.addView(vistaDoMapa, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        layoutDoMapa.atualizarPosicaoDoBotaoCentralizar();

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
            if (viagem.estaAtiva() && (evento.getActionMasked() == MotionEvent.ACTION_MOVE
                    || evento.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN)) {
                viagem.seguirUtilizador(false);
            }
            return false; // Deixa o TomTom receber o gesto para mover e ampliar o mapa.
        });
    }

    // Ajusta a posição dos botões e prepara as opções do menu.
    private void configurarMenuDoMapa() {
        // Ao tocar no botão, abre o menu com as opções disponíveis.
        interfaceMapa.btnMapMenu.setOnClickListener(view -> {
            PopupMenu menu = new PopupMenu(this, interfaceMapa.btnMapMenu, Gravity.END);
            menu.inflate(R.menu.map_menu);
            menu.getMenu().findItem(R.id.action_toggle_zone_circle)
                    .setVisible(zonasFavoritas.temZonas());
            if (zonasFavoritas.circulosVisiveis()) {
                menu.getMenu().findItem(R.id.action_toggle_zone_circle).setTitle("Ocultar círculo da zona");
            } else {
                menu.getMenu().findItem(R.id.action_toggle_zone_circle).setTitle("Mostrar círculo da zona");
            }
            menu.setOnMenuItemClickListener(item -> {
                // AÇÃO DO UTILIZADOR — Escolher Zonas Favoritas no menu
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
                    if (mapaTomTom == null || !zonasFavoritas.temZonas()) return false;
                    zonasFavoritas.alternarCirculos(mapaTomTom);
                    atualizarAlertasNoMapa();
                    if (zonasFavoritas.circulosVisiveis()) {
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

    // Ao tocar na bússola, coloca o norte no topo do mapa.
    private void orientarMapaParaNorte() {
        if (mapaTomTom == null) return;
        CameraPosition camara = mapaTomTom.getCameraPosition();
        mapaTomTom.moveCamera(new CameraOptions(
                camara.getPosition(), camara.getZoom(), camara.getTilt(), 0.0, camara.getFieldOfView()));
    }

    // Centra o mapa na última posição GPS.
    private void centrarNoUtilizador() {
        if (mapaTomTom == null || posicaoAtual == null) {
            Toast.makeText(this, R.string.location_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }
        // Volta a fazer o mapa acompanhar a posição do utilizador.
        viagem.seguirUtilizador(true);
        mapaTomTom.moveCamera(new CameraOptions(posicaoAtual, 16.0, 0.0, 0.0, 45.0));
    }

    // ===== SAÍDA DA CONTA =====
    // Termina a sessão e volta ao login.
    private void terminarSessao() {
        // Apaga a sessão do telemóvel, mas mantém a conta na Firebase.
        new SessaoUtilizador(this).limpar();
        if (gestorLocalizacao != null) gestorLocalizacao.parar();
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
        if (mapaTomTom != null) leituraDeAlertas.iniciar();
    }

    // Ao voltar ao ecrã, pede novamente o GPS se houver permissão.
    @Override
    protected void onResume() {
        super.onResume();
        if (vistaDoMapa != null) vistaDoMapa.onResume();
        if (mapaTomTom != null && temPermissaoDeLocalizacao()) {
            gestorLocalizacao.iniciar(this::mostrarLocalizacaoDoUtilizador);
        }
    }

    // Deixa de receber GPS quando o ecrã perde o foco.
    @Override
    protected void onPause() {
        if (gestorLocalizacao != null) gestorLocalizacao.parar();
        if (vistaDoMapa != null) vistaDoMapa.onPause();
        super.onPause();
    }

    // Avisa o mapa de que o ecrã deixou de aparecer.
    @Override
    protected void onStop() {
        // Quando o ecrã fica escondido, para os avisos e as consultas dos alertas.
        mapaVisivel = false;
        if (painelAvisos != null) painelAvisos.parar();
        if (leituraDeAlertas != null) leituraDeAlertas.parar();
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
        if (gestorLocalizacao != null) gestorLocalizacao.parar();
        if (leituraDeAlertas != null) leituraDeAlertas.parar();
        if (dialogoDeAlerta != null) dialogoDeAlerta.dismiss();
        if (painelDetalhesAlerta != null) painelDetalhesAlerta.fechar();
        if (painelAvisos != null) painelAvisos.parar();
        if (apiAlertas != null) apiAlertas.fechar();
        if (pesquisaDoMapa != null) pesquisaDoMapa.cancelarPesquisaPendente();
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
        viagem.definirMapa(map);
        // Mostra os nomes do mapa em português.
        mapaTomTom.setLanguage(Locale.forLanguageTag("pt-PT"));
        // Quando o mapa roda, a seta continua a apontar para norte.
        mapaTomTom.addCameraChangeListener(() -> runOnUiThread(() -> {
            if (mapaTomTom == null) return;
            double rotacao = mapaTomTom.getCameraPosition().getRotation();
            interfaceMapa.compassNeedle.setRotation((float) -rotacao);
        }));
        layoutDoMapa.atualizarPosicaoDaBussola();

        if (zonasFavoritas.temZonas()) {
            // Evita que a primeira posição GPS afaste o mapa da zona escolhida.
            mapaCentradoNoUtilizador = true;
            zonasFavoritas.mostrar(this, mapaTomTom);
        } else {
            // Mostra o ISEP enquanto não há posição GPS.
            GeoPoint isep = new GeoPoint(41.1779, -8.6080);
            mapaTomTom.moveCamera(new CameraOptions(isep, 14.0, 0.0, 0.0, 45.0));
        }

        // Verifica se o pino tocado é um alerta.
        mapaTomTom.addMarkerClickListener(marcador -> {
            // AÇÃO DO UTILIZADOR — Tocar num pino do mapa
            String tag = marcador.getTag();
            if (tag == null || !tag.startsWith("alerta:")) return;
            AlertaDTO alerta = alertasRecebidos.get(tag.substring("alerta:".length()));
            if (alerta != null) {
                boolean eAutor = utilizadorLogado.equals(alerta.getUtilizadorId());
                painelDetalhesAlerta.mostrar(alerta, eAutor, () -> eliminarAlerta(alerta));
            }
        });
        // Com o mapa pronto, começa a procurar alertas e pede o GPS.
        if (mapaVisivel) leituraDeAlertas.iniciar();

        pedirLocalizacaoDoUtilizador();
    }

    // ===== AVISOS EM VIAGEM E CONFIRMAÇÃO DOS PERIGOS =====
    // Avisa até 500 m antes do perigo e pede confirmação ao passar por ele.
    private void verificarAvisosDeAlertas() {
        if (painelAvisos == null || !mapaVisivel) return;
        List<PainelAvisos.Aviso> avisos = new ArrayList<>();
        if (viagem.estaAtiva() && obterPosicaoParaAlerta() != null) {
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
                if (!marcadoresDoMapa.temAlerta(alerta.getId())) continue;
                double distancia = VerificadorDeAlertas.distanciaAteAlerta(viagem.pontos(),
                        posicaoAtual.getLatitude(), posicaoAtual.getLongitude(),
                        alerta.getLatitude(), alerta.getLongitude());
                // O autor não pode votar. As outras contas só podem votar uma vez.
                // Pede confirmação ao passar por um perigo que já mostrou num aviso.
                boolean pedirConfirmacao = !utilizadorLogado.equals(alerta.getUtilizadorId())
                        && !alerta.jaConfirmou(utilizadorLogado)
                        && (painelAvisos.estaAConfirmar(alerta.getId())
                        || painelAvisos.foiAvisado(alerta.getId())
                            && VerificadorDeAlertas.estaAPassarPeloAlerta(viagem.pontos(),
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
                        leituraDeAlertas.carregar();
                    }
                    @Override public void aoFalhar(AlertasApi.Erro erro) {
                        resposta.aoFalhar(erro);
                    }
                });
    }

    private void receberAlertas(List<AlertaDTO> alertas) {
        if (isFinishing() || isDestroyed()) return;
        alertasRecebidos.clear();
        for (AlertaDTO alerta : alertas) alertasRecebidos.put(alerta.getId(), alerta);
        viagem.manterAlertasConsiderados(alertasRecebidos.keySet());
        atualizarAlertasNoMapa();
    }

    private void atualizarAlertasNoMapa() {
        if (mapaTomTom == null) return;
        marcadoresDoMapa.atualizar(mapaTomTom, alertasRecebidos, posicaoAtual,
                viagem.estaAtiva(), zonasFavoritas);
        viagem.verificarDesvioPorAlertaGrave();
        verificarAvisosDeAlertas();
    }

    // ===== BOTÃO DE REPORTE E CRIAÇÃO DE ALERTAS =====
    // Verifica se há uma posição GPS recente para usar.
    private GeoPoint obterPosicaoParaAlerta() {
        // Não permite reportar se o GPS estiver desligado ou sem posição.
        if (!temPermissaoDeLocalizacao() || ultimaLocalizacaoGPS == null
                || !gestorLocalizacao.gpsAtivo()) return null;
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
        pesquisaDoMapa.esconderTeclado();
        interfaceMapa.searchSuggestions.setVisibility(View.GONE);
        View formulario = getLayoutInflater().inflate(
                R.layout.dialog_criar_alerta, interfaceMapa.getRoot(), false);
        BottomSheetDialog dialogo = new BottomSheetDialog(this);
        dialogo.setContentView(formulario);
        dialogoDeAlerta = dialogo;
        new PainelCriarAlerta(dialogo, formulario, this::guardarAlerta);
        PainelInferior.mostrar(dialogo, formulario);
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
                leituraDeAlertas.carregar();
            }
            @Override public void aoFalhar(AlertasApi.Erro erro) {
                if (isFinishing() || isDestroyed()) return;
                painel.mostrarErro(getString(R.string.alert_post_uncertain,
                        getString(mensagemErroDeAlertas(erro))));
                leituraDeAlertas.carregar();
            }
        });
    }

    // ===== APRESENTAÇÃO DOS PAINÉIS E MENSAGENS =====
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

    private void eliminarAlerta(AlertaDTO alerta) {
        if (!utilizadorLogado.equals(alerta.getUtilizadorId())) return;
        apiAlertas.eliminar(alerta.getId(), new AlertasApi.Resposta<>() {
            @Override public void aoConcluir(Boolean resultado) {
                if (isFinishing() || isDestroyed()) return;
                painelDetalhesAlerta.fechar();
                // Remove o alerta apagado da lista e do mapa.
                alertasRecebidos.remove(alerta.getId());
                atualizarAlertasNoMapa();
                leituraDeAlertas.carregar();
                Toast.makeText(MapActivity.this, R.string.alert_delete_done, Toast.LENGTH_SHORT).show();
            }

            @Override public void aoFalhar(AlertasApi.Erro erro) {
                if (isFinishing() || isDestroyed()) return;
                Toast.makeText(MapActivity.this, R.string.alert_delete_error, Toast.LENGTH_LONG).show();
            }
        });
    }

    // ===== PERMISSÃO DE LOCALIZAÇÃO E ATUALIZAÇÕES GPS =====
    // Se houver permissão, usa a localização. Se não, pede permissão.
    private void pedirLocalizacaoDoUtilizador() {
        if (temPermissaoDeLocalizacao()) {
            gestorLocalizacao.iniciar(this::mostrarLocalizacaoDoUtilizador);
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
            gestorLocalizacao.iniciar(this::mostrarLocalizacaoDoUtilizador);
        } else {
            Toast.makeText(this, R.string.location_permission_hint, Toast.LENGTH_LONG).show();
        }
    }

    // Mostra a posição GPS e atualiza o pino do utilizador.
    private void mostrarLocalizacaoDoUtilizador(@NonNull Location location) {
        if (mapaTomTom == null) return;

        // Guarda a posição anterior e a nova para saber se passou pelo perigo.
        localizacaoGPSAnterior = ultimaLocalizacaoGPS;
        ultimaLocalizacaoGPS = new Location(location);
        posicaoAtual = new GeoPoint(location.getLatitude(), location.getLongitude());
        viagem.guardarPosicao(ultimaLocalizacaoGPS, posicaoAtual);
        if (!mapaCentradoNoUtilizador) {
            // Na primeira posição GPS, centra o mapa no utilizador.
            CameraOptions opcoesDaCamara = new CameraOptions(posicaoAtual, 16.0, 0.0, 0.0, 45.0);
            mapaTomTom.moveCamera(opcoesDaCamara);
            mapaCentradoNoUtilizador = true;
        }

        marcadoresDoMapa.mostrarLocalizacao(mapaTomTom, posicaoAtual);
        atualizarAlertasNoMapa();
        viagem.acompanharPosicao(location);
    }
}
