package de.jgrunert.osm_routing;

import android.annotation.SuppressLint;
import android.os.Environment;

import org.mapsforge.core.model.LatLong;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.jgrunert.andromapview.MainActivity;


/**
 * Solves routing problems using A* algorighm
 * @author Jonas Grunert
 *
 */
@SuppressWarnings("javadoc")
public class AStarRouteSolver implements IRouteSolver {
    
    // General constants
    private static final File OSM_BASE_DIR = new File(Environment.getExternalStorageDirectory(), "osm");
    private static final File MAP_GRIDS_DIR = new File(OSM_BASE_DIR, "routing_grids");
    
    
    // Routing constants
    private static final short CAR_MAXSPEED = 130;
    private static final short PED_MAXSPEED = 5;
    private static final int ROUTE_HEAP_CAPACITY = 1000000;

    // Max RAM heap space usage to limit grid loading
    private static final double GRID_RAM_LIMIT = 0.7f;
    private int maxGridCount = -1;
    
    // Start and end for route
    private Long startNodeGridIndex = null;
    @Override
    public void setStartNode(long nodeGridIndex) { startNodeGridIndex = nodeGridIndex;  }
    private Long targetNodeGridIndex = null;
    @Override
    public void setTargetNode(long nodeGridIndex) { targetNodeGridIndex = nodeGridIndex;  }

    @Override
    public Long getStartNode(){
        return startNodeGridIndex;
    }
    @Override
    public Long getTargetNode() {
        return targetNodeGridIndex;
    }
    
    @Override
    public LatLong getStartCoordinate() {
        if(startNodeGridIndex == null) { return null; }
        return getNodeCoordinates(startNodeGridIndex); 
    }
    @Override
    public LatLong getTargetCoordinate() {
        if(targetNodeGridIndex == null) { return null; }
        return getNodeCoordinates(targetNodeGridIndex); 
    }

    // Final route
    private List<LatLong> calculatedRoute = new LinkedList<LatLong>();
    @Override
    public List<LatLong> getCalculatedRoute() { return calculatedRoute; }


    public float distOfRoute = 0.0f; // Route distance in metres
    public float timeOfRoute = 0.0f; // Route time in hours

    
    private volatile RoutingState state = RoutingState.NotReady;
    @Override
    public RoutingState getRoutingState() { return state; }

    
    private boolean doFastFollow = false;
    @Override
    public boolean isDoFastFollow() {
        return doFastFollow;
    }
    @Override
    public void setDoFastFollow(boolean doFastFollow) {
        this.doFastFollow = doFastFollow;
    }
    
   
    private boolean doMotorwayBoost = true;       
    @Override
    public boolean isDoMotorwayBoost() {
        return doMotorwayBoost;
    }
    @Override
    public void setDoMotorwayBoost(boolean doMotorwayBoost) {
        this.doMotorwayBoost = doMotorwayBoost;
    }

    private volatile float routingProgress = 0.0f;
    float routingMaxDist = 0.0f;
    float routingNearestDist = Float.MAX_VALUE;
    @Override
    public float getRoutingProgress() { return routingProgress; }


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
    int gridLoadsTotal;
    int gridLoadsRoute;
    long gridVisitTimestamp;
    
    // Heap for rout finding
    NodeDistHeap routeDistHeap;

    private final MainActivity parent;


    /**
     * Constructor, loads grid data
     */
    public AStarRouteSolver(MainActivity parent) {

        this.parent = parent;

        System.out.println("Max memory: " + (Runtime.getRuntime().maxMemory() / 1048576) + "Mb");

        try {            
            intializeGrids();    
            
            routeDistHeap = new NodeDistHeap(ROUTE_HEAP_CAPACITY);
        } catch (Exception e) {
            System.err.println("Error at loadOsmData");
            e.printStackTrace();
        }
        
        //startNodeGridIndex = findNextNode(47.8f, 9.0f, (byte)0, (byte)0);
        //targetNodeGridIndex = findNextNode(49.15f, 9.22f, (byte)0, (byte)0);
        
        state = RoutingState.Standby;
        routingProgress = 1.0f;
    }
    

    /**
     * Read and initialize grid information
     * @throws Exception
     */
    private void intializeGrids() throws Exception 
    {
        System.out.println("Start loading grid index");
        ObjectInputStream gridReader = null;
        try {
            gridReader =
                    new ObjectInputStream(new BufferedInputStream(new FileInputStream(new File(MAP_GRIDS_DIR, "grids.index"))));
            gridRaster = gridReader.readFloat();
            gridMinLat = gridReader.readFloat();
            gridMinLon = gridReader.readFloat();
            gridLatCount = gridReader.readInt();
            gridLonCount = gridReader.readInt();

            grids = new ArrayList<MapGrid>(gridLatCount * gridLonCount);
            loadedGrids = new ArrayList<MapGrid>();
            gridIndices = new int[gridLatCount][gridLonCount];
            gridVisitTimestamp = 0;
            gridLoadsTotal = 0;
            
            int iGrid = 0;
            for (int iLat = 0; iLat < gridLatCount; iLat++) {
                for (int iLon = 0; iLon < gridLonCount; iLon++) {
                    grids.add(new MapGrid(iGrid)); // Initialize with empty, not loaded grids
                    gridIndices[iLat][iLon] = iGrid;
                    iGrid++;
                }
            }
        }
        finally {
            if(gridReader != null) {
                gridReader.close();
            }
        }
        System.out.println("Finished loading grid index");
    }
    
    
    /**
     * Loads grid, caches and returns it
     */
    private MapGrid loadGrid(int gridIndex) {
        try {
            // Unload grid if to many grids in buffer
            if(maxGridCount != -1) {
                while (loadedGrids.size() >= maxGridCount) {

                    // Find grid longest time not used
                    int sleepyGridIndex = 0;
                    long sleepyGridTimestamp = loadedGrids.get(0).visitTimestamp;
                    for (int i = 1; i < loadedGrids.size(); i++) {
                        if (loadedGrids.get(i).visitTimestamp < sleepyGridTimestamp) {
                            sleepyGridIndex = i;
                            sleepyGridTimestamp = loadedGrids.get(i).visitTimestamp;
                        }
                    }
                    MapGrid toUnload = loadedGrids.remove(sleepyGridIndex);

                    // Unload
                    grids.set(toUnload.index, new MapGrid(toUnload.index));
                    System.out.println("Unloaded grid " + toUnload.index + ". Grids loaded: " + loadedGrids.size());
                }
            }

            // Load grid
            MapGrid loaded = new MapGrid(gridIndex, gridVisitTimestamp,new File(MAP_GRIDS_DIR, gridIndex + ".grid"));
            grids.set(gridIndex, loaded);
            loadedGrids.add(loaded);
            gridLoadsTotal++;
            gridLoadsRoute++;
            System.out.println("Loaded grid " + gridIndex + ". Grids loaded: " + loadedGrids.size() +
                    ". Load operations: " + gridLoadsTotal +
                    ". Heap-Size: " + (Runtime.getRuntime().totalMemory() / 1048576) + "Mb");

            // Check if grid count is not limited yet but needs to be limited
            if(maxGridCount == -1 &&
                    (Runtime.getRuntime().totalMemory() / (double)Runtime.getRuntime().maxMemory()) > GRID_RAM_LIMIT) {
                maxGridCount = loadedGrids.size();
                System.out.println("RAM limit reached - limited grid buffer to " + maxGridCount);
            }

            return loaded;
        } catch (Exception e) {
            System.err.println("Failed while grid loading");
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
    private LatLong getNodeCoordinates(long nodeGridIndex) {
        return getNodeCoordinates((int)(nodeGridIndex >> 32), (int)nodeGridIndex);
    }
    
    /**
     * Tries to determine coordinates of a node, tries to load grid if necessary
     * @return Coordinates of node
     */
    private LatLong getNodeCoordinates(int gridIndex, int nodeIndex) {
        MapGrid grid = getGrid(gridIndex);
        return getNodeCoordinates(grid, nodeIndex);
    }
    
    /**
     * Tries to determine coordinates of a node, tries to load grid if necessary
     * @return Coordinates of node
     */
    private LatLong getNodeCoordinates(MapGrid grid, int nodeIndex) {
        return new LatLong(grid.nodesLat[nodeIndex], grid.nodesLon[nodeIndex]);
    }


    /**
     * Tries to find out index of next point to given coordinate
     * @return Index of next point
     */
    @Override
    public Long findNextNode(float lat, float lon) {
        return findNextNode(lat, lon, (byte)0, (byte)0);
    }

    /**
     * Tries to find out index of next point to given coordinate
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
                        
           float dist = MathUtils.calcNodeDistPrecise(lat, lon, grid.nodesLat[iN], grid.nodesLon[iN]);
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
    private static final byte carBitMask = 5;
    private static final byte carBitValue = 4;
    private static final byte pedBitMask = 2;
    private static final byte pedBitValue = 2;

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
    int startNodeIndex;
    MapGrid startGrid;

    int targetGridIndex;
    int targetNodeIndex;
    MapGrid targetGrid;
    float startLat;
    float startLon;
    float targetLat;
    float targetLon;
    
    int oldVisGridIndex;
    MapGrid visGrid;
    MapGridRoutingBuffer visGridRB;
    
    RoutingMode routeMode;
    


    /**
     * Calculates a route using an improved A Star algorithm
     */
    @SuppressLint("UseSparseArrays")
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
        routingMaxDist = MathUtils.calcNodeDistFast(startLat, startLon, targetLat, targetLon);
        routingProgress = 0.0f;
        routingNearestDist = routingMaxDist;
        parent.updateDisplay();

        this.startTime = System.currentTimeMillis();

        gridLoadsRoute = 0;
        
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
            LatLong startCoord = getNodeCoordinates(startNodeGridIndex);
            startNodeGridIndex = findNextNode((float)startCoord.latitude, (float)startCoord.longitude, edgeFilterBitMask, edgeFilterBitValue);
        }
        if(!checkNodeWithFilter(getGrid((int) (targetNodeGridIndex >> 32)), (int) (long) targetNodeGridIndex, edgeFilterBitMask, edgeFilterBitValue)) {
            LatLong targetCoord = getNodeCoordinates(targetNodeGridIndex);
            targetNodeGridIndex = findNextNode((float)targetCoord.latitude, (float)targetCoord.longitude, edgeFilterBitMask, edgeFilterBitValue);
        }   
        

        if (startNodeGridIndex == null || targetNodeGridIndex == null) {
            System.err.println("Cannot calculate route: Must select valid start and target");
            return;
        }

        startGridIndex = (int)(startNodeGridIndex >> 32);
        startNodeIndex = (int)(long)(startNodeGridIndex);
        //int startNodeIndex = (int)(long)(startNodeGridIndex);
        startGrid = getGrid(startGridIndex);
        startLat = startGrid.nodesLat[startNodeIndex];
        startLon = startGrid.nodesLon[startNodeIndex];

        targetGridIndex = (int)(targetNodeGridIndex >> 32);
        targetNodeIndex = (int)(long)(targetNodeGridIndex);
        targetGrid = getGrid(targetGridIndex);
        targetLat = targetGrid.nodesLat[targetNodeIndex];
        targetLon = targetGrid.nodesLon[targetNodeIndex];
        
        // Reset buffers and 
        routeDistHeap.resetEmpty();
        routingGridBuffers = new HashMap<Integer, MapGridRoutingBuffer>(); // Stores all buffers for all grids involved in routing
        openList = new HashSet<Long>(); // Stores all open nodes and their heuristic
        // TODO routingGridBuffers offloading (if no more nodes of this grid open? does this ever happen?)
        
        // Add start node  
        routeDistHeap.add(startNodeGridIndex, 0.0f);
        MapGridRoutingBuffer startGridRB = new MapGridRoutingBuffer(startGrid.nodeCount);
        routingGridBuffers.put(startGridIndex, startGridRB);
        openList.add(startNodeGridIndex);
        
        found = false;
        target = targetNodeGridIndex;
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


        System.out.println("Start routing from " + startLat + "/" + startLon + " to " + targetLat + "/" + targetLon);
        System.out.flush();

        
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
            if(state == RoutingState.Canceling) {
                canceledRouting();
                return;
            }

            // Remove and get index
            visNodeGridIndex = routeDistHeap.removeFirst();
        
            // Visit node/neighbors
            if(visitNode()) {
                found = true;
                break;
            }
            visitNodeEdges();            
        }
        

        System.out.println("H calc: " + hCalc);
        System.out.println("H reuse: " + hReuse);
        System.out.println("gridChanges: " + gridChanges);
        System.out.println("gridStays: " + gridStays);
        System.out.println("firstVisits: " + firstVisits);
        System.out.println("againVisits: " + againVisits);
        System.out.println("fastFollows: " + fastFollows);
        System.out.println("MaxHeapSize: " + routeDistHeap.getSizeUsageMax());
        System.out.println("gridLoads: " + gridLoadsRoute);
        

        // Routing finished
        if (found) {
            // Reconstruct route
            reconstructRoute();
        } else {
            // No route found
            calculatedRoute.clear();
            System.err.println("No way found");
        }
        
        
        // Cleanup
        routingGridBuffers = null;
        openList = null;
        this.state = RoutingState.Standby;
        routingProgress = 1.0f;
        parent.updateDisplay();

        System.out.println("Finished routing after " + (System.currentTimeMillis() - startTime) + "ms");

        parent.onRoutingFinished();
    }

    @SuppressWarnings("ConstantConditions")
    private void reconstructRoute() {

        this.state = RoutingState.Reconstructing;
        parent.updateDisplay();

        calculatedRoute.clear();

        if(!found) {
            return;
        }

        distOfRoute = 0.0f; // Route distance in metres
        timeOfRoute = 0.0f; // Route time in hours


        int iNodeIndex = 0;
        MapGrid iGrid = null;

        // Buffers for removed edge points in a grid to reconstruct points
        int gridRemovedEdgeGridIndex = -1;
        int[] gridRemovedEdgeCoordsOffsets = null;
        float[] gridRemovedEdgeCoordsLat = null;
        float[] gridRemovedEdgeCoordsLon = null;

        long i = targetNodeGridIndex;
        while (i != startNodeGridIndex) {

            if(state == RoutingState.Canceling) {
                canceledRouting();
                return;
            }

            int iGridIndex = (int)(i >> 32);
            iNodeIndex = (int)(long)(i);
            iGrid = getGrid(iGridIndex);
            MapGridRoutingBuffer iGridRB = routingGridBuffers.get(iGridIndex);

            // Route point coordinates (for view)
            LatLong coord = new LatLong(iGrid.nodesLat[iNodeIndex], iGrid.nodesLon[iNodeIndex]);
            calculatedRoute.add(coord);

            long pre = iGridRB.nodesPreBuffer[iNodeIndex];
            int edge = iGridRB.nodesRouteEdges[iNodeIndex];
            iGridIndex = (int)(pre >> 32);
            iNodeIndex = (int)(long)(pre);
            iGrid = getGrid(iGridIndex);
            //iGridRB = routingGridBuffers.get(iGridIndex);


            // Calculate distance and time
            float dist = iGrid.edgesLengths[edge];
            distOfRoute += dist;

            float maxSpeed = (float) (iGrid.edgesMaxSpeeds[edge] & 0xFF);
            maxSpeed = Math.max(allMinSpeed, Math.min(allMaxSpeed, maxSpeed));

            timeOfRoute += (dist / 1000.0f) / maxSpeed;


            // Reconstruct deleted points on edge
            if(gridRemovedEdgeGridIndex != iGridIndex) {
                // Load deleted edge points
                ObjectInputStream gridReader = null;
                try
                {
                    gridReader = new ObjectInputStream(new BufferedInputStream(new FileInputStream(MAP_GRIDS_DIR + "/" + iGridIndex + ".grid2")));
                    gridRemovedEdgeCoordsOffsets = (int[]) gridReader.readObject();
                    gridRemovedEdgeCoordsLat = (float[]) gridReader.readObject();
                    gridRemovedEdgeCoordsLon = (float[]) gridReader.readObject();
                    gridReader.close();

                    gridRemovedEdgeGridIndex = iGridIndex;
                } catch(Exception exc) {
                    exc.printStackTrace();
                } finally {
                    if(gridReader != null) {
                        try {
                            gridReader.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }

            // Reconstruct deleted points
            List<LatLong> coordsRem = new ArrayList<LatLong>();
            for(int iEdgeRem = gridRemovedEdgeCoordsOffsets[edge];
                (edge + 1 < gridRemovedEdgeCoordsOffsets.length && iEdgeRem < gridRemovedEdgeCoordsOffsets[edge + 1]) ||
                        (edge + 1 == gridRemovedEdgeCoordsOffsets.length && iEdgeRem < gridRemovedEdgeCoordsLat.length);
                iEdgeRem++) {
                LatLong coordRem = new LatLong(gridRemovedEdgeCoordsLat[iEdgeRem], gridRemovedEdgeCoordsLon[iEdgeRem]);
                coordsRem.add(coordRem);
            }
            Collections.reverse(coordsRem);
            calculatedRoute.addAll(coordsRem);

            // Go one step back
            i = pre;
        }

        LatLong coord = new LatLong(iGrid.nodesLat[iNodeIndex], iGrid.nodesLon[iNodeIndex]);
        calculatedRoute.add(coord);


        System.out.println("Route Distance: " + ((int) distOfRoute / 1000.0f) + "km");
        int timeHours = (int)timeOfRoute;
        int timeMinutes = (int)(60 * (timeOfRoute - timeHours));
        System.out.println("Route time: " + timeHours + ":" + timeMinutes);
    }
    
    

    private boolean visitNode() {
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
        // Update visitTimestamp to mark that grid is still in use
        visGrid.visitTimestamp = ++gridVisitTimestamp;

        // Mark as closed/visited
        visGridRB.nodesRouteClosedList[visNodeIndex] = true;
        openList.remove(visNodeGridIndex);
        visitedCount++;

        // Check if found
        if (visNodeGridIndex == target) {
            // Found! Return
            System.out.println("Found after " + visitedCount + " nodes visited. " + routeDistHeap.getSize()
                    + " still in heap");
            return true;
        }

        
        // Try fast follow
        if (doFastFollow) {
            int nbCount = 0;
            long nextVisitNodeGridIndex = -1;
            int nextVisitNodeIndex = -1;
            MapGridRoutingBuffer nextVisitGridRB = null;
            int nbEdge = -1;

            // Iterate over edges to neighbors
            for (int iEdge = visGrid.nodesEdgeOffset[visNodeIndex]; (visNodeIndex + 1 < visGrid.nodesEdgeOffset.length && iEdge < visGrid.nodesEdgeOffset[visNodeIndex + 1])
                    || (visNodeIndex + 1 == visGrid.nodesEdgeOffset.length && iEdge < visGrid.edgeCount); // Last node in offset array
            iEdge++) {
                // Skip if edge not accessible
                if ((visGrid.edgesInfobits[iEdge] & edgeFilterBitMask) != edgeFilterBitValue) {
                    continue;
                }

                // Get neighbor
                long nbNodeGridIndex = visGrid.edgesTargetNodeGridIndex[iEdge];
                int nbGridIndex = (int) (nbNodeGridIndex >> 32);
                int nbNodeIndex = (int) (long) (nbNodeGridIndex);

                // Skip loop edges
                if (nbNodeGridIndex == visNodeGridIndex) {
                    System.err.println("Warning: Loop edge - skipping");
                    continue;
                }

                // Get neighbor grid routing buffer
                MapGrid nbGrid;
                if (nbGridIndex == visGridIndex) {
                    nbGrid = visGrid;
                } else {
                    nbGrid = getGrid(nbGridIndex);
                }

                // Get neighbor grid routing buffer
                MapGridRoutingBuffer nbGridRB;
                if (nbGridIndex == visGridIndex) {
                    nbGridRB = visGridRB;
                } else {
                    nbGridRB = routingGridBuffers.get(nbGridIndex);
                    if (nbGridRB == null) {
                        nbGridRB = new MapGridRoutingBuffer(nbGrid.nodeCount);
                        routingGridBuffers.put(nbGridIndex, nbGridRB);
                    }
                }

                // Continue if target node node already in closed list
                if (nbGridRB.nodesRouteClosedList[nbNodeIndex]) {
                    continue;
                }

                nbCount++;
                if (nbCount > 1) {
                    break;
                }

                nextVisitNodeGridIndex = nbNodeGridIndex;
                nextVisitNodeIndex = nbNodeIndex;
                nextVisitGridRB = nbGridRB;
                nbEdge = iEdge;
            }

            if (nbCount == 1) {
                if (nextVisitNodeGridIndex != target) {
                    nextVisitGridRB.nodesPreBuffer[nextVisitNodeIndex] = visNodeGridIndex;
                    nextVisitGridRB.nodesRouteEdges[nextVisitNodeIndex] = nbEdge;
                    nextVisitGridRB.nodesRouteCosts[nextVisitNodeIndex] =
                            visGridRB.nodesRouteCosts[visNodeIndex] + calcNodeDist(nbEdge);

                    visNodeGridIndex = nextVisitNodeGridIndex;
                    //visGridIndex = (int) (visNodeGridIndex >> 32);
                    //visNodeIndex = (int) (long) (visNodeGridIndex);

                    //System.out.println("FastFollow to " + visNodeGridIndex);
                    fastFollows++;
                    return visitNode();
                } else {
                    //System.out.println("Target found during fast follow - dont follow to ensure routing properties");
                    return false;
                }
            }
            //        else {
            //            System.out.println("OUT");
            //        }
        }
        
        return false;
    }
    
    private void visitNodeEdges() 
    {
        // Get distance of node from start, remove and get index
        float nodeCost = visGridRB.nodesRouteCosts[visNodeIndex];

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
            final float nbCost = nodeCost + calcNodeDist(iEdge);

                        
            // Caching h or holding visited in a nodes does not make sense
            // Re-visiting rate seems to be below 1:10 and maps get very slow and memory consuming
            float h = MathUtils.calcNodeDistFast(nbGrid.nodesLat[nbNodeIndex], nbGrid.nodesLon[nbNodeIndex], targetLat, targetLon);

            if(h < routingNearestDist) {
                routingNearestDist = h;
                routingProgress = 1.0f - (routingNearestDist / routingMaxDist);
            }
            
            float MOTORWAY_BOOST_SUSPEND_RADIUS = 40000;
            float MOTORWAY_BOOST_DECREASE_RADIUS = 200000;
            
            if (routeMode == RoutingMode.Fastest) {
                // Underestimate if using fast routing
                if (doMotorwayBoost && h > MOTORWAY_BOOST_SUSPEND_RADIUS) {
                    // Do motorway boost (non-motorway overestimate) if enabled and not near start or target
                    float distToStart =
                            MathUtils.calcNodeDistFast(nbGrid.nodesLat[nbNodeIndex], nbGrid.nodesLon[nbNodeIndex],
                                    startLat, startLon);
                    if (distToStart > MOTORWAY_BOOST_SUSPEND_RADIUS) {
                        float distsMax = Math.min(h, distToStart);
                        
                        float maxSpeed = (float) (visGrid.edgesMaxSpeeds[iEdge] & 0xFF);
                        float boostFactor =  Math.min(1.0f, distsMax / MOTORWAY_BOOST_DECREASE_RADIUS);
                        //float motorwayBoost = (maxSpeed >= 120) ? 0.0f : 0.15f * boostFactor;
                        float motorwayBoost = (maxSpeed > 50) ? 
                                                    (maxSpeed >= 120) ? 0.0f : 0.10f * boostFactor : 
                                                                0.30f * boostFactor; 
                        //System.out.println(motorwayBoost);
                        h = h / allMaxSpeed * (1.0f + motorwayBoost);
                    } else {
                        h = h/ allMaxSpeed;                    
                    }
                } else {
                    h = h/ allMaxSpeed;
                }
            }
            

            if (openList.contains(nbNodeGridIndex)) {
                hReuse++;
                // Point open and not closed - update if necessary
                if (routeDistHeap.decreaseKeyIfSmaller(nbNodeGridIndex, nbCost + h)) {
                    nbGridRB.nodesPreBuffer[nbNodeIndex] = visNodeGridIndex;
                    nbGridRB.nodesRouteEdges[nbNodeIndex] = iEdge;
                    nbGridRB.nodesRouteCosts[nbNodeIndex] = nbCost;
                }
                againVisits++;
            } else {    
                // Point not found yet - add to heap and open list
                hCalc++;
                // Add
                routeDistHeap.add(nbNodeGridIndex, nbCost + h);
                //nodesRouteOpenList[nbIndex] = true;
                nbGridRB.nodesPreBuffer[nbNodeIndex] = visNodeGridIndex;
                nbGridRB.nodesRouteEdges[nbNodeIndex] = iEdge;
                nbGridRB.nodesRouteCosts[nbNodeIndex] = nbCost;
                openList.add(nbNodeGridIndex);
                firstVisits++;
            }
        }
    }
    
    
    private float calcNodeDist(int iEdge) {
        float edgeDist = visGrid.edgesLengths[iEdge];
        if (routeMode == RoutingMode.Fastest) {
            // Fastest route
            float maxSpeed = (float) (visGrid.edgesMaxSpeeds[iEdge] & 0xFF);
            maxSpeed = Math.max(allMinSpeed, Math.min(allMaxSpeed, maxSpeed));
            return (edgeDist / maxSpeed);
        } else if (routeMode == RoutingMode.Shortest) {
            // Shortest route
            return edgeDist;
        } else {
            throw new RuntimeException("Unsupported routing mode: " + routeMode);
        }
    }



    public void requestCancelRouting() {
        state = RoutingState.Canceling;
    }

    private void canceledRouting() {
        state = RoutingState.Standby;
        calculatedRoute.clear();
        parent.updateDisplay();
    }
}
