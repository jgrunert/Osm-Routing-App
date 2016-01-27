package jgrunert.osm_routing_app;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class ByteBufferTest {

	public static void main(String[] args) {

		String outDir = "D:\\Jonas\\OSM\\germany";
		//String outDir = "D:\\Jonas\\OSM\\hamburg";
		//String outDir = "D:\\Jonas\\OSM\\bawue";
		
	    int nodeCount = 0;
	    float[] nodesLat = null;
	    float[] nodesLon = null;
	    int[] nodesEdgeOffset = null;
	    long start;
		
		try {
			// Load nodes
			start = System.currentTimeMillis();
			ObjectInputStream nodeReader = new ObjectInputStream(new BufferedInputStream(
					new FileInputStream(outDir + "\\pass4-nodes.bin")));

			nodeCount = (Integer) nodeReader.readObject();
			nodesLat = (float[]) nodeReader.readObject();
			nodesLon = (float[]) nodeReader.readObject();
			nodesEdgeOffset = (int[]) nodeReader.readObject();

			nodeReader.close();
			OsmAppPreprocessor.LOG.info("Finished reading 1 in " + (System.currentTimeMillis() - start) + "ms");
			
			

//			start = System.currentTimeMillis();
//			ByteBuffer nodesLatBuffer = ByteBuffer.allocate(nodesLat.length * 4);
//			nodesLatBuffer.asFloatBuffer().put(nodesLat);
//
//			ByteBuffer nodesLonBuffer = ByteBuffer.allocate(nodesLon.length * 4);
//			nodesLonBuffer.asFloatBuffer().put(nodesLon);
//			
//			ByteBuffer nodesEdgeOffsetBuffer = ByteBuffer.allocate(nodesEdgeOffset.length * 4);
//			nodesEdgeOffsetBuffer.asIntBuffer().put(nodesEdgeOffset);
//			
//		    //float[] nodesLat2 = nodesLatBuffer.asFloatBuffer().array();
//			
//			FileChannel channel = new FileOutputStream(outDir + "\\iotest.bin", false).getChannel();			
//			channel.write(nodesLatBuffer);
//			channel.write(nodesLonBuffer);
//			channel.write(nodesEdgeOffsetBuffer);		
//			System.out.println(nodesLatBuffer.position());
//			channel.close();
//		    
//			OsmAppPreprocessor.LOG.info("Finished X in " + (System.currentTimeMillis() - start) + "ms");
			
			
			

			start = System.currentTimeMillis();
			ByteBuffer nodesOutBuffer = ByteBuffer.allocate(nodesLat.length * 4 * 3 + 4);
			
			nodesOutBuffer.asIntBuffer().put(nodeCount);
			nodesOutBuffer.asFloatBuffer().put(nodesLat);
			nodesOutBuffer.asFloatBuffer().put(nodesLon);			
			nodesOutBuffer.asIntBuffer().put(nodesEdgeOffset);
			
		    //float[] nodesLat2 = nodesLatBuffer.asFloatBuffer().array();
			
			FileChannel channel = new FileOutputStream(outDir + "\\iotest2.bin", false).getChannel();			
			channel.write(nodesOutBuffer);
			channel.close();
		    
			OsmAppPreprocessor.LOG.info("Finished X in " + (System.currentTimeMillis() - start) + "ms");
			
			
			

			// Seems to be only a bit faster than array deserialize
			start = System.currentTimeMillis();
		    //RandomAccessFile aFile = new RandomAccessFile
	        //        ("test.txt", "r");
	        //FileChannel inChannel = aFile.getChannel();
			FileChannel inChannel = new FileInputStream(outDir + "\\iotest2.bin").getChannel();
	        MappedByteBuffer buffer = inChannel.map(FileChannel.MapMode.READ_ONLY, 0, inChannel.size());
	        buffer.load(); 
	        System.out.println(buffer.position());
	        
	        float[] nodesLat2 = new float[nodeCount];
	        float[] nodesLon2 = new float[nodeCount];
	        int[] nodesEdgeOffset2 = new int[nodeCount];
	        buffer.asFloatBuffer().get(nodesLat2, 0, nodeCount);
	        buffer.asFloatBuffer().get(nodesLon2, 0, nodeCount);
	        buffer.asIntBuffer().get(nodesEdgeOffset2, 0, nodeCount);
	        
	        
	        buffer.clear(); // do something with the data and clear/compact it.
	        inChannel.close();
	        //aFile.close();
			OsmAppPreprocessor.LOG.info("Finished Y in " + (System.currentTimeMillis() - start) + "ms");
			
			
			// Seems to be not faster than array deserialize
//			start = System.currentTimeMillis();
//		    //RandomAccessFile aFile = new RandomAccessFile
//	        //        ("test.txt", "r");
//	        //FileChannel inChannel = aFile.getChannel();
//			FileChannel inChannel = new FileInputStream(outDir + "\\iotest2.bin").getChannel();
//			System.out.println("inChannel: " + inChannel.size());
//			ByteBuffer inBuf = ByteBuffer.allocate((int)inChannel.size());
//			inChannel.read(inBuf);
//			
//			//inBuf.rewind();
//			inBuf.position(4);
//
//			//nodeCount2 = (Integer) nodeReader.readObject();
//	        float[] nodesLat2 = new float[nodeCount];
//			inBuf.asFloatBuffer().get(nodesLat2, 0, nodeCount);
//			//nodesLon2 = (float[]) nodeReader.readObject();
//			//nodesEdgeOffset2 = (int[]) nodeReader.readObject();
//
//	        inChannel.close();
//	        
//	        //aFile.close();
//			OsmAppPreprocessor.LOG.info("Finished Y in " + (System.currentTimeMillis() - start) + "ms");
			 
		} catch (Exception exc) {
			exc.printStackTrace();
		}

	}

}
