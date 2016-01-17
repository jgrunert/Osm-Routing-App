package de.jgrunert.osm_routing;

import android.os.Debug;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.ObjectInputStream;


@SuppressWarnings("javadoc")
public class MapGrid {

    public final int index;
    // Indicates if grid data is loaded
    public final boolean loaded;
    // Timestamp of the last node visit in this array
    public long visitTimestamp;
    
    public final int nodeCount;
    public final float[] nodesLat;
    public final float[] nodesLon;
    public final int[] nodesEdgeOffset;
   
    public final int edgeCount;
    public final long[] edgesTargetNodeGridIndex;
    public final byte[] edgesInfobits;
    public final float[] edgesLengths;
    public final byte[] edgesMaxSpeeds;

    
    /**
     * Creates empty map grid
     */
    public MapGrid(int index) {
        super();
        this.index = index;
        this.visitTimestamp = 0;
        
        this.nodeCount = 0;
        this.edgeCount = 0;
        this.loaded = false;
        
        this.nodesLat = null;
        this.nodesLon = null;
        this.nodesEdgeOffset = null;

        this.edgesTargetNodeGridIndex = null;
        this.edgesInfobits = null;
        this.edgesLengths = null;
        this.edgesMaxSpeeds = null;
    }


    /**
     * Creates loaded map grid
     */
    public MapGrid(int index, long gridOperationTimestamp, File gridFile) throws Exception {
        super();

        this.index = index;
        this.visitTimestamp = gridOperationTimestamp;

        ObjectInputStream gridReader = null;
        try
        {
            gridReader = new ObjectInputStream(new BufferedInputStream(new FileInputStream(gridFile)));
            this.loaded = true;
            this.nodeCount = gridReader.readInt();
            this.edgeCount = gridReader.readInt();

            this.nodesLat = (float[]) gridReader.readObject();
            this.nodesLon = (float[]) gridReader.readObject();
            this.nodesEdgeOffset = (int[]) gridReader.readObject();
            this.edgesTargetNodeGridIndex = (long[]) gridReader.readObject();
            this.edgesInfobits = (byte[]) gridReader.readObject();
            this.edgesLengths = (float[]) gridReader.readObject();
            this.edgesMaxSpeeds = (byte[]) gridReader.readObject();

            gridReader.close();
        }
        finally {
            if(gridReader != null) {
                gridReader.close();
            }
        }
    }

}
