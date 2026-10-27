package com.example.roadguard;

import android.content.Context;
import android.graphics.Bitmap;
import android.location.Location;
import android.os.Handler;
import android.os.SystemClock;
import android.view.View;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.tomtom.sdk.location.GeoPoint;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;

/** Simula apenas dados em memória. Não envia alertas nem altera a Firebase. */
@RunWith(AndroidJUnit4.class)
public class AvisosNaRotaTest {
    private static final double LATITUDE = 41.178;
    private static final double LONGITUDE = -8.608;

    @Test public void avisoSoNaViagemFicaAcimaDoResumoESemRepeticoes() throws Exception {
        Context contexto = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertFalse("É necessária uma sessão local já iniciada para testar o mapa", contexto
                .getSharedPreferences("RoadGuardPrefs", Context.MODE_PRIVATE)
                .getString("UID_LOGADO", "").isEmpty());
        try (ActivityScenario<MapActivity> cenario = ActivityScenario.launch(MapActivity.class)) {
            AtomicBoolean pronto = new AtomicBoolean();
            long limite = SystemClock.elapsedRealtime() + 60000;
            while (!pronto.get() && SystemClock.elapsedRealtime() < limite) {
                cenario.onActivity(a -> pronto.set(ler(a, "mapaTomTom") != null));
                Thread.sleep(500);
            }
            assertTrue("O mapa não ficou pronto", pronto.get());
            // Aguarda o GET inicial antes de injetar os dados locais do teste.
            cenario.onActivity(a -> ((Handler) ler(a, "atualizacaoAlertas")).removeCallbacksAndMessages(null));
            AtomicBoolean leituraTerminada = new AtomicBoolean();
            limite = SystemClock.elapsedRealtime() + 40000;
            while (!leituraTerminada.get() && SystemClock.elapsedRealtime() < limite) {
                cenario.onActivity(a -> leituraTerminada.set(!(Boolean) ler(a, "aLerAlertas")));
                Thread.sleep(250);
            }
            assertTrue("A leitura inicial não terminou", leituraTerminada.get());
            cenario.onActivity(atividade -> {
                ((Handler) ler(atividade, "atualizacaoAlertas")).removeCallbacksAndMessages(null);
                chamar(atividade, "pararAtualizacoesDaLocalizacao");
                atualizarGps(atividade, LATITUDE);
                AlertaDTO alerta = new AlertaDTO();
                alerta.setId("teste_apenas_em_memoria");
                alerta.setUtilizadorId("teste");
                alerta.setCategoriaId("buraco");
                alerta.setGravidade("media");
                alerta.setAtivo(true);
                alerta.setLatitude(LATITUDE + 0.004);
                alerta.setLongitude(LONGITUDE);
                alerta.setDataCriacao(System.currentTimeMillis() - 120000);
                Map<String, AlertaDTO> alertas = (Map<String, AlertaDTO>) ler(atividade, "alertasRecebidos");
                alertas.clear();
                alertas.put(alerta.getId(), alerta);
                AlertaDTO distante = new AlertaDTO();
                distante.setId("teste_zona_distante");
                distante.setLatitude(LATITUDE + 0.03);
                distante.setLongitude(LONGITUDE);
                distante.setAtivo(true);
                distante.setCategoriaId("obras");
                distante.setGravidade("media");
                distante.setDataCriacao(System.currentTimeMillis());
                alertas.put(distante.getId(), distante);
                java.util.List<ZonaFavorita> zonas = (java.util.List<ZonaFavorita>)
                        ler(atividade, "zonasExibidasNoMapa");
                zonas.clear();
                zonas.add(new ZonaFavorita("teste", "teste", "Zona de teste", "Local",
                        LATITUDE + 0.03, LONGITUDE, 1));
                escrever(atividade, "circuloZonaFavoritaVisivel", true);
                chamar(atividade, "atualizarAlertasNoMapa");
                assertEquals(2, ((Map<?, ?>) ler(atividade, "marcadoresDeAlertas")).size());
                escrever(atividade, "circuloZonaFavoritaVisivel", false);
                chamar(atividade, "atualizarAlertasNoMapa");
                assertEquals(1, ((Map<?, ?>) ler(atividade, "marcadoresDeAlertas")).size());
                atualizarGps(atividade, LATITUDE + 0.03);
                assertFalse(((Map<?, ?>) ler(atividade, "marcadoresDeAlertas")).containsKey(alerta.getId()));
                assertTrue(((Map<?, ?>) ler(atividade, "marcadoresDeAlertas")).containsKey(distante.getId()));
                atualizarGps(atividade, LATITUDE);
                escrever(atividade, "circuloZonaFavoritaVisivel", true);
                chamar(atividade, "atualizarAlertasNoMapa");
                TomTomApi.Rota rota = new TomTomApi.Rota();
                rota.pontos.add(new GeoPoint(LATITUDE, LONGITUDE));
                rota.pontos.add(new GeoPoint(LATITUDE + 0.02, LONGITUDE));
                rota.distanciaDeCadaPonto.add(0.0);
                rota.distanciaDeCadaPonto.add(2224.0);
                rota.distanciaMetros = 2224;
                rota.duracaoSegundos = 300;
                escrever(atividade, "rotaPlaneada", rota);
                atividade.findViewById(R.id.route_panel).setVisibility(View.VISIBLE);
                ((TextView) atividade.findViewById(R.id.route_destination)).setText("Destino de teste");
                chamar(atividade, "mostrarResumoDaRota");
                chamar(atividade, "atualizarAlertasNoMapa");
                assertEquals(View.GONE, atividade.findViewById(R.id.painel_aviso).getVisibility());
                chamar(atividade, "iniciarViagem");
                assertFalse(((Map<?, ?>) ler(atividade, "marcadoresDeAlertas")).containsKey(distante.getId()));
                assertTrue("O alerta próximo deve continuar com um marcador",
                        ((Map<?, ?>) ler(atividade, "marcadoresDeAlertas")).containsKey(alerta.getId()));
                try {
                    Method gps = MapActivity.class.getDeclaredMethod("obterPosicaoParaAlerta");
                    gps.setAccessible(true);
                    assertNotNull("É necessária uma posição GPS recente", gps.invoke(atividade));
                } catch (ReflectiveOperationException erro) { throw new AssertionError(erro); }
                assertEquals(445, VerificadorDeAlertas.distanciaAteAlerta(
                        (java.util.List<double[]>) ler(atividade, "pontosDaViagem"), LATITUDE, LONGITUDE,
                        alerta.getLatitude(), alerta.getLongitude()), 2);
                PainelAvisos painel = (PainelAvisos) ler(atividade, "painelAvisos");
                try {
                    Field ativo = PainelAvisos.class.getDeclaredField("ativo");
                    ativo.setAccessible(true);
                    assertTrue("O painel deve estar ativo", ativo.getBoolean(painel));
                } catch (ReflectiveOperationException erro) { throw new AssertionError(erro); }
                assertEquals(View.VISIBLE, atividade.findViewById(R.id.painel_aviso).getVisibility());
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            Thread.sleep(1000); // Aguarda o posicionamento dos elementos depois do layout.
            cenario.onActivity(atividade -> {
                View aviso = atividade.findViewById(R.id.painel_aviso);
                assertTrue(((TextView) atividade.findViewById(R.id.aviso_contexto))
                        .getText().toString().contains("440 m"));
                View resumo = atividade.findViewById(R.id.route_panel);
                int[] posicaoAviso = new int[2];
                int[] posicaoResumo = new int[2];
                aviso.getLocationOnScreen(posicaoAviso);
                resumo.getLocationOnScreen(posicaoResumo);
                assertTrue("O aviso deve ficar acima do destino e do tempo",
                        posicaoAviso[1] + aviso.getHeight() <= posicaoResumo[1]);
                assertTrue("O aviso deve ser uma caixa pequena",
                        aviso.getHeight() < atividade.getResources().getDisplayMetrics().heightPixels / 3);
            });
            Bitmap imagem = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
            if (imagem != null) {
                try (FileOutputStream ficheiro = new FileOutputStream(
                        new File(contexto.getExternalFilesDir(null), "aviso-rota-teste.png"))) {
                    imagem.compress(Bitmap.CompressFormat.PNG, 100, ficheiro);
                }
                imagem.recycle();
            }
            cenario.onActivity(atividade -> {
                atividade.findViewById(R.id.aviso_fechar).performClick();
                chamar(atividade, "atualizarAlertasNoMapa");
                assertEquals("Um aviso fechado não deve reaparecer na mesma viagem", View.GONE,
                        atividade.findViewById(R.id.painel_aviso).getVisibility());
                // Passa 50 m depois do perigo. Não toca nas respostas: o teste não escreve na Firebase.
                atualizarGps(atividade, LATITUDE + 0.00445);
                assertEquals(View.VISIBLE, atividade.findViewById(R.id.painel_aviso).getVisibility());
                assertEquals(View.VISIBLE, atividade.findViewById(R.id.aviso_confirmacoes).getVisibility());
                assertTrue(((TextView) atividade.findViewById(R.id.aviso_contexto))
                        .getText().toString().contains("ainda existe"));
                atividade.findViewById(R.id.aviso_fechar).performClick();
                chamar(atividade, "atualizarAlertasNoMapa");
                assertEquals("Fechar a pergunta não deve fazê-la reaparecer na mesma passagem", View.GONE,
                        atividade.findViewById(R.id.painel_aviso).getVisibility());
                chamar(atividade, "terminarViagem");
                assertTrue(((Map<?, ?>) ler(atividade, "marcadoresDeAlertas")).containsKey("teste_zona_distante"));
                assertEquals(View.GONE, atividade.findViewById(R.id.painel_aviso).getVisibility());
            });
        }
    }

    private void atualizarGps(MapActivity atividade, double latitude) {
        Location localizacao = new Location("gps");
        localizacao.setLatitude(latitude);
        localizacao.setLongitude(LONGITUDE);
        localizacao.setTime(System.currentTimeMillis());
        localizacao.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
        try {
            Method metodo = MapActivity.class.getDeclaredMethod("mostrarLocalizacaoDoUtilizador", Location.class);
            metodo.setAccessible(true);
            metodo.invoke(atividade, localizacao);
        } catch (Exception erro) { throw new AssertionError(erro); }
    }

    private static Object ler(Object objeto, String nome) {
        try { Field campo = MapActivity.class.getDeclaredField(nome); campo.setAccessible(true); return campo.get(objeto); }
        catch (Exception erro) { throw new AssertionError(erro); }
    }
    private static void escrever(Object objeto, String nome, Object valor) {
        try { Field campo = MapActivity.class.getDeclaredField(nome); campo.setAccessible(true); campo.set(objeto, valor); }
        catch (Exception erro) { throw new AssertionError(erro); }
    }
    private static void chamar(Object objeto, String nome) {
        try { Method metodo = MapActivity.class.getDeclaredMethod(nome); metodo.setAccessible(true); metodo.invoke(objeto); }
        catch (Exception erro) { throw new AssertionError(erro); }
    }
}
