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

import org.mapsforge.core.model.LatLong;
import org.mapsforge.map.android.graphics.AndroidGraphicFactory;
import org.mapsforge.map.android.util.AndroidUtil;
import org.mapsforge.map.android.view.MapView;
import org.mapsforge.map.datastore.MapDataStore;
import org.mapsforge.map.layer.cache.TileCache;
import org.mapsforge.map.layer.renderer.TileRendererLayer;
import org.mapsforge.map.reader.MapFile;
import org.mapsforge.map.rendertheme.InternalRenderTheme;
//import org.mapsforge.map.reader.header.;

import java.io.File;

import de.jgrunert.osm_routing.AStarRouteSolver;
import de.jgrunert.osm_routing.IRouteSolver;

public class MainActivity extends ActionBarActivity {

    AStarRouteSolver routeSolver = new AStarRouteSolver();
    protected PowerManager.WakeLock mWakeLock;

    private MapView mapView;
    private TileCache tileCache;
    private TileRendererLayer tileRendererLayer;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(final Bundle icicle) {
        super.onCreate(icicle);

        AndroidGraphicFactory.createInstance(this.getApplication());

        setContentView(R.layout.activity_main);

        /* This code together with the one in onDestroy()
         * will make the screen be always on until this Activity gets destroyed. */
        final PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        this.mWakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "My Tag");
        this.mWakeLock.acquire();

        this.mapView = ((MapView)findViewById(R.id.view));
        //setContentView(this.mapView);
        //ViewGroup.LayoutParams lp =new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        //lp.gravity= Gravity.BOTTOM;
        //addContentView(this.mapView, lp);
        //findViewById(R.id.)

        //this.Fram

        this.mapView.setClickable(true);
        this.mapView.getMapScaleBar().setVisible(true);
        this.mapView.setBuiltInZoomControls(true);
        this.mapView.getMapZoomControls().setZoomLevelMin((byte) 10);
        this.mapView.getMapZoomControls().setZoomLevelMax((byte) 20);

        final File OSM_BASE_DIR = new File(Environment.getExternalStorageDirectory(), "osm");
        final File MAP_BASE_DIR = new File(OSM_BASE_DIR, "mapsforge");

        this.tileCache = AndroidUtil.createTileCache(this, "mapcache",
                mapView.getModel().displayModel.getTileSize(),1f,
                this.mapView.getModel().frameBufferModel.getOverdrawFactor());

        MapDataStore mapDataStore = new MapFile(new File(MAP_BASE_DIR, "germany.map"));
        this.tileRendererLayer = new TileRendererLayer(tileCache, mapDataStore,
                this.mapView.getModel().mapViewPosition, false, true, AndroidGraphicFactory.INSTANCE);
        tileRendererLayer.setXmlRenderTheme(InternalRenderTheme.OSMARENDER);

        this.mapView.getLayerManager().getLayers().add(tileRendererLayer);

        this.mapView.getModel().mapViewPosition.setCenter(new LatLong(52.517037, 13.38886));
        this.mapView.getModel().mapViewPosition.setZoomLevel((byte) 12);

        System.out.println("Initialized MainActivity");
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
