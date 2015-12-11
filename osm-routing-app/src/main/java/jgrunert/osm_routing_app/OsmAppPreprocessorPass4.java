package jgrunert.osm_routing_app;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectOutputStream;

public class OsmAppPreprocessorPass4 {
	
	
	public static void main(String[] args) {
		try {
			//String outDir = "D:\\Jonas\\OSM\\germany";
			String outDir = "D:\\Jonas\\OSM\\hamburg";
			//String outDir = "D:\\Jonas\\OSM\\bawue";
			
			doPass(outDir);
		} catch (Exception e) {
			System.err.println("Error in main");
			e.printStackTrace();
		}
	}
	
	
	public static void doPass(String outDir) throws Exception {
	{
		System.out.println("Start reading nodes");
        DataInputStream nodeReader = new DataInputStream(new FileInputStream(outDir + "\\pass3-nodes.bin"));
        
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
				System.out.println((i / perc100) + "%  reading nodes");
			}
        }
        
        nodeReader.close();
        System.out.println("Finished reading nodes");
        

        System.out.println("Start serializing nodes");    
        ObjectOutputStream os = new ObjectOutputStream(new FileOutputStream(outDir + "\\pass4-nodes.bin"));
        os.writeObject(nodeCount);
        os.writeObject(nodesLat);   
        os.writeObject(nodesLon);        
        os.writeObject(nodesEdgeOffset);      
        os.close();
        System.out.println("Finished serializing nodes");       
		}
        

		{
			// Read edge count from seperate file
			DataInputStream edgeCountReader = new DataInputStream(
					new FileInputStream(outDir + "\\pass3-edges-count.bin"));
			int edgeCount = edgeCountReader.readInt();
			edgeCountReader.close();

			// Read edges
			System.out.println("Start reading edges");
			DataInputStream edgeReader = new DataInputStream(
					new FileInputStream(outDir + "\\pass3-edges.bin"));
			int[] edgesTarget = new int[edgeCount];
			byte[] edgesInfobits = new byte[edgeCount];
			float[] edgeLengths = new float[edgeCount];
			byte[] edgeMaxSpeeds = new byte[edgeCount];

			int perc100 = edgeCount / 100;
			for (int i = 0; i < edgeCount; i++) {
				edgesTarget[i] = edgeReader.readInt();
				if (edgesTarget[i] == 0) {
					System.out.println(i);
				}
				edgesInfobits[i] = edgeReader.readByte();
				edgeLengths[i] = edgeReader.readFloat();
				edgeMaxSpeeds[i] = edgeReader.readByte();
				if (i % perc100 == 0) {
					System.out.println((i / perc100) + "% reading edges");
				}
			}

			// Serialize edges
			edgeReader.close();
			System.out.println("Finished reading edges");

			System.out.println("Start serializing edges");
			ObjectOutputStream os = new ObjectOutputStream(
					new FileOutputStream(outDir + "\\pass4-edges.bin"));
			os.writeObject(edgeCount);
			os.writeObject(edgesTarget);
			os.writeObject(edgesInfobits);
			os.writeObject(edgeLengths);
			os.writeObject(edgeMaxSpeeds);
			os.close();
			System.out.println("Finished serializing edges");
		}
	}
}