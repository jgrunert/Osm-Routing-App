// License: GPL. For details, see Readme.txt file.
package de.jgrunert.osm_routing;

import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.openstreetmap.gui.jmapviewer.Coordinate;
import org.openstreetmap.gui.jmapviewer.JMapViewer;
import org.openstreetmap.gui.jmapviewer.JMapViewerTree;
import org.openstreetmap.gui.jmapviewer.Layer;
import org.openstreetmap.gui.jmapviewer.LayerGroup;
import org.openstreetmap.gui.jmapviewer.MapMarkerCircle;
import org.openstreetmap.gui.jmapviewer.MapMarkerDot;
import org.openstreetmap.gui.jmapviewer.MapPolygonImpl;
import org.openstreetmap.gui.jmapviewer.MapRectangleImpl;
import org.openstreetmap.gui.jmapviewer.OsmTileLoader;
import org.openstreetmap.gui.jmapviewer.events.JMVCommandEvent;
import org.openstreetmap.gui.jmapviewer.interfaces.JMapViewerEventListener;
import org.openstreetmap.gui.jmapviewer.interfaces.MapPolygon;
import org.openstreetmap.gui.jmapviewer.interfaces.TileLoader;
import org.openstreetmap.gui.jmapviewer.interfaces.TileSource;
import org.openstreetmap.gui.jmapviewer.tilesources.BingAerialTileSource;
import org.openstreetmap.gui.jmapviewer.tilesources.MapQuestOpenAerialTileSource;
import org.openstreetmap.gui.jmapviewer.tilesources.MapQuestOsmTileSource;
import org.openstreetmap.gui.jmapviewer.tilesources.OsmTileSource;

import de.jgrunert.osm_routing.OsmRoutingMapController.RoutingMode;
import de.jgrunert.osm_routing.OsmRoutingMapController.TransportMode;

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

    /**
     * Constructs the {@code OsmRoutingMain}.
     */
    public OsmRoutingMain() {
        super("JMapViewer OsmRoutingMain");
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
                mapController.calculateRoute(TransportMode.Car, RoutingMode.Fastest);
            }
        });
        panelBottom.add(buttonCalcCarFast);

        JButton buttonCalcCarShort = new JButton("Car: Short");
        buttonCalcCarShort.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                mapController.calculateRoute(TransportMode.Car, RoutingMode.Shortest);
            }
        });
        panelBottom.add(buttonCalcCarShort);

        JButton buttonCalcPedFast = new JButton("Pedestrian: Fast");
        buttonCalcPedFast.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                mapController.calculateRoute(TransportMode.Pedestrian, RoutingMode.Shortest);
            }
        });
        panelBottom.add(buttonCalcPedFast);

        JButton buttonCalcPedShort = new JButton("Pedestrian: Short");
        buttonCalcPedShort.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                mapController.calculateRoute(TransportMode.Pedestrian, RoutingMode.Shortest);
            }
        });
        panelBottom.add(buttonCalcPedShort);

        JButton buttonCalcManiacFast = new JButton("Maniac: Fast");
        buttonCalcManiacFast.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                mapController.calculateRoute(TransportMode.Maniac, RoutingMode.Fastest);
            }
        });
        panelBottom.add(buttonCalcManiacFast);

        JButton buttonCalcManiacShort = new JButton("Maniac: Short");
        buttonCalcManiacShort.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                mapController.calculateRoute(TransportMode.Maniac, RoutingMode.Shortest);
            }
        });
        panelBottom.add(buttonCalcManiacShort);

        add(treeMap, BorderLayout.CENTER);

        // TODO Testing
        map().addMapMarker(new MapMarkerDot("A", new Coordinate(48.68, 9.00)));
        map().addMapMarker(new MapMarkerDot("B", new Coordinate(48.84, 9.26)));
        
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
