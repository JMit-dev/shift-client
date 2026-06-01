package org.phoebus.shift.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.phoebus.shift.client.model.Shift;
import org.phoebus.shift.client.model.ShiftType;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

/**
 * REST client for the shift service API.
 *
 * <p>Obtain an instance via the builder:
 * <pre>
 *   ShiftClient client = ShiftClient.builder()
 *       .baseUrl("http://shift-service/Shift/resources")
 *       .username("user")
 *       .password("pass")
 *       .build();
 * </pre>
 */
public class ShiftClient {

    private final String baseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String authHeader;
    private final Duration readTimeout;

    private ShiftClient(Builder builder) {
        String url = builder.baseUrl;
        this.baseUrl = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(builder.connectTimeout)
                .build();
        this.objectMapper = new ObjectMapper();
        this.readTimeout = builder.readTimeout;

        if (builder.username != null && !builder.username.isEmpty()) {
            String credentials = builder.username + ":" + builder.password;
            this.authHeader = "Basic " + Base64.getEncoder()
                    .encodeToString(credentials.getBytes());
        } else {
            this.authHeader = null;
        }
    }

    // -------------------------------------------------------------------------
    // Read operations
    // -------------------------------------------------------------------------

    /** Returns the currently active shift for the given type, or null if none / 404. */
    public Shift getShift(String type) throws ShiftClientException {
        String body = get("/shift/" + type);
        if (body == null) return null;
        try {
            return objectMapper.readValue(body, Shift.class);
        } catch (Exception e) {
            throw new ShiftClientException("Failed to parse shift response for type: " + type, e);
        }
    }

    /**
     * Searches shifts using optional server-side filters.
     * Pass null for any parameter to omit it from the query.
     */
    public List<Shift> findShifts(String type, String status, String owner,
                                   java.util.Date from, java.util.Date to) throws ShiftClientException {
        StringBuilder path = new StringBuilder("/shifts");
        java.util.List<String> params = new java.util.ArrayList<>();
        if (type   != null && !type.isEmpty())   params.add("type="   + encode(type));
        if (status != null && !status.isEmpty()) params.add("status=" + encode(status));
        if (owner  != null && !owner.isEmpty())  params.add("owner="  + encode(owner));
        if (from   != null) params.add("from=" + from.getTime());
        if (to     != null) params.add("to="   + to.getTime());
        if (!params.isEmpty()) path.append("?").append(String.join("&", params));

        String body = get(path.toString());
        if (body == null) return List.of();
        try {
            return objectMapper.readValue(body, new TypeReference<List<Shift>>() {});
        } catch (Exception e) {
            throw new ShiftClientException("Failed to parse findShifts response", e);
        }
    }

    /** Returns all shifts known to the service. */
    public List<Shift> listShifts() throws ShiftClientException {
        String body = get("/shifts");
        if (body == null) return List.of();
        try {
            return objectMapper.readValue(body, new TypeReference<List<Shift>>() {});
        } catch (Exception e) {
            throw new ShiftClientException("Failed to parse shifts response", e);
        }
    }

    /** Returns all shift types configured in the service. */
    public List<ShiftType> listTypes() throws ShiftClientException {
        String body = get("/shiftTypes");
        if (body == null) return List.of();
        try {
            return objectMapper.readValue(body, new TypeReference<List<ShiftType>>() {});
        } catch (Exception e) {
            throw new ShiftClientException("Failed to parse shift types response", e);
        }
    }

    // -------------------------------------------------------------------------
    // Write operations
    // -------------------------------------------------------------------------

    /**
     * Starts a new shift of the given type, owned by the given owner.
     * Returns the created shift as returned by the service.
     */
    public Shift startShift(String type, String owner) throws ShiftClientException {
        Shift payload = new Shift();
        ShiftType shiftType = new ShiftType();
        shiftType.setName(type);
        payload.setType(shiftType);
        payload.setOwner(owner);

        String body = post("/shifts", payload);
        try {
            return objectMapper.readValue(body, Shift.class);
        } catch (Exception e) {
            throw new ShiftClientException("Failed to parse start-shift response", e);
        }
    }

    /**
     * Ends the shift with the given id.
     * Returns the updated shift as returned by the service.
     */
    public Shift endShift(int shiftId) throws ShiftClientException {
        String body = put("/shifts/" + shiftId + "/end", null);
        try {
            return objectMapper.readValue(body, Shift.class);
        } catch (Exception e) {
            throw new ShiftClientException("Failed to parse end-shift response for id: " + shiftId, e);
        }
    }

    /**
     * Closes the shift with the given id (final state after ending).
     * Returns the updated shift as returned by the service.
     */
    public Shift closeShift(int shiftId) throws ShiftClientException {
        String body = put("/shifts/" + shiftId + "/close", null);
        try {
            return objectMapper.readValue(body, Shift.class);
        } catch (Exception e) {
            throw new ShiftClientException("Failed to parse close-shift response for id: " + shiftId, e);
        }
    }

    // -------------------------------------------------------------------------
    // HTTP helpers
    // -------------------------------------------------------------------------

    private String get(String path) throws ShiftClientException {
        HttpRequest request = newRequest(path).GET().build();
        return send(request, path);
    }

    private String post(String path, Object body) throws ShiftClientException {
        try {
            String json = (body != null) ? objectMapper.writeValueAsString(body) : "";
            HttpRequest request = newRequest(path)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            return send(request, path);
        } catch (ShiftClientException e) {
            throw e;
        } catch (Exception e) {
            throw new ShiftClientException("Failed to serialize request body for POST " + path, e);
        }
    }

    private String put(String path, Object body) throws ShiftClientException {
        try {
            String json = (body != null) ? objectMapper.writeValueAsString(body) : "";
            HttpRequest request = newRequest(path)
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            return send(request, path);
        } catch (ShiftClientException e) {
            throw e;
        } catch (Exception e) {
            throw new ShiftClientException("Failed to serialize request body for PUT " + path, e);
        }
    }

    private HttpRequest.Builder newRequest(String path) {
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(readTimeout);
        if (authHeader != null) b.header("Authorization", authHeader);
        return b;
    }

    private String send(HttpRequest request, String path) throws ShiftClientException {
        try {
            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status == 200 || status == 201) return response.body();
            if (status == 404) return null;
            throw new ShiftClientException(
                    "Shift service returned HTTP " + status + " for " + request.method() + " " + path);
        } catch (ShiftClientException e) {
            throw e;
        } catch (Exception e) {
            throw new ShiftClientException(
                    "Failed to reach shift service at " + baseUrl + path, e);
        }
    }

    private static String encode(String value) {
        try {
            return java.net.URLEncoder.encode(value, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            return value;
        }
    }

    /** Returns the base URL this client is configured with. */
    public String baseUrl() {
        return baseUrl;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String baseUrl = "http://localhost:8080/Shift/resources";
        private Duration connectTimeout = Duration.ofSeconds(3);
        private Duration readTimeout = Duration.ofSeconds(5);
        private String username;
        private String password = "";

        public Builder baseUrl(String baseUrl) { this.baseUrl = baseUrl; return this; }
        public Builder connectTimeout(Duration timeout) { this.connectTimeout = timeout; return this; }
        public Builder readTimeout(Duration timeout) { this.readTimeout = timeout; return this; }
        public Builder username(String username) { this.username = username; return this; }
        public Builder password(String password) { this.password = password; return this; }

        public ShiftClient build() { return new ShiftClient(this); }
    }
}
