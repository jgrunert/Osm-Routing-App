// License: GPL. For details, see Readme.txt file.
package org.openstreetmap.gui.jmapviewer;

import java.awt.Color;

import org.openstreetmap.gui.jmapviewer.interfaces.MapMarker;

/**
 * A simple implementation of the {@link MapMarker} interface. Each map marker
 * is painted as a circle with a black border line and filled with a specified
 * color.
 *
 * @author Jan Peter Stotz
 *
 */
@SuppressWarnings("javadoc")
public class MapMarkerDot extends MapMarkerCircle {

    public static final int DOT_RADIUS = 5;
    
    public final Color optColor;

    public MapMarkerDot(Coordinate coord) {
        this(null, null, null, coord);
    }
    
    public MapMarkerDot(Color color, Coordinate coord) {
        this(color, null, null, coord);
    }


    public MapMarkerDot(String name, Coordinate coord) {
        this(null, name, coord);
    }
    
    public MapMarkerDot(String name, Color color, Coordinate coord) {
        this(color, null, name, coord);
    }

    public MapMarkerDot(Layer layer, Coordinate coord) {
        this(layer, null, coord);
    }

    public MapMarkerDot(Layer layer, String name, Coordinate coord) {
        this(layer, name, coord, getDefaultStyle());
    }

    public MapMarkerDot(Color color, Layer layer, String name, Coordinate coord) {
        this(color, layer, name, coord, getDefaultStyle());
    }

    public MapMarkerDot(Color color, double lat, double lon) {
        this(color, null, null, lat, lon);
    }

    public MapMarkerDot(double lat, double lon) {
        this(null, null, lat, lon);
    }

    public MapMarkerDot(Layer layer, double lat, double lon) {
        this(layer, null, lat, lon);
    }

    public MapMarkerDot(Layer layer, String name, double lat, double lon) {
        this(layer, name, new Coordinate(lat, lon), getDefaultStyle());
    }

    public MapMarkerDot(Color color, Layer layer, String name, double lat, double lon) {
        this(color, layer, name, new Coordinate(lat, lon), getDefaultStyle());
    }

    public MapMarkerDot(Layer layer, String name, Coordinate coord, Style style) {
        super(layer, name, coord, DOT_RADIUS, STYLE.FIXED, getDefaultStyle());
        this.optColor = null;
    }

    public MapMarkerDot(Color color, Layer layer, String name, Coordinate coord, Style style) {
        super(layer, name, coord, DOT_RADIUS, STYLE.FIXED, new Style(Color.BLACK, color, null, getDefaultFont()));
        this.optColor = Color.YELLOW;
    }

    public static Style getDefaultStyle() {
        return new Style(Color.BLACK, Color.YELLOW, null, getDefaultFont());
    }
}
