package com.simgan.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.geojson.GeoJsonReader;

/**
 * Utilidades para trabajar con geometrías GeoJSON.
 *
 * Soporta entradas típicas que llegan desde el frontend o servicios externos:
 * - FeatureCollection (con features[0].geometry)
 * - Feature (con geometry)
 * - Geometry directa (Polygon/MultiPolygon, etc.)
 *
 * Internamente usa JTS para parsear y operar con geometría (por ejemplo, contains/covers).
 */
public final class GeoJsonUtils {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private GeoJsonUtils() {
    }

    /**
     * Convierte un GeoJSON (String) en una geometría JTS.
     *
     * @param geoJson GeoJSON en texto (FeatureCollection/Feature/Geometry)
     * @return Geometry parseada por JTS
     * @throws IllegalArgumentException si el texto es vacío o no contiene una geometría válida
     */
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

    /**
     * Verifica si la geometría "contenedora" cubre a la geometría "candidata".
     *
     * Semántica de covers (JTS):
     * - Retorna true si la candidata está totalmente dentro de la contenedora o tocando su borde.
     * - Es más permisivo que contains() porque permite el contacto con el borde.
     */
    public static boolean covers(String containerGeoJson, String candidateGeoJson) {
        Geometry container = parseGeometry(containerGeoJson);
        Geometry candidate = parseGeometry(candidateGeoJson);
        return container.covers(candidate);
    }
}
