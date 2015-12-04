package jgrunert.osm_routing_app;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.openstreetmap.osmosis.core.container.v0_6.EntityContainer;
import org.openstreetmap.osmosis.core.domain.v0_6.Entity;
import org.openstreetmap.osmosis.core.domain.v0_6.Node;
import org.openstreetmap.osmosis.core.domain.v0_6.Relation;
import org.openstreetmap.osmosis.core.domain.v0_6.Tag;
import org.openstreetmap.osmosis.core.domain.v0_6.Way;
import org.openstreetmap.osmosis.core.task.v0_6.RunnableSource;
import org.openstreetmap.osmosis.core.task.v0_6.Sink;

/**
 * Hello world!
 *
 */
public class OsmAppPreAnalyzer2 {
	static int nodes = 0;
	static int ways = 0;
	static int relations = 0;

	static int totalElements = 267101283;
	static int totalLoaded = 0;
	
	
	
	public static void main(String[] args) throws Exception {
		File file = new File("D:\\Jonas\\OSM\\germany-latest.osm.pbf");
		//File file = new File("D:\\Jonas\\OSM\\hamburg-latest.osm.pbf");
		
		PrintWriter waysHighwayWriter = new PrintWriter(new File("D:\\Jonas\\OSM\\highways.csv"));
		PrintWriter waysHighwaySimplWriter = new PrintWriter(new File("D:\\Jonas\\OSM\\highways-simplified.csv"));
		PrintWriter waysHighwayTypeWriter = new PrintWriter(new File("D:\\Jonas\\OSM\\highways-types.csv"));
		PrintWriter waysHighwayMaxWriter = new PrintWriter(new File("D:\\Jonas\\OSM\\highways-maxspeeds.csv"));

		long startTime = System.currentTimeMillis();

//		System.out.println("A");
//		int[] ids = new int[totalElements];
//		System.out.println("B");
//		try {
//			Thread.sleep(10000);
//		} catch (InterruptedException e1) {
//			// TODO Auto-generated catch block
//			e1.printStackTrace();
//		}
		
		System.out.println("Start reading");

//		Map<Long, Integer> nodeIds = new HashMap<>();
//		Map<Long, Integer> wayIds = new HashMap<>();
//		Map<Long, Integer> relIds = new HashMap<>();

		Map<String, Integer> types = new HashMap<>();
		Map<String, Integer> maxSpeeds = new HashMap<>();

		Sink sinkImplementation = new Sink() {

			public void process(EntityContainer entityContainer) {
				Entity entity = entityContainer.getEntity();
				if (entity instanceof Node) {
					//Node node = (Node) entity;
					// System.out.println("Node: " + ((Node)entity).getId());
					//nodeIds.put(node.getId(), nodes);
					nodes++;
					totalLoaded++;
				} 
				else if (entity instanceof Way) {
					Way way = (Way) entity;
					
					boolean isHighway = false;
					String highwayType = "";
					for(Tag tag : way.getTags()) {
						if(tag.getKey().equals("highway")) {
							isHighway = true;
							highwayType = tag.getValue();
							if(!types.containsKey(tag.getValue())) {
								types.put(tag.getValue(), 1);
							} else {
								types.put(tag.getValue(), types.get(tag.getValue()) + 1);
							}
						}
					}
					
					if (isHighway) {
						String maxSpeed = "?";
						for(Tag tag : way.getTags()) {
							if (tag.getKey().equals("maxspeed")) {
								if(!maxSpeeds.containsKey(tag.getValue())) {
									maxSpeeds.put(tag.getValue(), 1);
								} else {
									maxSpeeds.put(tag.getValue(), maxSpeeds.get(tag.getValue()) + 1);
								}
								maxSpeed = tag.getValue();
							}
						}
						
						String line = highwayType + ";" + way.getWayNodes().size() + ";";
						waysHighwaySimplWriter.println(highwayType + ";" + way.getWayNodes().size() + ";" + maxSpeed + ";");
						for (Tag tag : way.getTags()) {
							if (!tag.getKey().equals("highway")) {
								line += tag.getKey() + ";" + tag.getValue()
										+ ";";
							}
						}
						waysHighwayWriter.println(line);
					}
					ways++;
					totalLoaded++;
				} 
				else if (entity instanceof Relation) {
					//Relation rel = (Relation) entity;
					// System.out.println("Relation: " +
					// ((Relation)entity).getId());
					//relIds.put(rel.getId(), relations);
					relations++;
					totalLoaded++;
				}
				
				if ((totalLoaded % 100000) == 0) {
					System.out
							.println("Loaded " + totalLoaded + " elements ("
									+ (int) (((float) totalLoaded / totalElements) * 100) + "%)");
				}
			}

			public void release() {
			}

			public void complete() {
			}

			@Override
			public void initialize(Map<String, Object> arg0) {
				// TODO Auto-generated method stub

			}
		};

		if (!file.getName().endsWith(".pbf")) {
			throw new RuntimeException("Invalid file extension");
		}

		RunnableSource reader;
		try {
			reader = new crosby.binary.osmosis.OsmosisReader(
					new FileInputStream(file));
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
				/* do nothing */
			}
		}
		
		for(Entry<String, Integer> type : types.entrySet()) {
			waysHighwayTypeWriter.println(type.getKey() + ";" + type.getValue() + ";");
		}
		for(Entry<String, Integer> type : maxSpeeds.entrySet()) {
			waysHighwayMaxWriter.println(type.getKey() + ";" + type.getValue() + ";");
		}

		System.out.println("Nodes: " + nodes);
		System.out.println("ways: " + ways);
		System.out.println("relations: " + relations);
		System.out.println("Finished in "
				+ (System.currentTimeMillis() - startTime) + "ms");
		
		waysHighwayWriter.close();
		waysHighwayTypeWriter.close();
		waysHighwayMaxWriter.close();
		waysHighwaySimplWriter.close();
	}
}
