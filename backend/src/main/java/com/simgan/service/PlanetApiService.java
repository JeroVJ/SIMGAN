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
        JsonNode originalGeometry;

        // 1. Extraemos la geometría
        if (geoNode.has("features") && geoNode.get("features").isArray() && geoNode.get("features").size() > 0) {
            originalGeometry = geoNode.get("features").get(0).get("geometry");
        } else if (geoNode.has("geometry")) {
            originalGeometry = geoNode.get("geometry");
        } else {
            originalGeometry = geoNode;
        }

        // 2. Extraemos coordenadas para hacer un Punto Seguro
        JsonNode coords = originalGeometry.get("coordinates");
        double lon = 0;
        double lat = 0;

        try {
            if ("Polygon".equals(originalGeometry.get("type").asText())) {
                lon = coords.get(0).get(0).get(0).asDouble();
                lat = coords.get(0).get(0).get(1).asDouble();
            } else if ("MultiPolygon".equals(originalGeometry.get("type").asText())) {
                lon = coords.get(0).get(0).get(0).get(0).asDouble();
                lat = coords.get(0).get(0).get(0).get(1).asDouble();
            } else if ("Point".equals(originalGeometry.get("type").asText())) {
                lon = coords.get(0).asDouble();
                lat = coords.get(1).asDouble();
            }
        } catch (Exception e) {
            log.error("Error extrayendo coordenadas para Planet: {}", e.getMessage());
            return Collections.emptyList();
        }

        ObjectNode safeGeometry = mapper.createObjectNode();
        safeGeometry.put("type", "Point");
        ArrayNode pointCoords = mapper.createArrayNode();
        pointCoords.add(lon);
        pointCoords.add(lat);
        safeGeometry.set("coordinates", pointCoords);

        // 3. Construimos los filtros
        ObjectNode searchBody = mapper.createObjectNode();
        searchBody.put("type", "AndFilter");
        ArrayNode config = mapper.createArrayNode();

        ObjectNode geoFilter = mapper.createObjectNode();
        geoFilter.put("type", "GeometryFilter");
        geoFilter.put("field_name", "geometry");
        geoFilter.set("config", safeGeometry);
        config.add(geoFilter);

        ObjectNode dateFilter = mapper.createObjectNode();
        dateFilter.put("type", "DateRangeFilter");
        dateFilter.put("field_name", "acquired");
        ObjectNode dateConfig = mapper.createObjectNode();
        dateConfig.put("gte", startDate.toString() + "T00:00:00Z");
        dateConfig.put("lte", endDate.toString() + "T23:59:59Z");
        dateFilter.set("config", dateConfig);
        config.add(dateFilter);

        ObjectNode cloudFilter = mapper.createObjectNode();
        cloudFilter.put("type", "RangeFilter");
        cloudFilter.put("field_name", "cloud_cover");
        ObjectNode cloudConfig = mapper.createObjectNode();
        cloudConfig.put("lte", maxCloudCover);
        cloudFilter.set("config", cloudConfig);
        config.add(cloudFilter);

        searchBody.set("config", config);

        ObjectNode requestBody = mapper.createObjectNode();
        ArrayNode itemTypes = mapper.createArrayNode();
        itemTypes.add(itemType);
        requestBody.set("item_types", itemTypes);
        requestBody.set("filter", searchBody);

        String jsonPayload = mapper.writeValueAsString(requestBody);
        log.info("🚀 JSON ENVIADO A PLANET (Vía Cliente Nativo): {}", jsonPayload);

        // 4. FIX DEFINITIVO: Usar el cliente HTTP nativo de Java para evitar los bugs de OkHttp
        try {
            java.net.http.HttpClient nativeClient = java.net.http.HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(30))
                    .build();

            java.net.http.HttpRequest nativeRequest = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(baseUrl + "/quick-search"))
                    .header("Authorization", "Basic " + Base64.getEncoder().encodeToString((apiKey + ":").getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            java.net.http.HttpResponse<String> nativeResponse = nativeClient.send(nativeRequest, java.net.http.HttpResponse.BodyHandlers.ofString());

            if (nativeResponse.statusCode() < 200 || nativeResponse.statusCode() >= 300) {
                log.error("Planet search failed: {} {}", nativeResponse.statusCode(), nativeResponse.body());
                return Collections.emptyList();
            }

            JsonNode result = mapper.readTree(nativeResponse.body());
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

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("La búsqueda en Planet fue interrumpida", e);
            return Collections.emptyList();
        }
    }

    /**
     * Asset types en orden de preferencia para NDVI.
     * Education license a veces no tiene _sr (Surface Reflectance).
     *
     * 4-band: Red=Band3(idx2), NIR=Band4(idx3)
     * 8-band: Red=Band6(idx5), NIR=Band8(idx7)
     */
    private static final String[] ASSET_FALLBACKS = {
            "ortho_analytic_4b_sr",   // 4-band Surface Reflectance (ideal)
            "ortho_analytic_4b",      // 4-band DN (sin corrección atmosférica, funciona para NDVI)
            "ortho_analytic_8b_sr",   // 8-band Surface Reflectance
            "ortho_analytic_8b",      // 8-band DN
    };

    /**
     * Activa un asset para descarga, probando múltiples tipos en cascada.
     * Retorna Map con: "url", "assetType", "numBands" o null si ninguno sirve.
     */
    public Map<String, Object> activateAndGetDownloadUrl(String sceneId) throws IOException, InterruptedException {
        if (!isConfigured()) return null;

        String assetsUrl = String.format("%s/item-types/%s/items/%s/assets", baseUrl, itemType, sceneId);

        Request getAssets = new Request.Builder()
                .url(assetsUrl)
                .addHeader("Authorization", "Basic " + Base64.getEncoder().encodeToString((apiKey + ":").getBytes()))
                .build();

        try (Response response = client.newCall(getAssets).execute()) {
            if (!response.isSuccessful()) {
                log.error("Planet assets request failed: HTTP {} for scene {}", response.code(), sceneId);
                return null;
            }

            String responseBody = response.body().string();

            // Log raw response (truncated) para diagnosticar
            log.info("Planet assets RAW para {}: {}", sceneId,
                    responseBody.length() > 500 ? responseBody.substring(0, 500) + "..." : responseBody);

            JsonNode assets = mapper.readTree(responseBody);

            // Log ALL available assets
            List<String> availableAssets = new ArrayList<>();
            assets.fieldNames().forEachRemaining(name -> {
                String st = assets.get(name).has("status") ? assets.get(name).get("status").asText() : "?";
                String perm = assets.get(name).has("_permissions") ? assets.get(name).get("_permissions").toString() : "no-perms";
                availableAssets.add(name + "(" + st + ", " + perm + ")");
            });

            if (availableAssets.isEmpty()) {
                log.warn("⚠️ ZERO assets para escena {}. Tu licencia Education puede no tener permisos de descarga para esta zona.", sceneId);
                // Try fetching the item itself to check permissions
                String itemUrl = String.format("%s/item-types/%s/items/%s", baseUrl, itemType, sceneId);
                Request itemReq = new Request.Builder()
                        .url(itemUrl)
                        .addHeader("Authorization", "Basic " + Base64.getEncoder().encodeToString((apiKey + ":").getBytes()))
                        .build();
                try (Response itemResp = client.newCall(itemReq).execute()) {
                    if (itemResp.isSuccessful()) {
                        String itemBody = itemResp.body().string();
                        JsonNode item = mapper.readTree(itemBody);
                        if (item.has("_permissions")) {
                            log.info("Permisos del item {}: {}", sceneId, item.get("_permissions"));
                        }
                        if (item.has("assets")) {
                            log.info("Assets dentro del item: {}",
                                    item.get("assets").toString().substring(0, Math.min(500, item.get("assets").toString().length())));
                        }
                    }
                }
                return null;
            }

            log.info("Assets disponibles para {}: {}", sceneId, String.join(", ", availableAssets));

            // Try each asset type in order
            for (String tryAssetType : ASSET_FALLBACKS) {
                JsonNode asset = assets.get(tryAssetType);
                if (asset == null) continue;

                String status = asset.get("status").asText();
                log.info("Probando asset {} para {}: estado={}", tryAssetType, sceneId, status);

                String downloadUrl = null;

                if ("active".equals(status)) {
                    downloadUrl = asset.get("location").asText();
                } else if ("inactive".equals(status) || "activating".equals(status)) {
                    // Activate if needed
                    if ("inactive".equals(status) && asset.has("_links") && asset.get("_links").has("activate")) {
                        String activateUrl = asset.get("_links").get("activate").asText();
                        Request activate = new Request.Builder()
                                .url(activateUrl)
                                .post(RequestBody.create("", MediaType.parse("application/json")))
                                .addHeader("Authorization", "Basic " + Base64.getEncoder().encodeToString((apiKey + ":").getBytes()))
                                .build();
                        try (Response activateResp = client.newCall(activate).execute()) {
                            log.info("Activación enviada para {} ({}): HTTP {}", sceneId, tryAssetType, activateResp.code());
                        }
                    }

                    // Poll until active (max 5 min)
                    log.info("Esperando activación de {} ({})...", sceneId, tryAssetType);
                    for (int i = 0; i < 60; i++) {
                        Thread.sleep(5000);
                        try (Response pollResp = client.newCall(getAssets).execute()) {
                            JsonNode pollAssets = mapper.readTree(pollResp.body().string());
                            JsonNode pollAsset = pollAssets.get(tryAssetType);
                            if (pollAsset != null && "active".equals(pollAsset.get("status").asText())) {
                                downloadUrl = pollAsset.get("location").asText();
                                log.info("Asset {} activado exitosamente para {}", tryAssetType, sceneId);
                                break;
                            }
                        }
                    }
                }

                if (downloadUrl != null) {
                    int numBands = tryAssetType.contains("8b") ? 8 : 4;
                    Map<String, Object> result = new HashMap<>();
                    result.put("url", downloadUrl);
                    result.put("assetType", tryAssetType);
                    result.put("numBands", numBands);
                    log.info("✅ Asset listo: {} ({} bandas) para escena {}", tryAssetType, numBands, sceneId);
                    return result;
                }
            }

            log.warn("Ningún asset analítico disponible para escena {}", sceneId);
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
