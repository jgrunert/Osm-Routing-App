package jgrunert.osm_routing_app;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;












import java.util.Set;

import org.openstreetmap.osmosis.core.domain.v0_6.WayNode;

public class OsmAppPreprocessorPass2 {

	public static void main(String[] args) {
		try {
			process();
		} catch (Exception e) {
			System.err.println("Error in main");
			e.printStackTrace();
		}
	}
	
	
	private static void process() throws Exception {
		
		// Load highways
		DataInputStream highwayReader = new DataInputStream(new FileInputStream("D:\\Jonas\\OSM\\hamburg\\pass1-highways.bin"));	
		int highwayCount = highwayReader.readInt();
		List<HighwayInfos2> highways = new ArrayList<>(highwayCount);
		
		System.out.println("Start reading highways: " + highwayCount);
		for(int i = 0; i < highwayCount; i++) {

			byte infoBits = highwayReader.readByte();
			boolean oneway = highwayReader.readBoolean();
			byte maxSpeed = highwayReader.readByte();
			int numWaynodes = highwayReader.readInt();
			List<Integer> wayNodes = new ArrayList<Integer>();
			for(int iWn = 0; iWn < numWaynodes; iWn++) {
				int nodeId = highwayReader.readInt();
				if(nodeId != -1) { // Ignore invalid nodes
					wayNodes.add(nodeId);
				}
			}
			
			highways.add(new HighwayInfos2(infoBits, oneway, maxSpeed, wayNodes));
		}
		highwayReader.close();
		System.out.println("Finished reading highways: " + highwayCount);
		
		
		
		// Load waysOfNodes
		DataInputStream waysOfNodesReader = new DataInputStream(new FileInputStream("D:\\Jonas\\OSM\\hamburg\\pass1-waysOfNodes.bin"));
		int waynodeCount = waysOfNodesReader.readInt();
		System.out.println("Start reading waysOfNodesWriter: " + waynodeCount);
		List<List<Integer>> waysOfNodes = new ArrayList<List<Integer>>(waynodeCount);
		for(int i = 0; i < waynodeCount; i++) {
			int waysCount = waysOfNodesReader.readInt();
			List<Integer> nodeWays = new ArrayList<Integer>(waysCount);			
			for(int iNd = 0; iNd < waysCount; iNd++) {
				nodeWays.add(waysOfNodesReader.readInt());
			}
			waysOfNodes.add(nodeWays);
		}	
		waysOfNodesReader.close();
		System.out.println("Finished reading waysOfNodesWriter");
		
		

		// Load and process waynodes
		DataInputStream nodeReader = new DataInputStream(new FileInputStream("D:\\Jonas\\OSM\\hamburg\\pass1-waynodes.bin"));
		int nodeCount = nodeReader.readInt();
		
		if(nodeCount != waysOfNodes.size()) {
			System.err.println("nodeCount != waysOfNodes.size(): " + nodeCount + " and " + waysOfNodes.size());
		}
		
		DataOutputStream edgeWriter = new DataOutputStream(new FileOutputStream("D:\\Jonas\\OSM\\hamburg\\pass2-edges.bin"));
		DataOutputStream nodeWriter = new DataOutputStream(new FileOutputStream("D:\\Jonas\\OSM\\hamburg\\pass2-nodes.bin"));
				
		int edgeCounter = 0;
		nodeWriter.writeInt(nodeCount);
		int percAmnt = nodeCount / 100;
		for(int iNode = 0; iNode < nodeCount; iNode++) {

			int nodeIndex = nodeReader.readInt();
			if(nodeIndex != iNode) {
				System.err.println("Wrong nodeIndex: " + nodeIndex + " instead of " + iNode);
			}
			
			double lat = nodeReader.readDouble();
			double lon = nodeReader.readDouble();
			nodeReader.readLong();
			
			nodeWriter.writeDouble(lat);
			nodeWriter.writeDouble(lon);
			nodeWriter.writeDouble(edgeCounter); // Edge offset
			
			// Remove duplicate ways (eg. circles with multiple usages of node)
			List<Integer> waysInvolvedList = waysOfNodes.get(iNode);
			Set<Integer> waysInvolvedSet = new HashSet<>(waysInvolvedList); 
			
			for(int wayInvolved : waysInvolvedSet) {
				HighwayInfos2 highway = highways.get(wayInvolved);
				boolean containsNode = false;
				
				// Find ways this node is involved in
				for(int iWp = 0; iWp < highway.wayNodes.size(); iWp++) 
				{
					if(highway.wayNodes.get(iWp) == nodeIndex) 
					{
						containsNode = true;	
						
						// Edge in direction of way (if not last point)
						if(iWp + 1 < highway.wayNodes.size()) {
							edgeWriter.writeBoolean(true); // Next edge in file
							edgeWriter.writeInt(highway.wayNodes.get(iWp + 1)); // Target
							// TODO Write edge properties
							edgeCounter++;
						}

						// Edge in counter direction of way (if not oneway and not first point)
						if(!highway.Oneway && iWp > 0) {
							edgeWriter.writeBoolean(true); // Next edge in file
							edgeWriter.writeInt(highway.wayNodes.get(iWp - 1)); // Target
							// TODO Write edge properties
							edgeCounter++;
						}
					}
				}
				
				if(!containsNode) {
					System.err.println("Node cannot find entry in his wayNodes");
				}
			}
			
			if(iNode % percAmnt == 0) {
				System.out.println((iNode / percAmnt) + "%  processing nodes");
			}
		}		
		
		edgeWriter.writeBoolean(true); // End of nodes in file
		
		System.out.println("Nodes: " + nodeCount);
		System.out.println("Edges: " + edgeCounter);
		
		nodeReader.close();
		edgeWriter.close();
		nodeWriter.close();
	}

	
	
	private static class HighwayInfos2 {
		/** Info bits, bit0: Pedestrian, bit1: Car **/
		public final byte InfoBits;
		public final boolean Oneway;
		public final byte MaxSpeed; // TODO Byte
		public final List<Integer> wayNodes;
		
		
		public HighwayInfos2(byte infoBits, boolean oneway, byte maxSpeed, List<Integer> wayNodes) {
			this.InfoBits = infoBits;			
			this.Oneway = oneway;
			this.MaxSpeed = maxSpeed;
			this.wayNodes = wayNodes;
		}
	}
	
}
