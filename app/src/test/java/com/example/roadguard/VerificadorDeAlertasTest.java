package com.example.roadguard;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

public class VerificadorDeAlertasTest {
    private static final double METRO = 180.0 / (Math.PI * 6371000);
    private List<double[]> reta() {
        return Arrays.asList(new double[]{0, 0}, new double[]{0, 3000 * METRO});
    }
    @Test public void avisaPerigoAFrenteDentroDe500Metros() {
        assertEquals(400, VerificadorDeAlertas.distanciaAteAlerta(
                reta(), 0, 100 * METRO, 0, 500 * METRO), 1);
    }
    @Test public void naoAvisaAntesDeEntrarNoLimite() {
        assertEquals(-1, VerificadorDeAlertas.distanciaAteAlerta(
                reta(), 0, 100 * METRO, 0, 700 * METRO), 0);
    }
    @Test public void naoAvisaPerigoJaUltrapassado() {
        assertEquals(-1, VerificadorDeAlertas.distanciaAteAlerta(
                reta(), 0, 1000 * METRO, 0, 900 * METRO), 0);
    }
    @Test public void ignoraPerigoNumaRuaAoLado() {
        assertEquals(-1, VerificadorDeAlertas.distanciaAteAlerta(
                reta(), 0, 100 * METRO, 80 * METRO, 900 * METRO), 0);
    }
    @Test public void naoAvisaSeUtilizadorSaiuDaRota() {
        assertEquals(-1, VerificadorDeAlertas.distanciaAteAlerta(
                reta(), 100 * METRO, 100 * METRO, 0, 900 * METRO), 0);
    }
    @Test public void medePeloPercursoENaoPelaLinhaReta() {
        List<double[]> curva = Arrays.asList(new double[]{0,0},
                new double[]{0,800 * METRO}, new double[]{800 * METRO,800 * METRO},
                new double[]{800 * METRO,0});
        assertEquals(-1, VerificadorDeAlertas.distanciaAteAlerta(
                curva, 0, 0, 800 * METRO, 0), 0);
    }
    @Test public void aceitaSegmentosRepetidosSemDividirPorZero() {
        List<double[]> rota = Arrays.asList(new double[]{0,0}, new double[]{0,0},
                new double[]{0,1000 * METRO});
        assertEquals(500, VerificadorDeAlertas.distanciaAteAlerta(
                rota, 0, 0, 0, 500 * METRO), 1);
    }
    @Test public void semRotaNaoHaAvisosDeRota() {
        assertEquals(-1, VerificadorDeAlertas.distanciaAteAlerta(
                Collections.emptyList(), 0, 0, 0, 0), 0);
    }
    @Test public void medeRaioDasZonasEmMetros() {
        assertEquals(1000, VerificadorDeAlertas.medirDistancia(0, 0, 1000 * METRO, 0), 0.1);
    }

    private AlertaDTO alertaA(double metros) {
        AlertaDTO alerta = new AlertaDTO();
        alerta.setLatitude(0);
        alerta.setLongitude(metros * METRO);
        alerta.setAtivo(true);
        return alerta;
    }
    private List<ZonaFavorita> zonaDistante() {
        return Collections.singletonList(new ZonaFavorita("zona", "utilizador", "Casa", "Local",
                0, 10000 * METRO, 1));
    }
    private boolean mostrar(double distancia, boolean viagem, boolean zonaVisivel) {
        return VerificadorDeAlertas.deveMostrarNoMapa(alertaA(distancia), 0.0, 0.0,
                viagem, zonaVisivel, zonaDistante());
    }
    @Test public void mostraMarcadorDentroDeDoisQuilometros() {
        assertTrue(mostrar(1999, false, false));
        assertTrue(mostrar(2000, true, false));
        assertFalse(mostrar(2001, false, false));
    }
    @Test public void zonaVisivelMostraAlertaMesmoLongeDoUtilizador() {
        assertTrue(mostrar(10500, false, true));
        assertFalse(mostrar(11500, false, true));
    }
    @Test public void ocultarZonaRemoveExcecaoAoRaioDoUtilizador() {
        assertFalse(mostrar(10500, false, false));
        assertTrue(mostrar(500, false, false));
    }
    @Test public void emViagemIgnoraExcecaoDasZonasFavoritas() {
        assertFalse(mostrar(10500, true, true));
        assertTrue(mostrar(500, true, true));
    }
    @Test public void semGpsSoMostraAlertasDasZonasVisiveisForaDaViagem() {
        assertTrue(VerificadorDeAlertas.deveMostrarNoMapa(alertaA(10500), null, null,
                false, true, zonaDistante()));
        assertFalse(VerificadorDeAlertas.deveMostrarNoMapa(alertaA(500), null, null,
                false, false, zonaDistante()));
        assertFalse(VerificadorDeAlertas.deveMostrarNoMapa(alertaA(10500), null, null,
                true, true, zonaDistante()));
    }
    @Test public void aceitaQualquerUmaDasZonasSelecionadas() {
        List<ZonaFavorita> zonas = Arrays.asList(zonaDistante().get(0),
                new ZonaFavorita("outra", "utilizador", "Trabalho", "Local", 0, 20000 * METRO, 2));
        assertTrue(VerificadorDeAlertas.deveMostrarNoMapa(alertaA(21500), 0.0, 0.0,
                false, true, zonas));
    }
    @Test public void alertaInativoNuncaEVisivel() {
        AlertaDTO alerta = alertaA(500);
        alerta.setAtivo(false);
        assertFalse(VerificadorDeAlertas.deveMostrarNoMapa(alerta, 0.0, 0.0,
                false, true, zonaDistante()));
    }
    @Test public void deslocacaoDoGpsAlteraVisibilidadeSemNovaLeitura() {
        AlertaDTO alerta = alertaA(3000);
        assertFalse(VerificadorDeAlertas.deveMostrarNoMapa(alerta, 0.0, 0.0,
                false, false, Collections.emptyList()));
        assertTrue(VerificadorDeAlertas.deveMostrarNoMapa(alerta, 0.0, 1500 * METRO,
                false, false, Collections.emptyList()));
    }
    @Test public void popupSoComecaAoEntrarNos500Metros() {
        assertEquals(-1, VerificadorDeAlertas.distanciaAteAlerta(
                reta(), 0, 0, 0, 600 * METRO), 0);
        assertEquals(400, VerificadorDeAlertas.distanciaAteAlerta(
                reta(), 0, 200 * METRO, 0, 600 * METRO), 1);
    }
}
