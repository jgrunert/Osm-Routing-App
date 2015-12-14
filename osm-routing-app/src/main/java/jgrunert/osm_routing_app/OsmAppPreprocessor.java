package jgrunert.osm_routing_app;

import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class OsmAppPreprocessor {
	
	private static final Logger LOG = Logger.getLogger(OsmAppPreprocessor.class.getName()); 

	public static void main(String[] args) {
		try {		

			FileHandler fh = new FileHandler("passes.log");
			fh.setFormatter(new SimpleFormatter());
			LOG.addHandler(fh);
			LOG.info("Starting passes");
			
			//String inFile = "D:\\Jonas\\OSM\\germany-latest.osm.pbf";
			String inFile = "D:\\Jonas\\OSM\\hamburg-latest.osm.pbf";
			//String inFile = "D:\\Jonas\\OSM\\baden-wuerttemberg-latest.osm.pbf";
			
			//String outDir = "D:\\Jonas\\OSM\\germany";
			String outDir = "D:\\Jonas\\OSM\\hamburg";
			//String outDir = "D:\\Jonas\\OSM\\bawue";
			
			OsmAppPreprocessorPass1.doPass(inFile, outDir);
			OsmAppPreprocessorPass2.doPass(inFile, outDir);
			OsmAppPreprocessorPass3.doPass(outDir);
			OsmAppPreprocessorPass4.doPass(outDir);
			OsmAppPreprocessorPass5.doPass(outDir);
		} catch (Exception e) {
			LOG.severe("Failure at main");
			LOG.log(Level.SEVERE, "Exception", e);
		}
	}

}
