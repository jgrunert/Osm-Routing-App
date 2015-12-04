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
public class OsmAppPreprocessor {
	static int ways = 0;

	static int totalElements = 267101283;
	static int totalLoaded = 0;
	
	
	
	public static void main(String[] args) throws Exception {
		//File file = new File("D:\\Jonas\\OSM\\germany-latest.osm.pbf");
		File file = new File("D:\\Jonas\\OSM\\hamburg-latest.osm.pbf");
		
		PrintWriter waysHighwayWriter = new PrintWriter(new File("D:\\Jonas\\OSM\\highways-processed.csv"));

		long startTime = System.currentTimeMillis();
		
		System.out.println("Start reading");

		Sink sinkImplementation = new Sink() {

			public void process(EntityContainer entityContainer) {
				Entity entity = entityContainer.getEntity();
				
				if (entity instanceof Way) {
					Way way = (Way) entity;
					
					String highway = null;
					String maxspeed = null;
					String sidewalk = null;
					String oneway = null;
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
					}
					
					if (highway != null) {
						HighwayInfos hw = evaluateHighway(highway, maxspeed, sidewalk, oneway);
						if(hw != null) {
							waysHighwayWriter.println(highway + ";" + maxspeed + ";" + sidewalk + ";" + oneway + ";" + hw.getCsvString());
						} else {
							waysHighwayWriter.println(highway + ";" + maxspeed + ";" + sidewalk + ";" + oneway + ";Ignored;");
						}
					}
					ways++;
				} 
				
				totalLoaded++;
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
		
		System.out.println("ways: " + ways);
		System.out.println("Finished in "
				+ (System.currentTimeMillis() - startTime) + "ms");
		
		waysHighwayWriter.close();
	}
	
	
	
	private static class HighwayInfos {
		public final boolean Car;
		public final boolean Pedestrian;
		public final boolean Oneway;
		public final short MaxSpeed; // TODO Byte
		
		
		public HighwayInfos(boolean car, boolean pedestiran, boolean oneway, short maxSpeed) {
			this.Car = car;
			this.Pedestrian = pedestiran;
			this.Oneway = oneway;
			this.MaxSpeed = maxSpeed;
		}
		
		
		public String getCsvString() {
			return Car + ";" + Pedestrian + ";" + Oneway + ";" + MaxSpeed + ";";
		}
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
					System.err.println(originalMaxSpeed);
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
