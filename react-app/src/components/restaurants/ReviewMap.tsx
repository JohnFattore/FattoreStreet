import { useEffect, useMemo, useRef } from "react";
import L from "leaflet";
import "leaflet/dist/leaflet.css";
import { useSelector } from "react-redux";
import { RootState } from "../../main";
import { djangoApi, useGetReviewsQuery } from "../../functions/api";
import "leaflet.awesome-markers/dist/leaflet.awesome-markers.css";
import "leaflet.awesome-markers";

export default function ReviewMap() {
  const mapRef = useRef<L.Map | null>(null);

  const greenIcon = useMemo(
    () =>
      L.AwesomeMarkers.icon({
        icon: "star",
        markerColor: "green",
        prefix: "fa", // Using FontAwesome
      }),
    [],
  );

  const blueIcon = useMemo(
    () =>
      L.AwesomeMarkers.icon({
        icon: "star",
        markerColor: "blue",
        prefix: "fa", // Using FontAwesome
      }),
    [],
  );

  const { access } = useSelector((state: RootState) => state.user);
  const { data: reviews = [] } = useGetReviewsQuery(undefined, {
    skip: !access,
  });
  // Read the recommendations cache without triggering a fetch — markers only
  // appear once the recommendations button has been clicked
  const { data: restaurants = [] } =
    djangoApi.endpoints.getRestaurantRecommendations.useQueryState();

  useEffect(() => {
    if (!mapRef.current) {
      // Initialize the map only once
      mapRef.current = L.map("map").setView([36.140881, -86.819917], 13);

      // Add a tile layer (OpenStreetMap)
      L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
        maxZoom: 19,
        attribution: "© OpenStreetMap contributors",
      }).addTo(mapRef.current);
    }
  }, []);

  useEffect(() => {
    if (!mapRef.current) return; // Don't run if the map is not initialized

    // Add a marker
    for (const i in reviews) {
      const marker = L.marker([reviews[i].latitude, reviews[i].longitude], {
        icon: greenIcon,
      }).addTo(mapRef.current);
      marker
        .bindPopup(reviews[i].name.concat(": ", String(reviews[i].rating)))
        .openPopup();
    }

    for (const i in restaurants) {
      const marker = L.marker(
        [restaurants[i].latitude, restaurants[i].longitude],
        { icon: blueIcon },
      ).addTo(mapRef.current);
      marker.bindPopup(restaurants[i].name).openPopup();
    }

    return () => {
      //mapRef.remove(); // Cleanup map on component unmount
    };
  }, [reviews, restaurants, greenIcon, blueIcon]);

  return <div id="map" className="vh-100"></div>;
}
