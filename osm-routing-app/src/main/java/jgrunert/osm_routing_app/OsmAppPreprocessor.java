package jgrunert.osm_routing_app;

public class OsmAppPreprocessor {

	public static void main(String[] args) {
		try {
			
			//String inFile = "D:\\Jonas\\OSM\\germany-latest.osm.pbf";
			//String inFile = "D:\\Jonas\\OSM\\hamburg-latest.osm.pbf";
			String inFile = "D:\\Jonas\\OSM\\baden-wuerttemberg-latest.osm.pbf";
			
			//String outDir = "D:\\Jonas\\OSM\\germany";
			//String outDir = "D:\\Jonas\\OSM\\hamburg";
			String outDir = "D:\\Jonas\\OSM\\bawue";
			
			OsmAppPreprocessorPass1.doPass(inFile, outDir);
			OsmAppPreprocessorPass2.doPass(inFile, outDir);
			OsmAppPreprocessorPass3.doPass(outDir);
			OsmAppPreprocessorPass4.doPass(outDir);
			OsmAppPreprocessorPass5.doPass(outDir);
		} catch (Exception e) {
			System.err.println("Error in main");
			e.printStackTrace();
		}
	}

}
