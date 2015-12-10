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
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
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
@SuppressWarnings("javadoc")
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
    
    private int startIndex = -1;
    private int targetIndex = -1;

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

    double gridRaster;
    double gridMinLat;
    double gridMinLon;
    int gridLatCount;
    int gridLonCount;
    int[][] gridNodeOffsets;
    int[][] gridNodeCounts;
        
        
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
        

        
        
        // Test
        long startTime;
        
        startTime = System.currentTimeMillis();
        startIndex = findNextNode(new Coordinate(48.68, 9.00), (byte)0, (byte)0);
        targetIndex = findNextNode(new Coordinate(48.84, 9.26), (byte)0, (byte)0);
        calculateRoute(TransportMode.Car, RoutingMode.Fastest);
        System.out.println("Time: " + (System.currentTimeMillis() - startTime));

        startTime = System.currentTimeMillis();
        startIndex = findNextNode(new Coordinate(47.8, 9.0), (byte)0, (byte)0);
        targetIndex = findNextNode(new Coordinate(49.15, 9.22), (byte)0, (byte)0);
        calculateRoute(TransportMode.Car, RoutingMode.Fastest);
        System.out.println("Time: " + (System.currentTimeMillis() - startTime));
    }
    
    
    @SuppressWarnings("resource")
    private void loadOsmData() throws Exception {
        
        //String inDir = "D:\\Jonas\\OSM\\germany";
        String inDir = "D:\\Jonas\\OSM\\bawue";

        {
        System.out.println("Start reading nodes");
        ObjectInputStream nodeReader = new ObjectInputStream(new FileInputStream(inDir + "\\nodes-final.bin"));
        
        nodeCount = (Integer)nodeReader.readObject();
        nodesLat = (double[])nodeReader.readObject();
        nodesLon = (double[])nodeReader.readObject();
        nodesEdgeOffset = (int[])nodeReader.readObject();
        
        nodeReader.close();
        System.out.println("Finished reading nodes");
        }
        
        {
        System.out.println("Start reading edges");
        ObjectInputStream edgeReader = new ObjectInputStream(new FileInputStream(inDir + "\\edges-final.bin"));
        edgeCount = (Integer)edgeReader.readObject();
        edgesTarget = (int[])edgeReader.readObject();
        edgesInfobits =(byte[])edgeReader.readObject();
        edgesLengths = (short[])edgeReader.readObject();
        edgesMaxSpeeds = (byte[])edgeReader.readObject();

        edgeReader.close();
        System.out.println("Finished reading edges");
        }
        
        // Output
        {
            System.out.println("Start reading grid");
            ObjectInputStream gridReader = new ObjectInputStream(
                    new FileInputStream(inDir + "\\grid-final.bin"));
            gridRaster = gridReader.readDouble();
            gridMinLat = gridReader.readDouble();
            gridMinLon = gridReader.readDouble();
            gridLatCount = gridReader.readInt();
            gridLonCount = gridReader.readInt();
            gridNodeOffsets = (int[][])gridReader.readObject();
            gridNodeCounts = (int[][])gridReader.readObject();
            gridReader.close();
            System.out.println("Finished reading grid");
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
            
            int clickNextPt = findNextNode(clickCoord, (byte)0, (byte)0);            
            if(clickNextPt == -1) {
                System.err.println("No point found");
                return;
            }
            
            if(e.getButton() == MouseEvent.BUTTON1) {
                startIndex = clickNextPt;
            } 
            else if(e.getButton() == MouseEvent.BUTTON3) {
                targetIndex = clickNextPt;
            } 
            
            clearMarkers();
            if(startIndex != -1) {
                MapMarkerDot startDot = new MapMarkerDot("Start", new Coordinate(nodesLat[startIndex], nodesLon[startIndex]));
                map.addMapMarker(startDot);
                routeDots.add(startDot);
            }        
            if(targetIndex != -1) {
                MapMarkerDot targetDot = new MapMarkerDot("Target", new Coordinate(nodesLat[targetIndex], nodesLon[targetIndex]));
                map.addMapMarker(targetDot);
                routeDots.add(targetDot);
            }
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
    private int findNextNode(Coordinate coord, byte filterBitMask, byte filterBitValue) {
        int nextIndex = -1;
        double smallestDist = Double.MAX_VALUE;

        
        int latI = (int)((coord.getLat() - gridMinLat) / gridRaster);
        int lonI = (int)((coord.getLon() - gridMinLon) / gridRaster);
        
        latI = Math.max(0, Math.min(gridLatCount-1, latI));
        lonI = Math.max(0, Math.min(gridLonCount-1, lonI));
        
        int iMin = gridNodeOffsets[latI][lonI];
        int iMax = iMin + gridNodeCounts[latI][lonI];
        
        for(int i = iMin; i < iMax; i++) {                        
            if(!checkNodeWithFilter(i, filterBitMask, filterBitValue)) {
                continue;
            }
                        
            GeodesicData g = Geodesic.WGS84.Inverse(coord.getLat(), coord.getLon(), nodesLat[i], nodesLon[i]);
            if(g.s12 < smallestDist) {
                smallestDist = g.s12;
                nextIndex = i;
            }
        }
        return nextIndex;
    }
    
    private boolean checkNodeWithFilter(int i, byte filterBitMask, byte filterBitValue) {
        if (filterBitMask == 0) {
            return true;
        }

        boolean match = false;
        for (int iEdge = nodesEdgeOffset[i]; (i + 1 < nodesEdgeOffset.length && iEdge < nodesEdgeOffset[i + 1])
                || (i + 1 == nodesEdgeOffset.length && iEdge < edgeCount); // Last node in offset array
                iEdge++) {
            // Skip if edge not accessible
            if ((edgesInfobits[iEdge] & filterBitMask) == filterBitValue) {
                match = true;
                break;
            }
        }

        // Skip if edge not accessible
        return match;
    }
    
    private void clearMarkers() {        
        // Remove old dots and lines
        for(MapMarkerDot dot : routeDots) {
            map.removeMapMarker(dot);
        }
        routeDots.clear();
        for(MapPolygonImpl line : routeLines) {
            map.removeMapPolygon(line);
        }
        routeLines.clear();
    }

    
    // Info bits: 0,0,0,0,0,[Car],[Ped],[Oneway]
    // TODO Zugangsbeschränkung
    private byte carBitMask = 5;
    private byte carBitValue = 4;
    private byte pedBitMask = 2;
    private byte pedBitValue = 2;
    
    public enum TransportMode { Car, Pedestrian, Maniac }
    public enum RoutingMode { Fastest, Shortest }
    // TODO Intelligent? costs for street switching
    
    public void calculateRoute(TransportMode transportMode, RoutingMode routeMode) {

        if (startIndex == -1 || targetIndex == -1) {
            System.err.println("Cannot calculate route: Must select start and target");
            return;
        }
         
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
        
        
        Coordinate startCoord = new Coordinate(nodesLat[startIndex], nodesLon[startIndex]);
        Coordinate targetCoord = new Coordinate(nodesLat[targetIndex], nodesLon[targetIndex]);
        
        // Find better start and end points if not suitable
        if(!checkNodeWithFilter(startIndex, edgeFilterBitMask, edgeFilterBitValue)) {
            startIndex = findNextNode(startCoord, edgeFilterBitMask, edgeFilterBitValue);
            startCoord = new Coordinate(nodesLat[startIndex], nodesLon[startIndex]);
        }
        if(!checkNodeWithFilter(targetIndex, edgeFilterBitMask, edgeFilterBitValue)) {
            targetIndex = findNextNode(targetCoord, edgeFilterBitMask, edgeFilterBitValue);
            targetCoord = new Coordinate(nodesLat[targetIndex], nodesLon[targetIndex]);
        }   

        // Clear markers
        clearMarkers();
        // Draw start and target
        {
            MapMarkerDot startDot =
                    new MapMarkerDot("Start", startCoord);
            map.addMapMarker(startDot);
            routeDots.add(startDot);
            MapMarkerDot targetDot =
                    new MapMarkerDot("Target", targetCoord);
            map.addMapMarker(targetDot);
            routeDots.add(targetDot);
        }

        
        Random rd = new Random(123);
        double debugDispProp = 0.9999;
        int maxMarkerCountdown = 50;

        
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
        while (!routeDistHeap.isEmpty()) {
            // Get dist, remove and get index
            int nodeDist = routeDistHeap.peekNodeValue();

            // Break if node not visited yet
            if (nodeDist == Integer.MAX_VALUE) {
                System.err.println("Node with no distance set found, break after " + visitedCount + " nodes visited, "
                        + routeDistHeap.getSize() + " nodes not visited");
                break;
            }

            //System.out.println(nodeDist);

            // Remove and get index
            int nodeIndex = routeDistHeap.remove();

            if (nodeIndex == targetIndex) {
                System.out.println("Found after " + visitedCount + " nodes visited. " + routeDistHeap.getSize() + " nodes not visited");
                System.out.println("Dist: " + nodeDist);
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
                MapMarkerDot dot = new MapMarkerDot(new Coordinate(nodesLat[nodeIndex], nodesLon[nodeIndex]));
                map.addMapMarker(dot);
                routeDots.add(dot);
                maxMarkerCountdown--;
            }
            //System.out.println(nextIndex);

            // Iterate over edges to neighbors
            for (int iEdge = nodesEdgeOffset[nodeIndex]; (nodeIndex + 1 < nodesEdgeOffset.length && iEdge < nodesEdgeOffset[nodeIndex + 1])
                    || (nodeIndex + 1 == nodesEdgeOffset.length && iEdge < edgeCount); // Last node in offset array
                    iEdge++) {
                int nbIndex = edgesTarget[iEdge];

                // Skip if edge not accessible
                if ((edgesInfobits[iEdge] & edgeFilterBitMask) != edgeFilterBitValue) {
                    continue;
                }

                // Distance calculation, depending on routing mode
                int nbDist;
                int edgeDist = Short.toUnsignedInt(edgesLengths[iEdge]);
                if (routeMode == RoutingMode.Fastest) {
                    int maxSpeed = (int) Byte.toUnsignedLong(edgesMaxSpeeds[iEdge]);
                    maxSpeed = Math.max(allMinSpeed, Math.min(allMaxSpeed, maxSpeed));
                    nbDist = nodeDist + (edgeDist * 1000 / maxSpeed);
                } else if (routeMode == RoutingMode.Shortest) {
                    nbDist = nodeDist + edgeDist;
                } else {
                    throw new RuntimeException("Unsupported routing mode: " + routeMode);
                }

                if (!nodesVisitedBuffer[nbIndex]) {
                    if (routeDistHeap.decreaseKeyIfSmaller(nbIndex, nbDist)) {
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
