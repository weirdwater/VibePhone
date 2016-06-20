package com.sandman.bluedottest;

import android.hardware.SensorManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import com.google.android.gms.maps.model.LatLng;
import com.indooratlas.android.sdk.IALocation;
import com.indooratlas.android.sdk.IALocationManager;

import java.util.Observable;
import java.util.Observer;

public class NavigationActivity extends AppCompatActivity implements Observer {
    private NavigationManager _nvm;
    private VibrationManager _vbm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_navigation);

        _nvm = new NavigationManager(
                (SensorManager) getSystemService(SENSOR_SERVICE),
                IALocationManager.create(this),
                new LatLng(51.917, 4.484),
                (TextView) this.findViewById(R.id.north),
                (TextView) this.findViewById(R.id.waypoint),
                (TextView) this.findViewById(R.id.degrees)
        );
        _vbm = new VibrationManager(this);

        _nvm.addObserver(_vbm);
        _nvm.addObserver(this);

        _vbm.vibrateMessage(VibrateMessage.Right);
    }

    @Override
    protected void onResume() {
        super.onResume();
        _nvm.onResume();
    }

    @Override
    protected void onDestroy() {
        _nvm.onDestroy();
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        _nvm.onPause();
        super.onPause();
    }

    @Override
    public void update(Observable observable, Object data) {
        Log.d("VIBE", "Received update from subject.");

    }
}
