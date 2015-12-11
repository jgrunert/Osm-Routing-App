package de.jgrunert.osm_routing;

@SuppressWarnings("javadoc")
public class MapGrid {

    public final int index;
    public final float lat;
    public final float lon;
    public final boolean loaded;
    
    public final int nodeCount;
    public final float[] nodesLat;
    public final float[] nodesLon;
    public final int[] nodesEdgeOffset;
   
    public final int edgeCount;
    public final int[] edgesTarget;
    public final byte[] edgesInfobits;
    public final float[] edgesLengths;
    public final byte[] edgesMaxSpeeds;

    
    /**
     * Creates empty map grid
     */
    public MapGrid(int index, float lat, float lon) {
        super();
        this.index = index;
        this.lat = lat;
        this.lon = lon;
        this.nodeCount = 0;
        this.edgeCount = 0;
        this.loaded = false;
        
        this.nodesLat = null;
        this.nodesLon = null;
        this.nodesEdgeOffset = null;

        this.edgesTarget = null;
        this.edgesInfobits = null;
        this.edgesLengths = null;
        this.edgesMaxSpeeds = null;
    }


    /**
     * Creates loaded map grid
     */
    public MapGrid(int index, float lat, float lon, int nodeCount, float[] nodesLat, float[] nodesLon,
            int[] nodesEdgeOffset, int edgeCount, int[] edgesTarget, byte[] edgesInfobits, float[] edgesLengths,
            byte[] edgesMaxSpeeds) {
        super();
        this.index = index;
        this.lat = lat;
        this.lon = lon;
        this.loaded = true;
        this.nodeCount = nodeCount;
        this.nodesLat = nodesLat;
        this.nodesLon = nodesLon;
        this.nodesEdgeOffset = nodesEdgeOffset;
        this.edgeCount = edgeCount;
        this.edgesTarget = edgesTarget;
        this.edgesInfobits = edgesInfobits;
        this.edgesLengths = edgesLengths;
        this.edgesMaxSpeeds = edgesMaxSpeeds;
    }

}
