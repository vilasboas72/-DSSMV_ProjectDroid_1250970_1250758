package com.example.roadguard.Service;

import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Looper;
import android.widget.Toast;

import com.example.roadguard.R;

/** Recebe posições do GPS enquanto o ecrã do mapa está aberto. */
public final class GestorLocalizacao {
    private final Context contexto;
    private final LocationManager gestorDeLocalizacao;
    private LocationListener ouvinteDeLocalizacao;

    public GestorLocalizacao(Context contexto) {
        this.contexto = contexto;
        gestorDeLocalizacao = (LocationManager) contexto.getSystemService(Context.LOCATION_SERVICE);
    }

    public boolean gpsAtivo() {
        return gestorDeLocalizacao != null
                && gestorDeLocalizacao.isProviderEnabled(LocationManager.GPS_PROVIDER);
    }

    // Começa a receber posições pelo GPS do telemóvel.
    public void iniciar(LocationListener aoReceberPosicao) {
        // Se o GPS já está a enviar posições, não o configura outra vez.
        if (ouvinteDeLocalizacao != null) return;

        if (gestorDeLocalizacao == null) {
            Toast.makeText(contexto, R.string.location_access_error, Toast.LENGTH_LONG).show();
            return;
        }

        try {
            if (!gpsAtivo()) {
                Toast.makeText(contexto, R.string.gps_disabled, Toast.LENGTH_LONG).show();
                return;
            }

            // Mostra a última posição guardada enquanto espera por uma nova.
            // Antes de criar um alerta, confirma se a posição não é antiga.
            Location lastKnownLocation = gestorDeLocalizacao.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (lastKnownLocation != null) aoReceberPosicao.onLocationChanged(lastKnownLocation);

            // Quando chega uma posição GPS, chama mostrarLocalizacaoDoUtilizador.
            // Pede posições com pelo menos 1 segundo de intervalo, mesmo sem movimento.
            gestorDeLocalizacao.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1000L,
                    0.0f,
                    aoReceberPosicao,
                    Looper.getMainLooper());
            ouvinteDeLocalizacao = aoReceberPosicao;
        } catch (SecurityException e) {
            Toast.makeText(contexto, R.string.location_permission_denied, Toast.LENGTH_LONG).show();
        }
    }

    // Deixa de receber posições GPS para não gastar bateria.
    public void parar() {
        if (gestorDeLocalizacao != null && ouvinteDeLocalizacao != null) {
            gestorDeLocalizacao.removeUpdates(ouvinteDeLocalizacao);
            ouvinteDeLocalizacao = null;
        }
    }
}
