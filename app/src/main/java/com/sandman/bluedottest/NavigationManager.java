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

import java.util.ArrayList;
import java.util.List;
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

    private ConcurrentLinkedQueue<LatLng> _path = new ConcurrentLinkedQueue<LatLng>();
    private LatLng _nextWaypoint;
    private LatLng _lastLocation;
    private boolean _navigating = false;
    private List<PointOfInterest> _pointsOfInterest = new ArrayList<PointOfInterest>();

    private TextView _north, _waypoint, _degrees;

    public NavigationManager (SensorManager sensorManager, IALocationManager indoorAtlasLocMan, LatLng defaultLocation, TextView north, TextView waypoint, TextView degrees) {
        updateNextWaypoint();
        _lastLocation = defaultLocation;
        mSensorManager = sensorManager;
        mOrientation = mSensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION);
        mIALocationManager = indoorAtlasLocMan;

        _north = north;
        _waypoint = waypoint;
        _degrees = degrees;

        _pointsOfInterest.add(new PointOfInterest("Door", VibrateMessage.Door, new LatLng(51.91741532043742, 4.484621958028178)));
    }


    @Override
    public void onLocationChanged(IALocation iaLocation) {
        LatLng currentLocation = new LatLng(
                iaLocation.getLatitude(),
                iaLocation.getLongitude()
        );
        Log.d(TAG, "Latitude: " + currentLocation.latitude);
        Log.d(TAG, "Longitude: " + currentLocation.longitude);

        if (_navigating && atWaypoint(currentLocation, _nextWaypoint)) {
            Log.d(TAG, "Next waypoint within vicinity: " + atWaypoint(currentLocation, _nextWaypoint));
            updateNextWaypoint();
        }

        for (PointOfInterest poi : _pointsOfInterest) {
            if (atWaypoint(currentLocation, poi.get_coordinates())) {
                Log.d(TAG, "Came across a " + poi.get_name());
                setChanged();
                notifyObservers(poi.get_messageType());
            }
        }

        Log.d(TAG, "------------------");

        _lastLocation = currentLocation;
    }

    public void checkForPOIs(LatLng currentLocation) {
        // Loop through POI list
        //  Check wether there's one nearby, if so notify observers
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
        if (_navigating) {
            double directionInDegrees = Math.round(event.values[0]);
            double mdegreesToWaypoint = degreesToWaypoint(_lastLocation, _nextWaypoint);
            double rotationDirectionInDegrees = compareOrientation(mdegreesToWaypoint, directionInDegrees);
            Log.d(TAG, "Please turn by " + rotationDirectionInDegrees + " degrees");
            giveDirections(rotationDirectionInDegrees);
        }
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

    public void stopNavigating() {
        _navigating = false;
    }

    public void startNavigating() {
        _navigating = true;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    public void updateNextWaypoint() {
        try {
            _nextWaypoint = _path.remove();
            Log.d(TAG, "Retrieved next waypoint from queue: " + _nextWaypoint);
        } catch (Exception e) {
            destinationReached();
            Log.d(TAG, "No waypoints left in queue.");
        }
    }

    public void destinationReached() {
        notifyObservers(VibrateMessage.Arrived);
        _navigating = false;
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

    /**
     * Compares the difference between two sets of degrees and returns the shortest rotation to go
     * from angleA to angleB. Works in degrees.
     * @param angleToWaypoint
     * @param angleToNorth
     * @return
     */
    public double compareOrientation(double angleToWaypoint, double angleToNorth) {
        // We need to know the amount of degrees by which the user has to turn. The user has to turn
        // clockwise for the amount of degrees returned. If the value is negative the user has to
        // turn counter-clockwise.
        double degrees = angleToWaypoint - angleToNorth;

        // Because turning 90 degrees counter-clockwise is quicker than 270 degrees in the opposite
        // direction we need to compensate for return values over 180 degrees in either direction.
        if (degrees > 180) {
            degrees = angleToWaypoint - (angleToNorth + 360);
            // ex. 315 - (60 + 360) = 315 - 420 = -105 -> turn 105 degrees counter-clockwise
            // Would have been: 315 - 60 = 255
        }
        else if (degrees < -180) {
            degrees = (angleToWaypoint + 360) - angleToNorth;
            // ex. (0 + 360) - 270 = 360 - 270 = 90 -> turn 90 degrees clockwise
            // Would have been: 270 - 0 = 270
        }

        _north.setText("" + angleToNorth);
        _waypoint.setText("" + angleToWaypoint);
        _degrees.setText("" + degrees);

        return degrees;
    }

    /**
     * Calculates the angle between two coordinates in relation to the North in degrees.
     * If point B is due east of point A the return value will be 90. 225 for SouthWest and 315 for
     * NorthWest.
     * N: 0, E: 90, S: 180, W: 270
     * @param currentLocation
     * @param waypoint
     * @return
     */
    public double degreesToWaypoint(LatLng currentLocation, LatLng waypoint) {
        double deltaLat = currentLocation.latitude - waypoint.latitude;
        double deltaLng = currentLocation.longitude - waypoint.longitude;
        double angleInRadians = Math.atan(deltaLng / deltaLat);
        double angleInDegrees = Math.toDegrees(angleInRadians);

        // Because the angles are calculated from the poles we will get duplicate values. The North
        // Side will give -90 for West, 0 for north and 90 for East, while the opposite is true for
        // the southern hemisphere. West being 90, South 0 and East -90.

        // Southern Hemisphere
        if (currentLocation.latitude >= waypoint.latitude) {
            angleInDegrees = 180 + angleInDegrees;
            // ex. 180 + -90 = 90 -> East
            // ex. 180 + 90 = 270 -> West
            // ex. 180 + 0 = 180 -> South
        }
        // Northern hemisphere (only the West half)
        else if (angleInDegrees < 0) {
            angleInDegrees = 360 + angleInDegrees;
            // ex. 360 + -90 = 270 -> West
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

    public void setPath(ConcurrentLinkedQueue<LatLng> newPath) {
        _path = newPath;
        updateNextWaypoint();
        _navigating = true;
    }
}
