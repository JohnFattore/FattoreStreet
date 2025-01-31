import { useEffect, useRef } from 'react';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import { useSelector } from "react-redux";
import { RootState } from "../main";
import 'leaflet.awesome-markers/dist/leaflet.awesome-markers.css';
import 'leaflet.awesome-markers';

export default function LeafletMap() {

    const mapRef = useRef(null);

    const greenIcon = L.AwesomeMarkers.icon({
        icon: 'star',
        markerColor: 'green',
        prefix: 'fa', // Using FontAwesome
    });

    const blueIcon = L.AwesomeMarkers.icon({
        icon: 'star',
        markerColor: 'blue',
        prefix: 'fa', // Using FontAwesome
    });

    const { reviews } = useSelector((state: RootState) => state.reviews);
    const { restaurants } = useSelector((state: RootState) => state.restaurantRecommend);

    useEffect(() => {
        if (!mapRef.current) {
            // Initialize the map only once
            mapRef.current = L.map('map').setView([36.140881, -86.819917], 13);

            // Add a tile layer (OpenStreetMap)
            L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
                maxZoom: 19,
                attribution: '© OpenStreetMap contributors',
            }).addTo(mapRef.current);
        }
    }, []);

    useEffect(() => {
        if (!mapRef.current) return; // Don't run if the map is not initialized

        // Add a marker
        for (const i in reviews) {
            const marker = L.marker([reviews[i].latitude, reviews[i].longitude], { icon: greenIcon }).addTo(mapRef.current);
            marker.bindPopup(reviews[i].name.concat(": ", String(reviews[i].rating))).openPopup();
        }
        
        for (const i in restaurants) {
            const marker = L.marker([restaurants[i].latitude, restaurants[i].longitude], { icon: blueIcon }).addTo(mapRef.current);
            marker.bindPopup(restaurants[i].name).openPopup();
        }

        return () => {
            //mapRef.remove(); // Cleanup map on component unmount
        };
    }, [reviews, restaurants])

    return <div id="map" style={{ height: '100vh' }}></div>;
};