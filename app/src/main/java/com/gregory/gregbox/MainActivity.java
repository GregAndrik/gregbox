package com.gregory.gregbox;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ContextThemeWrapper;

import android.content.DialogInterface;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.mapbox.android.core.location.LocationEngine;
import com.mapbox.android.core.location.LocationEngineListener;
import com.mapbox.android.core.location.LocationEnginePriority;
import com.mapbox.android.core.location.LocationEngineProvider;
import com.mapbox.android.core.permissions.PermissionsListener;
import com.mapbox.android.core.permissions.PermissionsManager;
import com.mapbox.api.directions.v5.models.DirectionsResponse;
import com.mapbox.api.directions.v5.models.DirectionsRoute;
import com.mapbox.geojson.Point;
import com.mapbox.mapboxsdk.Mapbox;
import com.mapbox.mapboxsdk.annotations.Marker;
import com.mapbox.mapboxsdk.annotations.MarkerOptions;
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback;
import com.mapbox.mapboxsdk.plugins.locationlayer.LocationLayerPlugin;
import com.mapbox.mapboxsdk.plugins.locationlayer.modes.CameraMode;
import com.mapbox.mapboxsdk.plugins.locationlayer.modes.RenderMode;
import com.mapbox.services.android.navigation.ui.v5.NavigationLauncher;
import com.mapbox.services.android.navigation.ui.v5.NavigationLauncherOptions;
import com.mapbox.services.android.navigation.ui.v5.route.NavigationMapRoute;
import com.mapbox.services.android.navigation.v5.navigation.NavigationRoute;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity implements
        OnMapReadyCallback, // Map finished initializing and ready to operate.
        LocationEngineListener,
        PermissionsListener,
        MapboxMap.OnMapClickListener {

    private MapView mapView; // Creating map environment.
    private MapboxMap map; // Method associating with every map interaction.
    private Button startNavigation; // "Start Navigation" button.
    private PermissionsManager permissionsManager;
    private LocationEngine locationEngine; // Used to pinpoint the device's current location.
    private LocationLayerPlugin locationLayerPlugin; // Used to provide the display of the icon representing the user's current location.

    private Location originLocation = null;
    private Point originPosition;
    private Point destinationPosition;
    private Marker destinationMarker;
    private NavigationMapRoute navigationMapRoute; // Using it to draw our route.
    private static final String TAG = MainActivity.class.getSimpleName();

    public FloatingActionButton reCenterFab; // Re-center button.

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Mapbox.getInstance(this, getString(R.string.access_token)); // Providing our secret token.
        setContentView(R.layout.activity_main);
        mapView = findViewById(R.id.mapView);

        startNavigation = findViewById(R.id.startNavigation);

        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this); // Callback object to verify that the map is ready for action.

        startNavigation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                pickNavigationDialog(); // Prompts the user with an options dialog.
            }
        });

        reCenterFab = findViewById(R.id.reCenterFab);
        reCenterFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                originLocation = locationEngine.getLastLocation();
                if (originLocation != null) {
                    setCameraPosition(originLocation);
                } else {
                    Toast.makeText(getApplicationContext(),"Map is not ready", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    @Override
    public void onMapReady(MapboxMap mapboxMap) {
        map = mapboxMap;
        map.addOnMapClickListener(this);
        enableLocation();
    }

    private void enableLocation() {
        // If location is enabled proceeds, otherwise requests permissions again.
        if (PermissionsManager.areLocationPermissionsGranted(this)) {
            initializeLocationEngine();
            initializeLocationLayer();
        } else {
            permissionsManager = new PermissionsManager(this);
            permissionsManager.requestLocationPermissions(this);
        }
    }

    private void initializeLocationEngine() {
        locationEngine = new LocationEngineProvider(this).obtainBestLocationEngineAvailable();
        locationEngine.setPriority(LocationEnginePriority.HIGH_ACCURACY);
        locationEngine.activate();

        Location lastLocation = locationEngine.getLastLocation();
        if(lastLocation != null) {
            originLocation = lastLocation;
            setCameraPosition(lastLocation);
        } else {
            locationEngine.addLocationEngineListener(this);
        }
    }

    private void initializeLocationLayer() {
        locationLayerPlugin = new LocationLayerPlugin(mapView, map, locationEngine);
        locationLayerPlugin.setLocationLayerEnabled(true); // Activates the plugin that displays user location on the map.
        locationLayerPlugin.setCameraMode(CameraMode.TRACKING);
        locationLayerPlugin.setRenderMode(RenderMode.GPS);
    }

    private void setCameraPosition(Location location) {
        // Method called when the camera needs to be moved to the current user location with parameters LAT, LONG and zoom.
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(location.getLatitude(), location.getLongitude()), 14.0));
    }

    @Override
    public void onMapClick(@NonNull LatLng point) {
        if (destinationMarker != null) {
            map.removeMarker(destinationMarker); // Making sure that the old marker is removed before placing the new one.
        }
        destinationMarker = map.addMarker(new MarkerOptions().position(point));
        destinationPosition = Point.fromLngLat(point.getLongitude(), point.getLatitude());

        originPosition = Point.fromLngLat(originLocation.getLongitude(), originLocation.getLatitude());
        getRoute(originPosition, destinationPosition); // Obtaining route after inputting current position and destination into the method.

        startNavigation.setEnabled(true);
        startNavigation.setBackgroundResource(R.color.orange);
    }

    private void getRoute(Point origin, Point destination) {
        NavigationRoute.builder()
                .accessToken(Mapbox.getAccessToken())
                .origin(origin)
                .destination(destination)
                .build()
                .getRoute(new Callback<DirectionsResponse>() {
            @Override
            public void onResponse(Call<DirectionsResponse> call, Response<DirectionsResponse> response) {
                if (response.body() == null) {
                    Log.e(TAG, "Unable to find routes, check user and token.");
                    return;
                } else if (response.body().routes().size() == 0) {
                    // Routes do not exist.
                    Log.e(TAG, "Routes are not registered.");
                    return;
                }

                DirectionsRoute currentRoute = response.body().routes().get(0);

                if (navigationMapRoute != null) {
                    navigationMapRoute.removeRoute(); // Removes old route so a new one can be set when setting a new marker.
                } else {
                    navigationMapRoute = new NavigationMapRoute(null, mapView, map); // Initializing navigationMapRoute with navigation as null.
                }
                navigationMapRoute.addRoute(currentRoute); // Plotting the new route.
            }

            @Override
            public void onFailure(Call<DirectionsResponse> call, Throwable t) {
                Log.e(TAG, "Error" + t.getMessage());
            }
        });
    }

    @Override
    public void onConnected() {
        locationEngine.requestLocationUpdates();
    }

    @Override
    public void onLocationChanged(Location location) {
        if (location != null) {
            originLocation = location;
            setCameraPosition(location);
        }
    }

    @Override
    public void onExplanationNeeded(List<String> permissionsToExplain) {}

    @Override
    public void onPermissionResult(boolean granted) {
        if (granted) {
            enableLocation();
        } else {
            Toast.makeText(this, "Location permission is required in order to function properly.", Toast.LENGTH_SHORT).show();
            finish(); //Kills activity.
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        permissionsManager.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (locationEngine != null) {
            locationEngine.requestLocationUpdates();
        }
        if (locationLayerPlugin != null) {
            locationLayerPlugin.onStart();
        }
        mapView.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapView.onPause();
    }

    @Override
    protected void onStop() {
        // When the app is stopped, locationEngine and locationLayerPlugin should also be stopped.
        super.onStop();
        if (locationEngine != null) {
            locationEngine.removeLocationUpdates();
        }
        if (locationLayerPlugin != null) {
            locationLayerPlugin.onStop();
        }
        mapView.onStop();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapView.onLowMemory();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (locationEngine != null) {
            locationEngine.deactivate();
        }
        mapView.onDestroy();
    }

    public void pickNavigationDialog() {
        new AlertDialog.Builder(new ContextThemeWrapper(this, R.style.myDialog))
                .setTitle("Choose function")
                .setMessage("Would you like to simulate navigation?")

                // Specifying a listener allows you to take an action before dismissing the dialog.
                // The dialog is automatically dismissed when a dialog button is clicked.
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        try {
                            if (originPosition != null && destinationPosition != null) {
                                NavigationLauncherOptions options = NavigationLauncherOptions.builder()
                                        .origin(originPosition)
                                        .destination(destinationPosition)
                                        .shouldSimulateRoute(true) // Simulating navigation of selected route.
                                        .build();
                                if (destinationPosition != null) {
                                    NavigationLauncher.startNavigation(MainActivity.this, options); // Transitions to an other activity in order to start simulating navigation.
                                }
                            } else {
                                Toast.makeText(getApplicationContext(), "Destination is not set.", Toast.LENGTH_SHORT).show();
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                })

                // A null listener allows the button to dismiss the dialog and remove current marker and route.
                .setNeutralButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        navigationMapRoute.removeRoute();
                        map.removeMarker(destinationMarker);
                    }
                })

                // The same as PositiveButton but without simulating navigation for the selected route.
                .setNegativeButton("No", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        try {
                            if (originPosition != null && destinationPosition != null) {
                                NavigationLauncherOptions options = NavigationLauncherOptions.builder()
                                        .origin(originPosition)
                                        .destination(destinationPosition)
                                        .shouldSimulateRoute(false) // Does not simulate navigation.
                                        .build();
                                if (destinationPosition != null) {
                                    NavigationLauncher.startNavigation(MainActivity.this, options); //Transitions to an other activity in order to start navigation.
                                }
                            } else {
                                Toast.makeText(getApplicationContext(), "Destination is not set.", Toast.LENGTH_SHORT).show();
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                })
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

}
