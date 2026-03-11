package com.simgan.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Integración completa con Copernicus Data Space (Sentinel-2 L2A).
 *
 * Pipeline:
 *   1. STAC search (público, sin auth) → escenas sentinel-2-l2a
 *   2. OData search por nombre → obtener UUID del producto
 *   3. OData download (OAuth2) → ZIP SAFE
 *   4. Extraer B04 (Red 665nm) y B08 (NIR 842nm) de R10m/
 *   5. Parsear metadata UTM (EPSG, ULX, ULY) de MTD_TL.xml
 */
@Service
@Slf4j
public class SentinelApiService {

    @Value("${copernicus.username:}")
    private String username;

    @Value("${copernicus.password:}")
    private String password;

    private static final String STAC_URL = "https://stac.dataspace.copernicus.eu/v1/search";
    private static final String TOKEN_URL = "https://identity.dataspace.copernicus.eu/auth/realms/CDSE/protocol/openid-connect/token";
    private static final String ODATA_CATALOG = "https://catalogue.dataspace.copernicus.eu/odata/v1";
    private static final String ODATA_DOWNLOAD = "https://zipper.dataspace.copernicus.eu/odata/v1";
    private static final String DOWNLOAD_DIR = System.getProperty("java.io.tmpdir") + "/simgan-sentinel";

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(java.time.Duration.ofSeconds(30))
            .readTimeout(java.time.Duration.ofSeconds(300))
            .followRedirects(true)
            .build();

    private final ObjectMapper mapper = new ObjectMapper();
    private String cachedToken = null;
    private long tokenExpiry = 0;

    // ===== AUTH =====

    public boolean isConfigured() {
        boolean ok = username != null && !username.isBlank()
                && !username.equals("YOUR_COPERNICUS_EMAIL")
                && password != null && !password.isBlank()
                && !password.equals("YOUR_COPERNICUS_PASSWORD");
        if (!ok) {
            log.warn("Copernicus NO configurado. Pon tu email/password de dataspace.copernicus.eu en application.properties: " +
                    "copernicus.username=tu@email.com y copernicus.password=tuPassword");
        }
        return ok;
    }

    private String getAccessToken() throws IOException {
        if (cachedToken != null && System.currentTimeMillis() < tokenExpiry) {
            return cachedToken;
        }
        if (!isConfigured()) return null;

        RequestBody formBody = new FormBody.Builder()
                .add("grant_type", "password")
                .add("username", username)
                .add("password", password)
                .add("client_id", "cdse-public")
                .build();

        Request request = new Request.Builder().url(TOKEN_URL).post(formBody).build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String body = response.body() != null ? response.body().string() : "";
                log.error("Error obteniendo token Copernicus: {} {} - {}", response.code(), response.message(),
                        body.substring(0, Math.min(200, body.length())));
                return null;
            }
            JsonNode result = mapper.readTree(response.body().string());
            cachedToken = result.get("access_token").asText();
            int expiresIn = result.get("expires_in").asInt();
            tokenExpiry = System.currentTimeMillis() + (expiresIn - 30) * 1000L;
            log.info("✅ Token Copernicus obtenido (expira en {}s)", expiresIn);
            return cachedToken;
        }
    }

    // ===== STAC SEARCH =====

    private static final String[][] COLLECTION_ATTEMPTS = {
            {"sentinel-2-l2a"},
            {"SENTINEL-2"},
    };

    public List<Map<String, Object>> searchScenes(String geoJson, LocalDate startDate,
                                                   LocalDate endDate, double maxCloudCover) throws IOException {
        JsonNode geoNode = mapper.readTree(geoJson);
        JsonNode geometry;
        if (geoNode.has("features") && geoNode.get("features").isArray() && geoNode.get("features").size() > 0) {
            geometry = geoNode.get("features").get(0).get("geometry");
        } else if (geoNode.has("geometry")) {
            geometry = geoNode.get("geometry");
        } else {
            geometry = geoNode;
        }

        for (String[] collections : COLLECTION_ATTEMPTS) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("collections", List.of(collections));
            body.put("datetime", startDate + "T00:00:00Z/" + endDate + "T23:59:59Z");
            body.put("limit", 20);
            body.put("intersects", mapper.readValue(mapper.writeValueAsString(geometry), Map.class));

            String jsonBody = mapper.writeValueAsString(body);
            log.info("Sentinel STAC (collection={})", collections[0]);

            List<Map<String, Object>> results = executeStacSearch(jsonBody, maxCloudCover);
            if (results != null && !results.isEmpty()) {
                return results;
            }
        }
        return Collections.emptyList();
    }

    private List<Map<String, Object>> executeStacSearch(String jsonBody, double maxCloudCover) throws IOException {
        int maxRetries = 3;
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            Request request = new Request.Builder()
                    .url(STAC_URL)
                    .post(RequestBody.create(jsonBody, MediaType.parse("application/json")))
                    .addHeader("Content-Type", "application/json")
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (response.code() == 429) {
                    log.warn("Sentinel STAC 429 rate limit. Reintento {}/{}", attempt + 1, maxRetries + 1);
                    if (response.body() != null) response.body().close();
                    if (attempt < maxRetries) {
                        try { Thread.sleep((attempt + 1) * 5000L); }
                        catch (InterruptedException ie) { Thread.currentThread().interrupt(); return Collections.emptyList(); }
                        continue;
                    }
                    return Collections.emptyList();
                }

                String responseBody = response.body() != null ? response.body().string() : "";
                if (!response.isSuccessful()) {
                    log.error("Sentinel STAC failed: {}", response.code());
                    if (response.code() == 400 || response.code() == 404) return null;
                    return Collections.emptyList();
                }

                JsonNode result = mapper.readTree(responseBody);
                JsonNode features = result.get("features");
                if (features == null || !features.isArray() || features.size() == 0) {
                    return null;
                }

                // Log asset keys from first feature
                if (features.get(0).has("assets")) {
                    List<String> keys = new ArrayList<>();
                    features.get(0).get("assets").fieldNames().forEachRemaining(keys::add);
                    log.info("STAC asset keys: {}", String.join(", ", keys));
                }

                List<Map<String, Object>> scenes = new ArrayList<>();
                for (JsonNode feature : features) {
                    String featureId = feature.get("id").asText();
                    Map<String, Object> scene = new HashMap<>();
                    scene.put("id", featureId);

                    JsonNode props = feature.get("properties");
                    double cloudCover = 0;
                    if (props != null) {
                        scene.put("datetime", props.has("datetime") ? props.get("datetime").asText() : "");
                        cloudCover = props.has("eo:cloud_cover") ? props.get("eo:cloud_cover").asDouble() : 0;
                        scene.put("cloud_cover", cloudCover);
                    }
                    if (cloudCover > maxCloudCover * 100) continue;

                    // Extract PRODUCT download URL from assets
                    JsonNode assets = feature.get("assets");
                    if (assets != null) {
                        for (String key : List.of("PRODUCT", "product", "downloadLink")) {
                            if (assets.has(key) && assets.get(key).has("href")) {
                                scene.put("downloadUrl", assets.get(key).get("href").asText());
                                log.info("Scene {} download URL from STAC: {}", featureId,
                                        scene.get("downloadUrl").toString().substring(0, Math.min(100, scene.get("downloadUrl").toString().length())));
                                break;
                            }
                        }
                        // Direct band URLs (if available)
                        Map<String, String> bands = new HashMap<>();
                        for (String bk : List.of("B04", "B08", "B04_10m", "B08_10m", "red", "nir")) {
                            if (assets.has(bk) && assets.get(bk).has("href")) {
                                bands.put(bk, assets.get(bk).get("href").asText());
                            }
                        }
                        if (!bands.isEmpty()) scene.put("bands", bands);
                    }

                    scenes.add(scene);
                }

                // Sort by cloud cover ascending (best first)
                scenes.sort(Comparator.comparingDouble(s -> (double) s.getOrDefault("cloud_cover", 100.0)));

                log.info("Sentinel: {} features, {} tras filtro nubes (max {}%)",
                        features.size(), scenes.size(), (int)(maxCloudCover * 100));
                return scenes;
            }
        }
        return Collections.emptyList();
    }

    // ===== DOWNLOAD + EXTRACT =====

    /**
     * Descarga un producto Sentinel-2 y extrae B04 + B08.
     * Primero busca el UUID del producto vía OData catálogo.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> downloadAndExtractBands(Map<String, Object> scene) throws IOException {
        String sceneId = (String) scene.get("id");
        log.info("=== DESCARGA SENTINEL-2: {} ===", sceneId);

        String token = getAccessToken();
        if (token == null) {
            log.error("❌ No se pudo obtener token. Verifica copernicus.username/password en application.properties");
            throw new IOException("Token Copernicus no disponible");
        }

        File workDir = new File(DOWNLOAD_DIR, sceneId.substring(0, Math.min(30, sceneId.length())).replaceAll("[^a-zA-Z0-9_-]", "_"));
        if (!workDir.exists()) workDir.mkdirs();

        // Check cache
        File redFile = findBand(workDir, "B04");
        File nirFile = findBand(workDir, "B08");
        if (redFile != null && nirFile != null) {
            log.info("Bandas en caché: {}, {}", redFile.getName(), nirFile.getName());
            return buildResult(redFile, nirFile, workDir);
        }

        // 1. Get download URL (STAC href OR OData search by name)
        String downloadUrl = (String) scene.get("downloadUrl");

        if (downloadUrl == null || downloadUrl.contains("('S2")) {
            // Feature ID is product name, not UUID → search OData for UUID
            log.info("Buscando UUID del producto en OData catálogo...");
            String uuid = findProductUuid(sceneId);
            if (uuid != null) {
                downloadUrl = ODATA_DOWNLOAD + "/Products(" + uuid + ")/$value";
                log.info("UUID encontrado: {} → {}", uuid, downloadUrl);
            } else {
                log.error("No se encontró UUID para producto {}", sceneId);
                return null;
            }
        }

        // 2. Download ZIP
        File zipFile = new File(workDir, "product.zip");
        if (!zipFile.exists() || zipFile.length() < 10000) {
            log.info("Descargando producto: {}", downloadUrl.substring(0, Math.min(100, downloadUrl.length())));
            downloadWithAuth(downloadUrl, zipFile, token);
        }

        if (!zipFile.exists() || zipFile.length() < 10000) {
            log.error("Descarga fallida para {}", sceneId);
            return null;
        }
        log.info("Producto descargado: {} MB", zipFile.length() / (1024 * 1024));

        // 3. Extract B04, B08, MTD_TL.xml
        return extractBandsFromZip(zipFile, workDir);
    }

    /**
     * Busca el UUID de un producto Sentinel-2 por nombre en OData catálogo.
     */
    private String findProductUuid(String productName) {
        try {
            // Clean product name (remove .SAFE if present)
            String cleanName = productName.replace(".SAFE", "");

            String searchUrl = ODATA_CATALOG + "/Products?$filter=contains(Name,'" + cleanName + "')&$top=1";
            log.info("OData search: {}", searchUrl);

            Request req = new Request.Builder()
                    .url(searchUrl)
                    .addHeader("Accept", "application/json")
                    .build();

            try (Response resp = client.newCall(req).execute()) {
                if (!resp.isSuccessful()) {
                    log.error("OData search failed: {}", resp.code());
                    return null;
                }
                JsonNode body = mapper.readTree(resp.body().string());
                JsonNode values = body.get("value");
                if (values != null && values.isArray() && values.size() > 0) {
                    String uuid = values.get(0).get("Id").asText();
                    String name = values.get(0).get("Name").asText();
                    log.info("OData found: {} → UUID: {}", name, uuid);
                    return uuid;
                }
                log.warn("OData: no product found for name '{}'", cleanName);
            }
        } catch (Exception e) {
            log.error("Error buscando producto en OData: {}", e.getMessage());
        }
        return null;
    }

    private void downloadWithAuth(String url, File output, String token) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + token)
                .build();

        log.info("HTTP GET (auth) → {} ...", url.substring(0, Math.min(80, url.length())));

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errBody = response.body() != null ? response.body().string() : "";
                log.error("Download failed: HTTP {} {} - {}", response.code(), response.message(),
                        errBody.substring(0, Math.min(300, errBody.length())));
                throw new IOException("Download failed: HTTP " + response.code());
            }

            try (InputStream is = response.body().byteStream();
                 FileOutputStream fos = new FileOutputStream(output)) {
                byte[] buffer = new byte[65536];
                int bytesRead;
                long total = 0;
                while ((bytesRead = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                    total += bytesRead;
                    if (total % (50 * 1024 * 1024) < 65536) {
                        log.info("  descargando... {} MB", total / (1024 * 1024));
                    }
                }
                log.info("Descarga completa: {} MB", total / (1024 * 1024));
            }
        }
    }

    private Map<String, Object> extractBandsFromZip(File zipFile, File outputDir) {
        File redFile = null, nirFile = null, metadataFile = null;

        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                boolean isB04 = name.contains("_B04_10m.jp2") || name.contains("_B04_10m.tif");
                boolean isB08 = name.contains("_B08_10m.jp2") || name.contains("_B08_10m.tif");
                boolean isMtd = name.endsWith("MTD_TL.xml") && name.contains("GRANULE");

                if ((isB04 || isB08 || isMtd) && !entry.isDirectory()) {
                    String outName = isB04 ? "B04_10m.jp2" : isB08 ? "B08_10m.jp2" : "MTD_TL.xml";
                    File outFile = new File(outputDir, outName);
                    log.info("Extrayendo: {} → {}", name, outName);

                    try (FileOutputStream fos = new FileOutputStream(outFile)) {
                        byte[] buffer = new byte[65536];
                        int len;
                        while ((len = zis.read(buffer)) > 0) fos.write(buffer, 0, len);
                    }

                    if (isB04) redFile = outFile;
                    if (isB08) nirFile = outFile;
                    if (isMtd) metadataFile = outFile;
                    if (redFile != null && nirFile != null && metadataFile != null) break;
                }
                zis.closeEntry();
            }
        } catch (IOException e) {
            log.error("Error extrayendo ZIP: {}", e.getMessage());
        }

        if (redFile == null || nirFile == null) {
            log.error("❌ B04/B08 no encontrados en el ZIP");
            return null;
        }

        Map<String, Object> result = new HashMap<>();
        result.put("redFile", redFile);
        result.put("nirFile", nirFile);

        if (metadataFile != null) {
            parseGranuleMetadata(metadataFile, result);
        } else {
            log.warn("MTD_TL.xml no encontrado, usando defaults UTM 18N");
            result.put("epsg", 32618);
            result.put("ulx", 600000.0);
            result.put("uly", 500000.0);
        }

        log.info("✅ Bandas extraídas: B04={} MB, B08={} MB, EPSG:{}",
                redFile.length()/(1024*1024), nirFile.length()/(1024*1024), result.get("epsg"));
        return result;
    }

    private void parseGranuleMetadata(File mtdFile, Map<String, Object> result) {
        try {
            String xml = Files.readString(mtdFile.toPath());
            int epsg = 32618;
            int epsgIdx = xml.indexOf("EPSG:");
            if (epsgIdx >= 0) {
                String code = xml.substring(epsgIdx + 5).split("[<\\s\"]")[0];
                epsg = Integer.parseInt(code.trim());
            }

            double ulx = 0, uly = 0;
            int geoIdx = xml.indexOf("resolution=\"10\"");
            if (geoIdx > 0) {
                String block = xml.substring(geoIdx, Math.min(geoIdx + 300, xml.length()));
                ulx = parseXmlVal(block, "ULX");
                uly = parseXmlVal(block, "ULY");
            }
            result.put("epsg", epsg);
            result.put("ulx", ulx);
            result.put("uly", uly);
            log.info("Metadata: EPSG:{}, ULX={}, ULY={}", epsg, ulx, uly);
        } catch (Exception e) {
            log.error("Error parseando metadata: {}", e.getMessage());
            result.put("epsg", 32618);
            result.put("ulx", 600000.0);
            result.put("uly", 500000.0);
        }
    }

    private double parseXmlVal(String xml, String tag) {
        int idx = xml.indexOf("<" + tag + ">");
        if (idx < 0) return 0;
        return Double.parseDouble(xml.substring(idx + tag.length() + 2).split("<")[0].trim());
    }

    private Map<String, Object> buildResult(File red, File nir, File workDir) {
        Map<String, Object> r = new HashMap<>();
        r.put("redFile", red);
        r.put("nirFile", nir);
        File mtd = new File(workDir, "MTD_TL.xml");
        if (mtd.exists()) {
            parseGranuleMetadata(mtd, r);
        } else {
            r.put("epsg", 32618);
            r.put("ulx", 600000.0);
            r.put("uly", 500000.0);
        }
        return r;
    }

    private File findBand(File dir, String band) {
        if (!dir.exists()) return null;
        File[] files = dir.listFiles((d, n) -> n.contains(band) && (n.endsWith(".jp2") || n.endsWith(".tif")));
        return (files != null && files.length > 0 && files[0].length() > 1000) ? files[0] : null;
    }
}
