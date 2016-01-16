package de.jgrunert;

/**
 * Created by Jonas on 16.01.2016.
 */
public class Point2D {
    public static class Double {
        public double x;
        public double y;

        public Double() {
        }

        public Double(double x, double y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Double aDouble = (Double) o;
            return x == aDouble.x && y == aDouble.y;
        }

        @Override
        public int hashCode() {
            int result;
            long temp;
            temp = java.lang.Double.doubleToLongBits(x);
            result = (int) (temp ^ (temp >>> 32));
            temp = java.lang.Double.doubleToLongBits(y);
            result = 31 * result + (int) (temp ^ (temp >>> 32));
            return result;
        }
    }
}
