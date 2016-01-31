package de.jgrunert.osm_routing;

@SuppressWarnings("javadoc")
public class Utils {
    
    
    //http://riven8192.blogspot.de/2009/08/fastmath-atan2-lookup-table.html
//    private static final int ATAN2_BITS = 7;
//
//    private static final int ATAN2_BITS2 = ATAN2_BITS << 1;
//    private static final int ATAN2_MASK = ~(-1 << ATAN2_BITS2);
//    private static final int ATAN2_COUNT = ATAN2_MASK + 1;
//    private static final int ATAN2_DIM = (int) Math.sqrt(ATAN2_COUNT);
//
//    private static final float INV_ATAN2_DIM_MINUS_1 = 1.0f / (ATAN2_DIM - 1);
//    private static final float DEG = 180.0f / (float) Math.PI;
//
//    private static final float[] atan2 = new float[ATAN2_COUNT];
//
//
//
//    static
//    {
//       for (int i = 0; i < ATAN2_DIM; i++)
//       {
//          for (int j = 0; j < ATAN2_DIM; j++)
//          {
//             float x0 = (float) i / ATAN2_DIM;
//             float y0 = (float) j / ATAN2_DIM;
//
//             atan2[j * ATAN2_DIM + i] = (float) Math.atan2(y0, x0);
//          }
//       }
//    }
//
//
//    /**
//     * ATAN2
//     */
//
//    public static final float atan2Deg(float y, float x)
//    {
//       return atan2Fast(y, x) * DEG;
//    }
//
//    public static final float atan2DegStrict(float y, float x)
//    {
//       return (float) Math.atan2(y, x) * DEG;
//    }
//
//    public static final float atan2Fast(float y, float x)
//    {
//       float add, mul;
//
//       if (x < 0.0f)
//       {
//          if (y < 0.0f)
//          {
//             x = -x;
//             y = -y;
//
//             mul = 1.0f;
//          }
//          else
//          {
//             x = -x;
//             mul = -1.0f;
//          }
//
//          add = -3.141592653f;
//       }
//       else
//       {
//          if (y < 0.0f)
//          {
//             y = -y;
//             mul = -1.0f;
//          }
//          else
//          {
//             mul = 1.0f;
//          }
//
//          add = 0.0f;
//       }
//
//       float invDiv = 1.0f / (((x < y) ? y : x) * INV_ATAN2_DIM_MINUS_1);
//
//       int xi = (int) (x * invDiv);
//       int yi = (int) (y * invDiv);
//
//       return (atan2[yi * ATAN2_DIM + xi] + add) * mul;
//    }
    

    static final double Pi2 = Math.PI * 2.0;
    static final double sinCosPrecFactor = 1000000;
    static final int sinCosLUTSize = (int)(Pi2 * sinCosPrecFactor) + 1; // +1 probably not necessary
    static final double[] sinLUT = new double[sinCosLUTSize];
    static final double[] cosLUT = new double[sinCosLUTSize];
    
    static {

      System.out.println("Start calculating sin/cos LUT");
      
      for(int iRad = 0; iRad < sinCosLUTSize; iRad++) {

         double rad = (double)iRad / sinCosPrecFactor;
              
         sinLUT[iRad] = Math.sin(rad);
         cosLUT[iRad] = Math.cos(rad);
      }
      
      System.out.println("Finished calculating sin/cos LUT");
    }
    
    public static double fastSin(double rad) {
        return sinLUT[(int)(rad * sinCosPrecFactor)];
    }
    
    public static double fastCos(double rad) {
        return sinLUT[(int)(rad * sinCosPrecFactor)];
    }
    
    
    static final double distLutMax = Math.sqrt(0.005);
    static final double distLutPrec = 10000000;
    //static final double factorInv = 1.0f / 100000000;
    static final float[] distLUT = new float[(int)(distLutMax * distLutPrec)];
    
    static {
        double earthRadius = 6371000; //meters
        
        System.out.println("Start calculating atan LUT");
        for(int i = 0; i < distLUT.length; i++) {
            
            double a = Math.pow((double)i / distLutPrec, 2); 
            
            double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
            //double c = 2 * atan2Fast((float) Math.sqrt(a), (float) Math.sqrt(1 - a));
            
//            sum += System.nanoTime() - beg;           
//            if(sum > 800000000) {
//                System.out.println("ATAN");
//            }
            
            distLUT[i] = (float) (earthRadius * c);
        }
        System.out.println("Finished calculating atan LUT");
    }
    
    
    
    
    
//  static double min = Double.MAX_VALUE;
//  static double max = Double.MIN_VALUE;
    
//    static long sum;
//    static long invocs;
    
    
    
    
    public static float calcNodeDistPrecise(float lat1, float lon1, float lat2, float lon2) {
      double earthRadius = 6371000; //meters
              
      double dLat = Math.toRadians(lat2 - lat1);
      //System.out.println((lat2 - lat1) + " - " + dLat);
      double dLng = Math.toRadians(lon2 - lon1);

      
      double a =
              Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(Math.toRadians(lat1))
                      * Math.cos(Math.toRadians(lat2)) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
      
      double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
      float dist = (float) (earthRadius * c);
        
      return dist;
  }

 
    
    
    // From http://stackoverflow.com/questions/837872/calculate-distance-in-meters-when-you-know-longitude-and-latitude-in-java
    public static float calcNodeDistFast(float lat1, float lon1, float lat2, float lon2) {
//        long beg = System.nanoTime() ;
//        invocs++;
  
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lon2 - lon1);
        
        while(dLat > Pi2) {
            dLat -= Pi2;
        }
        while(dLat < 0) {
            dLat += Pi2;
        }
        while(dLng > Pi2) {
            dLng -= Pi2;
        }
        while(dLng < 0) {
            dLng += Pi2;
        }
        
        double lat1Rad = Math.toRadians(lat1);
        double lat2Rad = Math.toRadians(lat2);

        while(lat1Rad > Pi2) {
            lat1Rad -= Pi2;
        }
        while(lat1Rad < 0) {
            lat1Rad += Pi2;
        }
        while(lat2Rad > Pi2) {
            lat2Rad -= Pi2;
        }
        while(lat2Rad < 0) {
            lat2Rad += Pi2;
        }
        
        double a =
                fastSin(dLat / 2) * fastSin(dLat / 2) + fastCos(lat1Rad)
                        * fastCos(lat2Rad) * fastSin(dLng / 2) * fastSin(dLng / 2);
//        double a =
//                Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(Math.toRadians(lat1))
//                        * Math.cos(Math.toRadians(lat2)) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
//        System.out.println(a + "  " + a2); 
        
        
        
//        if(a < min) {
//            min = a;
//            System.out.println("min: " + min);
//        }
//        if(a > max) {
//            max = a;
//            System.out.println("max: " + max);
//        }
        
        //double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        
        //double c = 2 * atan2Fast((float) Math.sqrt(a), (float) Math.sqrt(1 - a));
        //float dist = (float) (earthRadius * c);
        float dist = distLUT[(int)(Math.sqrt(a)*distLutPrec)];
        //float dist2 = calcNodeDistPrecise(lat1, lon1, lat2, lon2);
        //System.out.println(dist + "  " + dist2);
           

        
//        float test1 = (float) (earthRadius * c);
//        float test2 = distLUT[(int)(a*factor)];
//        
//        System.out.println(test1 + " vs " + test2);


//        sum += System.nanoTime() - beg;        
//        if(sum > 200000000) {
//            System.out.println("DIST: " + (double)sum / invocs);
//            throw new RuntimeException();
//        }     

        return dist;
    }

    
    // From http://stackoverflow.com/questions/837872/calculate-distance-in-meters-when-you-know-longitude-and-latitude-in-java
//    public static float calcNodeDistFast(float lat1, float lon1, float lat2, float lon2) {
//        double earthRadius = 6371000; //meters
//        double dLat = Math.toRadians(lat2 - lat1);
//        double dLng = Math.toRadians(lon2 - lon1);
//        double a =
//                Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(Math.toRadians(lat1))
//                        * Math.cos(Math.toRadians(lat2)) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
//        double c = 2 * Math.atan2((float) Math.sqrt(a), (float) Math.sqrt(1 - a));
//        return (float) (earthRadius * c);
//    }
    
    
    
    // 5 times slower
    //  private float getNodeDist(float lat1, float lon1, float lat2, float lon2) {
    //  GeodesicData g = Geodesic.WGS84.Inverse(lat1, lon1, lat2, lon2);
    //  return g.s12;
    //}
}
