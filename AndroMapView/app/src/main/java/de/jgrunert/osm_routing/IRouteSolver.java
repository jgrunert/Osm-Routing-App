package de.jgrunert.osm_routing;

import java.util.List;

import org.mapsforge.core.model.LatLong;


@SuppressWarnings("javadoc")
public interface IRouteSolver {
    
    public enum RoutingMode { Fastest, Shortest }

    public enum TransportMode { Car, Pedestrian, Maniac }
    
    public enum RoutingState { NotReady, Standby, Routing }
    
    
    void setStartNode(long nodeGridIndex);
    void setTargetNode(long nodeGridIndex);

    LatLong getStartCoordinate();
    LatLong getTargetCoordinate();

    RoutingState getRoutingState();
    List<LatLong> getRoutingPreviewDots();
    List<LatLong> getCalculatedRoute();
    
    boolean getNeedsDispalyRefresh();
    void resetNeedsDispalyRefresh();

    Long findNextNode(float lat, float lon, byte filterBitMask, byte filterBitValue);
    
    void startCalculateRoute(TransportMode transportMode, RoutingMode routeMode);

    LatLong getBestCandidateCoords();
    
    boolean isDoFastFollow();
    void setDoFastFollow(boolean doFastFollow);
    
    boolean isDoMotorwayBoost();
    void setDoMotorwayBoost(boolean doMotorwayBoost);
}
