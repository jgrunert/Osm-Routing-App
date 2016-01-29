package de.jgrunert.andromapview;

import android.content.Context;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
import android.support.v7.app.ActionBarActivity;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

import org.mapsforge.core.graphics.Color;
import org.mapsforge.core.graphics.Paint;
import org.mapsforge.core.graphics.Style;
import org.mapsforge.core.model.BoundingBox;
import org.mapsforge.core.model.LatLong;
import org.mapsforge.map.android.graphics.AndroidGraphicFactory;
import org.mapsforge.map.android.util.AndroidUtil;
import org.mapsforge.map.android.view.MapView;
import org.mapsforge.map.datastore.MapDataStore;
import org.mapsforge.map.layer.cache.TileCache;
import org.mapsforge.map.layer.overlay.Circle;
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

    IRouteSolver routeSolver = new AStarRouteSolver();
    protected PowerManager.WakeLock mWakeLock;

    private MapView mapView;
    private TileCache tileCache;
    private TileRendererLayer tileRendererLayer;

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
        this.mapView = ((MapView)findViewById(R.id.view));
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


        System.out.println("Initialized MainActivity");


        // Set map focus
        mapFocus(routeSolver.getStartCoordinate());
        mapSetZoom((byte) 10);

        updateRouteOverlay();
    }



    public void updateRouteOverlay() {

        // instantiating the paint objects
        Paint paintRouteLine = AndroidGraphicFactory.INSTANCE.createPaint();
        paintRouteLine.setColor(Color.BLUE);
        paintRouteLine.setStrokeWidth(8);
        paintRouteLine.setStyle(Style.STROKE);

        Paint paintPointBorder = AndroidGraphicFactory.INSTANCE.createPaint();
        paintPointBorder.setColor(Color.BLACK);
        paintPointBorder.setStrokeWidth(12);
        paintPointBorder.setStyle(Style.STROKE);

        Paint paintStartPoint = AndroidGraphicFactory.INSTANCE.createPaint();
        paintStartPoint.setColor(Color.BLUE);
        paintStartPoint.setStyle(Style.FILL);

        Paint paintTargPoint = AndroidGraphicFactory.INSTANCE.createPaint();
        paintTargPoint.setColor(Color.RED);
        paintTargPoint.setStyle(Style.FILL);



        LatLong startCoord = routeSolver.getStartCoordinate();
        LatLong targCoord = routeSolver.getTargetCoordinate();


        // TODO Real route
        Polyline routeLine = new Polyline(paintRouteLine, AndroidGraphicFactory.INSTANCE);
        List<LatLong> coordinateList = routeLine.getLatLongs();
        coordinateList.add(startCoord);
        coordinateList.add(targCoord);
        mapView.getLayerManager().getLayers().add(routeLine);


        // Overlay for start and end points
        Circle cStart = new Circle(routeSolver.getStartCoordinate(), 120.0f, paintStartPoint, paintPointBorder);
        Circle cTarg = new Circle(routeSolver.getTargetCoordinate(), 120.0f, paintTargPoint, paintPointBorder);

        mapView.getLayerManager().getLayers().add(cStart);
        mapView.getLayerManager().getLayers().add(cTarg);
    }



    public void mapFocus(LatLong focusPoint) {
        //this.mapView.getModel().mapViewPosition.setCenter(new LatLong(52.517037, 13.38886));
        this.mapView.getModel().mapViewPosition.setCenter(focusPoint);
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
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
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
