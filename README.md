# Osm-Routing-App

Android App for offline routing with OpenStreetMap. Created during an exercise at the University of Stuttgart http://www.uni-stuttgart.de/.
It uses a partitioned routing graph and an optimized A* algorithm to run on mobile devices with scarce main memory while achieving good routing performance.

Uses Mapsforge https://github.com/mapsforge/mapsforge for map disaplay.
Uses OpenStreetMap data to generate routing graphs.

## AndroMapView

Java App for offline map display and routing. 

### Prerequisites

- For display needs a mapsforge file at SD card (ExternalStorageDirectory) osm/mapsforge/display.map
- For routing needs OSM road network chunks, called "grids" at SD card (ExternalStorageDirectory) osm/routing_grids. These can be generated using OsmConversionPipeline.


## OsmConversionPipeline

Java application to convert OpenStreetMap data (osm.pbf) to AndroMapViews format. Uses sevaral steps to simplify the routing graph and finally separate it into chunks.
