package jgrunert.osm_routing_app;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;










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
		DataInputStream highwayReader = new DataInputStream(new FileInputStream("D:\\Jonas\\OSM\\pass1-highways.bin"));	
		int highwayCount = highwayReader.readInt();
		List<HighwayInfos2> highways = new ArrayList<>(highwayCount);
		
		System.out.println("Start reading highways: " + highwayCount);
		for(int i = 0; i < highwayCount; i++) {

			byte infoBits = highwayReader.readByte();
			boolean oneway = highwayReader.readBoolean();
			byte maxSpeed = highwayReader.readByte();
			int numWaynodes = highwayReader.readInt();
			List<Integer> wayNodes = new ArrayList<Integer>(numWaynodes);
			for(int iWn = 0; iWn < numWaynodes; iWn++) {
				wayNodes.add(highwayReader.readInt());
			}
			
			highways.add(new HighwayInfos2(infoBits, oneway, maxSpeed, wayNodes));
		}
		highwayReader.close();
		System.out.println("Finished reading highways: " + highwayCount);
		
		
		
		// Load waysOfNodes
		DataInputStream waysOfNodesReader = new DataInputStream(new FileInputStream("D:\\Jonas\\OSM\\pass1-waysOfNodes.bin"));
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
		DataInputStream nodeReader = new DataInputStream(new FileInputStream("D:\\Jonas\\OSM\\pass1-waynodes.bin"));
		DataOutputStream edgeWriter = new DataOutputStream(new FileOutputStream("D:\\Jonas\\OSM\\pass2-edges.bin"));
		int nodeCount = nodeReader.readInt();
		int percAmnt = nodeCount / 100;
		for(int iNode = 0; iNode < nodeCount; iNode++) {

			int nodeIndex = nodeReader.readInt();
			double lat = nodeReader.readDouble();
			double lon = nodeReader.readDouble();
			nodeReader.readLong();
			
			for(int wayInvolved : waysOfNodes.get(nodeIndex)) {
				HighwayInfos2 highway = highways.get(wayInvolved);
				
				for(int iWp = 0; iWp < highway.wayNodes.size(); iWp++){
					edgeWriter.writeInt(iWp);
				}
			}
			
			if(iNode % percAmnt == 0) {
				System.out.println((iNode / percAmnt) + "%  provessing nodes");
			}
		}		
		nodeReader.close();
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
