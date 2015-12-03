package jgrunert.osm_routing_app;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Map;

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
public class OsmAppPreprocessor {
	static int nodes = 0;
	static int ways = 0;
	static int relations = 0;

	static int totalElements = 267101283;
	static int totalLoaded = 0;

	public static void main(String[] args) {
		// File file = new File("D:\\Jonas\\OSM\\germany-latest.osm.pbf");
		File file = new File("D:\\Jonas\\OSM\\hamburg-latest.osm.pbf");

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

		Map<Long, Integer> nodeIds = new HashMap<>();
		Map<Long, Integer> wayIds = new HashMap<>();
		Map<Long, Integer> relIds = new HashMap<>();

		Sink sinkImplementation = new Sink() {

			public void process(EntityContainer entityContainer) {
				Entity entity = entityContainer.getEntity();
				if (entity instanceof Node) {
					Node node = (Node) entity;
					// System.out.println("Node: " + ((Node)entity).getId());
					nodeIds.put(node.getId(), nodes);
					nodes++;
					totalLoaded++;
				} else if (entity instanceof Way) {
					Way way = (Way) entity;
					// do something with the way
					// System.out.println("Way: " + ((Way)entity).getId());
					wayIds.put(way.getId(), ways);
					for (Tag tag : way.getTags()) {
						System.out.println(tag);
					}
					ways++;
					totalLoaded++;
				} else if (entity instanceof Relation) {
					Relation rel = (Relation) entity;
					// do something with the relation
					// System.out.println("Relation: " +
					// ((Relation)entity).getId());
					relIds.put(rel.getId(), relations);
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

		System.out.println("Nodes: " + nodes);
		System.out.println("ways: " + ways);
		System.out.println("relations: " + relations);
		System.out.println("Finished in "
				+ (System.currentTimeMillis() - startTime) + "ms");

	}
}
