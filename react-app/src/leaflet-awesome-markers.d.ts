import * as L from "leaflet";

declare module "leaflet" {
  namespace AwesomeMarkers {
    function icon(options: {
      icon?: string;
      markerColor?: string;
      prefix?: string;
      iconColor?: string;
    }): L.Icon;
  }
}

declare module "leaflet.awesome-markers";
