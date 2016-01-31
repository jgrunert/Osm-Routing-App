package de.jgrunert.osm_routing;

import android.os.Environment;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

@SuppressWarnings("javadoc")
public class Utils {

    static final File CacheDir = new File(Environment.getExternalStorageDirectory(), "osm");

    static final File SinCosCacheFile = new File(CacheDir, "SinCosCache.bin");

    static final double Pi2 = Math.PI * 2.0;
    static final double sinCosPrecFactor = 200000;
    static final int sinCosLUTSize = (int)(Pi2 * sinCosPrecFactor) + 1; // +1 probably not necessary
    static double[] sinLUT = new double[sinCosLUTSize];
    static double[] cosLUT = new double[sinCosLUTSize];

    static {

        if(SinCosCacheFile.exists()) {

            ObjectInputStream sinCosReader = null;
            try {
                sinCosReader = new ObjectInputStream(
                        new BufferedInputStream(
                                new FileInputStream(SinCosCacheFile)));
                sinLUT = (double[]) sinCosReader.readObject();
                cosLUT = (double[]) sinCosReader.readObject();
                System.out.println("Loaded sin/cos LUT");
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if(sinCosReader != null) {
                    try {
                        sinCosReader.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        else {
            System.out.println("Start calculating sin/cos LUT");

            for(int iRad = 0; iRad < sinCosLUTSize; iRad++) {

                double rad = (double)iRad / sinCosPrecFactor;

                sinLUT[iRad] = Math.sin(rad);
                cosLUT[iRad] = Math.cos(rad);
            }

            System.out.println("Finished calculating sin/cos LUT");

            ObjectOutputStream sinCosWriter = null;
            try {
                sinCosWriter = new ObjectOutputStream(
                        new BufferedOutputStream(
                                new FileOutputStream(SinCosCacheFile)));
                sinCosWriter.writeObject(sinLUT);
                sinCosWriter.writeObject(cosLUT);
                System.out.println("Finished saving sin/cos LUT");
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if(sinCosWriter != null) {
                    try {
                        sinCosWriter.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    public static double fastSin(double rad) {
        return sinLUT[(int)(rad * sinCosPrecFactor)];
    }

    public static double fastCos(double rad) {
        return sinLUT[(int)(rad * sinCosPrecFactor)];
    }

    static final File DistCacheFile = new File(CacheDir, "DistCache.bin");

    static final double distLutMax = Math.sqrt(0.005);
    static final double distLutPrec = 10000000;
    //static final double factorInv = 1.0f / 100000000;
    static float[] distLUT = new float[(int)(distLutMax * distLutPrec)];

    static {
        if (DistCacheFile.exists()) {

            ObjectInputStream distCacheReader = null;
            try {
                distCacheReader = new ObjectInputStream(new BufferedInputStream(new FileInputStream(DistCacheFile)));
                distLUT = (float[]) distCacheReader.readObject();
                System.out.println("Loaded atan LUT");
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (distCacheReader != null) {
                    try {
                        distCacheReader.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        } else {

            double earthRadius = 6371000; //meters

            System.out.println("Start calculating atan LUT");
            for (int i = 0; i < distLUT.length; i++) {

                double a = Math.pow((double) i / distLutPrec, 2);

                double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

                distLUT[i] = (float) (earthRadius * c);
            }
            System.out.println("Finished calculating atan LUT");


            ObjectOutputStream distCacheWriter = null;
            try {
                distCacheWriter = new ObjectOutputStream(
                        new BufferedOutputStream(
                                new FileOutputStream(DistCacheFile)));
                distCacheWriter.writeObject(distLUT);
                System.out.println("Finished saving atan LUT");
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if(distCacheWriter != null) {
                    try {
                        distCacheWriter.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }




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

        float dist = distLUT[(int)(Math.sqrt(a)*distLutPrec)];

        return dist;
    }
}
