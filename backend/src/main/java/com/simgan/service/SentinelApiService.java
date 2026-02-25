package com.simgan.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.*;

/**
 * Integración con Copernicus Data Space Ecosystem (Sentinel-2).
 * API STAC gratuita, no requiere API key para búsqueda.
 *
 * Sentinel-2 L2A (Surface Reflectance):
 *   B02 = Blue (490nm)
 *   B03 = Green (560nm)
 *   B04 = Red (665nm)
 *   B08 = NIR (842nm)
 *   Resolución: 10m
 *
 * NDVI = (B08 - B04) / (B08 + B04)
 *
 * STAC endpoint (gratuito):
 *   https://catalogue.dataspace.copernicus.eu/stac/
 *
 * Para descarga se necesita cuenta gratuita en:
 *   https://dataspace.copernicus.eu
 */
@Service
@Slf4j
public class SentinelApiService {

    @Value("${sentinel.access.token:}")
    private String accessToken;

    private static final String STAC_URL = "https://catalogue.dataspace.copernicus.eu/stac/search";

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(java.time.Duration.ofSeconds(30))
            .readTimeout(java.time.Duration.ofSeconds(60))
            .build();

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Verifica si el token de acceso de Copernicus está configurado
     */
    public boolean isConfigured() {
        return accessToken != null && !accessToken.isBlank()
                && !accessToken.equals("YOUR_SENTINEL_TOKEN_HERE");
    }

    /**
     * Busca imágenes Sentinel-2 L2A usando STAC API (gratuito, sin auth)
     */
    public List<Map<String, Object>> searchScenes(String geoJson, LocalDate startDate,
                                                   LocalDate endDate, double maxCloudCover) throws IOException {
        JsonNode geoNode = mapper.readTree(geoJson);
        JsonNode geometry = geoNode.has("geometry") ? geoNode.get("geometry") : geoNode;

        // Build STAC search request
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("collections", List.of("SENTINEL-2"));
        body.put("datetime", startDate + "T00:00:00Z/" + endDate + "T23:59:59Z");
        body.put("limit", 10);
        body.put("intersects", mapper.readValue(mapper.writeValueAsString(geometry), Map.class));

        // Cloud filter
        Map<String, Object> filter = new LinkedHashMap<>();
        Map<String, Object> cloudFilter = new LinkedHashMap<>();
        cloudFilter.put("lte", (int) (maxCloudCover * 100));
        filter.put("eo:cloud_cover", cloudFilter);
        body.put("query", filter);

        String jsonBody = mapper.writeValueAsString(body);
        log.info("Sentinel STAC search: {}", jsonBody.substring(0, Math.min(200, jsonBody.length())));

        Request request = new Request.Builder()
                .url(STAC_URL)
                .post(RequestBody.create(jsonBody, MediaType.parse("application/json")))
                .addHeader("Content-Type", "application/json")
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "";
                log.error("Sentinel STAC search failed: {} {} - {}",
                        response.code(), response.message(), errorBody.substring(0, Math.min(300, errorBody.length())));
                return Collections.emptyList();
            }

            JsonNode result = mapper.readTree(response.body().string());
            JsonNode features = result.get("features");

            List<Map<String, Object>> scenes = new ArrayList<>();
            if (features != null && features.isArray()) {
                for (JsonNode feature : features) {
                    Map<String, Object> scene = new HashMap<>();
                    scene.put("id", feature.get("id").asText());

                    JsonNode props = feature.get("properties");
                    if (props != null) {
                        scene.put("datetime", props.has("datetime") ? props.get("datetime").asText() : "");
                        scene.put("cloud_cover", props.has("eo:cloud_cover")
                                ? props.get("eo:cloud_cover").asDouble() : 0);
                        scene.put("platform", props.has("platform")
                                ? props.get("platform").asText() : "sentinel-2");
                    }

                    // Extract download links
                    JsonNode assets = feature.get("assets");
                    if (assets != null) {
                        Map<String, String> bands = new HashMap<>();
                        // Sentinel-2 L2A band names in STAC
                        for (String bandKey : List.of("B04", "B08", "red", "nir")) {
                            if (assets.has(bandKey) && assets.get(bandKey).has("href")) {
                                bands.put(bandKey, assets.get(bandKey).get("href").asText());
                            }
                        }
                        scene.put("bands", bands);
                    }

                    scenes.add(scene);
                }
            }

            log.info("Sentinel search: {} escenas encontradas", scenes.size());
            return scenes;
        }
    }

    /**
     * Descarga una banda específica de Sentinel-2
     */
    public File downloadBand(String downloadUrl, String sceneId, String bandName) throws IOException {
        File dir = new File(System.getProperty("java.io.tmpdir") + "/simgan-sentinel");
        if (!dir.exists()) dir.mkdirs();

        File outputFile = new File(dir, sceneId + "_" + bandName + ".tif");
        if (outputFile.exists()) {
            log.info("Banda ya descargada: {}", outputFile.getName());
            return outputFile;
        }

        Request.Builder requestBuilder = new Request.Builder().url(downloadUrl);

        // Add auth token if configured (needed for actual download)
        if (isConfigured()) {
            requestBuilder.addHeader("Authorization", "Bearer " + accessToken);
        }

        try (Response response = client.newCall(requestBuilder.build()).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Download failed: " + response.code() + " " + response.message());
            }

            try (InputStream is = response.body().byteStream();
                 FileOutputStream fos = new FileOutputStream(outputFile)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                }
            }
        }

        log.info("Sentinel banda descargada: {} ({} MB)", outputFile.getName(),
                outputFile.length() / (1024 * 1024));
        return outputFile;
    }
}
