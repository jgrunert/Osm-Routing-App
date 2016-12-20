package de.jgrunert.andromapview;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Message;
import android.os.PowerManager;
import android.support.v7.app.ActionBarActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
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
import org.mapsforge.map.util.MapViewProjection;
//import org.mapsforge.map.reader.header.;

import java.io.File;
import java.util.List;
import java.util.TimerTask;
import java.util.logging.Handler;

import de.jgrunert.osm_routing.AStarRouteSolver;
import de.jgrunert.osm_routing.IRouteSolver;

public class MainActivity extends ActionBarActivity {

    private static final String TAG = MainActivity.class.getSimpleName();

    private static final int UI_UPDATE_INTERVAL = 500;

    IRouteSolver routeSolver;
    protected PowerManager.WakeLock mWakeLock;

    private MapView mapView;
    private TileCache tileCache;
    private TileRendererLayer tileRendererLayer;

    private LocationManager locationManager;

    private Polyline routeLine = null;
    private FixedPixelCircle cStart = null;
    private FixedPixelCircle cTarg = null;
    private FixedPixelCircle cCurr = null;
    private Circle cCurrAccur = null;

    private boolean startLocationSetting = false;
    private boolean targLocationSetting = false;
    // Flag to prevent endless event circles
    private boolean isSettingLocation = false;

    private boolean doGpsFollowing = false;

    private static final File MAP_VIEW_FILE = new File(Environment.getExternalStorageDirectory(), "osm/mapsforge/display.map");


    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(final Bundle icicle) {
        super.onCreate(icicle);

        routeSolver = new AStarRouteSolver(this);

        AndroidGraphicFactory.createInstance(this.getApplication());

        setContentView(R.layout.activity_main);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);     //  Fixed Portrait orientation


        /* This code together with the one in onDestroy()
         * will make the screen be always on until this Activity gets destroyed. */
        final PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        this.mWakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "lock_tag");
        this.mWakeLock.acquire();


        // Initialize MapView
        this.mapView = ((MapView) findViewById(R.id.mapView));
        this.mapView.setClickable(true);
        this.mapView.getMapScaleBar().setVisible(true);
        this.mapView.setBuiltInZoomControls(true);
        this.mapView.getMapZoomControls().setZoomLevelMin((byte) 10);
        this.mapView.getMapZoomControls().setZoomLevelMax((byte) 20);

        this.tileCache = AndroidUtil.createTileCache(this, "map_cache",
                mapView.getModel().displayModel.getTileSize(), 1f,
                this.mapView.getModel().frameBufferModel.getOverdrawFactor());

        MapDataStore mapDataStore = new MapFile(MAP_VIEW_FILE);
        this.tileRendererLayer = new TileRendererLayer(tileCache, mapDataStore,
                this.mapView.getModel().mapViewPosition, false, true, AndroidGraphicFactory.INSTANCE);
        tileRendererLayer.setXmlRenderTheme(InternalRenderTheme.OSMARENDER);

        this.mapView.getLayerManager().getLayers().add(tileRendererLayer);

        mapView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (startLocationSetting) {
                    LatLong touchedPos = new MapViewProjection(mapView).fromPixels(event.getX(), event.getY());
                    onStartChanged((float) touchedPos.latitude, (float) touchedPos.longitude);
                    startLocationSetting = false;
                    findViewById(R.id.btStartSel).setEnabled(true);
                } else if (targLocationSetting) {
                    LatLong touchedPos = new MapViewProjection(mapView).fromPixels(event.getX(), event.getY());
                    onTargetChanged((float) touchedPos.latitude, (float) touchedPos.longitude);
                    targLocationSetting = false;
                    findViewById(R.id.btTargSel).setEnabled(true);
                }
                return false;
            }
        });


        // Initialize location manager and listener
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        // Getting GPS and NETWORK status
        boolean isGPSEnabled = locationManager
                .isProviderEnabled(LocationManager.GPS_PROVIDER);

        if (isGPSEnabled) {
            FineLocationListener locationListener = new FineLocationListener(this);
            locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER, 5000, 10, locationListener);
            Log.i(TAG, "Started FineLocationListener");
        } else {
            Log.w(TAG, "Unable to start FineLocationListener - GPS not enabled");
            Toast.makeText(getBaseContext(),
                    "GPS not enabled - location listening disabled", Toast.LENGTH_LONG).show();
        }


        TextWatcher startEditWatcher = new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (isSettingLocation) {
                    return;
                }

                float lat = Float.parseFloat(((EditText) findViewById(R.id.editTextLat1)).getText().toString());
                float lon = Float.parseFloat(((EditText) findViewById(R.id.editTextLon1)).getText().toString());
                onStartChanged(lat, lon);
                mapFocus(new LatLong(lat, lon));
            }
        };
        ((EditText) findViewById(R.id.editTextLat1)).addTextChangedListener(startEditWatcher);
        ((EditText) findViewById(R.id.editTextLon1)).addTextChangedListener(startEditWatcher);

        TextWatcher targEditWatcher = new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (isSettingLocation) {
                    return;
                }

                float lat = Float.parseFloat(((EditText) findViewById(R.id.editTextLat2)).getText().toString());
                float lon = Float.parseFloat(((EditText) findViewById(R.id.editTextLon2)).getText().toString());
                onTargetChanged(lat, lon);
                mapFocus(new LatLong(lat, lon));
            }
        };
        ((EditText) findViewById(R.id.editTextLat2)).addTextChangedListener(targEditWatcher);
        ((EditText) findViewById(R.id.editTextLon2)).addTextChangedListener(targEditWatcher);


        findViewById(R.id.progressBar).setScaleY(3f);
        ((ProgressBar) findViewById(R.id.progressBar)).setProgress(0);

        Log.i(TAG, "Initialized MainActivity");


        // Initial focus and start/stop
        Location initialLoc = getBestCurrentLocation();
        LatLong initialLatLong;
        if (initialLoc != null) {
            initialLatLong = new LatLong(initialLoc.getLatitude(), initialLoc.getLongitude());
        } else {
            initialLatLong = new LatLong(48.78, 9.18);
        }
        onStartChanged((float) initialLatLong.latitude, (float) initialLatLong.longitude);
        onTargetChanged((float) initialLatLong.latitude, (float) initialLatLong.longitude);
        // Focus on initial position
        mapSetZoom((byte) 16);
        mapFocus(initialLatLong);


        updateRouteOverlay();
        updateDisplay();


        // UI Update thread
        Thread uiUpdateThread = new Thread(new Runnable() {
            @Override
            public void run() {
                Log.i(TAG, "UiUpdateThread running");

                while (!Thread.interrupted()) {
                    updateDisplay();
                    try {
                        Thread.sleep(UI_UPDATE_INTERVAL);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        return;
                    }
                }

                Log.i(TAG, "UiUpdateThread finished");
            }
        });
        uiUpdateThread.setName("UiUpdateThread");
        uiUpdateThread.setDaemon(true);
        uiUpdateThread.start();
        Log.i(TAG, "Started UiUpdateThread");
    }



    /**
     * Tries to return the location with best accuracy from GPS or NETWORK
     *
     * @return Best position or NULL if no location available
     */
    private Location getBestCurrentLocation() {

        Location locGps = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ?
                locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER) :
                null;
        Location locNet = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ?
                locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) :
                null;
        System.out.println("locGps: " + locGps + " locNet: " + locNet);

        if (locGps != null && (locNet == null || locGps.getAccuracy() < locNet.getAccuracy())) {
            return locGps;
        } else {
            return locNet;
        }
    }


    public void updateDisplay() {
        MainActivity.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                switch(routeSolver.getRoutingState()) {
                    case NotReady:
                        ((TextView) findViewById(R.id.textViewState)).setText("Not ready");
                        ((ProgressBar) findViewById(R.id.progressBar)).setProgress(0);
                        findViewById(R.id.btCancel).setVisibility(View.INVISIBLE);
                        break;
                    case Standby:
                        ((TextView) findViewById(R.id.textViewState)).setText("Ready");
                        ((ProgressBar) findViewById(R.id.progressBar)).setProgress(100);
                        findViewById(R.id.btCancel).setVisibility(View.INVISIBLE);
                        break;
                    case Routing:
                        int routingProgress = (int)(99 * routeSolver.getRoutingProgress());
                        ((TextView) findViewById(R.id.textViewState)).setText("Routing " + Integer.toString(routingProgress) + "%");
                        ((ProgressBar) findViewById(R.id.progressBar)).setProgress(routingProgress);
                        findViewById(R.id.btCancel).setVisibility(View.VISIBLE);
                        break;
                    case Reconstructing:
                        ((TextView) findViewById(R.id.textViewState)).setText("Route construction");
                        ((ProgressBar) findViewById(R.id.progressBar)).setProgress(99);
                        findViewById(R.id.btCancel).setVisibility(View.VISIBLE);
                        break;
                }

                boolean standbyState = routeSolver.getRoutingState() == IRouteSolver.RoutingState.Standby;
                findViewById(R.id.btRouteCarFast).setEnabled(standbyState);
                findViewById(R.id.btRouteCarFastPrec).setEnabled(standbyState);
                findViewById(R.id.btRouteCarShort).setEnabled(standbyState);
                findViewById(R.id.btRoutePed).setEnabled(standbyState);
            }
        });
    }


    private void updateRouteOverlay() {

        // Initialize paints
        Paint paintRouteLine = AndroidGraphicFactory.INSTANCE.createPaint();
        paintRouteLine.setColor(Color.BLUE);
        paintRouteLine.setStrokeWidth(8);
        paintRouteLine.setStyle(Style.STROKE);


        if (routeLine != null) {
            mapView.getLayerManager().getLayers().remove(routeLine);
        }

        routeLine = new Polyline(paintRouteLine, AndroidGraphicFactory.INSTANCE);
        List<LatLong> coordinateList = routeLine.getLatLongs();
        coordinateList.addAll(routeSolver.getCalculatedRoute());
        mapView.getLayerManager().getLayers().add(routeLine);


        // Draw points over route line
        updatePointOverlay();
    }

    private void updatePointOverlay() {

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
        if (cStart != null) {
            mapView.getLayerManager().getLayers().remove(cStart);
        }
        if (cTarg != null) {
            mapView.getLayerManager().getLayers().remove(cTarg);
        }

        // Add new overlay
        if (routeSolver.getStartNode() != null) {
            cStart = new FixedPixelCircle(routeSolver.getStartCoordinate(), 5.0f, paintStartPoint, paintPointBorder);
            mapView.getLayerManager().getLayers().add(cStart);
        }
        if (routeSolver.getTargetNode() != null) {
            cTarg = new FixedPixelCircle(routeSolver.getTargetCoordinate(), 5.0f, paintTargPoint, paintPointBorder);
            mapView.getLayerManager().getLayers().add(cTarg);
        }


        // Draw current location with accuracy if available
        Location ownLoc = getBestCurrentLocation();
        if (ownLoc != null) {
            LatLong ownLocLatLon = new LatLong(ownLoc.getLatitude(), ownLoc.getLongitude());

            Paint paintOwnPoint = AndroidGraphicFactory.INSTANCE.createPaint();
            paintOwnPoint.setColor(Color.BLUE);
            paintOwnPoint.setStyle(Style.FILL);

            // Remove old overlay
            if (cCurr != null) {
                mapView.getLayerManager().getLayers().remove(cCurr);
            }
            if (cCurrAccur != null) {
                mapView.getLayerManager().getLayers().remove(cCurrAccur);
            }

            // Add new overlay
            cCurr = new FixedPixelCircle(ownLocLatLon, 5.0f, paintOwnPoint, paintPointBorder);
            cCurrAccur = new Circle(ownLocLatLon, ownLoc.getAccuracy(), null, paintPointBorder);
            mapView.getLayerManager().getLayers().add(cCurr);
            mapView.getLayerManager().getLayers().add(cCurrAccur);
        }
    }


    private void onStartChanged(float lat, float lon) {
        isSettingLocation = true;
        Long node = routeSolver.findNextNode(lat, lon);
        if (node != null) {
            ((EditText) findViewById(R.id.editTextLat1)).setText(Float.toString(lat));
            ((EditText) findViewById(R.id.editTextLon1)).setText(Float.toString(lon));
            routeSolver.setStartNode(node);
            updatePointOverlay();
        }
        isSettingLocation = false;
    }

    private void onTargetChanged(float lat, float lon) {
        isSettingLocation = true;
        Long node = routeSolver.findNextNode(lat, lon);
        if (node != null) {
            ((EditText) findViewById(R.id.editTextLat2)).setText(Float.toString(lat));
            ((EditText) findViewById(R.id.editTextLon2)).setText(Float.toString(lon));
            routeSolver.setTargetNode(node);
            updatePointOverlay();
        }
        isSettingLocation = false;
    }


    private void mapFocus(LatLong focusPoint) {
        //this.mapView.getModel().mapViewPosition.setCenter(new LatLong(52.517037, 13.38886));
        this.mapView.getModel().mapViewPosition.setCenter(focusPoint);
    }

    private void mapFocusCurrent() {
        Location ownLoc = getBestCurrentLocation();
        if (ownLoc != null) {
            LatLong ownLocLatLon = new LatLong(ownLoc.getLatitude(), ownLoc.getLongitude());
            mapFocus(ownLocLatLon);
        }
    }

    private void mapSetZoom(byte zoom) {
        this.mapView.getModel().mapViewPosition.setZoomLevel(zoom);
    }


    public void onFineLocationChanged() {
        updatePointOverlay();
        if (doGpsFollowing) {
            mapFocusCurrent();
        }
    }


    public void onRoutingFinished() {
        updateRouteOverlay();
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

        // Main menu actuions
        if (id == R.id.action_follow_gps) {
            item.setChecked(!item.isChecked());
            doGpsFollowing = item.isChecked();
            if (doGpsFollowing) {
                mapFocusCurrent();
                mapSetZoom((byte) 16);
            }
        } else if (id == R.id.action_exit) {
            // Exit app
            System.exit(0);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }


    public void btClickCenterGps(View v) {
        updatePointOverlay();
        mapFocusCurrent();
        mapSetZoom((byte) 16);
    }

    public void btClickSetStartTargetGps(View v) {
        Location loc = getBestCurrentLocation();
        if (loc != null) {
            if (v.getId() == R.id.btStartGps) {
                onStartChanged((float) loc.getLatitude(), (float) loc.getLongitude());
                mapFocus(new LatLong(loc.getLatitude(), loc.getLongitude()));
            } else if (v.getId() == R.id.btTargGps) {
                onTargetChanged((float) loc.getLatitude(), (float) loc.getLongitude());
                mapFocus(new LatLong(loc.getLatitude(), loc.getLongitude()));
            }
            updatePointOverlay();
            mapFocusCurrent();
            mapSetZoom((byte) 16);
        } else {
            Toast.makeText(getBaseContext(),
                    "Unable to determine location", Toast.LENGTH_SHORT).show();
        }
    }

    public void btClickSetStartTargetSel(View v) {
        if (v.getId() == R.id.btStartSel) {
            startLocationSetting = true;
            targLocationSetting = false;
            findViewById(R.id.btStartSel).setEnabled(false);
            findViewById(R.id.btTargSel).setEnabled(true);
        } else if (v.getId() == R.id.btTargSel) {
            targLocationSetting = true;
            startLocationSetting = false;
            findViewById(R.id.btTargSel).setEnabled(false);
            findViewById(R.id.btStartSel).setEnabled(true);
        }
    }


    public void btClickRouting(View v) {

        if (routeSolver.getStartNode() == null) {
            Toast.makeText(getBaseContext(),
                    "Unable route: No start selected", Toast.LENGTH_SHORT).show();
            return;
        }
        if (routeSolver.getTargetNode() == null) {
            Toast.makeText(getBaseContext(),
                    "Unable route: No target selected", Toast.LENGTH_SHORT).show();
            return;
        }
        if (routeSolver.getStartNode().equals(routeSolver.getTargetNode())) {
            Toast.makeText(getBaseContext(),
                    "Unable route: Start and target identical", Toast.LENGTH_SHORT).show();
            return;
        }


        if (v.getId() == R.id.btRouteCarFast) {
            routeSolver.setDoMotorwayBoost(true);
            routeSolver.startCalculateRoute(IRouteSolver.TransportMode.Car, IRouteSolver.RoutingMode.Fastest);
        } if (v.getId() == R.id.btRouteCarFastPrec) {
            routeSolver.setDoMotorwayBoost(false);
            routeSolver.startCalculateRoute(IRouteSolver.TransportMode.Car, IRouteSolver.RoutingMode.Fastest);
        } else if (v.getId() == R.id.btRouteCarShort) {
            routeSolver.startCalculateRoute(IRouteSolver.TransportMode.Car, IRouteSolver.RoutingMode.Shortest);
        } else if (v.getId() == R.id.btRoutePed) {
            routeSolver.startCalculateRoute(IRouteSolver.TransportMode.Pedestrian, IRouteSolver.RoutingMode.Shortest);
        }
    }


    public void btClickCancel(View v) {
        routeSolver.requestCancelRouting();
        updateDisplay();
    }
}
