package de.jgrunert.andromapview;

import android.content.Context;
import android.os.Bundle;
import android.os.PowerManager;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;

import de.jgrunert.osm_routing.AStarRouteSolver;
import de.jgrunert.osm_routing.IRouteSolver;

public class MainActivity extends ActionBarActivity {

    AStarRouteSolver routeSolver = new AStarRouteSolver();
    protected PowerManager.WakeLock mWakeLock;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(final Bundle icicle) {
        super.onCreate(icicle);

        setContentView(R.layout.activity_main);

        /* This code together with the one in onDestroy()
         * will make the screen be always on until this Activity gets destroyed. */
        final PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        this.mWakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "My Tag");
        this.mWakeLock.acquire();
    }

    @Override
    public void onDestroy() {
        this.mWakeLock.release();
        super.onDestroy();
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

        Long startNode = routeSolver.findNextNode((float)lat1, (float)lon1, (byte)0, (byte)0);
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

        routeSolver.startCalculateRoute(IRouteSolver.TransportMode.Car, IRouteSolver.RoutingMode.Shortest);
    }
}
