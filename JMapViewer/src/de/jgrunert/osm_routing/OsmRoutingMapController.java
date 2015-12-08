// License: GPL. For details, see Readme.txt file.
package de.jgrunert.osm_routing;

import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;

import org.openstreetmap.gui.jmapviewer.Coordinate;
import org.openstreetmap.gui.jmapviewer.JMapController;
import org.openstreetmap.gui.jmapviewer.JMapViewer;
import org.openstreetmap.gui.jmapviewer.MapMarkerDot;
import org.openstreetmap.gui.jmapviewer.MapPolygonImpl;
import org.openstreetmap.gui.jmapviewer.interfaces.ICoordinate;

/**
 * Default map controller which implements map moving by pressing the right
 * mouse button and zooming by double click or by mouse wheel.
 *
 * @author Jan Peter Stotz
 *
 */
public class OsmRoutingMapController extends JMapController implements MouseListener, MouseMotionListener,
MouseWheelListener {

    private static final int MOUSE_BUTTONS_MASK = MouseEvent.BUTTON3_DOWN_MASK | MouseEvent.BUTTON1_DOWN_MASK
    | MouseEvent.BUTTON2_DOWN_MASK;

    private static final int MAC_MOUSE_BUTTON3_MASK = MouseEvent.CTRL_DOWN_MASK | MouseEvent.BUTTON1_DOWN_MASK;

    private Point lastDragPoint;

    private boolean isMoving = false;

    private boolean movementEnabled = true;

    private int movementMouseButton = MouseEvent.BUTTON1;
    private int movementMouseButtonMask = MouseEvent.BUTTON1_DOWN_MASK;

    private boolean wheelZoomEnabled = true;
    private boolean doubleClickZoomEnabled = true;
    
    private Coordinate startLoc = null;
    private Coordinate targetLoc = null;

    private List<MapMarkerDot> routeDots = new ArrayList<>();
    private List<MapPolygonImpl> routeLines = new ArrayList<>();
    
    double[] nodesLat = null;
    double[] nodesLon = null;
    int[] nodesEdgeOffset = null;
    
    int[] edgesTarget = null;
    
        
   
    public OsmRoutingMapController(JMapViewer map) {
        super(map);
        
        try {
            loadOsmData();
        } catch (Exception e) {
            System.err.println("Error at loadOsmData");
            e.printStackTrace();
        }
    }
    
    
    @SuppressWarnings("resource")
    private void loadOsmData() throws Exception {

        System.out.println("Start reading nodes");
        DataInputStream nodeReader = new DataInputStream(new FileInputStream("D:\\Jonas\\OSM\\hamburg\\pass2-nodes.bin"));
        
        int nodeCount = nodeReader.readInt();
        nodesLat = new double[nodeCount];
        nodesLon = new double[nodeCount];
        nodesEdgeOffset = new int[nodeCount];
        
        for(int i = 0; i < nodeCount; i++) {
            nodesLat[i] = nodeReader.readDouble();
            nodesLon[i] = nodeReader.readDouble();
            nodesEdgeOffset[i] = nodeReader.readInt();
        }
        
        nodeReader.close();
        System.out.println("Finished reading nodes");
        

        System.out.println("Start reading edges");
        DataInputStream edgeReader = new DataInputStream(new FileInputStream("D:\\Jonas\\OSM\\hamburg\\pass2-edges.bin"));
        int edgeCount = edgeReader.readInt();
        edgesTarget = new int[edgeCount];

        for(int i = 0; i < edgeCount; i++) {
            edgesTarget[i] = edgeReader.readInt();
        }
        
        edgeReader.close();
        System.out.println("Finished reading edges");
        

        for(int i = 0; i < 1; i++) {
            double lat = nodesLat[i];
            double lon = nodesLon[i];
            int edgeOffs = nodesEdgeOffset[i];            
            MapMarkerDot targetDot = new MapMarkerDot("Point " + i, new Coordinate(lat, lon));
            map.addMapMarker(targetDot);
            
            for(int iTarg = edgeOffs; iTarg < nodesEdgeOffset[i+1]; iTarg++) {
                mapPointDfs(edgesTarget[edgeOffs], lat, lon, 1, 20);
            }
        }
    }
    
    
    private void mapPointDfs(int i, double lastLat, double lastLon, int depth, int maxDepth) {
        if(depth < maxDepth) {
            double lat = nodesLat[i];
            double lon = nodesLon[i];
            int edgeOffs = nodesEdgeOffset[i];            
            MapMarkerDot targetDot = new MapMarkerDot("DFS " + i, new Coordinate(lat, lon));
            map.addMapMarker(targetDot);
            mapPointDfs(edgesTarget[edgeOffs], lat, lon, depth + 1, 20);
        }
    }
    

    @Override
    public void mouseDragged(MouseEvent e) {
        if (!movementEnabled || !isMoving)
            return;
        // Is only the selected mouse button pressed?
        if ((e.getModifiersEx() & MOUSE_BUTTONS_MASK) == movementMouseButtonMask 
                || isPlatformOsx() && e.getModifiersEx() == MAC_MOUSE_BUTTON3_MASK) {
            Point p = e.getPoint();
            if (lastDragPoint != null) {
                int diffx = lastDragPoint.x - p.x;
                int diffy = lastDragPoint.y - p.y;
                map.moveMap(diffx, diffy);
            }
            lastDragPoint = p;
        }
    }

    @Override
    public void mouseClicked(MouseEvent e) {
       
        if (e.getClickCount() == 1) {
            // Waypoint selection
            ICoordinate clickPt = map.getPosition(e.getPoint());
            Coordinate clickLoc = new Coordinate(clickPt.getLat(), clickPt.getLon());

            if(e.getButton() == MouseEvent.BUTTON1) {
                startLoc = clickLoc;
            } 
            else if(e.getButton() == MouseEvent.BUTTON3) {
                targetLoc = clickLoc;
            } 

            updateRoute();
        } 
        else if (doubleClickZoomEnabled && e.getClickCount() == 2 && e.getButton() == MouseEvent.BUTTON1) {
            // Zoom on doubleclick
            map.zoomIn(e.getPoint());
        } 
    }
    
    
    private void updateRoute() {
        // Remove old dots and lines
        for(MapMarkerDot dot : routeDots) {
            map.removeMapMarker(dot);
        }
        routeDots.clear();
        for(MapPolygonImpl line : routeLines) {
            map.removeMapPolygon(line);
        }
        routeLines.clear();
        
        // Add route points to map
        if(startLoc != null) {
            MapMarkerDot startDot = new MapMarkerDot("Start", startLoc);
            map.addMapMarker(startDot);
            routeDots.add(startDot);
        }        
        if(targetLoc != null) {
            MapMarkerDot targetDot = new MapMarkerDot("Target", targetLoc);
            map.addMapMarker(targetDot);
            routeDots.add(targetDot);
        }
        
        if(startLoc != null && targetLoc != null) {
            MapPolygonImpl routPoly = new MapPolygonImpl("Route", startLoc, targetLoc, targetLoc);
            routeLines.add(routPoly);
            map.addMapPolygon(routPoly);
        }
    }
    
    

    @Override
    public void mousePressed(MouseEvent e) {
        if (e.getButton() == movementMouseButton || isPlatformOsx() && e.getModifiersEx() == MAC_MOUSE_BUTTON3_MASK) {
            lastDragPoint = null;
            isMoving = true;
        }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        if (e.getButton() == movementMouseButton || isPlatformOsx() && e.getButton() == MouseEvent.BUTTON1) {
            lastDragPoint = null;
            isMoving = false;
        }
    }

    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
        if (wheelZoomEnabled) {
            map.setZoom(map.getZoom() - e.getWheelRotation(), e.getPoint());
        }
    }

    public boolean isMovementEnabled() {
        return movementEnabled;
    }

    /**
     * Enables or disables that the map pane can be moved using the mouse.
     *
     * @param movementEnabled {@code true} to allow the map pane to be moved using the mouse
     */
    public void setMovementEnabled(boolean movementEnabled) {
        this.movementEnabled = movementEnabled;
    }

    public int getMovementMouseButton() {
        return movementMouseButton;
    }

    /**
     * Sets the mouse button that is used for moving the map. Possible values are:
     * <ul>
     * <li>{@link MouseEvent#BUTTON1} (left mouse button)</li>
     * <li>{@link MouseEvent#BUTTON2} (middle mouse button)</li>
     * <li>{@link MouseEvent#BUTTON3} (right mouse button)</li>
     * </ul>
     *
     * @param movementMouseButton the mouse button that is used for moving the map
     */
    public void setMovementMouseButton(int movementMouseButton) {
        this.movementMouseButton = movementMouseButton;
        switch (movementMouseButton) {
            case MouseEvent.BUTTON1:
                movementMouseButtonMask = MouseEvent.BUTTON1_DOWN_MASK;
                break;
            case MouseEvent.BUTTON2:
                movementMouseButtonMask = MouseEvent.BUTTON2_DOWN_MASK;
                break;
            case MouseEvent.BUTTON3:
                movementMouseButtonMask = MouseEvent.BUTTON3_DOWN_MASK;
                break;
            default:
                throw new RuntimeException("Unsupported button");
        }
    }

    public boolean isWheelZoomEnabled() {
        return wheelZoomEnabled;
    }

    public void setWheelZoomEnabled(boolean wheelZoomEnabled) {
        this.wheelZoomEnabled = wheelZoomEnabled;
    }

    public boolean isDoubleClickZoomEnabled() {
        return doubleClickZoomEnabled;
    }

    public void setDoubleClickZoomEnabled(boolean doubleClickZoomEnabled) {
        this.doubleClickZoomEnabled = doubleClickZoomEnabled;
    }

    @Override
    public void mouseEntered(MouseEvent e) {
    }

    @Override
    public void mouseExited(MouseEvent e) {
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        // Mac OSX simulates with  ctrl + mouse 1  the second mouse button hence no dragging events get fired.
        //
        if (isPlatformOsx()) {
            if (!movementEnabled || !isMoving)
                return;
            // Is only the selected mouse button pressed?
            if (e.getModifiersEx() == MouseEvent.CTRL_DOWN_MASK) {
                Point p = e.getPoint();
                if (lastDragPoint != null) {
                    int diffx = lastDragPoint.x - p.x;
                    int diffy = lastDragPoint.y - p.y;
                    map.moveMap(diffx, diffy);
                }
                lastDragPoint = p;
            }

        }

    }

    /**
     * Replies true if we are currently running on OSX
     *
     * @return true if we are currently running on OSX
     */
    public static boolean isPlatformOsx() {
        String os = System.getProperty("os.name");
        return os != null && os.toLowerCase().startsWith("mac os x");
    }
}
