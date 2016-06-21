package com.sandman.bluedottest;

import com.google.android.gms.maps.model.LatLng;

/**
 * Created by bruijnes on 21/06/16.
 */
public class PointOfInterest {
    private String _name;
    private VibrateMessage _messageType;
    private LatLng _coordinates;

    public PointOfInterest (String name, VibrateMessage messageType, LatLng coordinates) {
        _name = name;
        _messageType = messageType;
        _coordinates = coordinates;
    }


    public VibrateMessage get_messageType() {
        return _messageType;
    }

    public void set_messageType(VibrateMessage _messageType) {
        this._messageType = _messageType;
    }

    public String get_name() {
        return _name;
    }

    public void set_name(String _name) {
        this._name = _name;
    }

    public LatLng get_coordinates() {
        return _coordinates;
    }

    public void set_coordinates(LatLng _coordinates) {
        this._coordinates = _coordinates;
    }
}
