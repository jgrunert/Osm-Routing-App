package de.jgrunert.osm_routing;

/**
 * Buffers data while routing for a grid
 * 
 * 
 * @author Jonas Grunert
 *
 */
@SuppressWarnings("javadoc")
public class MapGridRoutingBuffer {
    // Buffer for predecessors when calculating routes
    public final long[] nodesPreBuffer;
    // Buffer for node visisted when calculating routes
    public final boolean[] nodesRouteClosedList;
    public final float[] nodesRouteDists;
    
    
    /**
     * Creates new empty grid routing buffer
     * @param nodeCount Number of nodes in this grid
     */
    public MapGridRoutingBuffer(int nodeCount) {
        nodesPreBuffer = new long[nodeCount];
        nodesRouteClosedList = new boolean[nodeCount];
        nodesRouteDists = new float[nodeCount];
    }
}
