package jgrunert.osm_routing_app;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public class OsmAppPreprocessorPass5 {

	private static final double gridSize = 0.05;
	
	
	
	public static void main(String[] args) {
		try {
			//String outDir = "D:\\Jonas\\OSM\\germany";
			String outDir = "D:\\Jonas\\OSM\\hamburg";
			//String outDir = "D:\\Jonas\\OSM\\bawue";
			
			pass5(outDir + "\\pass4-nodes.bin", outDir + "\\pass4-edges.bin", outDir);
		} catch (Exception e) {
			System.err.println("Error in main");
			e.printStackTrace();
		}
	}
	
	
	private static void pass5(String nodeFile, String edgeFile, String outDir) throws Exception
	{

	    int nodeCount = 0;
	    double[] nodesLat;
	    double[] nodesLon;
	    int[] nodesEdgeOffset;
	    
	    int[] newNodeIndices;
	    int[] newNodeIndicesInverse;
	   
	    int edgeCount = 0;
	    int[] edgesTarget;
	    byte[] edgesInfobits;
	    short[] edgesLengths;
	    byte[] edgesMaxSpeeds;
	        
		
		{
			System.out.println("Start reading nodes");
			ObjectInputStream nodeReader = new ObjectInputStream(
					new FileInputStream(
							outDir + "\\pass4-nodes.bin"));

			nodeCount = (Integer) nodeReader.readObject();
			nodesLat = (double[]) nodeReader.readObject();
			nodesLon = (double[]) nodeReader.readObject();
			nodesEdgeOffset = (int[]) nodeReader.readObject();

			nodeReader.close();
			System.out.println("Finished reading nodes");
		}

		{
			System.out.println("Start reading edges");
			ObjectInputStream edgeReader = new ObjectInputStream(
					new FileInputStream(
							outDir + "\\pass4-edges.bin"));
			edgeCount = (Integer) edgeReader.readObject();
			edgesTarget = (int[]) edgeReader.readObject();
			edgesInfobits = (byte[]) edgeReader.readObject();
			edgesLengths = (short[]) edgeReader.readObject();
			edgesMaxSpeeds = (byte[]) edgeReader.readObject();

			edgeReader.close();
			System.out.println("Finished reading edges");
		}

		
		System.out.println("Start finding min/max");
		double minLat = Double.MAX_VALUE;
		double maxLat = Double.MIN_VALUE;
		double minLon = Double.MAX_VALUE;
		double maxLon = Double.MIN_VALUE;
		
		for(int i = 0; i < nodeCount; i++) {
			double lat = nodesLat[i];
			double lon = nodesLon[i];
			if(lat < minLat) 
				minLat = lat;
			if(lon < minLon) 
				minLon = lon;
			if(lat > maxLat) 
				maxLat = lat;
			if(lon > maxLon) 
				maxLon = lon;
		}
		
		int minLatI = (int)(minLat / gridSize);
		int maxLatI = (int)(maxLat / gridSize) + 1;
		minLat = minLatI * gridSize;
		maxLat = (maxLatI * gridSize);
		
		int minLonI = (int)(minLon / gridSize);
		int maxLonI = (int)(maxLon / gridSize) + 1;
		minLon = minLonI * gridSize;
		maxLon = (maxLonI * gridSize);
		
		System.out.println("Finished finding min/max");
		
		

		System.out.println("Start finding grid nodes");
		int nodeCounter = 0;
		newNodeIndices = new int[nodeCount];
		newNodeIndicesInverse = new int[nodeCount];
		int[][] gridNodeOffsets = new int[maxLatI - minLatI][maxLonI - minLonI];
		int[][] gridNodeCounts = new int[maxLatI - minLatI][maxLonI - minLonI];
				
		for(int iLat = 0; iLat < (maxLatI - minLatI); iLat++) {
			for(int iLon = 0; iLon < (maxLonI - minLonI); iLon++) {
				double latMin = minLat + iLat * gridSize;
				double latMax = minLat + (iLat+1) * gridSize;
				double lonMin = minLon + iLon * gridSize;
				double lonMax = minLon + (iLon+1) * gridSize;
				
				int gridNodes = 0;
				gridNodeOffsets[iLat][iLon] = nodeCounter;
				
				for(int iN = 0; iN < nodeCount; iN++) {
					double nLat = nodesLat[iN];
					double nLon = nodesLon[iN];
					
					if(nLat >= latMin && nLat < latMax && nLon >= lonMin && nLon < lonMax) {
						if(newNodeIndices[iN] != 0) {
							throw new RuntimeException("Node cannot be in two different grids");
						}
						newNodeIndices[iN] = nodeCounter;
						newNodeIndicesInverse[nodeCounter] = iN;
						gridNodes++;
						nodeCounter++;
					}
				}
				
				gridNodeCounts[iLat][iLon] = gridNodes;
			}
		}		
		
		if(nodeCounter != nodeCount) {
			throw new RuntimeException("nodeIndex != nodeCount");
		}
		System.out.println("Finished finding grid nodes");
		
		
		// Update node coordinates
		{
			double[] nodesLatNew = new double[nodeCount];
			for (int iN = 0; iN < nodeCount; iN++) {
				nodesLatNew[newNodeIndices[iN]] = nodesLat[iN];
			}
			nodesLat = nodesLatNew;
		}
	    
		{
			double[] nodesLonNew = new double[nodeCount];
			for (int iN = 0; iN < nodeCount; iN++) {
				nodesLonNew[newNodeIndices[iN]] = nodesLon[iN];
			}
			nodesLon = nodesLonNew;
		}
	    
	    
	    {
		// Update node edge tagets and offsets
	    int[] nodesEdgeOffsetNew = new int[nodeCount];
	    int[] edgesTargetNew = new int[edgeCount];
	    byte[] edgesInfobitsNew = new byte[edgeCount];
	    short[] edgesLengthsNew = new short[edgeCount];
	    byte[] edgesMaxSpeedsNew = new byte[edgeCount];

	    int edgeOffset = 0;
	    for(int iNNew = 0; iNNew < nodeCount; iNNew++) 
	    {
	    	nodesEdgeOffsetNew[iNNew] = edgeOffset;
	    	int iN = newNodeIndicesInverse[iNNew];
	        for (int iEdge = nodesEdgeOffset[iN]; (iN + 1 < nodesEdgeOffset.length && iEdge < nodesEdgeOffset[iN + 1])
                    || (iN + 1 == nodesEdgeOffset.length && iEdge < edgesTarget.length); // Last node in offset array
                    iEdge++) 
	        {
	        	edgesTargetNew[edgeOffset] = newNodeIndices[edgesTarget[iEdge]];
	        	edgesInfobitsNew[edgeOffset] = edgesInfobits[iEdge];
	        	edgesLengthsNew[edgeOffset] = edgesLengths[iEdge];
	        	edgesMaxSpeedsNew[edgeOffset] = edgesMaxSpeeds[iEdge];
	        	edgeOffset++;
	        }
	    }
	    nodesEdgeOffset = nodesEdgeOffsetNew;
	    edgesTarget = edgesTargetNew;
	    edgesInfobits = edgesInfobitsNew;
	    edgesLengths = edgesLengthsNew;
	    edgesMaxSpeeds = edgesMaxSpeedsNew;
	    }
		

		// Output
		{
			System.out.println("Start writing grid");
			ObjectOutputStream os = new ObjectOutputStream(
					new FileOutputStream(outDir + "\\grid-final.bin"));
			os.writeObject(minLat);
			os.writeObject(minLon);
			os.writeObject(maxLatI - minLatI);
			os.writeObject(maxLonI - minLonI);
			os.writeObject(gridNodeOffsets);
	        os.writeObject(gridNodeCounts);
			os.close();
			System.out.println("Finished serializing grid");
		}
		
		{
	        System.out.println("Start serializing nodes");    
	        ObjectOutputStream os = new ObjectOutputStream(new FileOutputStream(outDir + "\\nodes-final.bin"));
	        os.writeObject(nodeCount);
	        os.writeObject(nodesLat);   
	        os.writeObject(nodesLon);        
	        os.writeObject(nodesEdgeOffset);      
	        os.close();
	        System.out.println("Finished serializing nodes");    
		}
		
		{
	        System.out.println("Start serializing edges");    
	        ObjectOutputStream os = new ObjectOutputStream(new FileOutputStream(outDir + "\\edges-final.bin"));
	        os.writeObject(edgeCount);
	        os.writeObject(edgesTarget);   
	        os.writeObject(edgesInfobits);        
	        os.writeObject(edgesLengths);          
	        os.writeObject(edgesMaxSpeeds);      
	        os.close();
	        System.out.println("Finished serializing edges");  
		}
		
		System.out.println("Finished Pass5");
	}
}
