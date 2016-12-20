package jgrunert.osm_routing_app;

import java.io.File;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class OsmAppPreprocessor {
	
	public static final Logger LOG = Logger.getLogger("OsmAppPreprocessor"); 
	
	static {
		FileHandler fh;
		try {
			fh = new FileHandler("passes.log");
			fh.setFormatter(new SimpleFormatter());
			LOG.addHandler(fh);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void main(String[] args) {
		try {		
			if(args.length != 2) {
				LOG.info("Invalid number of arguments");
				printHelp();
				return;
			}

			// Input parameters
			String inFilePath = args[0];
			File inFile = new File(inFilePath);

			String outDirPath = args[1];
			File outDir = new File(outDirPath);
			
			
			// Check parameters
			if(!inFile.exists() || !inFile.isFile()) {
				LOG.severe("Invalid input file given: " + inFilePath);
				printHelp();
				return;
			}
			
			if(!outDir.exists() || !outDir.isDirectory()) {
				LOG.severe("Invalid output directory given: " + outDirPath);
				printHelp();
				return;
			}
			
			LOG.info("In: " + inFilePath);
			LOG.info("Out: " + outDirPath);

			
			// Start preprocessing
			LOG.info("Starting preprocessing passes");
			
			OsmAppPreprocessorPass1.doPass(inFilePath, outDirPath);
			OsmAppPreprocessorPass2.doPass(inFilePath, outDirPath);
			OsmAppPreprocessorPass3.doPass(outDirPath);
			OsmAppPreprocessorPass4.doPass(outDirPath);
			OsmAppPreprocessorPass5.doPass(outDirPath);
			OsmAppPreprocessorPass6.doPass(outDirPath);
		} catch (Exception e) {
			LOG.severe("Failure at main");
			LOG.log(Level.SEVERE, "Exception", e);
		}
	}

	
	private static void printHelp() {
		System.out.println("Usage: [InputFile] [Output Directory]");
	}
}
