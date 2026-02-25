package com.simgan.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
 * Integración con Planet Labs Data API v2.
 * Busca escenas PlanetScope, activa assets y descarga GeoTIFF.
 *
 * PlanetScope 4-band Surface Reflectance:
 *   Band 1 = Blue
 *   Band 2 = Green
 *   Band 3 = Red
 *   Band 4 = NIR
 * NDVI = (Band4 - Band3) / (Band4 + Band3)
 */
@Service
@Slf4j
public class PlanetApiService {

    @Value("${planet.api.key:}")
    private String apiKey;

    @Value("${planet.api.base-url:https://api.planet.com/data/v1}")
    private String baseUrl;

    @Value("${planet.item-type:PSScene}")
    private String itemType;

    @Value("${planet.asset-type:ortho_analytic_4b_sr}")
    private String assetType;

    @Value("${planet.download.dir:${java.io.tmpdir}/simgan-planet}")
    private String downloadDir;

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(java.time.Duration.ofSeconds(30))
            .readTimeout(java.time.Duration.ofSeconds(120))
            .build();

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Verifica si la API key está configurada
     */
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank() && !apiKey.equals("YOUR_PLANET_API_KEY_HERE");
    }

    /**
     * Busca escenas disponibles que cubran el GeoJSON dado
     *
     * @param geoJson GeoJSON del terreno/parcela (Feature o Polygon)
     * @param startDate inicio del rango
     * @param endDate fin del rango
     * @param maxCloudCover max porcentaje de nubes (0.0 - 1.0)
     * @return Lista de scene IDs con metadata
     */
    public List<Map<String, Object>> searchScenes(String geoJson, LocalDate startDate, LocalDate endDate, double maxCloudCover) throws IOException {
        if (!isConfigured()) {
            log.warn("Planet API key no configurada");
            return Collections.emptyList();
        }

        JsonNode geoNode = mapper.readTree(geoJson);
        JsonNode geometry = geoNode.has("geometry") ? geoNode.get("geometry") : geoNode;

        // Build search filter
        ObjectNode searchBody = mapper.createObjectNode();
        searchBody.put("type", "AndFilter");

        ArrayNode config = mapper.createArrayNode();

        // Geometry filter
        ObjectNode geoFilter = mapper.createObjectNode();
        geoFilter.put("type", "GeometryFilter");
        geoFilter.put("field_name", "geometry");
        geoFilter.set("config", geometry);
        config.add(geoFilter);

        // Date filter
        ObjectNode dateFilter = mapper.createObjectNode();
        dateFilter.put("type", "DateRangeFilter");
        dateFilter.put("field_name", "acquired");
        ObjectNode dateConfig = mapper.createObjectNode();
        dateConfig.put("gte", startDate.toString() + "T00:00:00Z");
        dateConfig.put("lte", endDate.toString() + "T23:59:59Z");
        dateFilter.set("config", dateConfig);
        config.add(dateFilter);

        // Cloud cover filter
        ObjectNode cloudFilter = mapper.createObjectNode();
        cloudFilter.put("type", "RangeFilter");
        cloudFilter.put("field_name", "cloud_cover");
        ObjectNode cloudConfig = mapper.createObjectNode();
        cloudConfig.put("lte", maxCloudCover);
        cloudFilter.set("config", cloudConfig);
        config.add(cloudFilter);

        searchBody.set("config", config);

        // Build request body
        ObjectNode requestBody = mapper.createObjectNode();
        ArrayNode itemTypes = mapper.createArrayNode();
        itemTypes.add(itemType);
        requestBody.set("item_types", itemTypes);
        requestBody.set("filter", searchBody);

        // Execute search
        Request request = new Request.Builder()
                .url(baseUrl + "/quick-search")
                .post(RequestBody.create(mapper.writeValueAsString(requestBody), MediaType.parse("application/json")))
                .addHeader("Authorization", "Basic " + Base64.getEncoder().encodeToString((apiKey + ":").getBytes()))
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.error("Planet search failed: {} {}", response.code(), response.message());
                return Collections.emptyList();
            }

            JsonNode result = mapper.readTree(response.body().string());
            JsonNode features = result.get("features");

            List<Map<String, Object>> scenes = new ArrayList<>();
            if (features != null && features.isArray()) {
                for (JsonNode feature : features) {
                    Map<String, Object> scene = new HashMap<>();
                    scene.put("id", feature.get("id").asText());
                    scene.put("acquired", feature.get("properties").get("acquired").asText());
                    scene.put("cloud_cover", feature.get("properties").get("cloud_cover").asDouble());
                    scene.put("pixel_resolution", feature.get("properties").has("pixel_resolution")
                            ? feature.get("properties").get("pixel_resolution").asDouble() : 3.0);
                    scenes.add(scene);
                }
            }

            log.info("Planet search: {} escenas encontradas", scenes.size());
            return scenes;
        }
    }

    /**
     * Activa un asset para descarga
     */
    public String activateAndGetDownloadUrl(String sceneId) throws IOException, InterruptedException {
        if (!isConfigured()) return null;

        String assetsUrl = String.format("%s/item-types/%s/items/%s/assets", baseUrl, itemType, sceneId);

        // Get assets
        Request getAssets = new Request.Builder()
                .url(assetsUrl)
                .addHeader("Authorization", "Basic " + Base64.getEncoder().encodeToString((apiKey + ":").getBytes()))
                .build();

        try (Response response = client.newCall(getAssets).execute()) {
            if (!response.isSuccessful()) return null;

            JsonNode assets = mapper.readTree(response.body().string());
            JsonNode asset = assets.get(assetType);

            if (asset == null) {
                log.warn("Asset {} no disponible para escena {}", assetType, sceneId);
                return null;
            }

            String status = asset.get("status").asText();

            // Activate if inactive
            if ("inactive".equals(status)) {
                String activateUrl = asset.get("_links").get("activate").asText();
                Request activate = new Request.Builder()
                        .url(activateUrl)
                        .post(RequestBody.create("", MediaType.parse("application/json")))
                        .addHeader("Authorization", "Basic " + Base64.getEncoder().encodeToString((apiKey + ":").getBytes()))
                        .build();

                try (Response activateResp = client.newCall(activate).execute()) {
                    log.info("Asset activado para escena {}: {}", sceneId, activateResp.code());
                }

                // Poll until active (max 60 seconds)
                for (int i = 0; i < 12; i++) {
                    Thread.sleep(5000);
                    try (Response pollResp = client.newCall(getAssets).execute()) {
                        JsonNode pollAssets = mapper.readTree(pollResp.body().string());
                        JsonNode pollAsset = pollAssets.get(assetType);
                        if ("active".equals(pollAsset.get("status").asText())) {
                            return pollAsset.get("location").asText();
                        }
                    }
                }
                log.warn("Timeout activando asset para escena {}", sceneId);
                return null;
            }

            if ("active".equals(status)) {
                return asset.get("location").asText();
            }

            return null;
        }
    }

    /**
     * Descarga un GeoTIFF desde Planet a un archivo local
     */
    public File downloadGeoTiff(String downloadUrl, String sceneId) throws IOException {
        File dir = new File(downloadDir);
        if (!dir.exists()) dir.mkdirs();

        File outputFile = new File(dir, sceneId + ".tif");
        if (outputFile.exists()) {
            log.info("GeoTIFF ya descargado: {}", outputFile.getAbsolutePath());
            return outputFile;
        }

        Request request = new Request.Builder()
                .url(downloadUrl)
                .addHeader("Authorization", "Basic " + Base64.getEncoder().encodeToString((apiKey + ":").getBytes()))
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Download failed: " + response.code());
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

        log.info("GeoTIFF descargado: {} ({} MB)", outputFile.getName(), outputFile.length() / (1024 * 1024));
        return outputFile;
    }
}
