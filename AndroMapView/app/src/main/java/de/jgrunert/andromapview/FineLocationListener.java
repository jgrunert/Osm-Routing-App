package de.jgrunert.andromapview;

import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * Created by Jonas on 30.01.2016.
 */
public class FineLocationListener implements LocationListener {

    private static final String TAG = MainActivity.class.getSimpleName();

    private final LocationManager locationManager;


    public FineLocationListener(LocationManager locationManager) {

        this.locationManager = locationManager;
    }

    @Override
    public void onLocationChanged(Location loc) {
        //editLocation.setText("");
        //pb.setVisibility(View.INVISIBLE);
        //Toast.makeText(
        //        getBaseContext(),
       // //        "Location changed: Lat: " + loc.getLatitude() + " Lng: "
       //                 + loc.getLongitude(), Toast.LENGTH_SHORT).show();
        Log.v(TAG, "Location change: Latitude: " + loc.getLatitude() + " Longitude: " + loc.getLongitude());
        System.out.println("getLastKnownLocation4 " + locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER));
        //loc.getAccuracy()
    }

    @Override
    public void onProviderDisabled(String provider) {}

    @Override
    public void onProviderEnabled(String provider) {}

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {}
}
