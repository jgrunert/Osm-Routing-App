package de.jgrunert.osm_routing;

import java.util.List;

import org.openstreetmap.gui.jmapviewer.Coordinate;


@SuppressWarnings("javadoc")
public interface IRouteSolver {
    
    public enum RoutingMode { Fastest, Shortest }

    public enum TransportMode { Car, Pedestrian, Maniac }
    
    public enum RoutingState { NotReady, Standby, Routing }
    

    void setStartNode(long nodeGridIndex);
    void setTargetNode(long nodeGridIndex);

    RoutingState getRoutingState();
    List<Coordinate> getRoutingPreviewDots();
    List<Coordinate> getCalculatedRoute();

    Long findNextNode(float lat, float lon, byte filterBitMask, byte filterBitValue);
    
    void startCalculateRoute(TransportMode transportMode, RoutingMode routeMode);
}
