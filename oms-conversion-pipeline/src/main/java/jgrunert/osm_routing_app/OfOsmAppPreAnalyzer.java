package jgrunert.osm_routing_app;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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
public class OfOsmAppPreAnalyzer {

	static int nodes = 0;
	static int ways = 0;
	static int relations = 0;

	static int totalElements = 267101283;
	static int totalLoaded = 0;



	private static boolean ignoreTag(String tag) {
		return tag.startsWith("addr") || tag.startsWith("alt_name") || tag.startsWith("amenity")
				|| tag.startsWith("architect") || tag.startsWith("brand") || tag.startsWith("building")
				|| tag.startsWith("capacity") || tag.startsWith("comment") || tag.startsWith("construction_year")
				|| tag.startsWith("contact") || tag.startsWith("cuisine") || tag.startsWith("description")
				|| tag.startsWith("destination") || tag.startsWith("email") || tag.startsWith("fax")
				|| tag.startsWith("height") || tag.startsWith("image") || tag.startsWith("loc_name")
				|| tag.startsWith("mtb:name") || tag.startsWith("name") || tag.startsWith("note")
				|| tag.startsWith("old_name") || tag.startsWith("opening_hours") || tag.startsWith("operator")
				|| tag.startsWith("phone") || tag.startsWith("postal_code") || tag.startsWith("railway:track_ref")
				|| tag.startsWith("ref") || tag.startsWith("short_name") || tag.startsWith("source")
				|| tag.startsWith("shop") || tag.startsWith("start_date") || tag.startsWith("turn:lanes")
				|| tag.startsWith("width") || tag.startsWith("wikipedia") || tag.startsWith("year")
				|| tag.startsWith("TMC") || tag.startsWith("wheelchair") || tag.equalsIgnoreCase("fixme")
				|| tag.contains("colour") || tag.contains("website") || tag.contains("url") || tag.contains("material");
	}


	public static void main(String[] args) throws Exception {
		File file = new File(
				"C:\\Users\\Jonas\\Projekte\\ConcurrentGraph_Data2\\original\\baden-wuerttemberg-latest.osm.pbf");
		String convertOutDir = "C:\\Users\\Jonas\\Projekte\\ConcurrentGraph_Data2\\converted";

		String suffix = "_bawue.csv";

		PrintWriter nodeTagKeys = new PrintWriter(new File(convertOutDir + "\\NodeTagKeys" + suffix));
		PrintWriter wayTagKeys = new PrintWriter(new File(convertOutDir + "\\WayTagKey" + suffix));
		PrintWriter relTagKeys = new PrintWriter(new File(convertOutDir + "\\RelationTagKeys" + suffix));


		PrintWriter nodeTags = new PrintWriter(new File(convertOutDir + "\\NodeTags_Reduced" + suffix));
		PrintWriter wayTags = new PrintWriter(new File(convertOutDir + "\\WayTags_Reduced" + suffix));
		PrintWriter relTags = new PrintWriter(new File(convertOutDir + "\\RelationTags_Reduced" + suffix));

		long startTime = System.currentTimeMillis();

		// System.out.println("A");
		// int[] ids = new int[totalElements];
		// System.out.println("B");
		// try {
		// Thread.sleep(10000);
		// } catch (InterruptedException e1) {
		// // TODO Auto-generated catch block
		// e1.printStackTrace();
		// }

		System.out.println("Start reading");

		// Map<Long, Integer> nodeIds = new HashMap<>();
		// Map<Long, Integer> wayIds = new HashMap<>();
		// Map<Long, Integer> relIds = new HashMap<>();

		Set<String> tagsN = new HashSet<>();
		Set<String> tagsW = new HashSet<>();
		Set<String> tagsR = new HashSet<>();

		Set<String> tagsN2 = new HashSet<>();
		Set<String> tagsW2 = new HashSet<>();
		Set<String> tagsR2 = new HashSet<>();


		Sink sinkImplementation = new Sink() {

			@Override
			public void process(EntityContainer entityContainer) {
				Entity entity = entityContainer.getEntity();
				if (entity instanceof Node) {
					Node node = (Node) entity;
					// System.out.println("Node: " + ((Node)entity).getId());
					// nodeIds.put(node.getId(), nodes);
					nodes++;
					totalLoaded++;

					for (Tag tag : node.getTags()) {
						if (!ignoreTag(tag.getKey()) && !tagsN2.contains(tag.toString())) {
							// System.out.println(tag.getKey() + ";" +
							// tag.getValue() + ";");
							nodeTags.println(tag.getKey() + ";" + tag.getValue() + ";");
							tagsN2.add(tag.toString());
						}
					}

					for (Tag tag : node.getTags()) {
						if (!tagsN.contains(tag.getKey())) {
							nodeTagKeys.println(tag.getKey() + ";");
							tagsN.add(tag.getKey());
						}
					}
				}
				else if (entity instanceof Way) {
					Way way = (Way) entity;
					// do something with the way
					// System.out.println("Way: " + ((Way)entity).getId());
					// wayIds.put(way.getId(), ways);

					for (Tag tag : way.getTags()) {
						if (!ignoreTag(tag.getKey()) && !tagsW2.contains(tag.toString())) {
							// System.out.println(tag.getKey() + ";" +
							// tag.getValue() + ";");
							wayTags.println(tag.getKey() + ";" + tag.getValue() + ";");
							tagsW2.add(tag.toString());
						}
					}

					for (Tag tag : way.getTags()) {
						if (!tagsW.contains(tag.getKey())) {
							// if(!tag.getKey().startsWith("addr") &&
							// !tag.getKey().equals("name") &&
							// !tags.contains(tag.toString())) {
							// System.out.println(tag.getKey() + ";" +
							// tag.getValue() + ";");
							wayTagKeys.println(tag.getKey() + ";");
							tagsW.add(tag.getKey());
						}
					}
					ways++;
					totalLoaded++;
				}
				else if (entity instanceof Relation) {
					Relation rel = (Relation) entity;
					// do something with the relation
					// System.out.println("Relation: " +
					// ((Relation)entity).getId());
					// relIds.put(rel.getId(), relations);
					relations++;
					totalLoaded++;

					for (Tag tag : rel.getTags()) {
						if (!ignoreTag(tag.getKey()) && !tagsR2.contains(tag.toString())) {
							// System.out.println(tag.getKey() + ";" +
							// tag.getValue() + ";");
							relTags.println(tag.getKey() + ";" + tag.getValue() + ";");
							tagsR2.add(tag.toString());
						}
					}

					for (Tag tag : rel.getTags()) {
						if (!tagsR.contains(tag.getKey())) {
							relTagKeys.println(tag.getKey() + ";");
							tagsR.add(tag.getKey());
						}
					}
				}

				if ((totalLoaded % 100000) == 0) {
					// System.out
					// .println("Loaded " + totalLoaded + " elements ("
					// + (int) (((float) totalLoaded / totalElements) * 100) +
					// "%)");
				}
			}

			@Override
			public void release() {
			}

			@Override
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
			reader = new crosby.binary.osmosis.OsmosisReader(new FileInputStream(file));
		}
		catch (FileNotFoundException e1) {
			e1.printStackTrace();
			return;
		}
		reader.setSink(sinkImplementation);

		Thread readerThread = new Thread(reader);
		readerThread.start();

		while (readerThread.isAlive()) {
			try {
				readerThread.join();
			}
			catch (InterruptedException e) {
				/* do nothing */
			}
		}

		System.out.println("Nodes: " + nodes);
		System.out.println("ways: " + ways);
		System.out.println("relations: " + relations);
		System.out.println("Finished in " + (System.currentTimeMillis() - startTime) + "ms");

		nodeTagKeys.close();
		wayTagKeys.close();
		relTagKeys.close();
		nodeTags.close();
		wayTags.close();
		relTags.close();
	}
}
