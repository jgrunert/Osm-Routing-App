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

import net.sf.geographiclib.Geodesic;
import net.sf.geographiclib.GeodesicData;

import org.openstreetmap.osmosis.core.domain.v0_6.WayNode;

public class OsmAppPreprocessorPass3 {


	private static double[] lats;
	private static double[] lons;
	
	
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
	
	
	public static void doPass(String outDir) throws Exception {

		System.out.println("OSM Preprocessor Pass3 v02");
		
		
		// Load highways
		DataInputStream highwayReader = new DataInputStream(new FileInputStream(outDir + "\\pass1-highways.bin"));	
		int highwayCount = highwayReader.readInt();
		List<HighwayInfos2> highways = new ArrayList<>(highwayCount);
		
		System.out.println("Start reading highways: " + highwayCount);
		int perc100 = highwayCount / 100;
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
			
			if(i % perc100 == 0) {
				System.out.println(i / perc100 + "% reading highways");
			}
		}
		highwayReader.close();
		System.out.println("Finished reading highways: " + highwayCount);
		
		
		
		// Load node coords
		// Load and process waynodes
		{
			System.out.println("Start loading node coords");
			DataInputStream nodeReader = new DataInputStream(
					new FileInputStream(outDir + "\\pass2-waynodes.bin"));
			int nodeCount = nodeReader.readInt();

			lats = new double[nodeCount];
			lons = new double[nodeCount];

			int edgeCounter = 0;
			perc100 = nodeCount / 100;
			for (int iNode = 0; iNode < nodeCount; iNode++) {
				int nodeIndex = nodeReader.readInt();
				if (nodeIndex != iNode) {
					System.err.println("Wrong nodeIndex: " + nodeIndex
							+ " instead of " + iNode);
				}

				lats[iNode] = nodeReader.readDouble();
				lons[iNode] = nodeReader.readDouble();
				nodeReader.readLong(); // Ignore old id
			}
			System.out.println("Finished loading node coords");
		}
		
		
		
		// Load waysOfNodes
		DataInputStream waysOfNodesReader = new DataInputStream(new FileInputStream(outDir + "\\pass1-waysOfNodes.bin"));
		int waynodeCount = waysOfNodesReader.readInt();
		System.out.println("Start reading waysOfNodes: " + waynodeCount);
		List<List<Integer>> waysOfNodes = new ArrayList<List<Integer>>(waynodeCount);
		perc100 = waynodeCount / 100;
		for(int i = 0; i < waynodeCount; i++) {
			int waysCount = waysOfNodesReader.readInt();
			List<Integer> nodeWays = new ArrayList<Integer>(waysCount);			
			for(int iNd = 0; iNd < waysCount; iNd++) {
				nodeWays.add(waysOfNodesReader.readInt());
			}
			waysOfNodes.add(nodeWays);
			
			if(i % perc100 == 0) {
				System.out.println(i / perc100 + "% reading waysOfNodes");
			}
		}	
		waysOfNodesReader.close();
		System.out.println("Finished reading waysOfNodes");
		
		

		// Load and process waynodes
		System.out.println("Start processing nodes");
		DataInputStream nodeReader = new DataInputStream(new FileInputStream(outDir + "\\pass2-waynodes.bin"));
		int nodeCount = nodeReader.readInt();
		
		if(nodeCount != waysOfNodes.size()) {
			System.err.println("nodeCount != waysOfNodes.size(): " + nodeCount + " and " + waysOfNodes.size());
		}
		
		DataOutputStream edgeWriter = new DataOutputStream(new FileOutputStream(outDir + "\\pass3-edges_tmp.bin"));
		DataOutputStream nodeWriter = new DataOutputStream(new FileOutputStream(outDir + "\\pass3-nodes.bin"));
				
		int edgeCounter = 0;
		nodeWriter.writeInt(nodeCount);
		perc100 = nodeCount / 100;
		for(int iNode = 0; iNode < nodeCount; iNode++) {

			int nodeIndex = nodeReader.readInt();
			if(nodeIndex != iNode) {
				System.err.println("Wrong nodeIndex: " + nodeIndex + " instead of " + iNode);
			}
			
			double lat = nodeReader.readDouble();
			double lon = nodeReader.readDouble();
			nodeReader.readLong(); // Ignore old id
			
			nodeWriter.writeDouble(lat);
			nodeWriter.writeDouble(lon);
			nodeWriter.writeInt(edgeCounter); // Edge offset
			
			// Remove duplicate ways (eg. circles with multiple usages of node)
			List<Integer> waysInvolvedList = waysOfNodes.get(iNode);
			Set<Integer> waysInvolvedSet = new HashSet<>(waysInvolvedList); 
			
			for(int wayInvolved : waysInvolvedSet) {
				HighwayInfos2 highway = highways.get(wayInvolved);
				boolean containsNode = false;
				
				// Infobits with oneway bit
				byte highwayBits = (byte)(highway.InfoBits << 1);
				
				// Find ways this node is involved in
				for(int iWp = 0; iWp < highway.wayNodes.size(); iWp++) 
				{
					if(highway.wayNodes.get(iWp) == nodeIndex) 
					{
						containsNode = true;	
						
						// Edge in direction of way (if not last point)
						if(iWp + 1 < highway.wayNodes.size()) 
						{
							int targetWp = highway.wayNodes.get(iWp + 1);
							edgeWriter.writeInt(targetWp); // Target
							edgeWriter.writeByte(highwayBits); // Info bits: 0,0,0,0,0,[Car],[Ped],[Oneway]
							edgeWriter.writeShort(calcGeoLength(nodeIndex, targetWp)); // Distance
							edgeWriter.writeByte(highway.MaxSpeed); // MaxSpeed
							
							edgeCounter++;
						}

						// Edge in counter direction of way (if not oneway and not first point)
						if(iWp > 0) 
						{	
							int targetWp = highway.wayNodes.get(iWp - 1);
							edgeWriter.writeInt(targetWp); // Target
							// Info bits: 0,0,0,0,0,[Car],[Ped],[Oneway]
							if(highway.Oneway) {
								// Oneway in counter direction (no cars)
								edgeWriter.writeByte((byte)(highwayBits + 1));
							} else {
								edgeWriter.writeByte(highwayBits);
							}
							edgeWriter.writeShort(calcGeoLength(nodeIndex, targetWp)); // Distance
							edgeWriter.writeByte(highway.MaxSpeed); // MaxSpeed
							
							edgeCounter++;
						}
					}
				}
				
				if(!containsNode) {
					System.err.println("Node cannot find entry in his wayNodes");
				}
			}
			
			if(iNode % perc100 == 0) {
				System.out.println((iNode / perc100) + "%  processing nodes");
			}
		}		

		nodeReader.close();
		nodeWriter.close();
		edgeWriter.close();
		System.out.println("Finished processing nodes");
		
		// Write edges again to file with number of edges at beginning (TODO Better way?)
		System.out.println("Start writing edges to final file");
		DataOutputStream edgeWriter2 = new DataOutputStream(new FileOutputStream(outDir + "\\pass3-edges.bin"));
		DataInputStream edgeReader = new DataInputStream(new FileInputStream(outDir + "\\pass3-edges_tmp.bin"));
		edgeWriter2.writeInt(edgeCounter);
		perc100 = edgeCounter / 100;
		for(int i = 0; i < edgeCounter; i++) {
			// TODO Make Faster
			edgeWriter2.writeInt(edgeReader.readInt());
			edgeWriter2.writeByte(edgeReader.readByte());
			edgeWriter2.writeShort(edgeReader.readShort());
			edgeWriter2.writeByte(edgeReader.readByte());
			if(i % perc100 == 0) {
				System.out.println((i / perc100) + "%  writing final edges");
			}
		}
		edgeReader.close();
		edgeWriter2.close();
		System.out.println("Finished writing edges to final file");

		System.out.println("Finished");		
		System.out.println("Nodes: " + nodeCount);
		System.out.println("Edges: " + edgeCounter);
	}
	
	
	private static short calcGeoLength(int i1, int i2) {
		 GeodesicData g = Geodesic.WGS84.Inverse(lats[i1], lons[i1], lats[i2], lons[i2]);
		 if(g.s12 > Short.MAX_VALUE) {
			 System.err.println("calcGeoLength > Short.MAX_VALUE");
			 throw new RuntimeException("calcGeoLength > Short.MAX_VALUE");
		 }
	     return (short)g.s12;
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
