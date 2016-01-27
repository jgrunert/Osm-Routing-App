package jgrunert.osm_routing_app;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * Pass to remove Level-2 nodes from main data structure.
 * Also removes loop edges
 * 
 * @author Jonas Grunert
 *
 */
public class OsmAppPreprocessorPass4B {

	
	public static void main(String[] args) {
		try {
			String outDir = "D:\\Jonas\\OSM\\germany";
			//String outDir = "D:\\Jonas\\OSM\\hamburg";
			//String outDir = "D:\\Jonas\\OSM\\bawue";
			
			doPass(outDir);
		} catch (Exception e) {
			OsmAppPreprocessor.LOG.severe("Error in main");
			OsmAppPreprocessor.LOG.log(Level.SEVERE, "Exception", e);
		}
	}
	
	
	public static void doPass(String outDir) throws Exception
	{

	    int nodeCount = 0;
	    float[] nodesLat;
	    float[] nodesLon;
	    int[] nodesEdgeOffset;
	    
	    //int[] newNodeIndices;
	    //int[] newNodeIndicesInverse;
	   
	    int edgeCount = 0;
	    int[] edgesTarget;
	    byte[] edgesInfobits;
	    float[] edgesLengths;
	    byte[] edgesMaxSpeeds;	    

	    int percTmp;
	        
	    
		// Load nodes and edges
		{
			OsmAppPreprocessor.LOG.info("Start reading nodes");
			ObjectInputStream nodeReader = new ObjectInputStream(
					new FileInputStream(
							outDir + "\\pass4-nodes.bin"));

			nodeCount = (Integer) nodeReader.readObject();
			nodesLat = (float[]) nodeReader.readObject();
			nodesLon = (float[]) nodeReader.readObject();
			nodesEdgeOffset = (int[]) nodeReader.readObject();

			nodeReader.close();
			OsmAppPreprocessor.LOG.info("Finished reading nodes");
		}

		{
			OsmAppPreprocessor.LOG.info("Start reading edges");
			ObjectInputStream edgeReader = new ObjectInputStream(
					new FileInputStream(
							outDir + "\\pass4-edges.bin"));
			edgeCount = (Integer) edgeReader.readObject();
			edgesTarget = (int[]) edgeReader.readObject();
			edgesInfobits = (byte[]) edgeReader.readObject();
			edgesLengths = (float[]) edgeReader.readObject();
			edgesMaxSpeeds = (byte[]) edgeReader.readObject();

			edgeReader.close();
			OsmAppPreprocessor.LOG.info("Finished reading edges");
		}

		
		
		// Find nodes to remove
		OsmAppPreprocessor.LOG.info("Start find nodes to remove");
		boolean[] nodesToDelete = new boolean[nodeCount];
		boolean[] edgesToDelete = new boolean[edgeCount];

		int node0Count = 0;
		int node1Count = 0;
		int node2Count = 0;
		int nodeEdgeDiffCount = 0;
		int nodesDeleted = 0;
		int nodesKeptCount = 0;
		int edgesKeptCount = 0;
		int zeroNodeCount = 0;
		int errorEdgeCount = 0;
		int loopEdgeCount = 0;


		int ndTmp;
		boolean edgesDifferTmp; // Indicates if there are edges with different properties (merging not possible)
		byte edgeInfobitsTmp = 0;
		byte edgeMaxSpeedTmp = 0;

	    percTmp = nodeCount / 10;
		for(int iNd = 0; iNd < nodeCount; iNd++) {
			
			ndTmp = 0;
			edgesDifferTmp = false;
			
			 for (int iEdge = nodesEdgeOffset[iNd]; (iNd + 1 < nodesEdgeOffset.length && iEdge < nodesEdgeOffset[iNd + 1])
	                    || (iNd + 1 == nodesEdgeOffset.length && iEdge < edgeCount); // Last node in offset array
					 iEdge++) {
				 if(ndTmp == 0) {
					 edgeInfobitsTmp = edgesInfobits[iEdge];
					 edgeMaxSpeedTmp = edgesMaxSpeeds[iEdge];
				 } else {
				     // Dont merge if maxspeed different or infobits
					 if((edgeInfobitsTmp) != (edgesInfobits[iEdge]) || edgeMaxSpeedTmp != edgesMaxSpeeds[iEdge]) {
				     // Dont merge if maxspeed different or infobits (except oneway flag, otherwise no merge of oneway-routes such as motorways)
					 //if((edgeInfobitsTmp >> 1) != (edgesInfobits[iEdge] >> 1) || edgeMaxSpeedTmp != edgesMaxSpeeds[iEdge]) {
						 // TODO Code does not merge motorways
						 edgesDifferTmp = true;
								 break;
					 }
				 }
				 
				 ndTmp++;
				 
				 if(ndTmp > 2) {
					 break;
				 }
			}

			if (edgesDifferTmp) {
				nodeEdgeDiffCount++;
			} else if (ndTmp == 0) {
				node0Count++;
			} else if (ndTmp == 1) {
				node1Count++;
			} else if (ndTmp == 2) {
				node2Count++;
			}
			
			//if(edgesDifferTmp || ndTmp != 2) {
			if(edgesDifferTmp || ndTmp > 2) {
				nodesKeptCount++;
			} else {
				nodesDeleted++;
				nodesToDelete[iNd] = true;
			}
			
			if (iNd % percTmp == 0) {
				System.out.println(iNd / percTmp * 10 + "% finding nodes to remove");
			}
		}		
		

		// Remove nodes, update edges
		OsmAppPreprocessor.LOG.info("Start removing nodes");
		
		List<Integer> nodesKeptIndices = new ArrayList<>(nodesKeptCount); // Initialize with nodesKeptCount
		//List<Integer> edgesKeptIndices = new ArrayList<>(nodesKeptCount); // Also use nodesKeptCount (at least this count)
		Map<Integer, List<NodeFollowPath.Coord>> edgeRemovedCoords = new HashMap<>();
		int edgeRemovedCoordsCount = 0;

	    percTmp = nodeCount / 100;
		for (int iNd = 0; iNd < nodeCount; iNd++) {
			
			// Ignore deleted nodes
			if(nodesToDelete[iNd]) {
				continue;
			}
			
			int nodeEdgeCount = 0;
			
			// Follow and update edges if necesary
			 for (int iEdge = nodesEdgeOffset[iNd]; (iNd + 1 < nodesEdgeOffset.length && iEdge < nodesEdgeOffset[iNd + 1])
	                    || (iNd + 1 == nodesEdgeOffset.length && iEdge < edgeCount); // Last node in offset array
					 iEdge++) {
				// Follow edge path
				NodeFollowPath followPath = followNodeEdge(iNd, iEdge,
						nodesEdgeOffset, nodesLat, nodesLon,
						edgeCount, edgesTarget, edgesLengths, nodesToDelete);
				
				if(followPath == null || followPath.Target == iNd) {
					if(followPath == null) {
						errorEdgeCount++;						
					} else {
						loopEdgeCount++;
					}
					// Delete edge if has error
					edgesToDelete[iEdge] = true;
					continue;
				}

				// Update edge length
				edgesLengths[iEdge] = edgesLengths[iEdge] + followPath.Dist;
				// Update edge target
				edgesTarget[iEdge] = followPath.Target;
				assert !nodesToDelete[followPath.Target];
				edgeRemovedCoords.put(iEdge, followPath.PathCoords);
				edgeRemovedCoordsCount += followPath.PathCoords.size();
				edgesKeptCount++;
				nodeEdgeCount++;
			}
			 
			if (nodeEdgeCount == 0) {
				zeroNodeCount++;
			} 
			
//			if(nodeEdgeCount == 2) {
//				System.out.println(edgesMaxSpeeds[nodesEdgeOffset[iNd]]);
//			}
			
			nodesKeptIndices.add(iNd);
			 
			if (iNd % percTmp == 0) {
				System.out.println(iNd / percTmp + "% removing nodes");
			}
		}

		OsmAppPreprocessor.LOG.info("Converting temporary kept-lists to arrays");
		
		assert nodesKeptCount == nodesKeptIndices.size();
		assert edgeRemovedCoords.size() == edgesKeptCount;
		
		Integer[] nodesKeptIndicesArray = nodesKeptIndices.toArray(new Integer[nodesKeptCount]);
		nodesKeptIndices = null;
		//Integer[] edgesKeptIndicesArray = edgesKeptIndices.toArray(new Integer[0]);
		//edgesKeptIndices = null;

		
		// Extract nodes and edges to keep
		OsmAppPreprocessor.LOG.info("Extract nodes and edges to keep");

	    float[] nodesLatKept = new float[nodesKeptCount];
	    float[] nodesLonKept = new float[nodesKeptCount];
	    int[] nodesEdgeOffsetKept = new int[nodesKeptCount];	
	    
	    int[] edgesTargetKept = new int[edgesKeptCount];
	    byte[] edgesInfobitsKept = new byte[edgesKeptCount];
	    float[] edgesLengthsKept = new float[edgesKeptCount];
	    byte[] edgesMaxSpeedsKept = new byte[edgesKeptCount];
	    
	    int[] removedEdgeCoordsOffsets = new int[edgesKeptCount];
	    float[] removedEdgeCoordsLat = new float[edgeRemovedCoordsCount];
	    float[] removedEdgeCoordsLon = new float[edgeRemovedCoordsCount];

	    int edgeOffsetTmp = 0;
	    int removedEdgeOffsetTmp = 0;
	    
	    percTmp = nodesKeptCount / 10;
		for (int iNd = 0; iNd < nodesKeptCount; iNd++) {
			// Extract node
			int oldIndex = nodesKeptIndicesArray[iNd];
			nodesLatKept[iNd] = nodesLat[oldIndex];
			nodesLonKept[iNd] = nodesLon[oldIndex];
			nodesEdgeOffsetKept[iNd] = edgeOffsetTmp;
			
			// Extract edges of node
			 for (int iEdge = nodesEdgeOffset[oldIndex]; (oldIndex + 1 < nodesEdgeOffset.length && iEdge < nodesEdgeOffset[oldIndex + 1])
	                    || (oldIndex + 1 == nodesEdgeOffset.length && iEdge < edgeCount); // Last node in offset array
					 iEdge++) {
				 if(edgesToDelete[iEdge]) {
					 continue;
				 }
				 
				 edgesTargetKept[edgeOffsetTmp] = edgesTarget[iEdge];
				 assert !nodesToDelete[edgesTarget[iEdge]];
				 edgesInfobitsKept[edgeOffsetTmp] = edgesInfobits[iEdge];
				 edgesLengthsKept[edgeOffsetTmp] = edgesLengths[iEdge];
				 edgesMaxSpeedsKept[edgeOffsetTmp] = edgesMaxSpeeds[iEdge];
				 
				 removedEdgeCoordsOffsets[edgeOffsetTmp] = removedEdgeOffsetTmp;
				 List<NodeFollowPath.Coord> removedCoords = edgeRemovedCoords.get(iEdge);
				 assert removedCoords != null;
				 
				 for(int i = 0; i < removedCoords.size(); i++) {
					 removedEdgeCoordsLat[removedEdgeOffsetTmp] = removedCoords.get(i).Lat;
					 removedEdgeCoordsLon[removedEdgeOffsetTmp] = removedCoords.get(i).Lon;
					 removedEdgeOffsetTmp++;
				 }
				 
				 // Edge offset counter
				 edgeOffsetTmp++;
			}

			 if(iNd % percTmp == 0) {
					System.out.println(iNd / percTmp * 10 + "% extracting edges");				 
			 }
		}
		
		// Free removedEdgeCoordsOffsets
		removedEdgeCoordsOffsets = null;
			
		
		// Update edge targets
		OsmAppPreprocessor.LOG.info("Updating edge targets");
		
		percTmp = edgesTargetKept.length / 100;
		for (int iEdge = 0; iEdge < edgesTargetKept.length; iEdge++) {
			int newTarget = Arrays.binarySearch(nodesKeptIndicesArray, edgesTargetKept[iEdge]);
			
			if(newTarget < 0) {
				OsmAppPreprocessor.LOG.severe("Cannot find new index for target " + edgesTargetKept[iEdge]);
			}
			
			edgesTargetKept[iEdge] = newTarget;
			
			 if(iEdge % percTmp == 0) {
					System.out.println(iEdge / percTmp + "% updating targets");				 
			 }
		}
		
		
		{
		// Save nodes
        OsmAppPreprocessor.LOG.info("Start serializing nodes");    
        ObjectOutputStream os = new ObjectOutputStream(new FileOutputStream(outDir + "\\pass4B-nodes.bin"));
        os.writeObject(nodesKeptCount);
        os.writeObject(nodesLatKept);   
        os.writeObject(nodesLonKept);        
        os.writeObject(nodesEdgeOffsetKept);      
        os.close();
        OsmAppPreprocessor.LOG.info("Finished serializing nodes"); 
		}
		
		{
		// Save edges
		OsmAppPreprocessor.LOG.info("Finished reading edges");

		OsmAppPreprocessor.LOG.info("Start serializing edges");
		ObjectOutputStream os = new ObjectOutputStream(
				new FileOutputStream(outDir + "\\pass4B-edges.bin"));
		os.writeObject(edgesKeptCount);
		os.writeObject(edgesTargetKept);
		os.writeObject(edgesInfobitsKept);
		os.writeObject(edgesLengthsKept);
		os.writeObject(edgesMaxSpeedsKept);
		os.writeObject(removedEdgeCoordsOffsets);
		os.writeObject(removedEdgeCoordsLat);
		os.writeObject(removedEdgeCoordsLon);
		os.close();
		OsmAppPreprocessor.LOG.info("Finished serializing edges");
		}
		
		
		OsmAppPreprocessor.LOG.warning("Dead ends: " + deadEnds);	

		System.out.println("nodeCount: " + nodeCount);
		System.out.println("node0Count: " + node0Count);
		System.out.println("node1Count: " + node1Count);
		System.out.println("node2Count: " + node2Count);
		System.out.println("nodeEdgeDiffCount: " + nodeEdgeDiffCount);
		System.out.println("nodesKept: " + nodesKeptCount);
		System.out.println("nodesDeleted: " + nodesDeleted);
		System.out.println("edgeCount: " + edgeCount);
		System.out.println("edgesKept: " + edgesKeptCount);
		System.out.println("zeroNodeCount: " + zeroNodeCount);
		System.out.println("errorEdgeCount: " + errorEdgeCount);
		System.out.println("loopEdgeCount: " + loopEdgeCount);
		
		OsmAppPreprocessor.LOG.info("Finished Pass4B");
	}
	
	
	static int deadEnds = 0;
	
	private static class NodeFollowPath {
		public final float Dist;
		public final int Target;
		public final List<Coord> PathCoords;
		
		public static class Coord {
			public final float Lat;
			public final float Lon;
			
			public Coord(float lat, float lon) {
				super();
				Lat = lat;
				Lon = lon;
			}			
		}
		
		public NodeFollowPath(float dist, int target, List<Coord> pathCoords) {
			super();
			Dist = dist;
			Target = target;
			PathCoords = pathCoords;
		}		
	}
	
	
	/**
	 * Follows edges as long as edges lead to deleted nodes
	 * @param nodeLast
	 * @param nodeNext
	 * @param nodesToDelete
	 * @return
	 */
	private static NodeFollowPath followNodeEdge(int nodeLast, int edge, 
			int[] nodesEdgeOffset, float[] nodesLat, float[] nodesLon, 
			int edgeCount, int[] edgesTarget, float[] edgesLengths, boolean[] nodesToDelete) {
		
		int nodeNext = edgesTarget[edge];
		
		if(!nodesToDelete[nodeNext]) {
			// Node not deleted, leave edge as it is
			return new NodeFollowPath(0.0f, nodeNext, new ArrayList<>());
		} else {
			// Deleted node - follow edges
			
			// Find edge to follow
			int nextEdge = -1;
			for (int iEdge = nodesEdgeOffset[nodeNext]; (nodeNext + 1 < nodesEdgeOffset.length && iEdge < nodesEdgeOffset[nodeNext + 1])
                    || (nodeNext + 1 == nodesEdgeOffset.length && iEdge < edgeCount); // Last node in offset array
				 iEdge++) {
				if(edgesTarget[iEdge] == nodeLast) {
					continue;
				} else {
					if(nextEdge != -1) {
						OsmAppPreprocessor.LOG.severe("Node to delete has multiple edges to follow - not allowed");
						assert false;
					} else {
						nextEdge = iEdge;
					}
				}
			}
			
			if(nextEdge == -1) {
				deadEnds++;
				nextEdge = -1;				
				
				if (!nodesToDelete[nodeNext]) { 
					// Return next node which is dead end and not deleted
					return new NodeFollowPath(0.0f, nodeNext, new ArrayList<>());
				} if (!nodesToDelete[nodeLast]) { 
					return new NodeFollowPath(0.0f, nodeLast, new ArrayList<>());
				} else {
					// Unable to follow edge - error node
					return null;
				}
			}
			 
			// Return sum of edges followed recursively
			NodeFollowPath follow = 
					followNodeEdge(nodeNext, nextEdge, 
							nodesEdgeOffset, nodesLat, nodesLon,
							edgeCount, edgesTarget, edgesLengths, nodesToDelete);
			if(follow == null) {
				return null;
			}
			assert !nodesToDelete[follow.Target];
			
			List<NodeFollowPath.Coord> pathCoords = new ArrayList<>();
			pathCoords.add(new NodeFollowPath.Coord(nodesLat[nodeNext], nodesLon[nodeNext]));
			pathCoords.addAll(follow.PathCoords);
			return new NodeFollowPath(follow.Dist + edgesLengths[nextEdge], follow.Target, pathCoords);
		}
	}
}
