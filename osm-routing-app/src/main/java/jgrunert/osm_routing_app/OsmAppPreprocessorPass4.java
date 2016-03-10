package jgrunert.osm_routing_app;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.util.logging.Level;

public class OsmAppPreprocessorPass4 {

	public static void main(String[] args) {
		try {
			//String outDir = "D:\\Jonas\\OSM\\germany";
			String outDir = "D:\\Jonas\\OSM\\hamburg";
			//String outDir = "D:\\Jonas\\OSM\\bawue";
			
			doPass(outDir);
		} catch (Exception e) {
			OsmAppPreprocessor.LOG.severe("Error in main");
			OsmAppPreprocessor.LOG.log(Level.SEVERE, "Exception", e);
		}
	}
	
	
	public static void doPass(String outDir) throws Exception {
	{
		OsmAppPreprocessor.LOG.info("Start reading nodes");
        DataInputStream nodeReader = new DataInputStream(new BufferedInputStream(new FileInputStream(outDir + File.separator + "pass3-nodes.bin")));
        
        int nodeCount = nodeReader.readInt();
        float[] nodesLat = new float[nodeCount];
        float[] nodesLon = new float[nodeCount];
        int[] nodesEdgeOffset = new int[nodeCount];
        
        int perc100 = nodeCount / 100;
        for(int i = 0; i < nodeCount; i++) {
            nodesLat[i] = nodeReader.readFloat();
            nodesLon[i] = nodeReader.readFloat();
            nodesEdgeOffset[i] = nodeReader.readInt();
			if(i % perc100 == 0) {
				OsmAppPreprocessor.LOG.info((i / perc100) + "%  reading nodes");
			}
        }
        
        nodeReader.close();
        OsmAppPreprocessor.LOG.info("Finished reading nodes");
        

        OsmAppPreprocessor.LOG.info("Start serializing nodes");    
        ObjectOutputStream os = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(outDir + File.separator + "pass4-nodes.bin")));
        os.writeObject(nodeCount);
        os.writeObject(nodesLat);   
        os.writeObject(nodesLon);        
        os.writeObject(nodesEdgeOffset);      
        os.close();
        OsmAppPreprocessor.LOG.info("Finished serializing nodes");       
		}
        

		{
			// Read edge count from seperate file
			DataInputStream edgeCountReader = new DataInputStream(
					new BufferedInputStream(new FileInputStream(outDir + File.separator + "pass3-edges-count.bin")));
			int edgeCount = edgeCountReader.readInt();
			edgeCountReader.close();

			// Read edges
			OsmAppPreprocessor.LOG.info("Start reading edges");
			DataInputStream edgeReader = new DataInputStream(
					new BufferedInputStream(new FileInputStream(outDir + File.separator + "pass3-edges.bin")));
			int[] edgesTarget = new int[edgeCount];
			byte[] edgesInfobits = new byte[edgeCount];
			float[] edgeLengths = new float[edgeCount];
			byte[] edgeMaxSpeeds = new byte[edgeCount];

			int perc100 = edgeCount / 100;
			for (int i = 0; i < edgeCount; i++) {
				edgesTarget[i] = edgeReader.readInt();
				if (edgesTarget[i] == 0) {
					OsmAppPreprocessor.LOG.info("" + i);
				}
				edgesInfobits[i] = edgeReader.readByte();
				edgeLengths[i] = edgeReader.readFloat();
				edgeMaxSpeeds[i] = edgeReader.readByte();
				if (i % perc100 == 0) {
					OsmAppPreprocessor.LOG.info((i / perc100) + "% reading edges");
				}
			}

			// Serialize edges
			edgeReader.close();
			OsmAppPreprocessor.LOG.info("Finished reading edges");

			OsmAppPreprocessor.LOG.info("Start serializing edges");
			ObjectOutputStream os = new ObjectOutputStream(new BufferedOutputStream(
					new FileOutputStream(outDir + File.separator + "pass4-edges.bin")));
			os.writeObject(edgeCount);
			os.writeObject(edgesTarget);
			os.writeObject(edgesInfobits);
			os.writeObject(edgeLengths);
			os.writeObject(edgeMaxSpeeds);
			os.close();
			OsmAppPreprocessor.LOG.info("Finished serializing edges");
		}
	}
}
