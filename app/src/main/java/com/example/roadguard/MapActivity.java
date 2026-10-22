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
import android.graphics.PointF;
import android.view.Gravity;
import android.content.pm.ApplicationInfo;
import android.widget.FrameLayout;
import android.widget.Toast;
import androidx.annotation.NonNull;
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
import com.tomtom.sdk.map.display.map.OnlineCachePolicy;
import com.tomtom.sdk.map.display.style.StyleMode;
import com.tomtom.sdk.map.display.marker.Marker;
import com.tomtom.sdk.map.display.marker.MarkerOptions;
import com.tomtom.sdk.map.display.ui.MapView;
import com.tomtom.sdk.map.display.ui.MapReadyCallback;
import com.tomtom.sdk.location.GeoPoint;
import com.tomtom.sdk.map.display.image.ImageFactory;

// Activity que apresenta o mapa TomTom e a posição atual do utilizador.
public class MapActivity extends FragmentActivity implements MapReadyCallback {

    private static final int REQUEST_LOCATION_PERMISSION = 42;

    private ActivityMapBinding binding;
    private MapView mapView;
    private TomTomMap tomtomMap;
    private Marker currentLocationMarker;
    private LocationManager locationManager;
    private LocationListener locationListener;
    private boolean hasCenteredOnUserLocation;

    /** Cria o ecrã, lê a chave TomTom e prepara o mapa. */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMapBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setupMapMenu();

        String apiKey;
        try {
            ApplicationInfo applicationInfo = getPackageManager().getApplicationInfo(
                    getPackageName(), PackageManager.GET_META_DATA);
            apiKey = applicationInfo.metaData == null
                    ? null
                    : applicationInfo.metaData.getString("com.tomtom.sdk.api.key");
        } catch (PackageManager.NameNotFoundException e) {
            Toast.makeText(this, "Não foi possível carregar a configuração da app.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        if (apiKey == null || apiKey.trim().isEmpty()) {
            Toast.makeText(this, "A chave da TomTom não está configurada.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

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
        binding.mapContainer.addView(mapView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        // O MapView precisa de receber os eventos de ciclo de vida da Activity.
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this);
    }

    // Mantém o botão sobre o mapa e afastado da barra de estado e do recorte do ecrã.
    private void setupMapMenu() {
        int margin = Math.round(16 * getResources().getDisplayMetrics().density);
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (view, windowInsets) -> {
            Insets insets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            FrameLayout.LayoutParams params =
                    (FrameLayout.LayoutParams) binding.btnMapMenu.getLayoutParams();
            params.topMargin = margin + insets.top;
            boolean isRtl = ViewCompat.getLayoutDirection(view) == ViewCompat.LAYOUT_DIRECTION_RTL;
            params.setMarginEnd(margin + (isRtl ? insets.left : insets.right));
            binding.btnMapMenu.setLayoutParams(params);
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
                    logout();
                    return true;
                }
                return false;
            });
            menu.show();
        });
    }

    // Limpa a identificação local e a pilha de ecrãs para não voltar ao mapa após sair.
    private void logout() {
        getSharedPreferences("RoadGuardPrefs", Context.MODE_PRIVATE)
                .edit()
                .remove("UID_LOGADO")
                .remove("EMAIL_LOGADO")
                .apply();
        stopLocationUpdates();
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
        if (tomtomMap != null && hasLocationPermission()) {
            startLocationUpdates();
        }
    }

    // Para as atualizações GPS enquanto a Activity não está em primeiro plano
    @Override
    protected void onPause() {
        stopLocationUpdates();
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
        stopLocationUpdates();
        if (mapView != null) mapView.onDestroy();
        super.onDestroy();
    }

    // É chamado quando o mapa TomTom terminou de carregar.
    @Override
    public void onMapReady(@NonNull TomTomMap map) {
        tomtomMap = map;

        // Centro de reserva enquanto a localização do dispositivo é obtida.
        GeoPoint isep = new GeoPoint(41.1779, -8.6080);
        CameraOptions cameraOptions = new CameraOptions(isep, 14.0, 0.0, 0.0, 45.0);
        tomtomMap.moveCamera(cameraOptions);

        requestUserLocation();
    }

    // Usa a localização se já houver permissão; caso contrário, pede-a ao utilizador.
    private void requestUserLocation() {
        if (hasLocationPermission()) {
            startLocationUpdates();
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
    private boolean hasLocationPermission() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    // Recebe a resposta do utilizador ao pedido de permissão de localização.
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_LOCATION_PERMISSION) return;

        if (hasLocationPermission()) {
            startLocationUpdates();
        } else {
            Toast.makeText(this, "Permite a localização para a mostrar no mapa.", Toast.LENGTH_LONG).show();
        }
    }

    // Usa apenas o GPS para obter a posição e começar as atualizações.
    private void startLocationUpdates() {
        if (locationListener != null) return;

        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (locationManager == null) {
            Toast.makeText(this, "Não foi possível aceder à localização do dispositivo.", Toast.LENGTH_LONG).show();
            return;
        }

        try {
            if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                Toast.makeText(this, "Ativa o GPS do telemóvel e tenta novamente.", Toast.LENGTH_LONG).show();
                return;
            }

            Location lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (lastKnownLocation != null) showUserLocation(lastKnownLocation);

            locationListener = new LocationListener() {
                // Executado sempre que o Android recebe uma nova posição.
                @Override
                public void onLocationChanged(@NonNull Location location) {
                    showUserLocation(location);
                }
            };
            locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1000L,
                    1.0f,
                    locationListener,
                    Looper.getMainLooper());
        } catch (SecurityException e) {
            Toast.makeText(this, "A permissão de localização foi recusada.", Toast.LENGTH_LONG).show();
        }
    }

    // Centra o mapa na primeira posição e move o mesmo marcador nas seguintes.
// Centra o mapa na primeira posição e move o mesmo marcador nas seguintes.
    private void showUserLocation(@NonNull Location location) {
        if (tomtomMap == null) return;

        GeoPoint currentPosition = new GeoPoint(location.getLatitude(), location.getLongitude());
        if (!hasCenteredOnUserLocation) {
            CameraOptions cameraOptions = new CameraOptions(currentPosition, 16.0, 0.0, 0.0, 45.0);
            tomtomMap.moveCamera(cameraOptions);
            hasCenteredOnUserLocation = true;
        }

        if (currentLocationMarker == null) {
            // Usa apenas os parâmetros obrigatórios e um ícone do próprio Android
            MarkerOptions markerOptions = new MarkerOptions(
                    currentPosition,
                    ImageFactory.INSTANCE.fromResource(android.R.drawable.ic_menu_mylocation), // Ícone padrão do Android que garante que compila
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
    }

    // Remove o listener GPS para evitar trabalho e consumo de bateria desnecessários.
    private void stopLocationUpdates() {
        if (locationManager != null && locationListener != null) {
            locationManager.removeUpdates(locationListener);
            locationListener = null;
        }
    }
}
