// License: GPL. For details, see Readme.txt file.
package de.jgrunert.osm_routing;

import java.awt.Color;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.openstreetmap.gui.jmapviewer.Coordinate;
import org.openstreetmap.gui.jmapviewer.JMapController;
import org.openstreetmap.gui.jmapviewer.JMapViewer;
import org.openstreetmap.gui.jmapviewer.MapMarkerDot;
import org.openstreetmap.gui.jmapviewer.MapPolygonImpl;
import org.openstreetmap.gui.jmapviewer.interfaces.ICoordinate;


/**
 * Default map controller which implements map moving by pressing the right
 * mouse button and zooming by float click or by mouse wheel.
 *
 * @author Jan Peter Stotz
 *
 */
@SuppressWarnings("javadoc")
public class OsmRoutingMapController extends JMapController implements MouseListener, MouseMotionListener,
MouseWheelListener {

    private static final int MOUSE_BUTTONS_MASK = MouseEvent.BUTTON3_DOWN_MASK | MouseEvent.BUTTON1_DOWN_MASK
    | MouseEvent.BUTTON2_DOWN_MASK;

    private static final int MAC_MOUSE_BUTTON3_MASK = MouseEvent.CTRL_DOWN_MASK | MouseEvent.BUTTON1_DOWN_MASK;

    private Point lastDragPoint;

    private boolean isMoving = false;

    private boolean movementEnabled = true;

    private int movementMouseButton = MouseEvent.BUTTON1;
    private int movementMouseButtonMask = MouseEvent.BUTTON1_DOWN_MASK;

    private boolean wheelZoomEnabled = true;
    private boolean floatClickZoomEnabled = true;
    
    private final IRouteSolver routeSolver;
    
            
        
    
   /**
    * Constructor
    * @param map JMapViewer
    */
    public OsmRoutingMapController(JMapViewer map) {
        super(map);
        
        routeSolver = new AStarRouteSolver();
    }
    
    
    

    @Override
    public void mouseDragged(MouseEvent e) {
        if (!movementEnabled || !isMoving)
            return;
        // Is only the selected mouse button pressed?
        if ((e.getModifiersEx() & MOUSE_BUTTONS_MASK) == movementMouseButtonMask 
                || isPlatformOsx() && e.getModifiersEx() == MAC_MOUSE_BUTTON3_MASK) {
            Point p = e.getPoint();
            if (lastDragPoint != null) {
                int diffx = lastDragPoint.x - p.x;
                int diffy = lastDragPoint.y - p.y;
                map.moveMap(diffx, diffy);
            }
            lastDragPoint = p;
        }
    }

    @Override
    public void mouseClicked(MouseEvent e) {
       
        if (e.getClickCount() == 1) {
            // Waypoint selection
            ICoordinate clickPt = map.getPosition(e.getPoint());
            Coordinate clickCoord = new Coordinate(clickPt.getLat(), clickPt.getLon());
            
            Long clickNextPt = routeSolver.findNextNode((float)clickCoord.getLat(), (float)clickCoord.getLon(), (byte)0, (byte)0);            
            if(clickNextPt == null) {
                System.err.println("No point found");
                return;
            }
            
            if(e.getButton() == MouseEvent.BUTTON1) {
                routeSolver.setStartNode(clickNextPt);
            } 
            else if(e.getButton() == MouseEvent.BUTTON3) {
                routeSolver.setTargetNode(clickNextPt);
            } 
            
//            clearMarkers();
//            if(startNodeGridIndex != null) {
//                MapMarkerDot startDot = new MapMarkerDot("Start", getNodeCoordinates(startNodeGridIndex));
//                map.addMapMarker(startDot);
//                routeDots.add(startDot);
//            }        
//            if(targetNodeGridIndex != null) {
//                MapMarkerDot targetDot = new MapMarkerDot("Target", getNodeCoordinates(targetNodeGridIndex));
//                map.addMapMarker(targetDot);
//                routeDots.add(targetDot);
//            }
        } 
        else if (floatClickZoomEnabled && e.getClickCount() == 2 && e.getButton() == MouseEvent.BUTTON1) {
            // Zoom on floatclick
            map.zoomIn(e.getPoint());
        } 
    }
    
    

    @Override
    public void mousePressed(MouseEvent e) {
        if (e.getButton() == movementMouseButton || isPlatformOsx() && e.getModifiersEx() == MAC_MOUSE_BUTTON3_MASK) {
            lastDragPoint = null;
            isMoving = true;
        }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        if (e.getButton() == movementMouseButton || isPlatformOsx() && e.getButton() == MouseEvent.BUTTON1) {
            lastDragPoint = null;
            isMoving = false;
        }
    }

    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
        if (wheelZoomEnabled) {
            map.setZoom(map.getZoom() - e.getWheelRotation(), e.getPoint());
        }
    }

    public boolean isMovementEnabled() {
        return movementEnabled;
    }

    /**
     * Enables or disables that the map pane can be moved using the mouse.
     *
     * @param movementEnabled {@code true} to allow the map pane to be moved using the mouse
     */
    public void setMovementEnabled(boolean movementEnabled) {
        this.movementEnabled = movementEnabled;
    }

    public int getMovementMouseButton() {
        return movementMouseButton;
    }

    /**
     * Sets the mouse button that is used for moving the map. Possible values are:
     * <ul>
     * <li>{@link MouseEvent#BUTTON1} (left mouse button)</li>
     * <li>{@link MouseEvent#BUTTON2} (middle mouse button)</li>
     * <li>{@link MouseEvent#BUTTON3} (right mouse button)</li>
     * </ul>
     *
     * @param movementMouseButton the mouse button that is used for moving the map
     */
    public void setMovementMouseButton(int movementMouseButton) {
        this.movementMouseButton = movementMouseButton;
        switch (movementMouseButton) {
            case MouseEvent.BUTTON1:
                movementMouseButtonMask = MouseEvent.BUTTON1_DOWN_MASK;
                break;
            case MouseEvent.BUTTON2:
                movementMouseButtonMask = MouseEvent.BUTTON2_DOWN_MASK;
                break;
            case MouseEvent.BUTTON3:
                movementMouseButtonMask = MouseEvent.BUTTON3_DOWN_MASK;
                break;
            default:
                throw new RuntimeException("Unsupported button");
        }
    }

    public boolean isWheelZoomEnabled() {
        return wheelZoomEnabled;
    }

    public void setWheelZoomEnabled(boolean wheelZoomEnabled) {
        this.wheelZoomEnabled = wheelZoomEnabled;
    }

    public boolean isFloatClickZoomEnabled() {
        return floatClickZoomEnabled;
    }

    public void setFloatClickZoomEnabled(boolean floatClickZoomEnabled) {
        this.floatClickZoomEnabled = floatClickZoomEnabled;
    }

    @Override
    public void mouseEntered(MouseEvent e) {
    }

    @Override
    public void mouseExited(MouseEvent e) {
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        // Mac OSX simulates with  ctrl + mouse 1  the second mouse button hence no dragging events get fired.
        //
        if (isPlatformOsx()) {
            if (!movementEnabled || !isMoving)
                return;
            // Is only the selected mouse button pressed?
            if (e.getModifiersEx() == MouseEvent.CTRL_DOWN_MASK) {
                Point p = e.getPoint();
                if (lastDragPoint != null) {
                    int diffx = lastDragPoint.x - p.x;
                    int diffy = lastDragPoint.y - p.y;
                    map.moveMap(diffx, diffy);
                }
                lastDragPoint = p;
            }

        }

    }

    /**
     * Replies true if we are currently running on OSX
     *
     * @return true if we are currently running on OSX
     */
    public static boolean isPlatformOsx() {
        String os = System.getProperty("os.name");
        return os != null && os.toLowerCase().startsWith("mac os x");
    }




    public IRouteSolver getRouteSolver() {
        return routeSolver;
    }
    
    
    
    
    
    
    
    
    
//    public void calculateRouteDijkstra(TransportMode transportMode, RoutingMode routeMode) {
//
//        if (startNodeIndex == -1 || targetNodeIndex == -1) {
//            System.err.println("Cannot calculate route: Must select start and target");
//            return;
//        }
//         
//        // Edge bitfilter and speed
//        final byte edgeFilterBitMask;
//        final byte edgeFilterBitValue;  
//        final int allMaxSpeed;
//        final int allMinSpeed = PED_MAXSPEED;
//        
//        switch (transportMode) {
//        case Car:
//            edgeFilterBitMask = carBitMask;
//            edgeFilterBitValue = carBitValue;
//            allMaxSpeed = CAR_MAXSPEED;
//            break;
//        case Pedestrian:
//            edgeFilterBitMask = pedBitMask;
//            edgeFilterBitValue = pedBitValue;
//            allMaxSpeed = PED_MAXSPEED;
//            break;
//        default:
//            edgeFilterBitMask = 0;
//            edgeFilterBitValue = 0;
//            allMaxSpeed = CAR_MAXSPEED;
//            break;
//        }
//        
//        
//        Coordinate startCoord = new Coordinate(nodesLat[startNodeIndex], nodesLon[startNodeIndex]);
//        Coordinate targetCoord = new Coordinate(nodesLat[targetNodeIndex], nodesLon[targetNodeIndex]);
//        
//        // Find better start and end points if not suitable
//        if(!checkNodeWithFilter(startNodeIndex, edgeFilterBitMask, edgeFilterBitValue)) {
//            startNodeIndex = findNextNode((float)startCoord.getLat(), (float)startCoord.getLon(), edgeFilterBitMask, edgeFilterBitValue);
//            startCoord = new Coordinate(nodesLat[startNodeIndex], nodesLon[startNodeIndex]);
//        }
//        if(!checkNodeWithFilter(targetNodeIndex, edgeFilterBitMask, edgeFilterBitValue)) {
//            targetNodeIndex = findNextNode((float)targetCoord.getLat(), (float)targetCoord.getLon(), edgeFilterBitMask, edgeFilterBitValue);
//            targetCoord = new Coordinate(nodesLat[targetNodeIndex], nodesLon[targetNodeIndex]);
//        }   
//
//        // Clear markers
//        clearMarkers();
//        // Draw start and target
//        {
//            MapMarkerDot startDot =
//                    new MapMarkerDot("Start", startCoord);
//            map.addMapMarker(startDot);
//            routeDots.add(startDot);
//            MapMarkerDot targetDot =
//                    new MapMarkerDot("Target", targetCoord);
//            map.addMapMarker(targetDot);
//            routeDots.add(targetDot);
//        }
//
//        
//        Random rd = new Random(123);
//        float debugDispProp = 0.9999f;
//        int maxMarkerCountdown = 50;
//
//        
//        // Reset buffers
//        routeDistHeap.resetFill(nodeCount);
//        Arrays.fill(nodesRouteClosedList, false);
//
//        
//        // Start node
//        routeDistHeap.decreaseKey(startNodeIndex, 0);
//        nodesRouteClosedList[startNodeIndex] = true;
//
//        int visitedCount = 0;
//        boolean found = false;
//
//        // Find route with Dijkstra
//        while (!routeDistHeap.isEmpty()) {
//            // Get dist, remove and get index
//            float nodeDist = routeDistHeap.peekNodeValue();
//
//            // Break if node not visited yet
//            if (nodeDist == Integer.MAX_VALUE) {
//                System.err.println("Node with no distance set found, break after " + visitedCount + " nodes visited, "
//                        + routeDistHeap.getSize() + " nodes not visited");
//                break;
//            }
//
//            //System.out.println(nodeDist);
//
//            // Remove and get index
//            int nodeIndex = routeDistHeap.remove();
//
//            if (nodeIndex == targetNodeIndex) {
//                System.out.println("Found after " + visitedCount + " nodes visited. " + routeDistHeap.getSize() + " nodes not visited");
//                System.out.println("Dist: " + nodeDist);
//                found = true;
//                break;
//            } else {
//                //System.out.println("Not found");
//            }
//
//            // Mark as visited
//            nodesRouteClosedList[nodeIndex] = true;
//            visitedCount++;
//
//            // Display
//            if (maxMarkerCountdown > 0 && rd.nextFloat() > debugDispProp) {
//                MapMarkerDot dot = new MapMarkerDot(new Coordinate(nodesLat[nodeIndex], nodesLon[nodeIndex]));
//                map.addMapMarker(dot);
//                routeDots.add(dot);
//                maxMarkerCountdown--;
//            }
//            //System.out.println(nextIndex);
//
//            // Iterate over edges to neighbors
//            for (int iEdge = nodesEdgeOffset[nodeIndex]; (nodeIndex + 1 < nodesEdgeOffset.length && iEdge < nodesEdgeOffset[nodeIndex + 1])
//                    || (nodeIndex + 1 == nodesEdgeOffset.length && iEdge < edgeCount); // Last node in offset array
//                    iEdge++) {
//                int nbIndex = edgesTarget[iEdge];
//
//                // Skip if edge not accessible
//                if ((edgesInfobits[iEdge] & edgeFilterBitMask) != edgeFilterBitValue) {
//                    continue;
//                }
//
//                // Distance calculation, depending on routing mode
//                float nbDist;
//                float edgeDist = edgesLengths[iEdge];
//                if (routeMode == RoutingMode.Fastest) {
//                    float maxSpeed = (int) Byte.toUnsignedLong(edgesMaxSpeeds[iEdge]);
//                    maxSpeed = Math.max(allMinSpeed, Math.min(allMaxSpeed, maxSpeed));
//                    nbDist = nodeDist + (edgeDist / maxSpeed);
//                } else if (routeMode == RoutingMode.Shortest) {
//                    nbDist = nodeDist + edgeDist;
//                } else {
//                    throw new RuntimeException("Unsupported routing mode: " + routeMode);
//                }
//
//                if (!nodesRouteClosedList[nbIndex]) {
//                    if (routeDistHeap.decreaseKeyIfSmaller(nbIndex, nbDist)) {
//                        nodesPreBuffer[nbIndex] = nodeIndex;
//                    }
//                } else {
//                    //System.out.println("Already visited");
//                }
//            }
//        }
//
//        if (found) {
//            // Reconstruct route
//            int i = targetNodeIndex;
//            while (i != startNodeIndex) {
//                int pre = nodesPreBuffer[i];
//                //                if (pre == targetIndex) {
//                //                    continue;
//                //                }
//
//                Coordinate c1 = new Coordinate(nodesLat[pre], nodesLon[pre]);
//                Coordinate c2 = new Coordinate(nodesLat[i], nodesLon[i]);
//
//                MapPolygonImpl routPoly = new MapPolygonImpl(c1, c2, c2);
//                routeLines.add(routPoly);
//                map.addMapPolygon(routPoly);
//
//                //                MapMarkerDot dot = new MapMarkerDot(new Coordinate(nodesLat[i], nodesLon[i]));
//                //                map.addMapMarker(dot);
//                //                routeDots.add(dot);
//
//                i = pre;
//            }
//        } else {
//            System.err.println("No way found");
//        }
//    }
}
