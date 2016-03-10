package jgrunert.osm_routing_app;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import org.openstreetmap.osmosis.core.container.v0_6.EntityContainer;
import org.openstreetmap.osmosis.core.domain.v0_6.Entity;
import org.openstreetmap.osmosis.core.domain.v0_6.Node;
import org.openstreetmap.osmosis.core.task.v0_6.RunnableSource;
import org.openstreetmap.osmosis.core.task.v0_6.Sink;

/**
 * Hello world!
 *
 */
public class OsmAppPreprocessorPass2 {

	static int relevantWays = 0;
	static int ways = 0;
	static int relevantWayNodeCounter = 0;
	static int nodes = 0;
	
	static int maxNodesPerWay = 0;
	static int maxWaysPerNode = 0;
	
	static int totalElements = 267101283;
	static int elementsPass1 = 0;
	static int elementsPass2 = 0;
	
	static int BUFFER_MAX_WAYNODES = 100000000;
	static int BUFFER_MAX_WAYSPERNODE = 20;

	//static List<HighwayInfos> highways = new ArrayList<>();
	static List<Long> waypointIds = new ArrayList<>();
	
	static boolean showedNodeIndexError = false;

	
	public static void main(String[] args) {
		try {
			//String outDir = "D:\\Jonas\\OSM\\germany";
			//String outDir = "D:\\Jonas\\OSM\\hamburg";
			String outDir = "D:\\Jonas\\OSM\\bawue";
			
			//String inFile = "D:\\Jonas\\OSM\\germany-latest.osm.pbf";
			//String inFile = "D:\\Jonas\\OSM\\hamburg-latest.osm.pbf";
			String inFile = "D:\\Jonas\\OSM\\baden-wuerttemberg-latest.osm.pbf";
			
			doPass(inFile, outDir);
		} catch (Exception e) {
			OsmAppPreprocessor.LOG.severe("Failure at main");
			OsmAppPreprocessor.LOG.log(Level.SEVERE, "Exception", e);
		}
	}
		
		
	public static void doPass(String inFile, String outDir) throws Exception {
		
		
		long startTime = System.currentTimeMillis();
		
		
		OsmAppPreprocessor.LOG.info("OSM Preprocessor Pass2 v01");
		

		OsmAppPreprocessor.LOG.info("Start processing nodes");
		DataInputStream waynodeIdReader = new DataInputStream(new FileInputStream(outDir + File.separator + "pass1-waynodeIds.bin"));
		int waypointCount = waynodeIdReader.readInt();
		List<Long> waypointIdsSet = new ArrayList<>(waypointCount);
		int percTmp100 = waypointCount / 100;
		for(int i = 0; i < waypointCount; i++) {
			waypointIdsSet.add(waynodeIdReader.readLong());
			if(i % percTmp100 == 0) {
				OsmAppPreprocessor.LOG.info(i / percTmp100 + "% load waypointIdsSet");
			}
		}
		waynodeIdReader.close();
		
		
		
		// Pass 1.2: 
		{
			OsmAppPreprocessor.LOG.info("Starting Pass 2");
			
			
			DataOutputStream connectionWriter = new DataOutputStream(new FileOutputStream(outDir + File.separator + "pass2-waynodes.bin"));
			connectionWriter.writeInt(waypointIdsSet.size());
						
			Sink sinkImplementation = new Sink() {

				public void process(EntityContainer entityContainer) {
					Entity entity = entityContainer.getEntity();
					
					if (entity instanceof Node) {
						Node node = (Node) entity;

						int nodeIndex = Collections.binarySearch(waypointIdsSet, node.getId());
						
						if(nodeIndex >= 0) {
							if(nodeIndex != relevantWayNodeCounter && !showedNodeIndexError) {
								OsmAppPreprocessor.LOG.severe("Invalid nodeIndex: " + nodeIndex + " instead of " + relevantWayNodeCounter);
								showedNodeIndexError = true;
							}
								
							try {
								connectionWriter.writeInt(nodeIndex);
								connectionWriter.writeFloat((float)node.getLatitude());	
								connectionWriter.writeFloat((float)node.getLongitude());	
								connectionWriter.writeLong(node.getId());							
							} catch (IOException e) {
								OsmAppPreprocessor.LOG.log(Level.SEVERE, "Exception", e);
							}
							relevantWayNodeCounter++;	
						}
						
						nodes++;
					} 
					
					elementsPass2++;
					if ((elementsPass2 % 100000) == 0) {
						OsmAppPreprocessor.LOG.info("Loaded " + elementsPass2 + " elements ("
										+ (int) (((float) elementsPass2 / totalElements) * 100) + "%)");
						OsmAppPreprocessor.LOG.info("" + relevantWayNodeCounter);
					}
				}

				public void release() {
				}

				public void complete() {
				}

				@Override
				public void initialize(Map<String, Object> arg0) {

				}
			};

			connectionWriter.close();

			RunnableSource reader;
			try {
				reader = new crosby.binary.osmosis.OsmosisReader(
						new FileInputStream(new File(inFile)));
			} catch (FileNotFoundException e1) {
				e1.printStackTrace();
				return;
			}
			reader.setSink(sinkImplementation);

			Thread readerThread = new Thread(reader);
			readerThread.start();

			while (readerThread.isAlive()) {
				try {
					readerThread.join();
				} catch (InterruptedException e) {
					OsmAppPreprocessor.LOG.log(Level.SEVERE, "Exception", e);	
					return;
				}
			}			
			
			
			if(relevantWayNodeCounter < waypointIdsSet.size()) {
				OsmAppPreprocessor.LOG.severe("Not all relevantWayNodes have nodes in file: " + 
						relevantWayNodeCounter + " insead of " + waypointIdsSet.size());
			}
			if(relevantWayNodeCounter > waypointIdsSet.size()) {
				OsmAppPreprocessor.LOG.severe("Duplicate nodes for relevantWayNodes in file: " + 
						relevantWayNodeCounter + " insead of " + waypointIdsSet.size());
			}
			
			OsmAppPreprocessor.LOG.info("Pass 2 finished");
		}
		
		
		OsmAppPreprocessor.LOG.info("Pass 2 finished");
		OsmAppPreprocessor.LOG.info("Relevant ways: " + relevantWays + ", total ways: " + ways);
		OsmAppPreprocessor.LOG.info("Relevant waynodes: " + relevantWayNodeCounter + ", total nodes: " + nodes);
		OsmAppPreprocessor.LOG.info("Max nodes per way: " + maxNodesPerWay);
		OsmAppPreprocessor.LOG.info("Max ways per node: " + maxWaysPerNode);
		
		OsmAppPreprocessor.LOG.info("Finished in "
				+ (System.currentTimeMillis() - startTime) + "ms");
	}
	
	
//	
//	private static class HighwayInfos {
//		/** Info bits, bit0: Pedestrian, bit1: Car **/
//		public final byte InfoBits;
//		public final boolean Oneway;
//		public final short MaxSpeed; // TODO Byte
//		public List<WayNode> wayNodes;
//		
//		
//		public HighwayInfos(boolean car, boolean pedestrian, boolean oneway, short maxSpeed) {
//			byte infoBitsTmp = car ? (byte)1 : (byte)0;
//			infoBitsTmp = (byte)(infoBitsTmp << 1);
//			infoBitsTmp += pedestrian ? (byte)1 : (byte)0;
//			this.InfoBits = infoBitsTmp;			
//			this.Oneway = oneway;
//			this.MaxSpeed = maxSpeed;
//		}
//		
//		
////		public String getCsvString() {
////			return InfoBits + ";" + Oneway + ";" + MaxSpeed + ";";
////		}
//	}
	
}
