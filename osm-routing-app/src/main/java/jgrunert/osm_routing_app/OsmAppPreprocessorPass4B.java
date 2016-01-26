package jgrunert.osm_routing_app;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
		
		int node1Count = 0;
		int node2Count = 0;
		int nodeEdgeDiffCount = 0;
		int nodesDeleted = 0;
		int nodesKeptCount = 0;
		int edgesKeptCount = 0;
		int errorNodeCount = 0;


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
					 if(edgeInfobitsTmp != edgesInfobits[iEdge] || edgeMaxSpeedTmp != edgesMaxSpeeds[iEdge]) {
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
			} else if (ndTmp == 1) {
				node1Count++;
			} else if (ndTmp == 2) {
				node2Count++;
			}
			
			if(edgesDifferTmp || ndTmp != 2) {
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

	    percTmp = nodeCount / 100;
		for (int iNd = 0; iNd < nodeCount; iNd++) {
			
			// Ignore deleted nodes
			if(nodesToDelete[iNd]) {
				continue;
			}
			
			boolean nodeHasError = false; // Skip node if has error
			int newEdgesKeptCount = edgesKeptCount;
			
			// Follow and update edges if necesary
			 for (int iEdge = nodesEdgeOffset[iNd]; (iNd + 1 < nodesEdgeOffset.length && iEdge < nodesEdgeOffset[iNd + 1])
	                    || (iNd + 1 == nodesEdgeOffset.length && iEdge < edgeCount); // Last node in offset array
					 iEdge++) {
				// Follow edge path
				NodeFollowPath followPath = followNodeEdge(iNd, iEdge,
						nodesEdgeOffset, edgeCount, edgesTarget, edgesLengths,
						nodesToDelete);
				
				if(followPath == null) {
					// Ignore node if has error in edge
					errorNodeCount++;
					nodesKeptCount--;
					nodeHasError = true;
					break;
				}

				// Update edge length
				edgesLengths[iEdge] = edgesLengths[iEdge] + followPath.Dist;
				// Update edge target
				edgesTarget[iEdge] = followPath.Target;
				assert !nodesToDelete[followPath.Target];
				newEdgesKeptCount++;
			}
			 
			if (!nodeHasError) {
				edgesKeptCount = newEdgesKeptCount;
				nodesKeptIndices.add(iNd);
			}
			 
			if (iNd % percTmp == 0) {
				System.out.println(iNd / percTmp + "% removing nodes");
			}
		}

		OsmAppPreprocessor.LOG.info("Converting temporary kept-lists to arrays");
		assert nodesKeptCount == nodesKeptIndices.size();
		Integer[] nodesKeptIndicesArray = nodesKeptIndices.toArray(new Integer[nodesKeptCount]);
		nodesKeptIndices = null;
		//Integer[] edgesKeptIndicesArray = edgesKeptIndices.toArray(new Integer[0]);
		//edgesKeptIndices = null;

		
		// Extract nodes and edges to keep
		OsmAppPreprocessor.LOG.info("Extract nodes and edges to keep");

	    float[] nodesLatKept = new float[nodesKeptCount];
	    float[] nodesLonKept = new float[nodesKeptCount];
	    int[] nodesEdgeOffsetKept = new int[nodesKeptCount];	
	    int edgeOffsetTmp = 0;
	    
	    int[] edgesTargetKept = new int[edgesKeptCount];
	    byte[] edgesInfobitsKept = new byte[edgesKeptCount];
	    float[] edgesLengthsKept = new float[edgesKeptCount];
	    byte[] edgesMaxSpeedsKept = new byte[edgesKeptCount];

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
				 edgesTargetKept[edgeOffsetTmp] = edgesTarget[iEdge];
				 assert !nodesToDelete[edgesTarget[iEdge]];
				 edgesInfobitsKept[edgeOffsetTmp] = edgesInfobits[iEdge];
				 edgesLengthsKept[edgeOffsetTmp] = edgesLengths[iEdge];
				 edgesMaxSpeedsKept[edgeOffsetTmp] = edgesMaxSpeeds[iEdge];
				 // Edge offset counter
				 edgeOffsetTmp++;
			}

			 if(iNd % percTmp == 0) {
					System.out.println(iNd / percTmp * 10 + "% extracting edges");				 
			 }
		}
			
		
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
		
		// TODO Node ID Mapping
		// TODO Export new nodes and edges
		// TODO Also export all nodes (for route reconstruction) for later use
		
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
		os.close();
		OsmAppPreprocessor.LOG.info("Finished serializing edges");
		}
		
		
		OsmAppPreprocessor.LOG.warning("Dead ends: " + deadEnds);	

		System.out.println("nodeCount: " + nodeCount);
		System.out.println("node1Count: " + node1Count);
		System.out.println("node2Count: " + node2Count);
		System.out.println("nodeEdgeDiffCount: " + nodeEdgeDiffCount);
		System.out.println("nodesKept: " + nodesKeptCount);
		System.out.println("nodesDeleted: " + nodesDeleted);
		System.out.println("edgeCount: " + edgeCount);
		System.out.println("edgesKept: " + edgesKeptCount);
		System.out.println("errorNodeCount: " + errorNodeCount);
		
		OsmAppPreprocessor.LOG.info("Finished Pass4B");
	}
	
	
	static int deadEnds = 0;
	
	private static class NodeFollowPath {
		public final float Dist;
		public final int Target;
		
		public NodeFollowPath(float dist, int target) {
			super();
			Dist = dist;
			Target = target;
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
			int[] nodesEdgeOffset, int edgeCount, int[] edgesTarget, float[] edgesLengths, boolean[] nodesToDelete) {
		
		int nodeNext = edgesTarget[edge];
		
		if(!nodesToDelete[nodeNext]) {
			// Node not deleted, leave edge as it is
			return new NodeFollowPath(0.0f, nodeNext);
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
				if (!nodesToDelete[nodeNext]) { 
					// Return next node which is dead end and not deleted
					return new NodeFollowPath(0.0f, nodeNext);
				} if (!nodesToDelete[nodeLast]) { 
					return new NodeFollowPath(0.0f, nodeLast);
				} else {
					// Unable to follow edge - error node
					return null;
				}
			}
			 
			// Return sum of edges followed recursively
			NodeFollowPath follow = 
					followNodeEdge(nodeNext, nextEdge, 
							nodesEdgeOffset, edgeCount, edgesTarget, edgesLengths, nodesToDelete);
			if(follow == null) {
				return null;
			}
			assert !nodesToDelete[follow.Target];
			return new NodeFollowPath(follow.Dist + edgesLengths[nextEdge], follow.Target);
		}
	}
}
