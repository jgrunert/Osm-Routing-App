package de.jgrunert.osm_routing;

import java.util.List;

import org.mapsforge.core.model.LatLong;


@SuppressWarnings("javadoc")
public interface IRouteSolver {
    
    public enum RoutingMode { Fastest, Shortest }

    public enum TransportMode { Car, Pedestrian, Maniac }
    
    public enum RoutingState { NotReady, Standby, Routing, Reconstructing, Canceling }
    
    
    void setStartNode(long nodeGridIndex);
    void setTargetNode(long nodeGridIndex);

    Long getStartNode();
    Long getTargetNode();

    LatLong getStartCoordinate();
    LatLong getTargetCoordinate();

    RoutingState getRoutingState();
    float getRoutingProgress();
    List<LatLong> getCalculatedRoute();

    Long findNextNode(float lat, float lon);
    Long findNextNode(float lat, float lon, byte filterBitMask, byte filterBitValue);
    
    void startCalculateRoute(TransportMode transportMode, RoutingMode routeMode);

    boolean isDoFastFollow();
    void setDoFastFollow(boolean doFastFollow);
    
    boolean isDoMotorwayBoost();
    void setDoMotorwayBoost(boolean doMotorwayBoost);


    void requestCancelRouting();
}
