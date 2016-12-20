package jgrunert.osm_routing_app;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

public class OsmAppPreprocessorPass3 {

	private static float[] lats;
	private static float[] lons;


	public static void main(String[] args) {
		try {
			String outDir = args[0];
			doPass(outDir);
		}
		catch (Exception e) {
			OsmAppPreprocessor.LOG.severe("Error in main");
			OsmAppPreprocessor.LOG.log(Level.SEVERE, "Exception", e);
		}
	}


	public static void doPass(String outDir) throws Exception {

		OsmAppPreprocessor.LOG.info("OSM Preprocessor Pass3 v02");


		// Load highways
		DataInputStream highwayReader = new DataInputStream(
				new BufferedInputStream(new FileInputStream(outDir + File.separator + "pass1-highways.bin")));
		int highwayCount = highwayReader.readInt();
		List<HighwayInfos2> highways = new ArrayList<>(highwayCount);

		OsmAppPreprocessor.LOG.info("Start reading highways: " + highwayCount);
		int perc100 = highwayCount / 100;
		for (int i = 0; i < highwayCount; i++) {

			byte infoBits = highwayReader.readByte();
			boolean oneway = highwayReader.readBoolean();
			byte maxSpeed = highwayReader.readByte();
			int numWaynodes = highwayReader.readInt();
			List<Integer> wayNodes = new ArrayList<Integer>();
			for (int iWn = 0; iWn < numWaynodes; iWn++) {
				int nodeId = highwayReader.readInt();
				if (nodeId != -1) { // Ignore invalid nodes
					wayNodes.add(nodeId);
				}
			}

			highways.add(new HighwayInfos2(infoBits, oneway, maxSpeed, wayNodes));

			if (i % perc100 == 0) {
				OsmAppPreprocessor.LOG.info(i / perc100 + "% reading highways");
			}
		}
		highwayReader.close();
		OsmAppPreprocessor.LOG.info("Finished reading highways: " + highwayCount);



		// Load node coords
		// Load and process waynodes
		{
			OsmAppPreprocessor.LOG.info("Start loading node coords");
			DataInputStream nodeReader = new DataInputStream(
					new BufferedInputStream(new FileInputStream(outDir + File.separator + "pass2-waynodes.bin")));
			int nodeCount = nodeReader.readInt();

			// TODO Faster, serialize
			lats = new float[nodeCount];
			lons = new float[nodeCount];

			// int edgeCounter = 0;
			perc100 = nodeCount / 100;
			for (int iNode = 0; iNode < nodeCount; iNode++) {
				int nodeIndex = nodeReader.readInt();
				if (nodeIndex != iNode) {
					OsmAppPreprocessor.LOG.severe("Wrong nodeIndex: " + nodeIndex + " instead of " + iNode);
				}

				lats[iNode] = nodeReader.readFloat();
				lons[iNode] = nodeReader.readFloat();
				nodeReader.readLong(); // Ignore old id

				if (iNode % perc100 == 0) {
					OsmAppPreprocessor.LOG.info(iNode / perc100 + "% loading node coords");
				}
			}
			OsmAppPreprocessor.LOG.info("Finished loading node coords");

			nodeReader.close();
		}



		// Load waysOfNodes
		ObjectInputStream waysOfNodesReader = new ObjectInputStream(
				new BufferedInputStream(new FileInputStream(outDir + File.separator + "pass1-waysOfNodes.bin")));
		int waynodeCount = waysOfNodesReader.readInt();
		OsmAppPreprocessor.LOG.info("Start reading waysOfNodes: " + waynodeCount);
		List<List<Integer>> waysOfNodes = new ArrayList<List<Integer>>(waynodeCount);
		perc100 = waynodeCount / 100;
		for (int i = 0; i < waynodeCount; i++) {
			// int waysCount = waysOfNodesReader.readInt();
			// Object[] nodesArray = (waysOfNodesReader.readObject();
			// List<Integer> nodeWays = new
			// ArrayList<Integer>(Arrays.asList((Integer[])waysOfNodesReader.readObject()));
			// for(int iNd = 0; iNd < waysCount; iNd++) {
			// nodeWays.add(waysOfNodesReader.readInt());
			// }
			waysOfNodes.add(Arrays.asList((Integer[]) waysOfNodesReader.readObject()));

			if (i % perc100 == 0) {
				OsmAppPreprocessor.LOG.info(i / perc100 + "% reading waysOfNodes");
			}
		}
		waysOfNodesReader.close();
		OsmAppPreprocessor.LOG.info("Finished reading waysOfNodes");



		// Load and process waynodes
		OsmAppPreprocessor.LOG.info("Start processing nodes");
		DataInputStream nodeReader = new DataInputStream(
				new BufferedInputStream(new FileInputStream(outDir + File.separator + "pass2-waynodes.bin")));
		int nodeCount = nodeReader.readInt();

		if (nodeCount != waysOfNodes.size()) {
			OsmAppPreprocessor.LOG
					.severe("nodeCount != waysOfNodes.size(): " + nodeCount + " and " + waysOfNodes.size());
		}

		DataOutputStream edgeWriter = new DataOutputStream(
				new BufferedOutputStream(new FileOutputStream(outDir + File.separator + "pass3-edges.bin")));
		DataOutputStream nodeWriter = new DataOutputStream(
				new BufferedOutputStream(new FileOutputStream(outDir + File.separator + "pass3-nodes.bin")));

		int edgeCounter = 0;
		nodeWriter.writeInt(nodeCount);
		perc100 = nodeCount / 100;
		for (int iNode = 0; iNode < nodeCount; iNode++) {

			int nodeIndex = nodeReader.readInt();
			if (nodeIndex != iNode) {
				OsmAppPreprocessor.LOG.severe("Wrong nodeIndex: " + nodeIndex + " instead of " + iNode);
			}

			float lat = nodeReader.readFloat();
			float lon = nodeReader.readFloat();
			nodeReader.readLong(); // Ignore old id

			nodeWriter.writeFloat(lat);
			nodeWriter.writeFloat(lon);
			nodeWriter.writeInt(edgeCounter); // Edge offset

			// Remove duplicate ways (eg. circles with multiple usages of node)
			List<Integer> waysInvolvedList = waysOfNodes.get(iNode);
			Set<Integer> waysInvolvedSet = new HashSet<>(waysInvolvedList);

			for (int wayInvolved : waysInvolvedSet) {
				HighwayInfos2 highway = highways.get(wayInvolved);
				boolean containsNode = false;

				// Infobits with oneway bit
				byte highwayBits = (byte) (highway.InfoBits << 1);

				// Find ways this node is involved in
				for (int iWp = 0; iWp < highway.wayNodes.size(); iWp++) {
					if (highway.wayNodes.get(iWp) == nodeIndex) {
						containsNode = true;

						// Edge in direction of way (if not last point)
						if (iWp + 1 < highway.wayNodes.size()) {
							int targetWp = highway.wayNodes.get(iWp + 1);
							edgeWriter.writeInt(targetWp); // Target
							edgeWriter.writeByte(highwayBits); // Info bits:
																// 0,0,0,0,0,[Car],[Ped],[Oneway]
							edgeWriter.writeFloat(calcGeoLength(nodeIndex, targetWp)); // Distance
							edgeWriter.writeByte(highway.MaxSpeed); // MaxSpeed

							edgeCounter++;
						}

						// Edge in counter direction of way (if not oneway and
						// not first point)
						if (iWp > 0) {
							int targetWp = highway.wayNodes.get(iWp - 1);
							edgeWriter.writeInt(targetWp); // Target
							// Info bits: 0,0,0,0,0,[Car],[Ped],[Oneway]
							if (highway.Oneway) {
								// Oneway in counter direction (no cars)
								edgeWriter.writeByte((byte) (highwayBits + 1));
							}
							else {
								edgeWriter.writeByte(highwayBits);
							}
							edgeWriter.writeFloat(calcGeoLength(nodeIndex, targetWp)); // Distance
							edgeWriter.writeByte(highway.MaxSpeed); // MaxSpeed

							edgeCounter++;
						}
					}
				}

				if (!containsNode) {
					OsmAppPreprocessor.LOG.severe("Node cannot find entry in his wayNodes");
				}
			}

			if (iNode % perc100 == 0) {
				OsmAppPreprocessor.LOG.info((iNode / perc100) + "%  processing nodes");
			}
		}

		nodeReader.close();
		nodeWriter.close();
		edgeWriter.close();
		OsmAppPreprocessor.LOG.info("Finished processing nodes");
		OsmAppPreprocessor.LOG.info("Longest edge: " + maxDist);

		// Write edges again to file with number of edges at beginning (TODO
		// Better way?)
		OsmAppPreprocessor.LOG.info("Start writing edges to final file");
		DataOutputStream edgeWriter2 = new DataOutputStream(
				new BufferedOutputStream(new FileOutputStream(outDir + File.separator + "pass3-edges-count.bin")));
		edgeWriter2.writeInt(edgeCounter);
		edgeWriter2.close();
		OsmAppPreprocessor.LOG.info("Finished writing edges to final file");

		OsmAppPreprocessor.LOG.info("Finished");
		OsmAppPreprocessor.LOG.info("Nodes: " + nodeCount);
		OsmAppPreprocessor.LOG.info("Edges: " + edgeCounter);
	}


	private static float maxDist = 0.0f;

	private static float calcGeoLength(int i1, int i2) {
		float dist = getNodeDist(lats[i1], lons[i1], lats[i2], lons[i2]);
		if (dist > maxDist) {
			maxDist = dist;
			// OsmAppPreprocessor.LOG.info(maxDist);
		}
		return dist;
	}

	// From
	// http://stackoverflow.com/questions/837872/calculate-distance-in-meters-when-you-know-longitude-and-latitude-in-java
	private static float getNodeDist(float lat1, float lon1, float lat2, float lon2) {
		double earthRadius = 6371000; // meters
		double dLat = Math.toRadians(lat2 - lat1);
		double dLng = Math.toRadians(lon2 - lon1);
		double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(Math.toRadians(lat1))
				* Math.cos(Math.toRadians(lat2)) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
		double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
		return (float) (earthRadius * c);
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
