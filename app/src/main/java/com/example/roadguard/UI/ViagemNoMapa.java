package com.example.roadguard.UI;

import android.app.Activity;
import android.location.Location;
import android.os.SystemClock;
import android.view.View;
import android.widget.Toast;

import com.example.roadguard.API.TomTomApi;
import com.example.roadguard.DTO.AlertaDTO;
import com.example.roadguard.R;
import com.example.roadguard.Service.GestorViagem;
import com.example.roadguard.Service.VerificadorDeAlertas;
import com.example.roadguard.databinding.ActivityMapBinding;
import com.tomtom.sdk.location.GeoPoint;
import com.tomtom.sdk.map.display.TomTomMap;
import com.tomtom.sdk.map.display.camera.CameraOptions;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Coordena o planeamento da rota e o acompanhamento da viagem no ecrã. */
final class ViagemNoMapa {
    private final Activity atividade;
    private final ActivityMapBinding interfaceMapa;
    private final TomTomApi apiTomTom;
    private final LayoutDoMapa layoutDoMapa;
    private final PesquisaDoMapa pesquisaDoMapa;
    private final PainelAvisos painelAvisos;
    private final Collection<AlertaDTO> alertasRecebidos;
    private final Runnable aoMudarViagem;
    private final GestorViagem gestorViagem = new GestorViagem();
    private final DesenhoDaRota desenhoDaRota = new DesenhoDaRota();
    private final List<double[]> pontosDaViagem = new ArrayList<>();
    private final Set<String> alertasGravesConsiderados = new HashSet<>();

    private TomTomMap mapaTomTom;
    private GeoPoint posicaoAtual;
    private Location ultimaLocalizacaoGPS;
    private TomTomApi.Local destinoSelecionado;
    private TomTomApi.Rota rotaPlaneada;
    private boolean viagemIniciada;
    private boolean seguirLocalNaViagem = true;
    private boolean aRecalcularRota;
    private long ultimaRequisicaoDeRota;

    ViagemNoMapa(Activity atividade, ActivityMapBinding interfaceMapa, TomTomApi apiTomTom,
                 LayoutDoMapa layoutDoMapa, PesquisaDoMapa pesquisaDoMapa, PainelAvisos painelAvisos,
                 Collection<AlertaDTO> alertasRecebidos, Runnable aoMudarViagem) {
        this.atividade = atividade;
        this.interfaceMapa = interfaceMapa;
        this.apiTomTom = apiTomTom;
        this.layoutDoMapa = layoutDoMapa;
        this.pesquisaDoMapa = pesquisaDoMapa;
        this.painelAvisos = painelAvisos;
        this.alertasRecebidos = alertasRecebidos;
        this.aoMudarViagem = aoMudarViagem;
        configurarBotoes();
    }

    void definirMapa(TomTomMap mapa) {
        mapaTomTom = mapa;
    }

    void guardarPosicao(Location localizacao, GeoPoint ponto) {
        ultimaLocalizacaoGPS = localizacao;
        posicaoAtual = ponto;
    }

    boolean estaAtiva() {
        return viagemIniciada;
    }

    void seguirUtilizador(boolean seguir) {
        seguirLocalNaViagem = seguir;
    }

    List<double[]> pontos() {
        return pontosDaViagem;
    }

    void manterAlertasConsiderados(Set<String> idsRecebidos) {
        alertasGravesConsiderados.retainAll(idsRecebidos);
    }

    void acompanharPosicao(Location location) {
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

    private void configurarBotoes() {
        interfaceMapa.btnStartTrip.setOnClickListener(view -> {
            // AÇÃO DO UTILIZADOR — Iniciar viagem
            iniciarViagem();
        });
        interfaceMapa.btnEndTrip.setOnClickListener(view -> terminarViagem());
        interfaceMapa.btnDismissRoute.setOnClickListener(view -> cancelarRotaPlaneada());
    }

    void selecionarDestino(TomTomApi.Local local) {
        destinoSelecionado = local;
        pedirRota();
    }

    // ===== PLANEAMENTO E DESENHO DA ROTA =====
    // Pede uma rota de carro da posição atual até ao destino.
    private void pedirRota() {
        if (posicaoAtual == null || destinoSelecionado == null) {
            Toast.makeText(atividade, R.string.route_needs_location, Toast.LENGTH_SHORT).show();
            return;
        }
        interfaceMapa.routePanel.setVisibility(View.VISIBLE);
        layoutDoMapa.atualizarPosicaoDoBotaoAlerta();
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
                desenhoDaRota.desenhar(mapaTomTom, rotaPlaneada);
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
                layoutDoMapa.atualizarPosicaoDoBotaoAlerta();
                Toast.makeText(atividade, R.string.route_error, Toast.LENGTH_LONG).show();
            }
        });
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
                atividade.getString(R.string.route_summary_format, distancia, minutos));
    }

    void limparRotaPlaneada() {
        destinoSelecionado = null;
        rotaPlaneada = null;
        desenhoDaRota.apagar(mapaTomTom);
        interfaceMapa.routePanel.setVisibility(View.GONE);
    }

    // Cancela a rota planeada e volta à pesquisa.
    private void cancelarRotaPlaneada() {
        limparRotaPlaneada();
        interfaceMapa.navigationBanner.setVisibility(View.GONE);
        interfaceMapa.mapSearchBar.setVisibility(View.VISIBLE);
        pesquisaDoMapa.reporCampo();
        layoutDoMapa.atualizarPosicaoDoBotaoAlerta();
        layoutDoMapa.atualizarPosicaoDoBotaoCentralizar();
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
        atualizarPontosDaViagem(rotaPlaneada);
        // Limpa os avisos da viagem anterior e mostra os botões da nova viagem.
        painelAvisos.reiniciarViagem();
        interfaceMapa.mapSearchBar.setVisibility(View.GONE);
        interfaceMapa.searchSuggestions.setVisibility(View.GONE);
        interfaceMapa.navigationBanner.setVisibility(View.VISIBLE);
        interfaceMapa.btnDismissRoute.setVisibility(View.GONE);
        interfaceMapa.btnStartTrip.setVisibility(View.GONE);
        interfaceMapa.btnEndTrip.setVisibility(View.VISIBLE);
        interfaceMapa.routePanel.post(layoutDoMapa::atualizarPosicaoDoBotaoCentralizar);
        if (mapaTomTom != null && posicaoAtual != null) {
            mapaTomTom.moveCamera(new CameraOptions(posicaoAtual, 16.0, 0.0, 0.0, 45.0));
        }
        String indicacao = atividade.getString(R.string.route_trip_started);
        for (TomTomApi.Instrucao instrucao : rotaPlaneada.instrucoes) {
            if (instrucao.distanciaDesdeInicio > 0) {
                String distancia;
                if (instrucao.distanciaDesdeInicio >= 1000) {
                    distancia = String.format(Locale.getDefault(), "%.1f km", instrucao.distanciaDesdeInicio / 1000.0);
                } else {
                    distancia = instrucao.distanciaDesdeInicio + " m";
                }
                indicacao += "\n" + atividade.getString(R.string.route_maneuver_format, distancia, instrucao.mensagem);
                break;
            }
        }

        interfaceMapa.navigationInstruction.setText(indicacao);
        layoutDoMapa.atualizarPosicaoDoAviso();
        aoMudarViagem.run();
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
        desenhoDaRota.mostrarPercursoRestante(rotaPlaneada, progresso.indice);
        interfaceMapa.routeSummary.setText(atividade.getString(R.string.route_remaining_format,
                formatarDistancia(progresso.metrosRestantes), String.valueOf(progresso.minutosRestantes)));
        if (progresso.proximaInstrucao != null) {
            interfaceMapa.navigationInstruction.setText(atividade.getString(R.string.route_maneuver_format,
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
                desenhoDaRota.desenhar(mapaTomTom, rotaPlaneada);
                Location posicaoMaisRecente = ultimaLocalizacaoGPS;
                if (posicaoMaisRecente == null) posicaoMaisRecente = localizacao;
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
    void verificarDesvioPorAlertaGrave() {
        if (!viagemIniciada || aRecalcularRota || ultimaLocalizacaoGPS == null
                || pontosDaViagem.size() < 2) return;

        List<AlertaDTO> novosAlertasGraves = new ArrayList<>();
        // Procura perigos graves que ainda não tentou evitar.
        for (AlertaDTO alerta : alertasRecebidos) {
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
        for (AlertaDTO alerta : alertasRecebidos) {
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
        aoMudarViagem.run();
        desenhoDaRota.apagar(mapaTomTom);
        rotaPlaneada = null;
        destinoSelecionado = null;
        interfaceMapa.routePanel.setVisibility(View.GONE);
        layoutDoMapa.atualizarPosicaoDoBotaoAlerta();
        interfaceMapa.btnStartTrip.setVisibility(View.VISIBLE);
        interfaceMapa.btnEndTrip.setVisibility(View.GONE);
        interfaceMapa.btnDismissRoute.setVisibility(View.VISIBLE);
        interfaceMapa.mapSearchBar.setVisibility(View.VISIBLE);
        interfaceMapa.navigationBanner.setVisibility(View.GONE);
        layoutDoMapa.atualizarPosicaoDoBotaoCentralizar();
    }
}
