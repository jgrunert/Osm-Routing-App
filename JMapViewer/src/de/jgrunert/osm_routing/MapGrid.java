package de.jgrunert.osm_routing;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInputStream;

@SuppressWarnings("javadoc")
public class MapGrid {

    public final boolean loaded;
    
    public final int nodeCount;
    public final float[] nodesLat;
    public final float[] nodesLon;
    public final int[] nodesEdgeOffset;
   
    public final int edgeCount;
    public final int[] edgesTargetGrid;
    public final short[] edgesTargetGridIndex;
    public final byte[] edgesInfobits;
    public final float[] edgesLengths;
    public final byte[] edgesMaxSpeeds;

    
    /**
     * Creates empty map grid
     */
    public MapGrid() {
        super();
        this.nodeCount = 0;
        this.edgeCount = 0;
        this.loaded = false;
        
        this.nodesLat = null;
        this.nodesLon = null;
        this.nodesEdgeOffset = null;

        this.edgesTargetGrid = null;
        this.edgesTargetGridIndex = null;
        this.edgesInfobits = null;
        this.edgesLengths = null;
        this.edgesMaxSpeeds = null;
    }


    /**
     * Creates loaded map grid
     */
    public MapGrid(String gridFile) throws Exception {
        super();
        
        ObjectInputStream gridReader = new ObjectInputStream(new FileInputStream(gridFile));
        this.loaded = true;
        this.nodeCount = gridReader.readInt();
        this.edgeCount = gridReader.readInt();
        
        this.nodesLat = (float[])gridReader.readObject();
        this.nodesLon = (float[])gridReader.readObject();
        this.nodesEdgeOffset = (int[])gridReader.readObject();
        this.edgesTargetGrid = (int[])gridReader.readObject();
        this.edgesTargetGridIndex = (short[])gridReader.readObject();
        this.edgesInfobits = (byte[])gridReader.readObject();
        this.edgesLengths = (float[])gridReader.readObject();
        this.edgesMaxSpeeds = (byte[])gridReader.readObject();
    }

}
