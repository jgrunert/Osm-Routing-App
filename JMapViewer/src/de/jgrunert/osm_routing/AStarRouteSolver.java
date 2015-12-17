package de.jgrunert.osm_routing;

import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.openstreetmap.gui.jmapviewer.Coordinate;


/**
 * Solves routing problems using A* algorighm
 * @author Jonas Grunert
 *
 */
@SuppressWarnings("javadoc")
public class AStarRouteSolver implements IRouteSolver {
    
    // General constants
    private static final String MAP_GRIDS_DIR = "D:\\Jonas\\OSM\\germany\\grids_020";
    //private static final String MAP_DIR = "D:\\Jonas\\OSM\\bawue";
    //private static final String MAP_DIR = "D:\\Jonas\\OSM\\hamburg";
    
    
    // Routing constants
    private static final short CAR_MAXSPEED = 150;
    private static final short PED_MAXSPEED = 5;
    private static int ROUTE_HEAP_CAPACITY = 1000000;
    // Number of grids to buffer
    private static int GRID_BUFFER_SIZE = 2000;
    
    
    // Start and end for route
    private Long startNodeGridIndex = null;
    @Override
    public void setStartNode(long nodeGridIndex) { startNodeGridIndex = nodeGridIndex; needsDispalyRefresh = true; }
    private Long targetNodeGridIndex = null;
    @Override
    public void setTargetNode(long nodeGridIndex) { targetNodeGridIndex = nodeGridIndex; needsDispalyRefresh = true; }
    
    @Override
    public Coordinate getStartCoordinate() {
        if(startNodeGridIndex == null) { return null; }
        return getNodeCoordinates(startNodeGridIndex); 
    }
    @Override
    public Coordinate getTargetCoordinate() {
        if(targetNodeGridIndex == null) { return null; }
        return getNodeCoordinates(targetNodeGridIndex); 
    }
    
    // Debugging and routing preview
    private List<Coordinate> routingPreviewDots = new LinkedList<>();
    private static final double routingPreviewDotPropability = 0.999;
    @Override
    public synchronized List<Coordinate> getRoutingPreviewDots() { return new ArrayList<>(routingPreviewDots); }
    private synchronized void addNewPreviewDot(Coordinate dot) { routingPreviewDots.add(dot); }
    
    private volatile boolean needsDispalyRefresh = false;
    @Override
    public boolean getNeedsDispalyRefresh() { return needsDispalyRefresh; }
    @Override
    public void resetNeedsDispalyRefresh() { needsDispalyRefresh = false; }
    
    // Final route
    private List<Coordinate> calculatedRoute = new LinkedList<>();
    @Override
    public List<Coordinate> getCalculatedRoute() { return calculatedRoute; }
    
    
    private volatile RoutingState state = RoutingState.NotReady;
    @Override
    public RoutingState getRoutingState() { return state; }
    
    private Random rd;
    

    
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
    // A simple queue with order of grids loaded to unload it in same order
    List<MapGrid> loadedGrids;
    int gridLoadOperations;
    long gridVisitTimestamp;
    
    // Heap for rout finding
    NodeDistHeap routeDistHeap;
    
    
    /**
     * Constructor, loads grid data
     */
    public AStarRouteSolver() {
                
        try {            
            intializeGrids();    
            
            routeDistHeap = new NodeDistHeap(ROUTE_HEAP_CAPACITY);
        } catch (Exception e) {
            System.err.println("Error at loadOsmData");
            e.printStackTrace();
        }
        
        startNodeGridIndex = findNextNode(47.8f, 9.0f, (byte)0, (byte)0);
        targetNodeGridIndex = findNextNode(49.15f, 9.22f, (byte)0, (byte)0);
        
        state = RoutingState.Standby;
        needsDispalyRefresh = true;
    }
    

    /**
     * Read and initialize grid information
     * @throws Exception
     */
    private void intializeGrids() throws Exception 
    {
        System.out.println("Start loading grid index");
        try (ObjectInputStream gridReader =
                new ObjectInputStream(new FileInputStream(MAP_GRIDS_DIR + "\\grids.index"))) {
            gridRaster = gridReader.readFloat();
            gridMinLat = gridReader.readFloat();
            gridMinLon = gridReader.readFloat();
            gridLatCount = gridReader.readInt();
            gridLonCount = gridReader.readInt();

            grids = new ArrayList<>(gridLatCount * gridLonCount);
            loadedGrids = new ArrayList<>();
            gridIndices = new int[gridLatCount][gridLonCount];
            gridVisitTimestamp = 0;
            gridLoadOperations = 0;
            
            int iGrid = 0;
            for (int iLat = 0; iLat < gridLatCount; iLat++) {
                for (int iLon = 0; iLon < gridLonCount; iLon++) {
                    grids.add(new MapGrid(iGrid)); // Initialize with empty, not loaded grids
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
            while(loadedGrids.size() >= GRID_BUFFER_SIZE) {
                // Unload grid if to many grids in buffer
                
                // Find grid longest time not used
                int sleepyGridIndex = 0;
                long sleepyGridTimestamp = loadedGrids.get(0).visitTimestamp;
                for(int i = 1; i < loadedGrids.size(); i++) {
                    if(loadedGrids.get(i).visitTimestamp < sleepyGridTimestamp) {
                        sleepyGridIndex = i;
                        sleepyGridTimestamp = loadedGrids.get(i).visitTimestamp;
                    }
                }
                MapGrid toUnload = loadedGrids.remove(sleepyGridIndex);
                
                // Unload
                grids.set(toUnload.index, new MapGrid(toUnload.index));
                System.out.println("Unloaded grid " + gridIndex + ". Grids loaded: " + loadedGrids.size());
            }
            
            MapGrid loaded = new MapGrid(gridIndex, gridVisitTimestamp, MAP_GRIDS_DIR + "\\" + gridIndex + ".grid");
            grids.set(gridIndex, loaded);
            loadedGrids.add(loaded);
            gridLoadOperations++;
            System.out.println("Loaded grid " + gridIndex + ". Grids loaded: " + loadedGrids.size() + ". Load operations: " + gridLoadOperations);
            return loaded;
        } catch (Exception e) {
            System.err.println("Failed to load grid");
            e.printStackTrace();
            return grids.get(gridIndex);
        }
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
        return getNodeCoordinates((int)(nodeGridIndex >> 32), (int)nodeGridIndex);
    }
    
    /**
     * Tries to determine coordinates of a node, tries to load grid if necessary
     * @return Coordinates of node
     */
    private Coordinate getNodeCoordinates(int gridIndex, int nodeIndex) {
        MapGrid grid = getGrid(gridIndex);
        return getNodeCoordinates(grid, nodeIndex);
    }
    
    /**
     * Tries to determine coordinates of a node, tries to load grid if necessary
     * @return Coordinates of node
     */
    private Coordinate getNodeCoordinates(MapGrid grid, int nodeIndex) {
        return new Coordinate(grid.nodesLat[nodeIndex], grid.nodesLon[nodeIndex]);
    }
    
    
    /**
     * Tries to find out index of next point to given coordinate
     * @param coord
     * @return Index of next point
     */
    @Override
    public Long findNextNode(float lat, float lon, byte filterBitMask, byte filterBitValue) 
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
                        
           float dist = Utils.calcNodeDistPrecise(lat, lon, grid.nodesLat[iN], grid.nodesLon[iN]);
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
    

    
    // Info bits: 0,0,0,0,0,[Car],[Ped],[Oneway]
    private byte carBitMask = 5;
    private byte carBitValue = 4;
    private byte pedBitMask = 2;
    private byte pedBitValue = 2;
    
    // TODO Intelligent? costs for street switching?
    
    
    // Edge bitfilter and speed
    boolean found;
    byte edgeFilterBitMask;
    byte edgeFilterBitValue;  
    int allMaxSpeed;
    int allMinSpeed;

    long visNodeGridIndex;
    int visGridIndex;
    int visNodeIndex;
    
    long target;
    int visitedCount;
    int hCalc;
    int hReuse;
    int gridChanges;
    int gridStays;
    int firstVisits;
    int againVisits;
    int fastFollows;
    long startTime;

    Map<Integer, MapGridRoutingBuffer> routingGridBuffers; // Stores all buffers for all grids involved in routing
    Set<Long> openList; // Stores all open nodes and their heuristic

    int startGridIndex;
    //int startNodeIndex = (int)(long)(startNodeGridIndex);
    MapGrid startGrid;

    int targetGridIndex;
    int targetNodeIndex;
    MapGrid targetGrid;
    float targetLat;
    float targetLon;
    
    int oldVisGridIndex;
    MapGrid visGrid;
    MapGridRoutingBuffer visGridRB;
    
    RoutingMode routeMode;
    


    /**
     * Calculates a route using an improved A Star algorithm
     */
    @Override
    public synchronized void startCalculateRoute(TransportMode transportMode, RoutingMode routeMode) {

        if(state != RoutingState.Standby) {
            System.err.println("Routing not available");
            return;
        }
        
        if (startNodeGridIndex == null || targetNodeGridIndex == null) {
            System.err.println("Cannot calculate route: Must select any start and target");
            return;
        }
        
        this.state = RoutingState.Routing;
        this.routeMode = routeMode;
        this.startTime = System.currentTimeMillis();
        needsDispalyRefresh = true;
        
        // Edge bitfilter and speed
        allMinSpeed = PED_MAXSPEED;
        
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
        

        
        // Find better start and end points if not suitable
        if(!checkNodeWithFilter(getGrid((int)(startNodeGridIndex >> 32)), (int)(long)startNodeGridIndex, edgeFilterBitMask, edgeFilterBitValue)) {
            Coordinate startCoord = getNodeCoordinates(startNodeGridIndex);
            startNodeGridIndex = findNextNode((float)startCoord.getLat(), (float)startCoord.getLon(), edgeFilterBitMask, edgeFilterBitValue);
        }
        if(!checkNodeWithFilter(getGrid((int)(targetNodeGridIndex >> 32)), (int)(long)targetNodeGridIndex, edgeFilterBitMask, edgeFilterBitValue)) {
            Coordinate targetCoord = getNodeCoordinates(targetNodeGridIndex);
            targetNodeGridIndex = findNextNode((float)targetCoord.getLat(), (float)targetCoord.getLon(), edgeFilterBitMask, edgeFilterBitValue);
        }   
        

        if (startNodeGridIndex == null || targetNodeGridIndex == null) {
            System.err.println("Cannot calculate route: Must select valid start and target");
            return;
        }
                
        rd = new Random(123);
        routingPreviewDots.clear();

        startGridIndex = (int)(startNodeGridIndex >> 32);
        //int startNodeIndex = (int)(long)(startNodeGridIndex);
        startGrid = getGrid(startGridIndex);

        targetGridIndex = (int)(targetNodeGridIndex >> 32);
        targetNodeIndex = (int)(long)(targetNodeGridIndex);
        targetGrid = getGrid(targetGridIndex);
        targetLat = targetGrid.nodesLat[targetNodeIndex];
        targetLon = targetGrid.nodesLon[targetNodeIndex];
        
        // Reset buffers and 
        routeDistHeap.resetEmpty();
        routingGridBuffers = new HashMap<>(); // Stores all buffers for all grids involved in routing
        openList = new HashSet<>(); // Stores all open nodes and their heuristic
        // TODO routingGridBuffers offloading (if no more nodes of this grid open? does this ever happen?)
        
        // Add start node  
        routeDistHeap.add(startNodeGridIndex, 0.0f);
        MapGridRoutingBuffer startGridRB = new MapGridRoutingBuffer(startGrid.nodeCount);
        routingGridBuffers.put(startGridIndex, startGridRB);
        openList.add(startNodeGridIndex);
        
        found = false;
        target = (long)targetNodeGridIndex;
        visitedCount = 0;
        hCalc = 0;
        hReuse = 0;
        gridChanges = 0;
        gridStays = 0;
        firstVisits = 0;
        againVisits = 0;
        fastFollows = 0;
        
        oldVisGridIndex = startGridIndex;
        visGrid = startGrid;
        visGridRB = startGridRB;
        
        
        Thread routingThread = new Thread(new Runnable() {            
            @Override
            public void run() {
                System.out.println("Start doRouting thread");
                doRouting();
                System.out.println("Finishing doRouting thread");
            }
        });
        routingThread.setName("RoutingThread");
        routingThread.start();
    }


    private void doRouting() {
        // Find route with A*
        while (!routeDistHeap.isEmpty()) {
            // Remove and get index
            visNodeGridIndex = routeDistHeap.removeFirst();
            //System.out.println("VIS: " + visNodeGridIndex);
            //System.out.println(nextIndex);
            
            // Fast follow
//            int nbCount;
//            long nbIndex;
//            while(true) {
//                nbCount = 0;
//                nbIndex = -1;
//                

//                
//
//                // Iterate over edges to neighbors
//                for (int iEdge = visGrid.nodesEdgeOffset[visNodeIndex]; 
//                        (visNodeIndex + 1 < visGrid.nodesEdgeOffset.length && iEdge < visGrid.nodesEdgeOffset[visNodeIndex + 1])
//                        || (visNodeIndex + 1 == visGrid.nodesEdgeOffset.length && iEdge < visGrid.edgeCount); // Last node in offset array
//                        iEdge++) {
//                    
//                    // Skip if edge not accessible
//                    if ((visGrid.edgesInfobits[iEdge] & edgeFilterBitMask) != edgeFilterBitValue) {
//                        continue;
//                    }
//                    
//                    // Get neighbor
//                    long nbNodeGridIndex = visGrid.edgesTargetNodeGridIndex[iEdge];
//                    int nbGridIndex = (int)(nbNodeGridIndex >> 32);
//                    int nbNodeIndex = (int)(long)(nbNodeGridIndex);
//
//                    // Skip loop edges
//                    if(nbNodeGridIndex == visNodeGridIndex) {
//                        System.err.println("Warning: Loop edge - skipping");
//                        continue;
//                    }
//                    
//                    // Get neighbor grid routing buffer
//                    MapGrid nbGrid;
//                    if(nbGridIndex == visGridIndex) {
//                        nbGrid = visGrid;
//                    } else {
//                        nbGrid = getGrid(nbGridIndex);
//                    }     
//                    
//                    // Get neighbor grid routing buffer
//                    MapGridRoutingBuffer nbGridRB;
//                    if(nbGridIndex == visGridIndex) {
//                        nbGridRB = visGridRB;
//                    } else {
//                        nbGridRB = routingGridBuffers.get(nbGridIndex);
//                        if(nbGridRB == null) {
//                            nbGridRB = new MapGridRoutingBuffer(nbGrid.nodeCount);
//                            routingGridBuffers.put(nbGridIndex, nbGridRB);
//                        }
//                    }
//                    
//                    // Continue if target node node already in closed list
//                    if (nbGridRB.nodesRouteClosedList[nbNodeIndex]) {
//                        continue;
//                    }
//                    
//
//                    nbCount++;
//                    if(nbCount > 1) {
//                        break;
//                    }
//                    
//                    nbIndex = visGrid.edgesTargetNodeGridIndex[iEdge];
//                }
//                
//                // TODO Update Dist and more for node?
//
//
//
//                // Mark as closed/visited
//                visGridRB.nodesRouteClosedList[visNodeIndex] = true;
//                openList.remove(visNodeGridIndex);
//                visitedCount++;
//                if (maxMarkerCountdown > 0 && rd.nextFloat() > debugDispProp) {
//                    MapMarkerDot dot = new MapMarkerDot(new Color(255 - (255 * Math.max(maxMarkerCountdown, 0) / maxMarkerCount), 0, 
//                            (255 * Math.max(maxMarkerCountdown, 0) / maxMarkerCount)),
//                            getNodeCoordinates(visGrid, visNodeIndex));
//                    map.addMapMarker(dot);
//                    routeDots.add(dot);
//                    maxMarkerCountdown--;
//                }
//                
//                if(nbCount == 1) {
//                    visNodeGridIndex = nbIndex;
//                    fastFollows++;
//                    System.out.println("fol: " + fastFollows + " " + visNodeGridIndex);
//                } else {
//                    System.out.println("out: " + fastFollows);
//                    break;
//                }
//            }
            
            
            // Visit node/neighbors
            if(visitNode()) {
                found = true;
                break;
            }
            visitNodeEdges();
            
            
            // Display
//            if (maxMarkerCountdown > 0 && rd.nextFloat() > debugDispProp) {
//                MapMarkerDot dot = new MapMarkerDot(new Color(255 - (255 * maxMarkerCountdown / maxMarkerCount), 0, (255 * maxMarkerCountdown / maxMarkerCount)),
//                        getNodeCoordinates(visGrid, visNodeIndex));
//                map.addMapMarker(dot);
//                routeDots.add(dot);
//                maxMarkerCountdown--;
//            }
        }

        System.out.println("H calc: " + hCalc);
        System.out.println("H reuse: " + hReuse);
        System.out.println("gridChanges: " + gridChanges);
        System.out.println("gridStays: " + gridStays);
        System.out.println("firstVisits: " + firstVisits);
        System.out.println("againVisits: " + againVisits);
        System.out.println("fastFollows: " + fastFollows);
        System.out.println("MaxHeapSize: " + routeDistHeap.getSizeUsageMax());
        
        
        // TODO Time and dist and output (as table?)
        if (found) {
            // Reconstruct route
            reconstructRoute();
            // For testing: Connect debug markers
//            for(int iM = 0; iM < routeDots.size() - 1; iM++) {
//
//                MapPolygonImpl routPoly = new MapPolygonImpl(new Color(255 - (255 * iM / routeDots.size()), 0, 255 * iM / routeDots.size()),
//                        routeDots.get(iM).getCoordinate(), routeDots.get(iM).getCoordinate(), routeDots.get(iM+1).getCoordinate());
//                routeLines.add(routPoly);
//                map.addMapPolygon(routPoly);
//            }
        } else {
            System.err.println("No way found");
        }
        
        
        // Cleanup
        routingGridBuffers = null;
        openList = null;
        this.state = RoutingState.Standby;
        needsDispalyRefresh = true;
        System.out.println("Finished routing after " + (System.currentTimeMillis() - startTime) + "ms");
    }

    private void reconstructRoute() {
        
        calculatedRoute.clear();
        
        if(!found) {
            return;
        }
        
        // TODO Calculate dist and time
        
        long i = targetNodeGridIndex;
        while (i != startNodeGridIndex) {

            int iGridIndex = (int)(i >> 32);
            int iNodeIndex = (int)(long)(i);
            MapGrid iGrid = getGrid(iGridIndex);
            MapGridRoutingBuffer iGridRB = routingGridBuffers.get(iGridIndex);
            
            long pre = iGridRB.nodesPreBuffer[iNodeIndex];
//            assert pre != i;
//            int preGridIndex = (int)(pre >> 32);
//            int preNodeIndex = (int)(long)(pre);
//            MapGrid preGrid;
//            if(iGridIndex == preGridIndex) {
//                preGrid = iGrid;
//            } else {
//                preGrid = getGrid(preGridIndex);
//            }

            Coordinate coord = new Coordinate(iGrid.nodesLat[iNodeIndex], iGrid.nodesLon[iNodeIndex]);            
            calculatedRoute.add(coord);
            
            i = pre;
        }
    }
    
    

    private boolean visitNode() 
    {                
        visGridIndex = (int) (visNodeGridIndex >> 32);
        visNodeIndex = (int) (long) (visNodeGridIndex);

        if (visGridIndex != oldVisGridIndex) {
            oldVisGridIndex = visGridIndex;
            visGrid = getGrid(visGridIndex);
            visGridRB = routingGridBuffers.get(visGridIndex);
            if (visGridRB == null) {
                visGridRB = new MapGridRoutingBuffer(visGrid.nodeCount);
                routingGridBuffers.put(visGridIndex, visGridRB);
            }
            gridChanges++;
        } else {
            gridStays++;
        }

    // Mark as closed/visited
    visGridRB.nodesRouteClosedList[visNodeIndex] = true;
    openList.remove(visNodeGridIndex);
    visitedCount++;

        if (rd.nextFloat() > routingPreviewDotPropability) {
            addNewPreviewDot(getNodeCoordinates(visGrid, visNodeIndex));
        }
        
        if(visGridIndex != oldVisGridIndex) {
            oldVisGridIndex = visGridIndex;
            visGrid = getGrid(visGridIndex);
            visGridRB = routingGridBuffers.get(visGridIndex);  
            if(visGridRB == null) {
                visGridRB = new MapGridRoutingBuffer(visGridIndex);
                routingGridBuffers.put(visGridIndex, visGridRB);
            }
            gridChanges++;
        } else {
            gridStays++;
        }
        //System.out.println(visGridIndex);
        
        // Update visitTimestamp to mark that grid is still in use
        visGrid.visitTimestamp = ++gridVisitTimestamp;
         
        // Found! Return
        if (visNodeGridIndex == target) {
            System.out.println("Found after " + visitedCount + " nodes visited. " + routeDistHeap.getSize() + " still in heap");
            return true;
        }
        
        return false;
    }
    
    private void visitNodeEdges() 
    {
        // Get distance of node from start, remove and get index
        float nodeDist = visGridRB.nodesRouteDists[visNodeIndex];

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
            long nbNodeGridIndex = visGrid.edgesTargetNodeGridIndex[iEdge];
            int nbGridIndex = (int)(nbNodeGridIndex >> 32);
            int nbNodeIndex = (int)(long)(nbNodeGridIndex);

            // Skip loop edges
            if(nbNodeGridIndex == visNodeGridIndex) {
                System.err.println("Warning: Loop edge - skipping");
                continue;
            }
            
            // Get neighbor grid routing buffer
            MapGrid nbGrid;
            if(nbGridIndex == visGridIndex) {
                nbGrid = visGrid;
            } else {
                nbGrid = getGrid(nbGridIndex);
            }     
            
            // Get neighbor grid routing buffer
            MapGridRoutingBuffer nbGridRB;
            if(nbGridIndex == visGridIndex) {
                nbGridRB = visGridRB;
            } else {
                nbGridRB = routingGridBuffers.get(nbGridIndex);
                if(nbGridRB == null) {
                    nbGridRB = new MapGridRoutingBuffer(nbGrid.nodeCount);
                    routingGridBuffers.put(nbGridIndex, nbGridRB);
                }
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
                //float motorwayBoost = (maxSpeed > 10) ? (maxSpeed > 50) ? (maxSpeed > 100) ? 1.6f : 1.3f : 1.2f : 1.0f; 
                //float motorwayBoost = (maxSpeed > 110) ? 1.5f : 1.0f; 
                //hFactor = 1.0f / allMaxSpeed / motorwayBoost;
                hFactor = 1.0f / allMaxSpeed;
            } else if (routeMode == RoutingMode.Shortest) {
                // Shortest route
                nbDist = nodeDist + edgeDist;
                hFactor = 1.0f;
            } else {
                throw new RuntimeException("Unsupported routing mode: " + routeMode);
            }
            
            // Caching h or holding visited in a nodes does not make sense
            // Re-visiting rate seems to be below 1:10 and maps get very slow and memory consuming
            float h = Utils.calcNodeDistFast(nbGrid.nodesLat[nbNodeIndex], nbGrid.nodesLon[nbNodeIndex], targetLat, targetLon) * hFactor;

            if (openList.contains(nbNodeGridIndex)) {
                hReuse++;
                // Point open and not closed - update if necessary
                if (routeDistHeap.decreaseKeyIfSmaller(nbNodeGridIndex, nbDist + h)) {
                    nbGridRB.nodesPreBuffer[nbNodeIndex] = visNodeGridIndex; 
                    nbGridRB.nodesRouteDists[nbNodeIndex] = nbDist;
                }
                againVisits++;
            } else {    
                // Point not found yet - add to heap and open list
                hCalc++;
                // Add
                routeDistHeap.add(nbNodeGridIndex, nbDist + h);
                //nodesRouteOpenList[nbIndex] = true;
                nbGridRB.nodesPreBuffer[nbNodeIndex] = visNodeGridIndex; 
                nbGridRB.nodesRouteDists[nbNodeIndex] = nbDist;
                openList.add(nbNodeGridIndex);
                firstVisits++;
            }
        }
    }
}
