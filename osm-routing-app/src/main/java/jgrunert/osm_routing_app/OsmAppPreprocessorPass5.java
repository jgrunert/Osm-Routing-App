package jgrunert.osm_routing_app;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public class OsmAppPreprocessorPass5 {

	private static final float gridRaster = 0.20f;
	
	
	
	public static void main(String[] args) {
		try {
			//String outDir = "D:\\Jonas\\OSM\\germany";
			//String outDir = "D:\\Jonas\\OSM\\hamburg";
			String outDir = "D:\\Jonas\\OSM\\bawue";
			
			doPass(outDir);
		} catch (Exception e) {
			System.err.println("Error in main");
			e.printStackTrace();
		}
	}
	
	
	public static void doPass(String outDir) throws Exception
	{

	    int nodeCount = 0;
	    float[] nodesLat;
	    float[] nodesLon;
	    int[] nodesEdgeOffset;
	    
	    int[] newNodeIndices;
	    int[] newNodeIndicesInverse;
	   
	    int edgeCount = 0;
	    int[] edgesTarget;
	    byte[] edgesInfobits;
	    float[] edgesLengths;
	    byte[] edgesMaxSpeeds;
	        
		
		{
			System.out.println("Start reading nodes");
			ObjectInputStream nodeReader = new ObjectInputStream(
					new FileInputStream(
							outDir + "\\pass4-nodes.bin"));

			nodeCount = (Integer) nodeReader.readObject();
			nodesLat = (float[]) nodeReader.readObject();
			nodesLon = (float[]) nodeReader.readObject();
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
			edgesLengths = (float[]) edgeReader.readObject();
			edgesMaxSpeeds = (byte[]) edgeReader.readObject();

			edgeReader.close();
			System.out.println("Finished reading edges");
		}

		
		System.out.println("Start finding min/max");
		float minLat = Float.MAX_VALUE;
		float maxLat = Float.MIN_VALUE;
		float minLon = Float.MAX_VALUE;
		float maxLon = Float.MIN_VALUE;
		
		for(int i = 0; i < nodeCount; i++) {
			float lat = nodesLat[i];
			float lon = nodesLon[i];
			if(lat < minLat) 
				minLat = lat;
			if(lon < minLon) 
				minLon = lon;
			if(lat > maxLat) 
				maxLat = lat;
			if(lon > maxLon) 
				maxLon = lon;
		}
		
		int minLatI = (int)(minLat / gridRaster);
		int maxLatI = (int)(maxLat / gridRaster) + 1;
		minLat = minLatI * gridRaster;
		maxLat = (maxLatI * gridRaster);
		
		int minLonI = (int)(minLon / gridRaster);
		int maxLonI = (int)(maxLon / gridRaster) + 1;
		minLon = minLonI * gridRaster;
		maxLon = (maxLonI * gridRaster);
		
		System.out.println("Finished finding min/max");
		
		

		System.out.println("Start finding grid nodes");
		int nodeCounter = 0;
		newNodeIndices = new int[nodeCount];
		newNodeIndicesInverse = new int[nodeCount];
		int[] nodesGrids = new int[nodeCount]; // Indices of grids a node is located in
		int[] nodesGridIndex = new int[nodeCount]; // Indices of nodes inside a grid
		int[][] gridNodeOffsets = new int[maxLatI - minLatI][maxLonI - minLonI];
		int[][] gridNodeCounts = new int[maxLatI - minLatI][maxLonI - minLonI];
			
		int gridIndex = 0;
		int largestGrid = 0;
		for(int iLat = 0; iLat < (maxLatI - minLatI); iLat++) {
			for(int iLon = 0; iLon < (maxLonI - minLonI); iLon++) {
				float latMin = minLat + iLat * gridRaster;
				float latMax = minLat + (iLat+1) * gridRaster;
				float lonMin = minLon + iLon * gridRaster;
				float lonMax = minLon + (iLon+1) * gridRaster;
				
				int gridNodeCounter = 0;
				gridNodeOffsets[iLat][iLon] = nodeCounter;
				
				for(int iN = 0; iN < nodeCount; iN++) {
					float nLat = nodesLat[iN];
					float nLon = nodesLon[iN];
					
					if(nLat >= latMin && nLat < latMax && nLon >= lonMin && nLon < lonMax) {
						if(newNodeIndices[iN] != 0) {
							throw new RuntimeException("Node cannot be in two different grids");
						}
						if(gridNodeCounter > Integer.MAX_VALUE) {
							throw new RuntimeException("To many nodes in grid: gridIndex > Integer.MAX_VALUE");
						}
						newNodeIndices[iN] = nodeCounter;
						newNodeIndicesInverse[nodeCounter] = iN;
						nodesGrids[nodeCounter] = gridIndex;
						nodesGridIndex[nodeCounter] = gridNodeCounter;
						gridNodeCounter++;
						nodeCounter++;
						if(gridNodeCounter > largestGrid) {
							largestGrid = gridNodeCounter;
						}
					}
				}
				
				gridNodeCounts[iLat][iLon] = gridNodeCounter;
				gridIndex++;
			}
			
			System.out.println(iLat * 100 / (maxLatI - minLatI) + "% finding grid nodes");
		}		
		
		if(nodeCounter != nodeCount) {
			throw new RuntimeException("nodeIndex != nodeCount");
		}
		System.out.println("Finished finding grid nodes");
		System.out.println("Largest grid: " + largestGrid);
		
		
		// Update node coordinates
    	System.out.println("Start updating nodes");
		{
			float[] nodesLatNew = new float[nodeCount];
			for (int iN = 0; iN < nodeCount; iN++) {
				nodesLatNew[newNodeIndices[iN]] = nodesLat[iN];
			}
			nodesLat = nodesLatNew;
		}
	    
		{
			float[] nodesLonNew = new float[nodeCount];
			for (int iN = 0; iN < nodeCount; iN++) {
				nodesLonNew[newNodeIndices[iN]] = nodesLon[iN];
			}
			nodesLon = nodesLonNew;
		}
    	System.out.println("Finished updating nodes");
	    

	    int[] edgesTargetGrid = new int[edgeCount];
	    int[] edgesTargetGridIndex = new int[edgeCount];
	    {
	    System.out.println("Start updating edges");
		// Update node edge tagets and offsets
	    int[] nodesEdgeOffsetNew = new int[nodeCount];
	    int[] edgesTargetNew = new int[edgeCount];
	    byte[] edgesInfobitsNew = new byte[edgeCount];
	    float[] edgesLengthsNew = new float[edgeCount];
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
	        	int target = newNodeIndices[edgesTarget[iEdge]];
	        	edgesTargetNew[edgeOffset] = target;
	        	edgesTargetGrid[edgeOffset] = nodesGrids[target];
	        	edgesTargetGridIndex[edgeOffset] = nodesGridIndex[target];
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
    	System.out.println("Finshed updating edges");
	    }
		

		// Output
		{
			System.out.println("Start writing grid");
			ObjectOutputStream os = new ObjectOutputStream(
					new FileOutputStream(outDir + "\\grid-final.bin"));
			os.writeFloat(gridRaster);
			os.writeFloat(minLat);
			os.writeFloat(minLon);
			os.writeInt(maxLatI - minLatI);
			os.writeInt(maxLonI - minLonI);
			os.writeObject(gridNodeOffsets);
	        os.writeObject(gridNodeCounts);
			os.close();
			System.out.println("Finished serializing grid");
		}
		
		// Output
		{
			System.out.println("Start writing grid2");
			ObjectOutputStream os = new ObjectOutputStream(
					new FileOutputStream(outDir + "\\grids\\grids.index"));
			os.writeFloat(gridRaster);
			os.writeFloat(minLat);
			os.writeFloat(minLon);
			os.writeInt(maxLatI - minLatI);
			os.writeInt(maxLonI - minLonI);
			os.close();
			System.out.println("Finished serializing grid2");
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
		
		
		
		// Save grid files 
		{
	        System.out.println("Start exporting grids"); 

			gridIndex = 0;
			for(int iLat = 0; iLat < (maxLatI - minLatI); iLat++) {
				for(int iLon = 0; iLon < (maxLonI - minLonI); iLon++) {
					// Get node count and offset
					int gridNodeFirst = gridNodeOffsets[iLat][iLon];
					int gridNodeCount = gridNodeCounts[iLat][iLon];
					int nodeLast = gridNodeFirst + gridNodeCount - 1;
					
					// Get edge count and offset
					int gridEdgeCount;
					int gridEdgeFirst;
					if (gridNodeCount > 0) {
						// Get edge count
						gridEdgeFirst = nodesEdgeOffset[gridNodeFirst];

						if (nodeLast + 1 == nodesEdgeOffset.length) {
							gridEdgeCount = edgeCount - gridEdgeFirst;
						} else {
							gridEdgeCount = nodesEdgeOffset[nodeLast + 1]
									- gridEdgeFirst;
						}
					} else {
						gridEdgeFirst = 0;
						gridEdgeCount = 0;
					}
			        
					// Extract nodes
			        float[] gridNodesLat = new float[gridNodeCount];
			        float[] gridNodesLon = new float[gridNodeCount];
			        int[] gridNodesEdgeOffset = new int[gridNodeCount];
			       
					for(int iN = 0; iN < gridNodeCount; iN++) {
						gridNodesLat[iN] = nodesLat[gridNodeFirst + iN];
						gridNodesLon[iN] = nodesLon[gridNodeFirst + iN];
						gridNodesEdgeOffset[iN] = nodesEdgeOffset[gridNodeFirst + iN] - gridEdgeFirst;
					}

					// Extract edges					
			        long[] gridEdgesTargetNodeGrid = new long[gridEdgeCount];
			        byte[] gridEdgesInfobits = new byte[gridEdgeCount];
			        float[] gridEdgesLengths = new float[gridEdgeCount];
			        byte[] gridEdgesMaxSpeeds = new byte[gridEdgeCount];
					for(int iE = 0; iE < gridEdgeCount; iE++) {
						int gridI = edgesTargetGrid[gridEdgeFirst + iE];
						int nodeI = edgesTargetGridIndex[gridEdgeFirst + iE];
		                long nbNodeGridIndex = (((long)gridI) << 32) | (nodeI & 0xffffffffL);						
		                gridEdgesTargetNodeGrid[iE] = nbNodeGridIndex;
		                
						gridEdgesInfobits[iE] = edgesInfobits[gridEdgeFirst + iE];
						gridEdgesLengths[iE] = edgesLengths[gridEdgeFirst + iE];
						gridEdgesMaxSpeeds[iE] = edgesMaxSpeeds[gridEdgeFirst + iE];
					}
					
					
					// Check for loop free
					for(int iN = 0; iN < gridNodeCount; iN++) {

		                long nodeGridIndex = (((long)gridIndex) << 32) | (iN & 0xffffffffL);	
						
					     for (int iEdge = gridNodesEdgeOffset[iN]; 
				                    (iN + 1 < gridNodesEdgeOffset.length && iEdge < gridNodesEdgeOffset[iN + 1])
				                    || (iN + 1 == gridNodesEdgeOffset.length && iEdge < gridEdgeCount); // Last node in offset array
				                    iEdge++) {
					    	 //System.out.println(gridEdgesTargetGrid[iEdge] + "!=" + gridIndex + "||" + gridEdgesTargetGridIndex[iEdge] + "!=" +  iN);
					    	 //assert(gridEdgesTargetGrid[iEdge] != gridIndex || gridEdgesTargetGridIndex[iEdge] != iN);
					    	 if(gridEdgesTargetNodeGrid[iEdge] == nodeGridIndex) {
					    		 System.err.println("Warning: Loop at " + gridIndex + ":" + iN);
					    	 }
				             //long a = (((long)gridEdgesTargetGrid[iEdge]) << 32) | (gridEdgesTargetGridIndex[iEdge] & 0xffffffffL);
				             //long b = (((long)gridIndex) << 32) | (iN & 0xffffffffL);
				             //assert a != b;
					    	 
					    	 //System.out.println(iN + " to " + gridEdgesTargetGridIndex[iEdge]);
					     }
					}
			        
					
					// Write output
			        ObjectOutputStream os = new ObjectOutputStream(new FileOutputStream(outDir + "\\grids\\" + gridIndex + ".grid"));
			        os.writeInt(gridNodeCount); // Node count
			        os.writeInt(gridEdgeCount); // Edge count
			        // Nodes
			        os.writeObject(gridNodesLat);
			        os.writeObject(gridNodesLon);
			        os.writeObject(gridNodesEdgeOffset);
			        // Edges
			        os.writeObject(gridEdgesTargetNodeGrid);
			        os.writeObject(gridEdgesInfobits);
			        os.writeObject(gridEdgesLengths);
			        os.writeObject(gridEdgesMaxSpeeds);
					os.close();
					
					gridIndex++;
				}
				System.out.println(iLat * 100 / (maxLatI - minLatI) + "% exporting grids");
			}
			
	        System.out.println("Finished exporting grids");  
	        System.out.println("Grids: " + gridIndex + "(" + (maxLatI - minLatI) + "*" + (maxLonI - minLonI) + ")");
		}
		
		System.out.println("Finished Pass5");
	}
}
