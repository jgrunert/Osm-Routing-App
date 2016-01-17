// License: GPL. For details, see Readme.txt file.
package de.jgrunert.osm_routing;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;

import org.openstreetmap.gui.jmapviewer.Coordinate;
import org.openstreetmap.gui.jmapviewer.JMapViewer;
import org.openstreetmap.gui.jmapviewer.JMapViewerTree;
import org.openstreetmap.gui.jmapviewer.MapMarkerDot;
import org.openstreetmap.gui.jmapviewer.MapPolygonImpl;
import org.openstreetmap.gui.jmapviewer.OsmTileLoader;
import org.openstreetmap.gui.jmapviewer.events.JMVCommandEvent;
import org.openstreetmap.gui.jmapviewer.interfaces.JMapViewerEventListener;
import org.openstreetmap.gui.jmapviewer.interfaces.TileLoader;
import org.openstreetmap.gui.jmapviewer.interfaces.TileSource;
import org.openstreetmap.gui.jmapviewer.tilesources.BingAerialTileSource;
import org.openstreetmap.gui.jmapviewer.tilesources.MapQuestOpenAerialTileSource;
import org.openstreetmap.gui.jmapviewer.tilesources.MapQuestOsmTileSource;
import org.openstreetmap.gui.jmapviewer.tilesources.OsmTileSource;

import de.jgrunert.osm_routing.IRouteSolver.RoutingMode;
import de.jgrunert.osm_routing.IRouteSolver.RoutingState;
import de.jgrunert.osm_routing.IRouteSolver.TransportMode;

/**
 * Demonstrates the usage of {@link JMapViewer}
 *
 * @author Jan Peter Stotz
 *
 */
public class OsmRoutingMain extends JFrame implements JMapViewerEventListener  {

    private static final long serialVersionUID = 1L;

    private final JMapViewerTree treeMap;
    private final OsmRoutingMapController mapController;

    private final JLabel zoomLabel;
    private final JLabel zoomValue;

    private final JLabel mperpLabelName;
    private final JLabel mperpLabelValue;
    
    private final JLabel routeDistLabel;
    private final JLabel routeTimeLabel;
    
    private static final int MAX_ROUTE_PREVIEW_DOTS = 50;
    
    

    /**
     * Constructs the {@code Demo}.
     */
    public OsmRoutingMain() {
        super("JMapViewer Demo");
        setSize(400, 400);
        
        String cacheFolder = "D:\\Jonas\\OSM\\JMapViewerCache";
        boolean doCaching = true;
        
        treeMap = new JMapViewerTree("Zones", cacheFolder, doCaching);
        mapController = treeMap.getMapController();

        // Listen to the map viewer for user operations so components will
        // receive events and update
        map().addJMVListener(this);

        setLayout(new BorderLayout());
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        JPanel panel = new JPanel();
        JPanel panelTop = new JPanel();
        JPanel panelBottom = new JPanel();
        JPanel helpPanel = new JPanel();

        mperpLabelName = new JLabel("Meters/Pixels: ");
        mperpLabelValue = new JLabel(String.format("%s", map().getMeterPerPixel()));

        zoomLabel = new JLabel("Zoom: ");
        zoomValue = new JLabel(String.format("%s", map().getZoom()));

        add(panel, BorderLayout.NORTH);
        add(helpPanel, BorderLayout.SOUTH);
        panel.setLayout(new BorderLayout());
        panel.add(panelTop, BorderLayout.NORTH);
        panel.add(panelBottom, BorderLayout.SOUTH);
        JLabel helpLabel = new JLabel("Use right mouse button to move,\n "
                + "left double click or mouse wheel to zoom.");
        helpPanel.add(helpLabel);
        JButton buttonFitMarkers = new JButton("Fit Markers");
        buttonFitMarkers.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                map().setDisplayToFitMapMarkers();
            }
        });
        JComboBox<TileSource> tileSourceSelector = new JComboBox<>(new TileSource[] {
                new OsmTileSource.Mapnik(),
                new OsmTileSource.CycleMap(),
                new BingAerialTileSource(),
                new MapQuestOsmTileSource(),
                new MapQuestOpenAerialTileSource() });
        tileSourceSelector.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                map().setTileSource((TileSource) e.getItem());
            }
        });
        JComboBox<TileLoader> tileLoaderSelector;
        tileLoaderSelector = new JComboBox<>(new TileLoader[] {new OsmTileLoader(map(), cacheFolder, doCaching)});
        tileLoaderSelector.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                map().setTileLoader((TileLoader) e.getItem());
            }
        });
        map().setTileLoader((TileLoader) tileLoaderSelector.getSelectedItem());
        panelTop.add(tileSourceSelector);
        panelTop.add(tileLoaderSelector);
        final JCheckBox showMapMarker = new JCheckBox("Map markers visible");
        showMapMarker.setSelected(map().getMapMarkersVisible());
        showMapMarker.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                map().setMapMarkerVisible(showMapMarker.isSelected());
            }
        });
        panelTop.add(showMapMarker);
        ///
//        final JCheckBox showTreeLayers = new JCheckBox("Tree Layers visible");
//        showTreeLayers.addActionListener(new ActionListener() {
//            @Override
//            public void actionPerformed(ActionEvent e) {
//                treeMap.setTreeVisible(showTreeLayers.isSelected());
//            }
//        });
//        panelBottom.add(showTreeLayers);
        ///
        final JCheckBox showToolTip = new JCheckBox("ToolTip visible");
        showToolTip.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                map().setToolTipText(null);
            }
        });
        panelTop.add(showToolTip);
        ///
        final JCheckBox showTileGrid = new JCheckBox("Tile grid visible");
        showTileGrid.setSelected(map().isTileGridVisible());
        showTileGrid.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                map().setTileGridVisible(showTileGrid.isSelected());
            }
        });
        panelTop.add(showTileGrid);
        
        final JCheckBox doFastForward = new JCheckBox("FastFollow");
        doFastForward.setSelected(mapController.getRouteSolver().isDoFastFollow());
        doFastForward.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                mapController.getRouteSolver().setDoFastFollow(doFastForward.isSelected());
            }
        });
        panelTop.add(doFastForward);
        
        final JCheckBox doMotorwayBoost = new JCheckBox("MotorwayBoost");
        doMotorwayBoost.setSelected(mapController.getRouteSolver().isDoMotorwayBoost());
        doMotorwayBoost.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                mapController.getRouteSolver().setDoMotorwayBoost(doMotorwayBoost.isSelected());
            }
        });

        routeDistLabel = new JLabel("0 km");
        panelBottom.add(routeDistLabel);
        routeTimeLabel = new JLabel("0:00");
        panelBottom.add(routeTimeLabel);
        
        
//        final JCheckBox showZoomControls = new JCheckBox("Show zoom controls");
//        showZoomControls.setSelected(map().getZoomControlsVisible());
//        showZoomControls.addActionListener(new ActionListener() {
//            @Override
//            public void actionPerformed(ActionEvent e) {
//                map().setZoomContolsVisible(showZoomControls.isSelected());
//            }
//        });
//        panelBottom.add(showZoomControls);
//        final JCheckBox scrollWrapEnabled = new JCheckBox("Scrollwrap enabled");
//        scrollWrapEnabled.addActionListener(new ActionListener() {
//            @Override
//            public void actionPerformed(ActionEvent e) {
//                map().setScrollWrapEnabled(scrollWrapEnabled.isSelected());
//            }
//        });
//        panelBottom.add(scrollWrapEnabled);
        panelTop.add(buttonFitMarkers);

        panelTop.add(zoomLabel);
        panelTop.add(zoomValue);
        panelTop.add(mperpLabelName);
        panelTop.add(mperpLabelValue);
        
        JButton buttonCalcCarFast = new JButton("Car: Fast");
        buttonCalcCarFast.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                mapController.getRouteSolver().startCalculateRoute(TransportMode.Car, RoutingMode.Fastest);
            }
        });
        panelBottom.add(buttonCalcCarFast);

        JButton buttonCalcCarShort = new JButton("Car: Short");
        buttonCalcCarShort.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                mapController.getRouteSolver().startCalculateRoute(TransportMode.Car, RoutingMode.Shortest);
            }
        });
        panelBottom.add(buttonCalcCarShort);

        JButton buttonCalcPedShort = new JButton("Pedestrian");
        buttonCalcPedShort.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                mapController.getRouteSolver().startCalculateRoute(TransportMode.Pedestrian, RoutingMode.Shortest);
            }
        });
        panelBottom.add(buttonCalcPedShort);

        JButton buttonCalcManiacFast = new JButton("Maniac: Fast");
        buttonCalcManiacFast.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                mapController.getRouteSolver().startCalculateRoute(TransportMode.Maniac, RoutingMode.Fastest);
            }
        });
        panelBottom.add(buttonCalcManiacFast);

        JButton buttonCalcManiacShort = new JButton("Maniac: Short");
        buttonCalcManiacShort.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                mapController.getRouteSolver().startCalculateRoute(TransportMode.Maniac, RoutingMode.Shortest);
            }
        });
        panelBottom.add(buttonCalcManiacShort);

        add(treeMap, BorderLayout.CENTER);

        //map().addMapMarker(new MapMarkerDot("A", new Coordinate(48.68, 9.00)));
        //map().addMapMarker(new MapMarkerDot("B", new Coordinate(48.84, 9.26)));
        
        map().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1) {
                    map().getAttribution().handleAttribution(e.getPoint(), true);
                }
            }
        });

        map().addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                Point p = e.getPoint();
                boolean cursorHand = map().getAttribution().handleAttributionCursor(p);
                if (cursorHand) {
                    map().setCursor(new Cursor(Cursor.HAND_CURSOR));
                } else {
                    map().setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
                }
                if (showToolTip.isSelected()) map().setToolTipText(map().getPosition(p).toString());
            }
        });
        
        
        // Poll timer
        Timer timer = new Timer(500, 
                new ActionListener() {
                    
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        if(mapController.getRouteSolver().getRoutingState() == RoutingState.Routing || 
                           mapController.getRouteSolver().getNeedsDispalyRefresh()) {
                            refreshRouteDisplay();
                            mapController.getRouteSolver().resetNeedsDispalyRefresh();
                        }
                    }
                });
        timer.start();         
    }

    private List<MapMarkerDot> routeDots = new ArrayList<>();
    private List<MapPolygonImpl> routeLines = new ArrayList<>();
        
    private void clearRouteDisplay() 
    {
        // Clear dots
        for(MapMarkerDot dot : routeDots) {
            map().removeMapMarker(dot);
        }
        routeDots.clear();
        
        // Clear lines
        for(MapPolygonImpl line : routeLines) {
            map().removeMapPolygon(line);
        }
        routeLines.clear();
        
        // Display start and target
        MapMarkerDot start = new MapMarkerDot("Start", Color.BLUE, mapController.getRouteSolver().getStartCoordinate());
        MapMarkerDot targ = new MapMarkerDot("Target", Color.RED, mapController.getRouteSolver().getTargetCoordinate());
        map().addMapMarker(start);
        map().addMapMarker(targ);
        routeDots.add(start);
        routeDots.add(targ);
    }
    
    private void refreshRouteDisplay() 
    {
        clearRouteDisplay();
        
        if(mapController.getRouteSolver().getRoutingState() == RoutingState.Standby) 
        {
            Coordinate lastCoord = null;
            for(Coordinate coord : mapController.getRouteSolver().getCalculatedRoute()) {
                if(lastCoord != null) {
                    MapPolygonImpl routPoly = new MapPolygonImpl(Color.BLUE, lastCoord, coord, coord);
                    routeLines.add(routPoly);
                    map().addMapPolygon(routPoly);
                }
                lastCoord = coord;
            }         
            
            routeDistLabel.setText(((int)mapController.getRouteSolver().getDistOfRoute() / 1000.0f) + " km");
            int timeHours = (int)mapController.getRouteSolver().getTimeOfRoute();
            int timeMinutes = (int)(60 * (mapController.getRouteSolver().getTimeOfRoute() - timeHours));
            routeTimeLabel.setText(timeHours + ":" + timeMinutes + " h");
        }
        

        if(mapController.getRouteSolver().getRoutingState() != RoutingState.NotReady) 
        {
            List<Coordinate> routingPreviewDots = mapController.getRouteSolver().getRoutingPreviewDots();
            
            for(int i = Math.max(0, routingPreviewDots.size() - MAX_ROUTE_PREVIEW_DOTS); i < routingPreviewDots.size(); i++) {
                MapMarkerDot dot = new MapMarkerDot(
                        new Color(255 - 255 *(routingPreviewDots.size() - i) / MAX_ROUTE_PREVIEW_DOTS, 0, 255 *(routingPreviewDots.size() - i) / MAX_ROUTE_PREVIEW_DOTS),   
                        routingPreviewDots.get(i));
                map().addMapMarker(dot);
                routeDots.add(dot);
            }
        }
        
        if(mapController.getRouteSolver().getRoutingState() == RoutingState.Routing) {
            Coordinate candCoord = mapController.getRouteSolver().getBestCandidateCoords();
            if (candCoord != null) {
                MapMarkerDot dot = new MapMarkerDot(Color.GREEN, candCoord);
                map().addMapMarker(dot);
                routeDots.add(dot);
            }
        }
    }
    
    
    
    private JMapViewer map() {
        return treeMap.getViewer();
    }
    

    /**
     * @param args Main program arguments
     */
    public static void main(String[] args) {
        new OsmRoutingMain().setVisible(true);
    }

    private void updateZoomParameters() {
        if (mperpLabelValue != null)
            mperpLabelValue.setText(String.format("%s", map().getMeterPerPixel()));
        if (zoomValue != null)
            zoomValue.setText(String.format("%s", map().getZoom()));
    }

    @Override
    public void processCommand(JMVCommandEvent command) {
        if (command.getCommand().equals(JMVCommandEvent.COMMAND.ZOOM) ||
                command.getCommand().equals(JMVCommandEvent.COMMAND.MOVE)) {
            updateZoomParameters();
        }
    }
}
