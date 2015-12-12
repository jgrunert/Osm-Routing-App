package de.jgrunert.osm_routing;

@SuppressWarnings("javadoc")
public class Utils {
    
    
    //http://riven8192.blogspot.de/2009/08/fastmath-atan2-lookup-table.html
//    public static final float atan2Fast(float y, float x) {
//        float add, mul;
//
//        if (x < 0.0f) {
//            if (y < 0.0f) {
//                x = -x;
//                y = -y;
//
//                mul = 1.0f;
//            } else {
//                x = -x;
//                mul = -1.0f;
//            }
//
//            add = -3.141592653f;
//        } else {
//            if (y < 0.0f) {
//                y = -y;
//                mul = -1.0f;
//            } else {
//                mul = 1.0f;
//            }
//
//            add = 0.0f;
//        }
//
//        float invDiv = ATAN2_DIM_MINUS_1 / ((x < y) ? y : x);
//
//        int xi = (int) (x * invDiv);
//        int yi = (int) (y * invDiv);
//
//        return (atan2[yi * ATAN2_DIM + xi] + add) * mul;
//    }
//
//    private static final int ATAN2_BITS = 7;
//
//    private static final int ATAN2_BITS2 = ATAN2_BITS << 1;
//    private static final int ATAN2_MASK = ~(-1 << ATAN2_BITS2);
//    private static final int ATAN2_COUNT = ATAN2_MASK + 1;
//    private static final int ATAN2_DIM = (int) Math.sqrt(ATAN2_COUNT);
//
//    private static final float ATAN2_DIM_MINUS_1 = (ATAN2_DIM - 1);
//
//    private static final float[] atan2 = new float[ATAN2_COUNT];
//
//    static {
//        for (int i = 0; i < ATAN2_DIM; i++) {
//            for (int j = 0; j < ATAN2_DIM; j++) {
//                float x0 = (float) i / ATAN2_DIM;
//                float y0 = (float) j / ATAN2_DIM;
//
//                atan2[j * ATAN2_DIM + i] = (float) Math.atan2(y0, x0);
//            }
//        }
//    }
//    
    

    // From http://stackoverflow.com/questions/837872/calculate-distance-in-meters-when-you-know-longitude-and-latitude-in-java
    public static float calcNodeDist(float lat1, float lon1, float lat2, float lon2) {
        double earthRadius = 6371000; //meters
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lon2 - lon1);
        double a =
                Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(Math.toRadians(lat1))
                        * Math.cos(Math.toRadians(lat2)) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return (float) (earthRadius * c);
    }

    
    // From http://stackoverflow.com/questions/837872/calculate-distance-in-meters-when-you-know-longitude-and-latitude-in-java
//    public static float calcNodeDistFast(float lat1, float lon1, float lat2, float lon2) {
//        double earthRadius = 6371000; //meters
//        double dLat = Math.toRadians(lat2 - lat1);
//        double dLng = Math.toRadians(lon2 - lon1);
//        double a =
//                Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(Math.toRadians(lat1))
//                        * Math.cos(Math.toRadians(lat2)) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
//        double c = 2 * atan2Fast((float) Math.sqrt(a), (float) Math.sqrt(1 - a));
//        return (float) (earthRadius * c);
//    }
    // 5 times slower
    //  private float getNodeDist(float lat1, float lon1, float lat2, float lon2) {
    //  GeodesicData g = Geodesic.WGS84.Inverse(lat1, lon1, lat2, lon2);
    //  return g.s12;
    //}
}
