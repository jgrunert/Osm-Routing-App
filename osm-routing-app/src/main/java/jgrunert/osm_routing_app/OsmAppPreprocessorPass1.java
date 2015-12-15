package jgrunert.osm_routing_app;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import org.openstreetmap.osmosis.core.container.v0_6.EntityContainer;
import org.openstreetmap.osmosis.core.domain.v0_6.Entity;
import org.openstreetmap.osmosis.core.domain.v0_6.Tag;
import org.openstreetmap.osmosis.core.domain.v0_6.Way;
import org.openstreetmap.osmosis.core.domain.v0_6.WayNode;
import org.openstreetmap.osmosis.core.task.v0_6.RunnableSource;
import org.openstreetmap.osmosis.core.task.v0_6.Sink;

/**
 * Hello world!
 *
 */
public class OsmAppPreprocessorPass1 {

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

	
	public static void main(String[] args) {
		try {
			String outDir = "D:\\Jonas\\OSM\\germany";
			//String outDir = "D:\\Jonas\\OSM\\hamburg";
			//String outDir = "D:\\Jonas\\OSM\\bawue";
			
			String inFile = "D:\\Jonas\\OSM\\germany-latest.osm.pbf";
			//String inFile = "D:\\Jonas\\OSM\\hamburg-latest.osm.pbf";
			//String inFile = "D:\\Jonas\\OSM\\baden-wuerttemberg-latest.osm.pbf";
			
			doPass(inFile, outDir);
		} catch (Exception e) {
			OsmAppPreprocessor.LOG.severe("Failure at main");
			OsmAppPreprocessor.LOG.log(Level.SEVERE, "Exception", e);
		}
	}
		
		
	public static void doPass(String inFile, String outDir) throws Exception {
		
		
		//PrintWriter highwayCsvAllWriter = new PrintWriter(new File(outDir + "\\highways-processed-all.csv"));

		long startTime = System.currentTimeMillis();
		
		
		OsmAppPreprocessor.LOG.info("OSM Preprocessor Pass1 v03");
		
		
		//Map<Long, Short> waysPerNode = new HashMap<>();
		
		// List of all highways, Int32-index in this array will later be their index
		
		// Pass 1 - read highways
		{
		OsmAppPreprocessor.LOG.info("Starting Pass 1");
		
		//PrintWriter highwayCsvWriter = new PrintWriter(new File(outDir + "\\highways-processed.csv"));
		
		Sink sinkImplementation = new Sink() {

			public void process(EntityContainer entityContainer) {
				Entity entity = entityContainer.getEntity();
				
				if (entity instanceof Way) {
					Way way = (Way) entity;
					
					String highway = null;
					String maxspeed = null;
					String sidewalk = null;
					String oneway = null;
					String access = null;
					for(Tag tag : way.getTags()) {
						if(tag.getKey().equals("highway")) {
							highway = tag.getValue();
						}
						else if(tag.getKey().equals("maxspeed")) {
							maxspeed = tag.getValue();
						}
						else if(tag.getKey().equals("sidewalk")) {
							sidewalk = tag.getValue();
						}
						else if(tag.getKey().equals("oneway")) {
							oneway = tag.getValue();
						}
						else if(tag.getKey().equals("access")) {
							access = tag.getValue();
						}
					}
					
					boolean accessOk;
					if(access != null && 
							(access.equals("private") || access.equals("no") || access.equals("emergency") || access.equals("agricultural") || access.equals("bus"))) {
						accessOk = false;
					} else {
						accessOk = true;
					}
					
					try {
						if (highway != null && accessOk) {
							HighwayInfos hw = evaluateHighway(highway,
									maxspeed, sidewalk, oneway);
							if (hw != null) {
//								highwayCsvAllWriter.println(highway + ";"
//										+ maxspeed + ";" + sidewalk + ";"
//										+ oneway + ";" + hw.getCsvString());
								// Write csv
								//highwayCsvWriter.println(hw.getCsvString());
								// Write binary
								//highwayBinWriter.writeBoolean(hw.Car);
								//highwayBinWriter.writeBoolean(hw.Pedestrian);
								//highwayBinWriter.writeBoolean(hw.Oneway);
								relevantWays++;
								
								for(WayNode waynode : way.getWayNodes()) {									
									//Short ways = waysPerNode.get(waynode.getNodeId());
									//if(ways == null) { ways = 0; }
									//ways++;
									//OsmAppPreprocessor.LOG.info(ways);
									//maxWaysPerNode = Math.max(maxWaysPerNode, ways);
									//waysPerNode.put(waynode.getNodeId(), ways);
									//waynodes.add(waynode.getNodeId());		
									waypointIds.add(waynode.getNodeId());
								}
								
								hw.wayNodes = way.getWayNodes();
								highways.add(hw);
								
								maxNodesPerWay = Math.max(maxNodesPerWay, way.getWayNodes().size());
							} 
//							else {
//								highwayCsvAllWriter.println(highway + ";"
//										+ maxspeed + ";" + sidewalk + ";"
//										+ oneway + ";Ignored;");
//							}
						}
					} catch (Exception e) {
						e.printStackTrace();
					}
					ways++;
				} 
				
				elementsPass1++;
				if ((elementsPass1 % 100000) == 0) {
					OsmAppPreprocessor.LOG.info("Loaded " + elementsPass1 + " elements ("
									+ (int) (((float) elementsPass1 / totalElements) * 100) + "%)");
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
		
		//highwayCsvWriter.close();
		
		OsmAppPreprocessor.LOG.info("Finised pass 1");
		OsmAppPreprocessor.LOG.info("Time elapsed: " + (System.currentTimeMillis() - startTime) + "ms");
		}
		
		
		
		// Between pass 1 and 2
		// Sort waypointIds
		OsmAppPreprocessor.LOG.info("Start sorting waypointIds with size " + waypointIds.size());
		Collections.sort(waypointIds);
		OsmAppPreprocessor.LOG.info("Sorted waypointIds");
				
		// Create sorted waypointIdsSet. It maps old to new indices: newIndex == waypointIdsSet.indexOf(oldIndex)
		OsmAppPreprocessor.LOG.info("Start creating waypointIdsSet");
		List<Long> waypointIdsSet = new ArrayList<>();
		long lastIndex = waypointIds.get(0);
		waypointIdsSet.add(lastIndex);
		for(int i = 1; i < waypointIds.size(); i++) {
			if(waypointIds.get(i) != lastIndex) {
				lastIndex = waypointIds.get(i);
				waypointIdsSet.add(lastIndex);
			}
		}
		OsmAppPreprocessor.LOG.info("Finished creating waypointIdsSet with size " + waypointIdsSet.size());
		OsmAppPreprocessor.LOG.info("Time elapsed: " + (System.currentTimeMillis() - startTime) + "ms");
		
		// TODO Sort by location? Sort by ways (in next step)? Are ways sorted?
				
		// Save waypointIdsSet	
		OsmAppPreprocessor.LOG.info("Start saving waypointIdsSet");
		//DataOutputStream waypointIdsWriter = new DataOutputStream(new FileOutputStream(outDir + "\\pass1-waynodeIds.bin"));
		//waypointIdsWriter.writeInt(waypointIdsSet.size());
		int percTmp10 = waypointIdsSet.size() / 10;
		int percTmp100 = waypointIdsSet.size() / 100;
		int percCounter = 0;
		for(long id : waypointIdsSet) {
			//waypointIdsWriter.writeLong(id);
			percCounter++;
			if(percCounter % percTmp10 == 0) {
				OsmAppPreprocessor.LOG.info(percCounter / percTmp100 + "% save waypointIdsSet");
			}
		}
		//waypointIdsWriter.close();
		OsmAppPreprocessor.LOG.info("Finished saving waypointIdsSet");
		
		
		
		// Evaluate and save waypoint highway relations
		// List of Lists for each node with indices of all ways he is involved in
		//DataOutputStream highwayBinWriter = new DataOutputStream(new FileOutputStream(outDir + "\\pass1-highways.bin"));	
		//highwayBinWriter.writeInt(highways.size());
		
		OsmAppPreprocessor.LOG.info("Start finding waysOfNodes");
		List<List<Integer>> waysOfNodes = new ArrayList<List<Integer>>(waypointIdsSet.size());
		for(int i = 0; i < waypointIdsSet.size(); i++) {
			waysOfNodes.add(new LinkedList<Integer>());
		}		
		int percAmnt = highways.size() / 100;
		for(int i = 0; i < highways.size(); i++) {
			HighwayInfos hw = highways.get(i);

			//highwayBinWriter.writeByte(hw.InfoBits);
			//highwayBinWriter.writeBoolean(hw.Oneway);
			//highwayBinWriter.writeByte((byte)hw.MaxSpeed);
			//highwayBinWriter.writeInt(hw.wayNodes.size());
			
			for(WayNode wnode : hw.wayNodes) {
				int nodeIndex = Collections.binarySearch(waypointIdsSet, wnode.getNodeId());
				if(nodeIndex > 0) {
					//highwayBinWriter.writeInt(nodeIndex);
					waysOfNodes.get(nodeIndex).add(i);
				} else {
					// Cannot find node with this ID
					//highwayBinWriter.writeInt(-1);					
				}
			}
			highways.set(i, null);
			if(i % percAmnt == 0) {
				OsmAppPreprocessor.LOG.info((i / percAmnt) + "%  finding waysOfNodes");
			}
		}
		//highwayBinWriter.close();
		OsmAppPreprocessor.LOG.info("Finished finding waysOfNodes");
		OsmAppPreprocessor.LOG.info("Time elapsed: " + (System.currentTimeMillis() - startTime) + "ms");

		
		// Clean up highways
		OsmAppPreprocessor.LOG.info("Start clean up highways");
		highways.clear();
		highways = null;
		OsmAppPreprocessor.LOG.info("Finished clean up highways");
		
		
		// Save waysOfNodes
		OsmAppPreprocessor.LOG.info("Start saving waysOfNodes");
		ObjectOutputStream waysOfNodesWriter = new ObjectOutputStream(new FileOutputStream(outDir + "\\pass1-waysOfNodes.bin"));
		waysOfNodesWriter.writeInt(waysOfNodes.size());
		percTmp100 = waysOfNodes.size() / 100;
		for(int i = 0; i < waysOfNodes.size(); i++) {
			List<Integer> nodeWays = waysOfNodes.get(i);
			waysOfNodesWriter.writeObject(nodeWays.toArray(new Integer[0]));
//			waysOfNodesWriter.writeInt(nodeWays.size());
//			for(int id : nodeWays) {
//				waysOfNodesWriter.writeInt(id);				
//			}
			waysOfNodes.set(i, null);
			if(i % percTmp100 == 0) {
				OsmAppPreprocessor.LOG.info(i / percTmp100 + "% save waysOfNodes");
			}
		}	
		waysOfNodesWriter.close();
		OsmAppPreprocessor.LOG.info("Finished saving waysOfNodesWriter");
		

		// Clean up waysOfNodes
		OsmAppPreprocessor.LOG.info("Start clean up waysOfNodes");
		waysOfNodes.clear();
		waysOfNodes = null;
		OsmAppPreprocessor.LOG.info("Finished clean up waysOfNodes");
		
		
		
		OsmAppPreprocessor.LOG.info("Pass 1 finished");
		OsmAppPreprocessor.LOG.info("Relevant ways: " + relevantWays + ", total ways: " + ways);
		OsmAppPreprocessor.LOG.info("Relevant waynodes: " + relevantWayNodeCounter + ", total nodes: " + nodes);
		OsmAppPreprocessor.LOG.info("Max nodes per way: " + maxNodesPerWay);
		OsmAppPreprocessor.LOG.info("Max ways per node: " + maxWaysPerNode);
		
		OsmAppPreprocessor.LOG.info("Finished in "
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

	enum SidwalkMode { Yes, No, Unspecified };
	
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
					OsmAppPreprocessor.LOG.severe("Illegal maxspeed: " + originalMaxSpeed);
					maxSpeed = null;
				}
			}
		} else {
			maxSpeed = null;
		}
		
		
		// Try to find out if has sidewalk
		SidwalkMode sidewalk = SidwalkMode.Unspecified;
		if(sidewalkTag != null) {
			if(sidewalkTag.equals("no") || sidewalkTag.equals("none")) {
				sidewalk = SidwalkMode.No;
			} else {
				sidewalk = SidwalkMode.Yes;
			}
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
			
			// Residential by default with sideway
			return new HighwayInfos(true, (sidewalk != SidwalkMode.No), oneway, maxSpeed);
		}
		else if(highwayTag.equals("service")) {
			// service road
			if(maxSpeed == null)
				maxSpeed = 30;
			if(oneway == null)
				oneway = false;

			// Residential by default with sideway
			return new HighwayInfos(true, (sidewalk != SidwalkMode.No), oneway, maxSpeed);
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
		else if(highwayTag.startsWith("primary")) {
			// country road etc
			if(maxSpeed == null)
				maxSpeed = 100;
			if(oneway == null)
				oneway = false;
			
			return new HighwayInfos(true, (sidewalk == SidwalkMode.Yes), oneway, maxSpeed);
		}
		else if(highwayTag.startsWith("secondary") || highwayTag.startsWith("tertiary")) {
					// country road etc
					if(maxSpeed == null)
						maxSpeed = 100;
					if(oneway == null)
						oneway = false;
					
					return new HighwayInfos(true, (sidewalk != SidwalkMode.No), oneway, maxSpeed);
		}
		else if(highwayTag.equals("unclassified")) {
			// unclassified (small road)
			if(maxSpeed == null)
				maxSpeed = 50;
			if(oneway == null)
				oneway = false;
			
			return new HighwayInfos(true, (sidewalk != SidwalkMode.No), oneway, maxSpeed);
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
			
			return new HighwayInfos(true, (sidewalk == SidwalkMode.Yes), oneway, maxSpeed);
		}
		else if(highwayTag.startsWith("trunk")) {
			// trunk road
			if(maxSpeed == null)
				maxSpeed = 255;
			if(oneway == null)
				oneway = false;
			
			return new HighwayInfos(true, (sidewalk == SidwalkMode.Yes), oneway, maxSpeed);
		}
		
		// Ignore this road if no useful classification available
		return null;
	}
}
