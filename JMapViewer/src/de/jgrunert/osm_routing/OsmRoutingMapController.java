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
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.Set;

import net.sf.geographiclib.Geodesic;
import net.sf.geographiclib.GeodesicData;

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
    
    
    private static final short CAR_MAXSPEED = 150;
    private static final short PED_MAXSPEED = 5;
    
    private int startIndex;
    private Coordinate startLoc = null;
    private int targetIndex;
    private Coordinate targetLoc = null;

    private List<MapMarkerDot> routeDots = new ArrayList<>();
    private List<MapPolygonImpl> routeLines = new ArrayList<>();
    
    int nodeCount = 0;
    double[] nodesLat;
    double[] nodesLon;
    int[] nodesEdgeOffset;
   
    // Buffer for predecessors when calculating routes
    int[] nodesPreBuffer;
    // Buffer for node distances when calculating routes
    int[] nodesDistBuffer;
    // Buffer for node visisted when calculating routes
    boolean[] nodesVisitedBuffer;
    
    int edgeCount = 0;
    int[] edgesTarget;
    byte[] edgesInfobits;
    short[] edgeLengths;
    byte[] edgeMaxSpeeds;
        
        
   /**
    * Constructor
    * @param map JMapViewer
    */
    public OsmRoutingMapController(JMapViewer map) {
        super(map);
        
        try {
            loadOsmData();    
            
            nodesPreBuffer = new int[nodeCount];
            nodesDistBuffer = new int[nodeCount];
            nodesVisitedBuffer = new boolean[nodeCount];
        } catch (Exception e) {
            System.err.println("Error at loadOsmData");
            e.printStackTrace();
        }
        
        
        int[] distBuffer = new int[5];
        distBuffer[0] = 9;
        distBuffer[1] = 1;
        distBuffer[2] = 8;
        distBuffer[3] = 3;
        distBuffer[4] = 2;
        
        BinaryHeap beap = new BinaryHeap();    
        beap.add(9);
        beap.add(8);
        beap.add(2);
        beap.add(3);
        beap.add(4);
        
        System.out.println(beap.remove());
        System.out.println(beap.remove());
        System.out.println(beap.remove());
        System.out.println(beap.remove());
        System.out.println(beap.remove());
        
        System.out.println("---");
        
        NodeDistHeap neap = new NodeDistHeap(5);
//        beap.decreaseDist(0, 9);       
//        beap.decreaseDist(1, 8);  
//        beap.decreaseDist(2, 2);   
//        beap.decreaseDist(3, 3);  
//        beap.decreaseDist(4, 4);      
        neap.add(9);
        neap.add(8);
        neap.add(2);
        neap.add(3);
        neap.add(4);
        
        System.out.println(neap.peekNodeDist() + " " + neap.remove());
        System.out.println(neap.peekNodeDist() + " " + neap.remove());
        System.out.println(neap.peekNodeDist() + " " + neap.remove());
        System.out.println(neap.peekNodeDist() + " " + neap.remove());
        System.out.println(neap.peekNodeDist() + " " + neap.remove());
        System.out.println(neap.peekNodeDist() + " " + neap.remove());
    }
    
    
    @SuppressWarnings("resource")
    private void loadOsmData() throws Exception {

        {
        System.out.println("Start reading nodes");
        ObjectInputStream nodeReader = new ObjectInputStream(new FileInputStream("D:\\Jonas\\OSM\\hamburg\\nodes-final.bin"));
        
        nodeCount = (Integer)nodeReader.readObject();
        nodesLat = (double[])nodeReader.readObject();
        nodesLon = (double[])nodeReader.readObject();
        nodesEdgeOffset = (int[])nodeReader.readObject();
        
        nodeReader.close();
        System.out.println("Finished reading nodes");
        }
        
        {
        System.out.println("Start reading edges");
        ObjectInputStream edgeReader = new ObjectInputStream(new FileInputStream("D:\\Jonas\\OSM\\hamburg\\edges-final.bin"));
        edgeCount = (Integer)edgeReader.readObject();
        edgesTarget = (int[])edgeReader.readObject();
        edgesInfobits =(byte[])edgeReader.readObject();
        edgeLengths = (short[])edgeReader.readObject();
        edgeMaxSpeeds = (byte[])edgeReader.readObject();

        edgeReader.close();
        System.out.println("Finished reading edges");
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
            Coordinate clickCoord = new Coordinate(clickPt.getLat(), clickPt.getLon());
            
            int clickNextPt = findNextPoint(clickCoord);            
            if(clickNextPt == -1) {
                System.err.println("No point found");
                return;
            }
            
            Coordinate clickNextPtCoord = new Coordinate(nodesLat[clickNextPt], nodesLon[clickNextPt]);

            if(e.getButton() == MouseEvent.BUTTON1) {
                startIndex = clickNextPt;
                startLoc = clickNextPtCoord;
            } 
            else if(e.getButton() == MouseEvent.BUTTON3) {
                targetIndex = clickNextPt;
                targetLoc = clickNextPtCoord;
            } 

            updateRoute();
        } 
        else if (doubleClickZoomEnabled && e.getClickCount() == 2 && e.getButton() == MouseEvent.BUTTON1) {
            // Zoom on doubleclick
            map.zoomIn(e.getPoint());
        } 
    }
    
    /**
     * Tries to find out index of next point to given coordinate
     * @param coord
     * @return Index of next point
     */
    private int findNextPoint(Coordinate coord) {
        int nextIndex = -1;
        double smallestDist = Double.MAX_VALUE;
        for(int i = 0; i < nodeCount; i++) {
            GeodesicData g = Geodesic.WGS84.Inverse(coord.getLat(), coord.getLon(), nodesLat[i], nodesLon[i]);
            if(g.s12 < smallestDist) {
                smallestDist = g.s12;
                nextIndex = i;
            }
        }
        return nextIndex;
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
        
//        if(startLoc != null && targetLoc != null) {
//            MapPolygonImpl routPoly = new MapPolygonImpl("Route", startLoc, targetLoc, targetLoc);
//            routeLines.add(routPoly);
//            map.addMapPolygon(routPoly);
//        }

        
        Random rd = new Random(123);
        double debugDispProp = 0.99999;
        
        if (startLoc != null && targetLoc != null) {

            // Reset buffers
            Arrays.fill(nodesDistBuffer, Integer.MAX_VALUE);
            Arrays.fill(nodesVisitedBuffer, false);
            
            
            // Start node
            nodesDistBuffer[startIndex] = 0;
            nodesVisitedBuffer[startIndex] = true;
            

            // Find route with Dijkstra
            Queue<Integer> queue = new LinkedList<>();
            queue.add(startIndex);
            while (!queue.isEmpty()) {
                int nextIndex = queue.remove();

                if (nextIndex == targetIndex) {
                    System.out.println("Found!");
                    break;
                } else {
                    //System.out.println("Not found");
                }

                nodesVisitedBuffer[nextIndex] = true;

                // Display
                if (rd.nextDouble() > debugDispProp) {
                    MapMarkerDot targetDot = new MapMarkerDot(new Coordinate(nodesLat[nextIndex], nodesLon[nextIndex]));
                    map.addMapMarker(targetDot);
                    routeDots.add(targetDot);
                }
                //                  System.out.println(nextIndex);

                for (int iTarg = nodesEdgeOffset[nextIndex]; (nextIndex + 1 < nodesEdgeOffset.length && iTarg < nodesEdgeOffset[nextIndex + 1])
                        || (nextIndex + 1 == nodesEdgeOffset.length && iTarg < edgesTarget.length); // Last node in offset array
                iTarg++) {
                    int targ = edgesTarget[iTarg];
                    if (!nodesVisitedBuffer[targ]) {
                        nodesPreBuffer[targ] = nextIndex;
                        queue.add(targ);
                    } else {
                        //System.out.println("Already visited");
                    }
                }
            }
            
            // TODO: What when not found?

            // Reconstruct route
            int i = targetIndex;
            while (i != startIndex) {
                int pre = nodesPreBuffer[i];
                //                if (pre == targetIndex) {
                //                    continue;
                //                }

                Coordinate c1 = new Coordinate(nodesLat[pre], nodesLon[pre]);
                Coordinate c2 = new Coordinate(nodesLat[i], nodesLon[i]);

                MapPolygonImpl routPoly = new MapPolygonImpl(c1, c2, c2);
                routeLines.add(routPoly);
                map.addMapPolygon(routPoly);

                //                MapMarkerDot dot = new MapMarkerDot(new Coordinate(nodesLat[i], nodesLon[i]));
                //                map.addMapMarker(dot);
                //                routeDots.add(dot);

                i = pre;
            }
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

    @SuppressWarnings("javadoc")
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

    @SuppressWarnings("javadoc")
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

    @SuppressWarnings("javadoc")
    public boolean isWheelZoomEnabled() {
        return wheelZoomEnabled;
    }

    @SuppressWarnings("javadoc")
    public void setWheelZoomEnabled(boolean wheelZoomEnabled) {
        this.wheelZoomEnabled = wheelZoomEnabled;
    }

    @SuppressWarnings("javadoc")
    public boolean isDoubleClickZoomEnabled() {
        return doubleClickZoomEnabled;
    }

    @SuppressWarnings("javadoc")
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
