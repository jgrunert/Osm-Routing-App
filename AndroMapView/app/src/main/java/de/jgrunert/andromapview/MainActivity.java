package de.jgrunert.andromapview;

import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;

import de.jgrunert.osm_routing.AStarRouteSolver;

public class MainActivity extends ActionBarActivity {

    AStarRouteSolver solver = new AStarRouteSolver();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
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


    }
}
