// License: GPL. For details, see Readme.txt file.
package de.jgrunert.osm_routing;

import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.openstreetmap.gui.jmapviewer.Coordinate;
import org.openstreetmap.gui.jmapviewer.JMapController;
import org.openstreetmap.gui.jmapviewer.JMapViewer;
import org.openstreetmap.gui.jmapviewer.MapMarkerDot;
import org.openstreetmap.gui.jmapviewer.MapPolygonImpl;
import org.openstreetmap.gui.jmapviewer.interfaces.ICoordinate;


/**
 * Default map controller which implements map moving by pressing the right
 * mouse button and zooming by float click or by mouse wheel.
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
    private boolean floatClickZoomEnabled = true;
    
    
    // General constants
    //private static final String MAP_DIR = "D:\\Jonas\\OSM\\germany";
    //private static final String MAP_DIR = "D:\\Jonas\\OSM\\bawue";
    private static final String MAP_DIR = "D:\\Jonas\\OSM\\hamburg";
    
    
    // Routing constants
    private static final short CAR_MAXSPEED = 150;
    private static final short PED_MAXSPEED = 5;
    private static int ROUTE_HEAP_CAPACITY = 1000000;
    
    
    // Start and end for route
    private Long startNodeGridIndex = null;
    private Long targetNodeGridIndex = null;
    
    
    // Route and point display
    private List<MapMarkerDot> routeDots = new ArrayList<>();
    private List<MapPolygonImpl> routeLines = new ArrayList<>();

    
    // Grid information
    float gridRaster;
    float gridMinLat;
    float gridMinLon;
    int gridLatCount;
    int gridLonCount;
    // Indices of grids (iLat/iLon to index)
    int[][] gridIndices;
    // List of all grids, loaded or not loaded
    ArrayList<MapGrid> grids;
    int gridsLoadedCount;
    // TODO Grid offloading
    
    // Heap for rout finding
    NodeDistHeap routeDistHeap;
            
        
    
   /**
    * Constructor
    * @param map JMapViewer
    */
    public OsmRoutingMapController(JMapViewer map) {
        super(map);
        
        try {            
            loadGridIndex();    
            
            routeDistHeap = new NodeDistHeap(ROUTE_HEAP_CAPACITY);
        } catch (Exception e) {
            System.err.println("Error at loadOsmData");
            e.printStackTrace();
        }
        

        
        
        // Test
//        long startTime;
        
//        startTime = System.currentTimeMillis();
//        startNodeIndex = findNextNode(new Coordinate(48.68, 9.00), (byte)0, (byte)0);
//        targetNodeIndex = findNextNode(new Coordinate(48.84, 9.26), (byte)0, (byte)0);
//        //calculateRoute(TransportMode.Car, RoutingMode.Fastest);
//        calculateRoute(TransportMode.Car, RoutingMode.Shortest);
//        System.out.println("Time: " + (System.currentTimeMillis() - startTime));
        
//        System.out.println(Utils.calcNodeDist(48.68f, 9.00f,48.84f, 9.26f));
//        System.out.println(Utils.calcNodeDistFast(48.68f, 9.00f,48.84f, 9.26f));
//        System.out.println(Utils.calcNodeDist(47.8f, 9.0f, 49.15f, 9.22f));
//        System.out.println(Utils.calcNodeDistFast(47.8f, 9.0f, 49.15f, 9.22f));

//        startTime = System.currentTimeMillis();
//        float max = 0.0f;
//        for(int i = 0; i < nodeCount; i++) {
//            max = Math.max(max, Utils.calcNodeDist(nodesLat[i], nodesLon[i], (float)nodesLat[i], (float)nodesLon[i]));
//        }
//        System.err.println(max);
//        System.out.println("Time: " + (System.currentTimeMillis() - startTime));

//        startTime = System.currentTimeMillis();
        startNodeGridIndex = findNextNode(47.8f, 9.0f, (byte)0, (byte)0);
        targetNodeGridIndex = findNextNode(49.15f, 9.22f, (byte)0, (byte)0);
        //calculateRoute(TransportMode.Car, RoutingMode.Fastest);
        //calculateRoute(TransportMode.Car, RoutingMode.Shortest);
    }
    
    /**
     * Read and initialize grid information
     * @throws Exception
     */
    private void loadGridIndex() throws Exception 
    {
        System.out.println("Start loading grid index");
        try (ObjectInputStream gridReader =
                new ObjectInputStream(new FileInputStream(MAP_DIR + "\\grids\\grids.index"))) {
            gridRaster = gridReader.readFloat();
            gridMinLat = gridReader.readFloat();
            gridMinLon = gridReader.readFloat();
            gridLatCount = gridReader.readInt();
            gridLonCount = gridReader.readInt();

            grids = new ArrayList<>(gridLatCount * gridLonCount);
            gridsLoadedCount = 0;
            gridIndices = new int[gridLatCount][gridLonCount];
            int iGrid = 0;
            for (int iLat = 0; iLat < gridLatCount; iLat++) {
                for (int iLon = 0; iLon < gridLonCount; iLon++) {
                    grids.add(new MapGrid()); // Initialize with empty, not loaded grids
                    gridIndices[iLat][iLon] = iGrid;
                    iGrid++;
                }
            }

            gridReader.close();
        }
        System.out.println("Finished loading grid index");
    }
    
    
    /**
     * Loads grid, caches and returns it
     */
    private MapGrid loadGrid(int gridIndex) {
        try {
            MapGrid loaded = new MapGrid(MAP_DIR + "\\grids\\" + gridIndex + ".grid");
            grids.set(gridIndex, loaded);
            gridsLoadedCount++;
            System.out.println("Loaded grid " + gridIndex + ". Grids loaded: " + gridsLoadedCount);
            return loaded;
        } catch (Exception e) {
            System.err.println("Failed to load grid");
            e.printStackTrace();
            return grids.get(gridIndex);
        }
    }
    
    private void unloadGrid(int gridIndex) {
        grids.set(gridIndex, new MapGrid());
        gridsLoadedCount--;
        System.out.println("Unloaded grid " + gridIndex + ". Grids loaded: " + gridsLoadedCount);
    }
    
    
    /**
     * Returns grid a point is located in or next grid if not in any grid
     */
    private int getGridOfPoint(float lat, float lon) {
        int latI = (int)((lat - gridMinLat) / gridRaster);
        int lonI = (int)((lon - gridMinLon) / gridRaster);        
        latI = Math.max(0, Math.min(gridLatCount-1, latI));
        lonI = Math.max(0, Math.min(gridLonCount-1, lonI));        
        return gridIndices[latI][lonI];
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
            
            Long clickNextPt = findNextNode((float)clickCoord.getLat(), (float)clickCoord.getLon(), (byte)0, (byte)0);            
            if(clickNextPt == null) {
                System.err.println("No point found");
                return;
            }
            
            if(e.getButton() == MouseEvent.BUTTON1) {
                startNodeGridIndex = clickNextPt;
            } 
            else if(e.getButton() == MouseEvent.BUTTON3) {
                targetNodeGridIndex = clickNextPt;
            } 
            
            clearMarkers();
            if(startNodeGridIndex != null) {
                MapMarkerDot startDot = new MapMarkerDot("Start", getNodeCoordinates(startNodeGridIndex));
                map.addMapMarker(startDot);
                routeDots.add(startDot);
            }        
            if(targetNodeGridIndex != null) {
                MapMarkerDot targetDot = new MapMarkerDot("Target", getNodeCoordinates(targetNodeGridIndex));
                map.addMapMarker(targetDot);
                routeDots.add(targetDot);
            }
        } 
        else if (floatClickZoomEnabled && e.getClickCount() == 2 && e.getButton() == MouseEvent.BUTTON1) {
            // Zoom on floatclick
            map.zoomIn(e.getPoint());
        } 
    }
    
    

    /**
     * Tries to return a grid, tries to load grid if necessary
     */
    private MapGrid getGrid(int gridIndex) {
        MapGrid grid = grids.get(gridIndex);
        if(!grid.loaded) {
            grid = loadGrid(gridIndex);
        }
        return grid;
    }

    /**
     * Tries to determine coordinates of a node, tries to load grid if necessary
     * @return Coordinates of node
     */
    private Coordinate getNodeCoordinates(long nodeGridIndex) {
        return getNodeCoordinates((int)(nodeGridIndex >> 32), (short)nodeGridIndex);
    }
    
    /**
     * Tries to determine coordinates of a node, tries to load grid if necessary
     * @return Coordinates of node
     */
    private Coordinate getNodeCoordinates(int gridIndex, short nodeIndex) {
        MapGrid grid = getGrid(gridIndex);
        return getNodeCoordinates(grid, nodeIndex);
    }
    
    /**
     * Tries to determine coordinates of a node, tries to load grid if necessary
     * @return Coordinates of node
     */
    private Coordinate getNodeCoordinates(MapGrid grid, short nodeIndex) {
        return new Coordinate(grid.nodesLat[nodeIndex], grid.nodesLon[nodeIndex]);
    }
    
    
    /**
     * Tries to find out index of next point to given coordinate
     * @param coord
     * @return Index of next point
     */
    private Long findNextNode(float lat, float lon, byte filterBitMask, byte filterBitValue) 
    {
        // Get grid
        int gridIndex = getGridOfPoint(lat, lon);
        MapGrid grid = grids.get(gridIndex);
        if(!grid.loaded) {
            grid = loadGrid(gridIndex);
        }
        
        
        int nextIndex = -1;
        float smallestDist = Float.MAX_VALUE;
        
        for(int iN = 0; iN < grid.nodeCount; iN++) {                        
            if(!checkNodeWithFilter(grid, iN, filterBitMask, filterBitValue)) {
                continue;
            }
                        
           float dist = Utils.calcNodeDist(lat, lon, grid.nodesLat[iN], grid.nodesLon[iN]);
            if(dist < smallestDist) {
                smallestDist = dist;
                nextIndex = iN;
            }
        }
        
        if(nextIndex == -1) { return null; }
        
        return (((long)gridIndex) << 32) | (nextIndex & 0xffffffffL);
    }
    
    
    private boolean checkNodeWithFilter(MapGrid grid, int nodeIndex, byte filterBitMask, byte filterBitValue) {
        if (filterBitMask == 0) {
            return true;
        }

        boolean match = false;
        for (int iEdge = grid.nodesEdgeOffset[nodeIndex]; (nodeIndex + 1 < grid.nodesEdgeOffset.length && iEdge < grid.nodesEdgeOffset[nodeIndex + 1])
                || (nodeIndex + 1 == grid.nodesEdgeOffset.length && iEdge < grid.edgeCount); // Last node in offset array
                iEdge++) {
            // Skip if edge not accessible
            if ((grid.edgesInfobits[iEdge] & filterBitMask) == filterBitValue) {
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
    // TODO Intelligent? costs for street switching?
    
    

    public void calculateRoute(TransportMode transportMode, RoutingMode routeMode) {

        long startTime = System.currentTimeMillis();
        //calculateRouteDijkstra(transportMode, routeMode);
        calculateRouteAStar(transportMode, routeMode);
        System.out.println("Time: " + (System.currentTimeMillis() - startTime));
    }
    
  
    
    public void calculateRouteAStar(TransportMode transportMode, RoutingMode routeMode) {

        if (startNodeGridIndex == null || targetNodeGridIndex == null) {
            System.err.println("Cannot calculate route: Must select any start and target");
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
        
        
        Coordinate startCoord = getNodeCoordinates(startNodeGridIndex);
        Coordinate targetCoord = getNodeCoordinates(targetNodeGridIndex);
        
        // Find better start and end points if not suitable
        if(!checkNodeWithFilter(getGrid((int)(startNodeGridIndex >> 32)), (short)(long)startNodeGridIndex, edgeFilterBitMask, edgeFilterBitValue)) {
            startNodeGridIndex = findNextNode((float)startCoord.getLat(), (float)startCoord.getLon(), edgeFilterBitMask, edgeFilterBitValue);
            startCoord = getNodeCoordinates(startNodeGridIndex);
        }
        if(!checkNodeWithFilter(getGrid((int)(targetNodeGridIndex >> 32)), (short)(long)targetNodeGridIndex, edgeFilterBitMask, edgeFilterBitValue)) {
            targetNodeGridIndex = findNextNode((float)targetCoord.getLat(), (float)targetCoord.getLon(), edgeFilterBitMask, edgeFilterBitValue);
            getNodeCoordinates(targetNodeGridIndex);
        }   
        

        if (startNodeGridIndex == null || targetNodeGridIndex == null) {
            System.err.println("Cannot calculate route: Must select valid start and target");
            return;
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
        
        // DebugDisplay
        Random rd = new Random(123);
        float debugDispProp = 0.9999f;
        int maxMarkerCountdown = 50;


        int startGridIndex = (int)(startNodeGridIndex >> 32);
        short startNodeIndex = (short)(long)(startNodeGridIndex);
        MapGrid startGrid = getGrid(startGridIndex);

        int targetGridIndex = (int)(targetNodeGridIndex >> 32);
        short targetNodeIndex = (short)(long)(targetNodeGridIndex);
        MapGrid targetGrid = getGrid(targetGridIndex);
        float targetLat = targetGrid.nodesLat[targetNodeIndex];
        float targetLon = targetGrid.nodesLon[targetNodeIndex];
        
        // Reset buffers and 
        routeDistHeap.resetEmpty();
        Map<Integer, MapGridRoutingBuffer> routingGridBuffers = new HashMap<>(); // Stores all buffers for all grids involved in routing
        Map<Long, Float> nodesRouteOpenMap = new HashMap<>(); // Stores all open nodes and their heuristic
        // TODO routingBuffers offloading
        
        // Add startnode  
        routeDistHeap.add(startNodeGridIndex, 0.0f);
        routingGridBuffers.put(startGridIndex, new MapGridRoutingBuffer(startGrid.nodeCount));
        nodesRouteOpenMap.put(startNodeGridIndex, 0.0f);
        
        boolean found = false;
        long target = (long)targetNodeGridIndex;
        int visitedCount = 0;
        int hCalc = 0;
        int hReuse = 0;

        // Find route with A*
        while (!routeDistHeap.isEmpty()) {
            // Remove and get index
            long visNodeGridIndex = routeDistHeap.removeFirst();
            int visGridIndex = (int)(startNodeGridIndex >> 32);
            short visNodeIndex = (short)(long)(startNodeGridIndex);
            
            MapGrid visGrid = getGrid(visGridIndex);
            MapGridRoutingBuffer visGridRB = routingGridBuffers.get(visGridIndex);

            // Get distance of node from start, remove and get index
            float nodeDist = visGridRB.nodesRouteDists[visNodeIndex];

            // Found! Break loop
            if (visNodeIndex == target) {
                System.out.println("Found after " + visitedCount + " nodes visited. " + routeDistHeap.getSize() + " still in heap");
                System.out.println("Dist: " + nodeDist);
                found = true;
                break;
            }

            // Mark as closed/visited
            visGridRB.nodesRouteClosedList[visNodeIndex] = true;
            nodesRouteOpenMap.remove(visNodeGridIndex);
            visitedCount++;

            
            // Display
            if (maxMarkerCountdown > 0 && rd.nextFloat() > debugDispProp) {
                MapMarkerDot dot = new MapMarkerDot(getNodeCoordinates(visGrid, visNodeIndex));
                map.addMapMarker(dot);
                routeDots.add(dot);
                maxMarkerCountdown--;
            }
            //System.out.println(nextIndex);

            
            // Iterate over edges to neighbors
            for (int iEdge = visGrid.nodesEdgeOffset[visNodeIndex]; 
                    (visNodeIndex + 1 < visGrid.nodesEdgeOffset.length && iEdge < visGrid.nodesEdgeOffset[visNodeIndex + 1])
                    || (visNodeIndex + 1 == visGrid.nodesEdgeOffset.length && iEdge < visGrid.edgeCount); // Last node in offset array
                    iEdge++) {
                // Skip if edge not accessible
                if ((visGrid.edgesInfobits[iEdge] & edgeFilterBitMask) != edgeFilterBitValue) {
                    continue;
                }

                // Get neighbor
                int nbGridIndex = visGrid.edgesTargetGrid[iEdge];
                short nbNodeIndex = visGrid.edgesTargetGridIndex[iEdge];
                long nbNodeGridIndex = (((long)nbGridIndex) << 32) | (nbNodeIndex & 0xffffffffL);
                // Get neighbor grid routing buffer
                MapGridRoutingBuffer nbGridRB;
                if(nbGridIndex == visGridIndex) {
                    nbGridRB = visGridRB;
                } else {
                    nbGridRB = routingGridBuffers.get(nbGridIndex);
                }
                
                // Continue if target node node already in closed list
                if (nbGridRB.nodesRouteClosedList[nbNodeIndex]) {
                    continue;
                }
                
                // Distance/Time calculation, depending on routing mode
                final float nbDist;
                // Factor for heuristic
                final float hFactor;
                //float h;
                float edgeDist = visGrid.edgesLengths[iEdge];
                if (routeMode == RoutingMode.Fastest) {
                    // Fastest route
                    float maxSpeed = (float) Byte.toUnsignedLong(visGrid.edgesMaxSpeeds[iEdge]);
                    maxSpeed = Math.max(allMinSpeed, Math.min(allMaxSpeed, maxSpeed));
                    nbDist = nodeDist + (edgeDist / maxSpeed);
                    //float highwayBoost = (maxSpeed > 10) ? (maxSpeed > 50) ? (maxSpeed > 100) ? 1.6f : 1.3f : 1.2f : 1.0f; 
                    //float highwayBoost = (maxSpeed >= 100) ? 5.0f : 1.0f; 
                    //hFactor = 1.0f / allMaxSpeed / highwayBoost;
                    hFactor = 1.0f / allMaxSpeed;
                } else if (routeMode == RoutingMode.Shortest) {
                    // Shortest route
                    nbDist = nodeDist + edgeDist;
                    hFactor = 1.0f;
                } else {
                    throw new RuntimeException("Unsupported routing mode: " + routeMode);
                }
                


                //nodesPreBuffer[nbIndex] = nodeIndex; // TODO outside if?
                
                Float hExistign = nodesRouteOpenMap.get(nbNodeGridIndex);
                if (hExistign != null) {
                    hReuse++;
                    // Point open and not closed - update if necessary
                    if (routeDistHeap.decreaseKeyIfSmaller(nbNodeGridIndex, nbDist + hExistign)) {
                        nbGridRB.nodesPreBuffer[nbNodeIndex] = nbNodeGridIndex; 
                        nbGridRB.nodesRouteDists[nbNodeIndex] = nbDist;
                    }
                } else {
                    // Get neighbor grid routing buffer
                    MapGrid nbGrid;
                    if(nbGridIndex == visGridIndex) {
                        nbGrid = visGrid;
                    } else {
                        nbGrid = getGrid(nbGridIndex);
                    }                    
                    
                    float hNew = Utils.calcNodeDist(nbGrid.nodesLat[nbNodeIndex], nbGrid.nodesLon[nbNodeIndex], targetLat, targetLon) * hFactor;
                    //float hnew = 0.0f;
                    hCalc++;
                    nodesRouteOpenMap.put(nbNodeGridIndex, hNew);
                    // Point not found yet - add to heap and open list
                    routeDistHeap.add(nbNodeGridIndex, nbDist + hNew);
                    //nodesRouteOpenList[nbIndex] = true;
                    nbGridRB.nodesPreBuffer[nbNodeIndex] = nbNodeGridIndex; 
                    nbGridRB.nodesRouteDists[nbNodeIndex] = nbDist;
                }
            }
        }

        System.out.println("H calc: " + hCalc);
        System.out.println("H reuse: " + hReuse);
        System.out.println("MaxHeapSize: " + routeDistHeap.getSizeUsageMax());
        
        // TODO Time and dist
        if (found) {
            // Reconstruct route
            long i = targetNodeIndex;
            while (i != startNodeIndex) {

                int iGridIndex = (int)(i >> 32);
                short iNodeIndex = (short)(long)(i);
                MapGrid iGrid = getGrid(iGridIndex);
                MapGridRoutingBuffer iGridRB = routingGridBuffers.get(iGridIndex);
                
                long pre = iGridRB.nodesPreBuffer[iNodeIndex];
                int preGridIndex = (int)(pre >> 32);
                short preNodeIndex = (short)(long)(pre);
                MapGrid preGrid;
                if(iGridIndex == preGridIndex) {
                    preGrid = iGrid;
                } else {
                    preGrid = getGrid(preGridIndex);
                }

                Coordinate c1 = new Coordinate(preGrid.nodesLat[preNodeIndex], preGrid.nodesLon[preNodeIndex]);
                Coordinate c2 = new Coordinate(iGrid.nodesLat[iNodeIndex], iGrid.nodesLon[iNodeIndex]);

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

    public boolean isFloatClickZoomEnabled() {
        return floatClickZoomEnabled;
    }

    public void setFloatClickZoomEnabled(boolean floatClickZoomEnabled) {
        this.floatClickZoomEnabled = floatClickZoomEnabled;
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
    
    
    
    
    
    
    
    
    
//    public void calculateRouteDijkstra(TransportMode transportMode, RoutingMode routeMode) {
//
//        if (startNodeIndex == -1 || targetNodeIndex == -1) {
//            System.err.println("Cannot calculate route: Must select start and target");
//            return;
//        }
//         
//        // Edge bitfilter and speed
//        final byte edgeFilterBitMask;
//        final byte edgeFilterBitValue;  
//        final int allMaxSpeed;
//        final int allMinSpeed = PED_MAXSPEED;
//        
//        switch (transportMode) {
//        case Car:
//            edgeFilterBitMask = carBitMask;
//            edgeFilterBitValue = carBitValue;
//            allMaxSpeed = CAR_MAXSPEED;
//            break;
//        case Pedestrian:
//            edgeFilterBitMask = pedBitMask;
//            edgeFilterBitValue = pedBitValue;
//            allMaxSpeed = PED_MAXSPEED;
//            break;
//        default:
//            edgeFilterBitMask = 0;
//            edgeFilterBitValue = 0;
//            allMaxSpeed = CAR_MAXSPEED;
//            break;
//        }
//        
//        
//        Coordinate startCoord = new Coordinate(nodesLat[startNodeIndex], nodesLon[startNodeIndex]);
//        Coordinate targetCoord = new Coordinate(nodesLat[targetNodeIndex], nodesLon[targetNodeIndex]);
//        
//        // Find better start and end points if not suitable
//        if(!checkNodeWithFilter(startNodeIndex, edgeFilterBitMask, edgeFilterBitValue)) {
//            startNodeIndex = findNextNode((float)startCoord.getLat(), (float)startCoord.getLon(), edgeFilterBitMask, edgeFilterBitValue);
//            startCoord = new Coordinate(nodesLat[startNodeIndex], nodesLon[startNodeIndex]);
//        }
//        if(!checkNodeWithFilter(targetNodeIndex, edgeFilterBitMask, edgeFilterBitValue)) {
//            targetNodeIndex = findNextNode((float)targetCoord.getLat(), (float)targetCoord.getLon(), edgeFilterBitMask, edgeFilterBitValue);
//            targetCoord = new Coordinate(nodesLat[targetNodeIndex], nodesLon[targetNodeIndex]);
//        }   
//
//        // Clear markers
//        clearMarkers();
//        // Draw start and target
//        {
//            MapMarkerDot startDot =
//                    new MapMarkerDot("Start", startCoord);
//            map.addMapMarker(startDot);
//            routeDots.add(startDot);
//            MapMarkerDot targetDot =
//                    new MapMarkerDot("Target", targetCoord);
//            map.addMapMarker(targetDot);
//            routeDots.add(targetDot);
//        }
//
//        
//        Random rd = new Random(123);
//        float debugDispProp = 0.9999f;
//        int maxMarkerCountdown = 50;
//
//        
//        // Reset buffers
//        routeDistHeap.resetFill(nodeCount);
//        Arrays.fill(nodesRouteClosedList, false);
//        // TODO Add only relevant nodes?
//
//        
//        // Start node
//        routeDistHeap.decreaseKey(startNodeIndex, 0);
//        nodesRouteClosedList[startNodeIndex] = true;
//
//        int visitedCount = 0;
//        boolean found = false;
//
//        // Find route with Dijkstra
//        while (!routeDistHeap.isEmpty()) {
//            // Get dist, remove and get index
//            float nodeDist = routeDistHeap.peekNodeValue();
//
//            // Break if node not visited yet
//            if (nodeDist == Integer.MAX_VALUE) {
//                System.err.println("Node with no distance set found, break after " + visitedCount + " nodes visited, "
//                        + routeDistHeap.getSize() + " nodes not visited");
//                break;
//            }
//
//            //System.out.println(nodeDist);
//
//            // Remove and get index
//            int nodeIndex = routeDistHeap.remove();
//
//            if (nodeIndex == targetNodeIndex) {
//                System.out.println("Found after " + visitedCount + " nodes visited. " + routeDistHeap.getSize() + " nodes not visited");
//                System.out.println("Dist: " + nodeDist);
//                found = true;
//                break;
//            } else {
//                //System.out.println("Not found");
//            }
//
//            // Mark as visited
//            nodesRouteClosedList[nodeIndex] = true;
//            visitedCount++;
//
//            // Display
//            if (maxMarkerCountdown > 0 && rd.nextFloat() > debugDispProp) {
//                MapMarkerDot dot = new MapMarkerDot(new Coordinate(nodesLat[nodeIndex], nodesLon[nodeIndex]));
//                map.addMapMarker(dot);
//                routeDots.add(dot);
//                maxMarkerCountdown--;
//            }
//            //System.out.println(nextIndex);
//
//            // Iterate over edges to neighbors
//            for (int iEdge = nodesEdgeOffset[nodeIndex]; (nodeIndex + 1 < nodesEdgeOffset.length && iEdge < nodesEdgeOffset[nodeIndex + 1])
//                    || (nodeIndex + 1 == nodesEdgeOffset.length && iEdge < edgeCount); // Last node in offset array
//                    iEdge++) {
//                int nbIndex = edgesTarget[iEdge];
//
//                // Skip if edge not accessible
//                if ((edgesInfobits[iEdge] & edgeFilterBitMask) != edgeFilterBitValue) {
//                    continue;
//                }
//
//                // Distance calculation, depending on routing mode
//                float nbDist;
//                float edgeDist = edgesLengths[iEdge];
//                if (routeMode == RoutingMode.Fastest) {
//                    float maxSpeed = (int) Byte.toUnsignedLong(edgesMaxSpeeds[iEdge]);
//                    maxSpeed = Math.max(allMinSpeed, Math.min(allMaxSpeed, maxSpeed));
//                    nbDist = nodeDist + (edgeDist / maxSpeed);
//                } else if (routeMode == RoutingMode.Shortest) {
//                    nbDist = nodeDist + edgeDist;
//                } else {
//                    throw new RuntimeException("Unsupported routing mode: " + routeMode);
//                }
//
//                if (!nodesRouteClosedList[nbIndex]) {
//                    if (routeDistHeap.decreaseKeyIfSmaller(nbIndex, nbDist)) {
//                        nodesPreBuffer[nbIndex] = nodeIndex;
//                    }
//                } else {
//                    //System.out.println("Already visited");
//                }
//            }
//        }
//
//        if (found) {
//            // Reconstruct route
//            int i = targetNodeIndex;
//            while (i != startNodeIndex) {
//                int pre = nodesPreBuffer[i];
//                //                if (pre == targetIndex) {
//                //                    continue;
//                //                }
//
//                Coordinate c1 = new Coordinate(nodesLat[pre], nodesLon[pre]);
//                Coordinate c2 = new Coordinate(nodesLat[i], nodesLon[i]);
//
//                MapPolygonImpl routPoly = new MapPolygonImpl(c1, c2, c2);
//                routeLines.add(routPoly);
//                map.addMapPolygon(routPoly);
//
//                //                MapMarkerDot dot = new MapMarkerDot(new Coordinate(nodesLat[i], nodesLon[i]));
//                //                map.addMapMarker(dot);
//                //                routeDots.add(dot);
//
//                i = pre;
//            }
//        } else {
//            System.err.println("No way found");
//        }
//    }
}
