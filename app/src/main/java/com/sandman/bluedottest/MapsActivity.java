package com.sandman.bluedottest;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.indooratlas.android.sdk.IALocation;
import com.indooratlas.android.sdk.IALocationListener;
import com.indooratlas.android.sdk.IALocationManager;
import com.indooratlas.android.sdk.IALocationRequest;


import java.util.concurrent.ConcurrentLinkedQueue;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback, IALocationListener, SensorEventListener {

    private GoogleMap mMap;
    private Marker locationMarker;
    private Marker waypointMarker;
    private final String TAG = "BlueDot";
    private IALocationManager mIALocationManager;
    private ConcurrentLinkedQueue<LatLng> waypoints = new ConcurrentLinkedQueue<LatLng>();
    private LatLng nextWaypoint;
    private double lastOrientation;
    private LatLng lastLocation;
    private final double MARGIN = 0.00003;

    private SensorManager mSensorManager;
    private Sensor mOrientation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        waypoints.offer(new LatLng(51.917437458857066, 4.484745936568862));
        waypoints.offer(new LatLng(51.91739853185142, 4.48457666843423));

        setContentView(R.layout.activity_maps);
        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        mOrientation = mSensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION);

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        mIALocationManager = IALocationManager.create(this);

        LatLng baseLoc = new LatLng(51.917, 4.484);
        lastLocation = baseLoc;
        String locTag = "DEGREES";
        Log.d(locTag, "East: " + degreesToWaypoint(baseLoc, new LatLng(51.917, 4.4845)));
        Log.d(locTag, "SouthEast: " + degreesToWaypoint(baseLoc, new LatLng(51.9165, 4.4845)));
        Log.d(locTag, "South: " + degreesToWaypoint(baseLoc, new LatLng(51.9165, 4.484)));
        Log.d(locTag, "SouthWest: " + degreesToWaypoint(baseLoc, new LatLng(51.9165, 4.4835)));
        Log.d(locTag, "West: " + degreesToWaypoint(baseLoc, new LatLng(51.917, 4.4835)));
        Log.d(locTag, "NorthWest: " + degreesToWaypoint(baseLoc, new LatLng(51.9175, 4.4835)));
        Log.d(locTag, "North: " + degreesToWaypoint(baseLoc, new LatLng(51.9175, 4.484)));
        Log.d(locTag, "NorthEast: " + degreesToWaypoint(baseLoc, new LatLng(51.9175, 4.4845)));
    }

    public void updateNextWaypoint() {
        try {
            nextWaypoint = waypoints.remove();
            if (waypointMarker != null) waypointMarker.remove();
            waypointMarker = mMap.addMarker(
                    new MarkerOptions().position(nextWaypoint)
            );
            Log.d(TAG, "Got the next waypoint from queue!");
        } catch (Exception e) {
            Log.e(TAG, "No waypoints left in queue!" + e.getMessage());
            Log.d(TAG, "Reached destination!");
            waypointMarker.remove();
        }
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        // Add a marker in Rotterdam and move the camera
        updateNextWaypoint();
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(nextWaypoint, 17));
    }

    @Override
    public void onResume() {
        super.onResume();
        mIALocationManager.requestLocationUpdates(IALocationRequest.create(), this);
        mSensorManager.registerListener(this, mOrientation, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    public void onPause() {
        mIALocationManager.removeLocationUpdates(this);
        mSensorManager.unregisterListener(this);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        mIALocationManager.destroy();
        super.onDestroy();
    }

    @Override
    public void onLocationChanged(IALocation iaLocation) {
        LatLng currentLocation = new LatLng(
                iaLocation.getLatitude(),
                iaLocation.getLongitude()
        );
        Log.d(TAG, "Latitude: " + currentLocation.latitude);
        Log.d(TAG, "Longitude: " + currentLocation.longitude);
        Log.d(TAG, "------------------");

        float markerColor = BitmapDescriptorFactory.HUE_BLUE;
        if (atWaypoint(currentLocation, nextWaypoint)) {
            markerColor = BitmapDescriptorFactory.HUE_GREEN;
            updateNextWaypoint();
        }

        if(locationMarker != null) {
            locationMarker.remove();
        }
        locationMarker = mMap.addMarker(new MarkerOptions()
                .position(currentLocation)
                .icon(BitmapDescriptorFactory.defaultMarker(markerColor))
        );
        mMap.moveCamera(CameraUpdateFactory.newLatLng(currentLocation));

        lastLocation = currentLocation;
    }

    public boolean atWaypoint(LatLng location, LatLng waypoint) {
        LatLngBounds vicinity = new LatLngBounds(
                new LatLng(
                        location.latitude - MARGIN,
                        location.longitude - MARGIN
                ), // NorthEast of location
                new LatLng(
                        location.latitude + MARGIN,
                        location.longitude + MARGIN
                ) // SouthWest of location
        );
        Log.d(TAG, "LatLngBounds: " + vicinity);
        Log.d(TAG, "Waypoint within bounds: " + vicinity.contains(waypoint));
        Log.d(TAG, "-----------");
        return vicinity.contains(waypoint);
    }

    public static double compareOrientation(double angleToWaypoint, double angleToNorth) {
        double degrees = angleToWaypoint - angleToNorth;
        if (degrees > 180 || degrees < -180) {
            degrees = angleToNorth - (angleToNorth + 360);
        }
        return degrees;
    }

    public double degreesToWaypoint(LatLng currentLocation, LatLng waypoint) {
        double deltaLat = currentLocation.latitude - waypoint.latitude;
        double deltaLng = currentLocation.longitude - waypoint.longitude;
        double hypotenuse = Math.sqrt(Math.pow(deltaLat, 2) + Math.pow(deltaLng, 2));
        double angleInRadians = Math.atan(deltaLng / deltaLat);
        double angleInDegrees = Math.toDegrees(angleInRadians);

        Log.d(TAG, "------------");
        Log.d(TAG, "deltaLat: " + deltaLat + "\ndeltaLng: " + deltaLng + "\nhypotenuse: " + hypotenuse + "\nRadians: " + angleInRadians + "\nRaw Degrees: " + angleInDegrees);

        if (currentLocation.latitude >= waypoint.latitude) {
            angleInDegrees = 180 + angleInDegrees;
        }
        else if (angleInDegrees < 0) {
            Log.d(TAG, "North, and below 0");
            angleInDegrees = 360 + angleInDegrees;
        }
        return angleInDegrees;
    }

    @Override
    public void onStatusChanged(String s, int i, Bundle bundle) {

    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        float directionInDegrees = Math.round(event.values[0]);
        Log.d("SNSR_CHANGE", "Turn by: " + compareOrientation( degreesToWaypoint(lastLocation, nextWaypoint), directionInDegrees));
        lastOrientation = directionInDegrees;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        Log.d("SNSR_ACCRCY", "Sensor: " + sensor +"Accuracy: " + accuracy);
    }
}
