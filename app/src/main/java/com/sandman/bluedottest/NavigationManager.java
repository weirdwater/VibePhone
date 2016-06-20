package com.sandman.bluedottest;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.indooratlas.android.sdk.IALocation;
import com.indooratlas.android.sdk.IALocationListener;
import com.indooratlas.android.sdk.IALocationManager;
import com.indooratlas.android.sdk.IALocationRequest;

import java.util.Observable;
import java.util.Observer;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Created by bruijnes on 20/06/16.
 */
public class NavigationManager extends Observable implements IALocationListener, SensorEventListener {
    private final String TAG = "NAVMAN";
    private final double PROXIMITY_MARGIN = 0.00003;
    private final double ORIENTATION_MARGIN = 5;

    private Sensor mOrientation;
    private SensorManager mSensorManager;
    private IALocationManager mIALocationManager;

    private ConcurrentLinkedQueue<LatLng> _waypoints = new ConcurrentLinkedQueue<LatLng>();
    private LatLng _nextWaypoint;
    private LatLng _lastLocation;

    private TextView _north, _waypoint, _degrees;

    public NavigationManager (SensorManager sensorManager, IALocationManager indoorAtlasLocMan, LatLng defaultLocation, TextView north, TextView waypoint, TextView degrees) {
        _waypoints.offer(new LatLng(51.917437458857066, 4.484745936568862));
        //_waypoints.offer(new LatLng(51.91739853185142, 4.48457666843423));
        updateNextWaypoint();
        _lastLocation = defaultLocation;
        mSensorManager = sensorManager;
        mOrientation = mSensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION);
        mIALocationManager = indoorAtlasLocMan;

        _north = north;
        _waypoint = waypoint;
        _degrees = degrees;
    }

    @Override
    public void onLocationChanged(IALocation iaLocation) {
        LatLng currentLocation = new LatLng(
                iaLocation.getLatitude(),
                iaLocation.getLongitude()
        );

        if (atWaypoint(currentLocation, _nextWaypoint)) {
            updateNextWaypoint();
        }

        _lastLocation = currentLocation;

        Log.d(TAG, "Latitude: " + currentLocation.latitude);
        Log.d(TAG, "Longitude: " + currentLocation.longitude);
        Log.d(TAG, "Next waypoint within vicinity: " + atWaypoint(currentLocation, _nextWaypoint));
        Log.d(TAG, "------------------");
    }

    public void onDestroy() {
        mIALocationManager.destroy();
    }

    public void onPause() {
        mIALocationManager.removeLocationUpdates(this);
        mSensorManager.unregisterListener(this);
    }

    public void onResume() {
        mIALocationManager.requestLocationUpdates(IALocationRequest.create(), this);
        mSensorManager.registerListener(this, mOrientation, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    public void onStatusChanged(String s, int i, Bundle bundle) {

    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        double directionInDegrees = Math.round(event.values[0]);
        double mdegreesToWaypoint = degreesToWaypoint(_lastLocation, _nextWaypoint);
        double rotationDirectionInDegrees = compareOrientation(mdegreesToWaypoint, directionInDegrees);
        Log.d(TAG, "Please turn by " + rotationDirectionInDegrees + " degrees");
        giveDirections(rotationDirectionInDegrees);
    }

    public void giveDirections(double direction)
    {
        String dirTag = "DIRECTIONS";
        // Allow a margin of ORIENTATION_MARGIN in both directions e.g. [-5...+5]
        if (direction > ORIENTATION_MARGIN || direction < (ORIENTATION_MARGIN * -1)) {
            setChanged();
            // Higher is clockwise, on a compass that is turning right
            if (direction > 0) {
                Log.d(TAG, "Turn Right");
                notifyObservers(VibrateMessage.Right);
            }
            // Lower is counterclockwise, on a compass that is turning left
            else {
                Log.d(TAG, "Turn Left");
                notifyObservers(VibrateMessage.Left);
            }
        }
        else {
            Log.d(TAG, "More or less straight");
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    public void updateNextWaypoint() {
        try {
            _nextWaypoint = _waypoints.remove();
            Log.d(TAG, "Retrieved next waypoint from queue: " + _nextWaypoint);
        } catch (Exception e) {
            notifyObservers(VibrateMessage.Arrived);
            Log.d(TAG, "No waypoints left in queue.");
        }
    }

    public boolean atWaypoint(LatLng location, LatLng waypoint) {
        LatLngBounds vicinity = new LatLngBounds(
                new LatLng(
                        location.latitude - PROXIMITY_MARGIN,
                        location.longitude - PROXIMITY_MARGIN
                ),
                new LatLng (
                        location.latitude + PROXIMITY_MARGIN,
                        location.longitude + PROXIMITY_MARGIN
                )
        );
        return vicinity.contains(waypoint);
    }

//    public double compareOrientation(double angleToWaypoint, double angleToNorth) {
//        double degrees = angleToWaypoint - angleToNorth;
//        System.out.print("Orientation: ");
//        System.out.println(angleToNorth);
//        _north.setText("" + angleToNorth);
//        System.out.print("Waypoint: ");
//        _waypoint.setText("" + angleToWaypoint);
//        System.out.println(angleToWaypoint);
//        if (angleToNorth - angleToWaypoint > 180) {
//            degrees = (angleToWaypoint + 360) - angleToNorth;
//        }
//        System.out.print("Degrees: ");
//        _degrees.setText("" + degrees);
//        System.out.println(degrees);
//        return degrees;
//    }

    public double compareOrientation(double angleToWaypoint, double angleToNorth) {
        double degrees = angleToWaypoint - angleToNorth;

        if (degrees > 180) {
            degrees = angleToWaypoint - (angleToNorth + 360);
        }
        else if (degrees < -180) {
            degrees = (angleToWaypoint + 360) - angleToNorth;
        }

        _north.setText("" + angleToNorth);
        _waypoint.setText("" + angleToWaypoint);
        _degrees.setText("" + degrees);

        return degrees;
    }

    public double degreesToWaypoint(LatLng currentLocation, LatLng waypoint) {
        double deltaLat = currentLocation.latitude - waypoint.latitude;
        double deltaLng = currentLocation.longitude - waypoint.longitude;
        double angleInRadians = Math.atan(deltaLng / deltaLat);
        double angleInDegrees = Math.toDegrees(angleInRadians);
        if (currentLocation.latitude >= waypoint.latitude) {
            angleInDegrees = 180 + angleInDegrees;
        }
        else if (angleInDegrees < 0) {
            angleInDegrees = 360 + angleInDegrees;
        }
        return angleInDegrees;
    }

    @Override
    public void addObserver(Observer observer) {
        super.addObserver(observer);
        Log.d(TAG, "Observer added! Navigation Manager now has " + countObservers() + " observer(s).");
        notifyObservers("You've been added!");
    }

    @Override
    public void notifyObservers(Object data) {
        Log.d(TAG, "Notifying " + countObservers() + " observer(s).");
        super.notifyObservers(data);
    }
}
