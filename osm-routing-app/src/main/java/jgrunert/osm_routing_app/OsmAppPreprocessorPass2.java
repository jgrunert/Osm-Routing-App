package jgrunert.osm_routing_app;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;

import org.openstreetmap.osmosis.core.container.v0_6.EntityContainer;
import org.openstreetmap.osmosis.core.domain.v0_6.Entity;
import org.openstreetmap.osmosis.core.domain.v0_6.Node;
import org.openstreetmap.osmosis.core.domain.v0_6.Relation;
import org.openstreetmap.osmosis.core.domain.v0_6.Tag;
import org.openstreetmap.osmosis.core.domain.v0_6.Way;
import org.openstreetmap.osmosis.core.domain.v0_6.WayNode;
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

	static List<HighwayInfos> highways = new ArrayList<>();
	static List<Long> waypointIds = new ArrayList<>();
	
	static boolean showedNodeIndexError = false;
	
	
	public static void main(String[] args) {
		try {
			preprocess();
		} catch (Exception e) {
			System.err.println("Failure at main");
			e.printStackTrace();
		}
	}
		
		
	private static void preprocess() throws Exception {
		
		//String outDir = "D:\\Jonas\\OSM\\germany";
		String outDir = "D:\\Jonas\\OSM\\hamburg";
		
		//String inFile = "D:\\Jonas\\OSM\\germany-latest.osm.pbf";
		String inFile = "D:\\Jonas\\OSM\\hamburg-latest.osm.pbf";
		//String inFile = "D:\\Jonas\\OSM\\baden-wuerttemberg-140101.osm.pbf";
		
		long startTime = System.currentTimeMillis();
		
		
		System.out.println("OSM Preprocessor Pass2 v01");
		

		System.out.println("Start processing nodes");
		DataInputStream waynodeIdReader = new DataInputStream(new FileInputStream(outDir + "\\pass1-waynodeIds.bin"));
		int waypointCount = waynodeIdReader.readInt();
		List<Long> waypointIdsSet = new ArrayList<>(waypointCount);
		int percTmp100 = waypointCount / 100;
		for(int i = 0; i < waypointCount; i++) {
			waypointIdsSet.add(waynodeIdReader.readLong());
			if(i % percTmp100 == 0) {
				System.out.println(i / percTmp100 + "% load waypointIdsSet");
			}
		}
		waynodeIdReader.close();
		
		
		
		// Pass 1.2: 
		{
			System.out.println("Starting Pass 2");
			
			
			DataOutputStream connectionWriter = new DataOutputStream(new FileOutputStream(outDir + "\\pass2-waynodes.bin"));
			connectionWriter.writeInt(waypointIdsSet.size());
						
			Sink sinkImplementation = new Sink() {

				public void process(EntityContainer entityContainer) {
					Entity entity = entityContainer.getEntity();
					
					if (entity instanceof Node) {
						Node node = (Node) entity;

						int nodeIndex = Collections.binarySearch(waypointIdsSet, node.getId());
						
						if(nodeIndex >= 0) {
							if(nodeIndex != relevantWayNodeCounter && !showedNodeIndexError) {
								System.err.println("Invalid nodeIndex: " + nodeIndex + " instead of " + relevantWayNodeCounter);
								showedNodeIndexError = true;
							}
								
							try {
								connectionWriter.writeInt(nodeIndex);
								connectionWriter.writeDouble(node.getLatitude());	
								connectionWriter.writeDouble(node.getLongitude());	
								connectionWriter.writeLong(node.getId());							
							} catch (IOException e) {
								e.printStackTrace();
							}
							relevantWayNodeCounter++;	
						}
						
						nodes++;
					} 
					
					elementsPass2++;
					if ((elementsPass2 % 100000) == 0) {
						System.out
								.println("Loaded " + elementsPass2 + " elements ("
										+ (int) (((float) elementsPass2 / totalElements) * 100) + "%)");
						System.out.println(relevantWayNodeCounter);
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
					e.printStackTrace();	
					return;
				}
			}			
			
			
			if(relevantWayNodeCounter < waypointIdsSet.size()) {
				System.err.println("Not all relevantWayNodes have nodes in file: " + 
						relevantWayNodeCounter + " insead of " + waypointIdsSet.size());
			}
			if(relevantWayNodeCounter > waypointIdsSet.size()) {
				System.err.println("Duplicate nodes for relevantWayNodes in file: " + 
						relevantWayNodeCounter + " insead of " + waypointIdsSet.size());
			}
			
			System.out.println("Pass 2 finished");

			connectionWriter.close();
		}
		
		
		System.out.println("Pass 2 finished");
		System.out.println("Relevant ways: " + relevantWays + ", total ways: " + ways);
		System.out.println("Relevant waynodes: " + relevantWayNodeCounter + ", total nodes: " + nodes);
		System.out.println("Max nodes per way: " + maxNodesPerWay);
		System.out.println("Max ways per node: " + maxWaysPerNode);
		
		System.out.println("Finished in "
				+ (System.currentTimeMillis() - startTime) + "ms");
	}
	
	
	
	private static class HighwayInfos {
		/** Info bits, bit0: Pedestrian, bit1: Car **/
		public final byte InfoBits;
		public final boolean Oneway;
		public final short MaxSpeed; // TODO Byte
		public List<WayNode> wayNodes;
		
		
		public HighwayInfos(boolean car, boolean pedestrian, boolean oneway, short maxSpeed) {
			byte infoBitsTmp = car ? (byte)1 : (byte)0;
			infoBitsTmp = (byte)(infoBitsTmp << 1);
			infoBitsTmp += pedestrian ? (byte)1 : (byte)0;
			this.InfoBits = infoBitsTmp;			
			this.Oneway = oneway;
			this.MaxSpeed = maxSpeed;
		}
		
		
//		public String getCsvString() {
//			return InfoBits + ";" + Oneway + ";" + MaxSpeed + ";";
//		}
	}
	

	
	static final int SPEED_WALK = 0;
	static final int SPEED_LIVINGSTREET = 5;
	static final int SPEED_UNLIMITED = 255;
	
	private static HighwayInfos evaluateHighway(String highwayTag, String maxspeedTag, 
			String sidewalkTag, String onewayTag) {
		
		String originalMaxSpeed = maxspeedTag;

		// Try to find out maxspeed
		Short maxSpeed;
		if(maxspeedTag != null) {			
			if(maxspeedTag.equals("none") || maxspeedTag.equals("signals") || 
					maxspeedTag.equals("variable") || maxspeedTag.equals("unlimited")) {
				// No speed limitation
				maxSpeed = 255;
			}
			else if(maxspeedTag.contains("living_street")) {
				maxSpeed = SPEED_LIVINGSTREET;
			}
			else if(maxspeedTag.contains("walk") || maxspeedTag.contains("foot")) {
				maxSpeed = SPEED_WALK;
			}
			else {
				try {
					boolean mph = false;
					// Try to parse speed limit
					if (maxspeedTag.contains("mph")) {
						mph = true;
						maxspeedTag = maxspeedTag.replace("mph", "");
					}
					if (maxspeedTag.contains("km/h"))
						maxspeedTag = maxspeedTag.replace("km/h", "");
					if (maxspeedTag.contains("kmh"))
						maxspeedTag = maxspeedTag.replace("kmh", "");
					if (maxspeedTag.contains("."))
						maxspeedTag = maxspeedTag.split("\\.")[0];
					if (maxspeedTag.contains(","))
						maxspeedTag = maxspeedTag.split(",")[0];
					if (maxspeedTag.contains(";"))
						maxspeedTag = maxspeedTag.split(";")[0];
					if (maxspeedTag.contains("-"))
						maxspeedTag = maxspeedTag.split("-")[0];
					if (maxspeedTag.contains(" "))
						maxspeedTag = maxspeedTag.split(" ")[0];

					maxSpeed = Short.parseShort(maxspeedTag);
					if(mph) {
						maxSpeed = (short)(maxSpeed * 1.60934);
					}
				} catch (Exception e) {
					System.err.println("Illegal maxspeed: " + originalMaxSpeed);
					maxSpeed = null;
				}
			}
		} else {
			maxSpeed = null;
		}
		
		
		// Try to find out if has sidewalk
		boolean sidewalk;
		if(sidewalkTag != null) {
			sidewalk = !(sidewalkTag.equals("no") || sidewalkTag.equals("none"));
		}
		else {
			sidewalk = false;
		}
		
		
		// Try to find out if is oneway
		Boolean oneway;
		if(onewayTag != null) {
			oneway = onewayTag.equals("yes");
		}
		else {
			oneway = null;
		}
		
		
		// Try to classify highway
		if(highwayTag.equals("track")) {
			// track
			if(maxSpeed == null)
				maxSpeed = 10;
			if(oneway == null)
				oneway = false;
			
			return new HighwayInfos(true, true, oneway, maxSpeed);
		}
		else if(highwayTag.equals("residential")) {
			// residential road
			if(maxSpeed == null)
				maxSpeed = 50;
			if(oneway == null)
				oneway = false;
			
			return new HighwayInfos(true, sidewalk, oneway, maxSpeed);
		}
		else if(highwayTag.equals("service")) {
			// service road
			if(maxSpeed == null)
				maxSpeed = 30;
			if(oneway == null)
				oneway = false;
			
			return new HighwayInfos(true, sidewalk, oneway, maxSpeed);
		}
		else if(highwayTag.equals("footway") || highwayTag.equals("path") || highwayTag.equals("steps") ||
				highwayTag.equals("bridleway") || highwayTag.equals("pedestrian")) {
			// footway etc.
			if(maxSpeed == null)
				maxSpeed = 0;
			if(oneway == null)
				oneway = false;
			
			return new HighwayInfos(false, true, oneway, maxSpeed);
		}
		else if(highwayTag.startsWith("primary") || highwayTag.startsWith("secondary") || 
		   highwayTag.startsWith("tertiary")) {
			// country road etc
			if(maxSpeed == null)
				maxSpeed = 100;
			if(oneway == null)
				oneway = false;
			
			return new HighwayInfos(true, sidewalk, oneway, maxSpeed);
		}
		else if(highwayTag.equals("unclassified")) {
			// unclassified (small road)
			if(maxSpeed == null)
				maxSpeed = 50;
			if(oneway == null)
				oneway = false;
			
			return new HighwayInfos(true, sidewalk, oneway, maxSpeed);
		}
		else if(highwayTag.equals("living_street")) {
			// living street
			if(maxSpeed == null)
				maxSpeed = SPEED_LIVINGSTREET;
			if(oneway == null)
				oneway = false;
			
			return new HighwayInfos(true, true, oneway, maxSpeed);
		}
		else if(highwayTag.startsWith("motorway")) {
			// track
			if(maxSpeed == null)
				maxSpeed = 255;
			if(oneway == null)
				oneway = true;
			
			return new HighwayInfos(true, sidewalk, oneway, maxSpeed);
		}
		else if(highwayTag.startsWith("trunk")) {
			// trunk road
			if(maxSpeed == null)
				maxSpeed = 255;
			if(oneway == null)
				oneway = false;
			
			return new HighwayInfos(true, sidewalk, oneway, maxSpeed);
		}
		
		// Ignore this road if no useful classification available
		return null;
	}
}
