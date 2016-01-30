package de.jgrunert.andromapview;

import android.location.Location;
import android.location.LocationListener;
import android.os.Bundle;
import android.util.Log;

/**
 * Listener listening on location updates
 * Created by Jonas Grunert on 30.01.2016.
 */
public class FineLocationListener implements LocationListener {

    private static final String TAG = MainActivity.class.getSimpleName();

    private final MainActivity mainActivity;


    public FineLocationListener(MainActivity mainActivity) {
        this.mainActivity = mainActivity;
    }

    @Override
    public void onLocationChanged(Location loc) {
        Log.v(TAG, "Location change: Latitude: " + loc.getLatitude() +
                " Longitude: " + loc.getLongitude() +
                " Accuracy: " + loc.getAccuracy());
        mainActivity.onFineLocationChanged();
    }

    @Override
    public void onProviderDisabled(String provider) {}

    @Override
    public void onProviderEnabled(String provider) {}

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {}
}
