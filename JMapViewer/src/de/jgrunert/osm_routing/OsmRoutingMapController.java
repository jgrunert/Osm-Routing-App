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
    // Buffer for node visisted when calculating routes
    boolean[] nodesVisitedBuffer;
    // Heap for rout finding
    NodeDistHeap routeDistHeap;
    
    int edgeCount = 0;
    int[] edgesTarget;
    byte[] edgesInfobits;
    short[] edgesLengths;
    byte[] edgesMaxSpeeds;
        
        
   /**
    * Constructor
    * @param map JMapViewer
    */
    public OsmRoutingMapController(JMapViewer map) {
        super(map);
        
        try {
            loadOsmData();    
            
            nodesPreBuffer = new int[nodeCount];
            nodesVisitedBuffer = new boolean[nodeCount];
            routeDistHeap = new NodeDistHeap(nodeCount);
        } catch (Exception e) {
            System.err.println("Error at loadOsmData");
            e.printStackTrace();
        }
        
        
        //NodeDistHeap beap = new NodeDistHeap(nodeCount);    
//        beap.add(9, 0);
//        beap.add(8, 1);
//        beap.add(2, 2);
//        beap.add(3, 4);
//        beap.add(4, 3);
//        System.out.println("1");
//        beap.decreaseKey(4, 10);
//        beap.decreaseKey(2, 5);
//        beap.decreaseKey(3, 3);
//        beap.decreaseKey(4, 2);
//        System.out.println("2");
//        
//        System.out.println(beap.peekValue() + " " + beap.remove());
//        System.out.println(beap.peekValue() + " " + beap.remove());
//        System.out.println(beap.peekValue() + " " + beap.remove());
//        System.out.println(beap.peekValue() + " " + beap.remove());
//        System.out.println(beap.peekValue() + " " + beap.remove());
    }
    
    
    @SuppressWarnings("resource")
    private void loadOsmData() throws Exception {

        {
        System.out.println("Start reading nodes");
        //ObjectInputStream nodeReader = new ObjectInputStream(new FileInputStream("D:\\Jonas\\OSM\\germany\\nodes-final.bin"));
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
        //ObjectInputStream edgeReader = new ObjectInputStream(new FileInputStream("D:\\Jonas\\OSM\\germany\\edges-final.bin"));
        ObjectInputStream edgeReader = new ObjectInputStream(new FileInputStream("D:\\Jonas\\OSM\\hamburg\\edges-final.bin"));
        edgeCount = (Integer)edgeReader.readObject();
        edgesTarget = (int[])edgeReader.readObject();
        edgesInfobits =(byte[])edgeReader.readObject();
        edgesLengths = (short[])edgeReader.readObject();
        edgesMaxSpeeds = (byte[])edgeReader.readObject();

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

            updateRoute(TransportMode.Car, RoutingMode.Shortest);
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
        for(int i = 0; i < 100000; i++) {  // TODO TEST
         // for(int i = 0; i < nodeCount; i++) {
            GeodesicData g = Geodesic.WGS84.Inverse(coord.getLat(), coord.getLon(), nodesLat[i], nodesLon[i]);
            if(g.s12 < smallestDist) {
                smallestDist = g.s12;
                nextIndex = i;
            }
        }
        return nextIndex;
    }

    
    // Info bits: 0,0,0,0,0,[Car],[Ped],[Oneway]
    // TODO Zugangsbeschränkung
    byte carBitMask = 5;
    byte carBitValue = 4;
    byte pedBitMask = 2;
    byte pedBitValue = 2;
    
    enum TransportMode { Car, Pedestrian, IDoWhatIWant }
    enum RoutingMode { Fastest, Shortest }
    // TODO Intelligent? costs for street switching
    
    private void updateRoute(TransportMode transportMode, RoutingMode routeMode) {
                
        // Edge bitfilter and speed
        final byte edgeFilterBitMask;
        final byte edgeFilterBitValue;  
        final int allMaxSpeed;
        final int allMinSpeed = PED_MAXSPEED;
        
        switch (transportMode) {
        case Car:
            edgeFilterBitMask = carBitMask;
            edgeFilterBitValue = carBitValue;
            allMaxSpeed = CAR_MAXSPEED;
            break;
        case Pedestrian:
            edgeFilterBitMask = pedBitMask;
            edgeFilterBitValue = pedBitValue;
            allMaxSpeed = PED_MAXSPEED;
            break;
        default:
            edgeFilterBitMask = 0;
            edgeFilterBitValue = 0;
            allMaxSpeed = CAR_MAXSPEED;
            break;
        }
        
        
        
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
        double debugDispProp = 0.9999;
        int maxMarkerCountdown = 50;
        
        if (startLoc != null && targetLoc != null) {

            // Reset buffers
            routeDistHeap.reset();
            Arrays.fill(nodesVisitedBuffer, false);
            // TODO Add only relevant nodes?
            
            
            // Start node
            routeDistHeap.decreaseKey(startIndex, 0);
            nodesVisitedBuffer[startIndex] = true;
            

            int visitedCount = 0;
            boolean found = false;
            
            // Find route with Dijkstra
            while (!routeDistHeap.isEmpty()) 
            {
                // Get dist, remove and get index
                int nodeDist = routeDistHeap.peekNodeValue();
                
                // Break if node not 
                if(nodeDist == Integer.MAX_VALUE) {
                    System.err.println("Node with no distance set found, break after " + visitedCount + " nodes visited, " + 
                                        routeDistHeap.getSize() + " nodes not visited");
                    break;
                }
                
                //System.out.println(nodeDist);
                
                // Remove and get index
                int nodeIndex = routeDistHeap.remove();

                if (nodeIndex == targetIndex) {
                    System.out.println("Found! Dist: " + nodeDist);
                    found = true;
                    break;
                } else {
                    //System.out.println("Not found");
                }
                
                // Mark as visited
                nodesVisitedBuffer[nodeIndex] = true;
                visitedCount++;


                // Display
                if (maxMarkerCountdown > 0 && rd.nextDouble() > debugDispProp) {
                    MapMarkerDot targetDot = new MapMarkerDot(new Coordinate(nodesLat[nodeIndex], nodesLon[nodeIndex]));
                    map.addMapMarker(targetDot);
                    routeDots.add(targetDot);
                    maxMarkerCountdown--;
                }
                //System.out.println(nextIndex);

                // Iterate over edges to neighbors
                for (int iEdge = nodesEdgeOffset[nodeIndex]; (nodeIndex + 1 < nodesEdgeOffset.length && iEdge < nodesEdgeOffset[nodeIndex + 1])
                        || (nodeIndex + 1 == nodesEdgeOffset.length && iEdge < edgesTarget.length); // Last node in offset array
                        iEdge++) 
                {
                    int nbIndex = edgesTarget[iEdge];
                    
                    if((edgesInfobits[iEdge] & edgeFilterBitMask) != edgeFilterBitValue) {
                        continue;
                    }
                    
                    // Distance calculation, depending on routing mode
                    int nbDist;
                    if (routeMode == RoutingMode.Fastest) {
                        int maxSpeed = (int) Byte.toUnsignedLong(edgesMaxSpeeds[iEdge]);
                        maxSpeed = Math.min(allMinSpeed, Math.max(allMaxSpeed, maxSpeed));
                        nbDist = (edgesLengths[iEdge] * 1000 / maxSpeed) + nodeDist;
                    } else if(routeMode == RoutingMode.Shortest) {
                        nbDist = edgesLengths[iEdge] + nodeDist;                        
                    } else {
                        throw new RuntimeException("Unsupported routing mode: " + routeMode);
                    }
                    
                    if (!nodesVisitedBuffer[nbIndex]) {
                        if(routeDistHeap.decreaseKeyIfSmaller(nbIndex, nbDist)) {
                            nodesPreBuffer[nbIndex] = nodeIndex;
                        }
                    } else {
                        //System.out.println("Already visited");
                    }
                }
            }
            
            if (found) {
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
            } else {
                System.err.println("No way found");
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
