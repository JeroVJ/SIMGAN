package com.simgan.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.geojson.GeoJsonReader;

public final class GeoJsonUtils {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private GeoJsonUtils() {
    }

    public static Geometry parseGeometry(String geoJson) {
        if (geoJson == null || geoJson.isBlank()) {
            throw new IllegalArgumentException("El GeoJSON es obligatorio.");
        }

        try {
            JsonNode root = MAPPER.readTree(geoJson);
            JsonNode geometryNode;
            if (root.has("features") && root.get("features").isArray() && !root.get("features").isEmpty()) {
                geometryNode = root.get("features").get(0).get("geometry");
            } else if (root.has("geometry")) {
                geometryNode = root.get("geometry");
            } else {
                geometryNode = root;
            }

            if (geometryNode == null || geometryNode.isNull()) {
                throw new IllegalArgumentException("El GeoJSON no contiene una geometría válida.");
            }

            return new GeoJsonReader().read(geometryNode.toString());
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("El GeoJSON no es válido.", ex);
        }
    }

    public static boolean covers(String containerGeoJson, String candidateGeoJson) {
        Geometry container = parseGeometry(containerGeoJson);
        Geometry candidate = parseGeometry(candidateGeoJson);
        return container.covers(candidate);
    }
}