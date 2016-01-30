package de.jgrunert.andromapview;

import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import org.mapsforge.core.graphics.Color;
import org.mapsforge.core.graphics.Paint;
import org.mapsforge.core.graphics.Style;
import org.mapsforge.core.model.LatLong;
import org.mapsforge.map.android.graphics.AndroidGraphicFactory;
import org.mapsforge.map.android.util.AndroidUtil;
import org.mapsforge.map.android.view.MapView;
import org.mapsforge.map.datastore.MapDataStore;
import org.mapsforge.map.layer.cache.TileCache;
import org.mapsforge.map.layer.overlay.Circle;
import org.mapsforge.map.layer.overlay.FixedPixelCircle;
import org.mapsforge.map.layer.overlay.Polyline;
import org.mapsforge.map.layer.renderer.TileRendererLayer;
import org.mapsforge.map.reader.MapFile;
import org.mapsforge.map.rendertheme.InternalRenderTheme;
//import org.mapsforge.map.reader.header.;

import java.io.File;
import java.util.List;

import de.jgrunert.osm_routing.AStarRouteSolver;
import de.jgrunert.osm_routing.IRouteSolver;

public class MainActivity extends ActionBarActivity {

    private static final String TAG = MainActivity.class.getSimpleName();

    IRouteSolver routeSolver = new AStarRouteSolver();
    protected PowerManager.WakeLock mWakeLock;

    private MapView mapView;
    private TileCache tileCache;
    private TileRendererLayer tileRendererLayer;

    private LocationManager locationManager;

    Polyline routeLine = null;
    FixedPixelCircle cStart = null;
    FixedPixelCircle cTarg = null;
    FixedPixelCircle cCurr = null;
    Circle cCurrAccur = null;

    private static final File MAP_VIEW_FILE = new File(Environment.getExternalStorageDirectory(), "osm/mapsforge/germany.map");



    /** Called when the activity is first created. */
    @Override
    public void onCreate(final Bundle icicle) {
        super.onCreate(icicle);

        AndroidGraphicFactory.createInstance(this.getApplication());

        setContentView(R.layout.activity_main);


        /* This code together with the one in onDestroy()
         * will make the screen be always on until this Activity gets destroyed. */
        final PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        this.mWakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "lock_tag");
        this.mWakeLock.acquire();


        // Initialize MapView
        this.mapView = ((MapView)findViewById(R.id.mapView));
        this.mapView.setClickable(true);
        this.mapView.getMapScaleBar().setVisible(true);
        this.mapView.setBuiltInZoomControls(true);
        this.mapView.getMapZoomControls().setZoomLevelMin((byte) 10);
        this.mapView.getMapZoomControls().setZoomLevelMax((byte) 20);

        this.tileCache = AndroidUtil.createTileCache(this, "map_cache",
                mapView.getModel().displayModel.getTileSize(),1f,
                this.mapView.getModel().frameBufferModel.getOverdrawFactor());

        MapDataStore mapDataStore = new MapFile(MAP_VIEW_FILE);
        this.tileRendererLayer = new TileRendererLayer(tileCache, mapDataStore,
                this.mapView.getModel().mapViewPosition, false, true, AndroidGraphicFactory.INSTANCE);
        tileRendererLayer.setXmlRenderTheme(InternalRenderTheme.OSMARENDER);

        this.mapView.getLayerManager().getLayers().add(tileRendererLayer);



        // Initialize location manager and listener
        locationManager = (LocationManager)getSystemService(Context.LOCATION_SERVICE);


        // Getting GPS and NETWORK status
        boolean isGPSEnabled = locationManager
                .isProviderEnabled(LocationManager.GPS_PROVIDER);

        if(isGPSEnabled) {
            FineLocationListener locationListener = new FineLocationListener(locationManager);
            locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER, 5000, 10, locationListener);
            Log.i(TAG, "Started FineLocationListener");
        } else {
            Log.w(TAG, "Unable to start FineLocationListener - GPS not enabled");
            Toast.makeText(getBaseContext(),
                    "GPS not enabled - location listening disabled", Toast.LENGTH_LONG).show();
        }

        Log.i(TAG, "Initialized MainActivity");


        // Default focus
        mapFocus(routeSolver.getStartCoordinate());
        mapSetZoom((byte) 10);
        // Try to focus current position
        mapFocusCurrent();

        updateRouteOverlay();
    }


    /**
     * Tries to return the location with best accuracy from GPS or NETWORK
     * @return Best position or NULL if no location available
     */
    private Location getBestCurrentLocation() {

        Location locGps = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ?
                locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER) :
                null;
        Location locNet = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ?
                locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) :
                null;

        if(locGps != null && (locNet == null || locGps.getAccuracy() < locNet.getAccuracy())) {
            return locGps;
        } else {
            return locNet;
        }
    }


    public void updateRouteOverlay() {

        // Initialize paints
        Paint paintRouteLine = AndroidGraphicFactory.INSTANCE.createPaint();
        paintRouteLine.setColor(Color.BLUE);
        paintRouteLine.setStrokeWidth(8);
        paintRouteLine.setStyle(Style.STROKE);


        LatLong startCoord = routeSolver.getStartCoordinate();
        LatLong targCoord = routeSolver.getTargetCoordinate();

        // TODO Real route
        routeLine = new Polyline(paintRouteLine, AndroidGraphicFactory.INSTANCE);
        List<LatLong> coordinateList = routeLine.getLatLongs();
        coordinateList.add(startCoord);
        coordinateList.add(targCoord);
        mapView.getLayerManager().getLayers().add(routeLine);


        // Draw points over route line
        updatePointOverlay();
    }

    public void updatePointOverlay() {

        // Initialize paints
        Paint paintPointBorder = AndroidGraphicFactory.INSTANCE.createPaint();
        paintPointBorder.setColor(Color.BLACK);
        paintPointBorder.setStrokeWidth(6);
        paintPointBorder.setStyle(Style.STROKE);

        Paint paintStartPoint = AndroidGraphicFactory.INSTANCE.createPaint();
        paintStartPoint.setColor(Color.GREEN);
        paintStartPoint.setStyle(Style.FILL);

        Paint paintTargPoint = AndroidGraphicFactory.INSTANCE.createPaint();
        paintTargPoint.setColor(Color.RED);
        paintTargPoint.setStyle(Style.FILL);


        // Remove old overlay
        if(cCurrAccur != null) {
            mapView.getLayerManager().getLayers().remove(cCurrAccur);
        }
        if(cCurrAccur != null) {
            mapView.getLayerManager().getLayers().remove(cCurrAccur);
        }

        // Add new overlay
        cStart = new FixedPixelCircle(routeSolver.getStartCoordinate(), 5.0f, paintStartPoint, paintPointBorder);
        cTarg = new FixedPixelCircle(routeSolver.getTargetCoordinate(), 5.0f, paintTargPoint, paintPointBorder);
        mapView.getLayerManager().getLayers().add(cStart);
        mapView.getLayerManager().getLayers().add(cTarg);


        // Draw current location with accuracy if available
        Location ownLoc = getBestCurrentLocation();
        if(ownLoc != null) {
            LatLong ownLocLatLon = new LatLong(ownLoc.getLatitude(), ownLoc.getLongitude());

            Paint paintOwnPoint = AndroidGraphicFactory.INSTANCE.createPaint();
            paintOwnPoint.setColor(Color.BLUE);
            paintOwnPoint.setStyle(Style.FILL);

            // Remove old overlay
            if(cCurr != null) {
                mapView.getLayerManager().getLayers().remove(cCurr);
            }
            if(cCurrAccur != null) {
                mapView.getLayerManager().getLayers().remove(cCurrAccur);
            }

            // Add new overlay
            cCurr = new FixedPixelCircle(ownLocLatLon, 5.0f, paintOwnPoint, paintPointBorder);
            cCurrAccur = new Circle(ownLocLatLon, ownLoc.getAccuracy(), null, paintPointBorder);
            mapView.getLayerManager().getLayers().add(cCurr);
            mapView.getLayerManager().getLayers().add(cCurrAccur);
        }
    }



    public void mapFocus(LatLong focusPoint) {
        //this.mapView.getModel().mapViewPosition.setCenter(new LatLong(52.517037, 13.38886));
        this.mapView.getModel().mapViewPosition.setCenter(focusPoint);
    }

    public void mapFocusCurrent() {
        Location ownLoc = getBestCurrentLocation();
        if(ownLoc != null) {
            LatLong ownLocLatLon = new LatLong(ownLoc.getLatitude(), ownLoc.getLongitude());
            mapFocus(ownLocLatLon);
            mapSetZoom((byte)16);
        }
    }

    public void mapSetZoom(byte zoom) {
        this.mapView.getModel().mapViewPosition.setZoomLevel(zoom);
    }





    @Override
    public void onDestroy() {
        this.mWakeLock.release();
        super.onDestroy();
        this.mapView.destroyAll();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        // Exit app
        if (id == R.id.action_exit) {
            System.exit(0);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }




    public void btClickCenterGps(View v) {
        updatePointOverlay();
        mapFocusCurrent();
    }

    public void btClickSetStartTargetGps(View v) {
        Location loc = getBestCurrentLocation();
        if(loc != null) {
            if(v.getId() == R.id.btStartGps) {
                routeSolver.setStartNode(routeSolver.findNextNode((float) loc.getLatitude(), (float) loc.getLongitude()));
            } else if(v.getId() == R.id.btTargGps) {
                routeSolver.setTargetNode(routeSolver.findNextNode((float) loc.getLatitude(), (float) loc.getLongitude()));
            }
            updatePointOverlay();
            mapFocusCurrent();
        } else {
            Toast.makeText(getBaseContext(),
                    "Unable to determine location", Toast.LENGTH_SHORT).show();
        }
    }

    public void btClickSetStartTargetSel(View v) {
        if(v.getId() == R.id.btStartSel) {
            //routeSolver.setStartNode(routeSolver.findNextNode((float) loc.getLatitude(), (float) loc.getLongitude()));
        } else if(v.getId() == R.id.btTargSel) {
            //routeSolver.setTargetNode(routeSolver.findNextNode((float) loc.getLatitude(), (float) loc.getLongitude()));
        }
    }




    public void btClickRouteCarFast(View v) {

    }

    public void btClickRouteCarShort(View v) {

    }

    public void btClickRoutePed(View v) {

    }


    public void mapCalcButtonClick(View v) {
        double lat1 = Double.parseDouble(((EditText) findViewById(R.id.editTextLat1)).getText().toString());
        double lon1 = Double.parseDouble(((EditText) findViewById(R.id.editTextLon1)).getText().toString());
        double lat2 = Double.parseDouble(((EditText) findViewById(R.id.editTextLat2)).getText().toString());
        double lon2 = Double.parseDouble(((EditText) findViewById(R.id.editTextLon2)).getText().toString());

        Long startNode = routeSolver.findNextNode((float)lat1, (float)lon1, (byte) 0, (byte) 0);
        Long targetNode = routeSolver.findNextNode((float)lat2, (float)lon2, (byte)0, (byte)0);

        if(startNode == null) {
            System.err.println("No start node found");
            return;
        }
        if(targetNode == null) {
            System.err.println("No target node found");
            return;
        }

        routeSolver.setStartNode(startNode);
        routeSolver.setTargetNode(targetNode);

        routeSolver.startCalculateRoute(IRouteSolver.TransportMode.Car, IRouteSolver.RoutingMode.Fastest);
    }
}
