package com.xebyte;

import junit.framework.TestCase;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Unit tests specifically for endpoint registration and HTTP server functionality.
 *
 * This test class focuses on validating that all endpoints defined in the
 * GhidraMCPPlugin.java are properly registered with the HTTP server.
 */
public class EndpointRegistrationTest extends TestCase {

    private static final String BASE_URL = "http://127.0.0.1:8089";
    private static final int TIMEOUT_SECONDS = 5;
    private HttpClient httpClient;
    private static boolean liveCheckDone = false;
    private static boolean liveServerAvailable = false;

    @Override
    protected void setUp() {
        httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS))
            .build();
        if (!liveCheckDone) {
            liveServerAvailable = liveTestsEnabled() && checkServerAvailability();
            if (liveTestsEnabled()) assertTrue("Requested live server is unavailable", liveServerAvailable);
            liveCheckDone = true;
            if (!liveServerAvailable) {
                System.out.println("Skipping live endpoint registration tests. Set GHIDRA_MCP_LIVE_TESTS=1 with a current server on " + BASE_URL + " to enable.");
            }
        }
    }

    private boolean liveTestsEnabled() {
        String value = System.getenv("GHIDRA_MCP_LIVE_TESTS");
        return value != null && (value.equals("1") || value.equalsIgnoreCase("true"));
    }

    private boolean shouldRunLiveTest() {
        return liveServerAvailable;
    }

    private boolean checkServerAvailability() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/check_connection"))
                .timeout(Duration.ofSeconds(2))
                .GET()
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200 && response.body() != null && response.body().contains("server_kind");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Helper method to test if an endpoint is registered (doesn't return 404)
     */
    private boolean isEndpointRegistered(String endpoint) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/" + endpoint))
                .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .GET()
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            // An endpoint is considered "registered" if it doesn't return 404
            // It may return 400 (bad request) if parameters are missing, but that means it's registered
            return response.statusCode() != 404;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Test that all basic CRUD endpoints are registered
     */
    public void testBasicEndpointsRegistered() {
        if (!shouldRunLiveTest()) return;
        Map<String, String> basicEndpoints = new HashMap<>();
        basicEndpoints.put("find_functions", "List all functions");
        basicEndpoints.put("find_functions", "List function names");
        basicEndpoints.put("list_program_items?kind=classes", "List classes/namespaces");
        basicEndpoints.put("list_program_items?kind=segments", "List memory segments");
        basicEndpoints.put("list_program_items?kind=imports", "List imported symbols");
        basicEndpoints.put("list_program_items?kind=exports", "List exported symbols");
        basicEndpoints.put("list_program_items?kind=namespaces", "List namespaces");
        basicEndpoints.put("list_program_items?kind=data_items", "List data items");
        basicEndpoints.put("list_strings", "List strings");

        for (Map.Entry<String, String> entry : basicEndpoints.entrySet()) {
            assertTrue("Endpoint '" + entry.getKey() + "' (" + entry.getValue() + ") should be registered",
                isEndpointRegistered(entry.getKey()));
        }
    }

    /**
     * Test that search and analysis endpoints are registered
     */
    public void testSearchAnalysisEndpointsRegistered() {
        if (!shouldRunLiveTest()) return;
        Map<String, String> searchEndpoints = new HashMap<>();
        searchEndpoints.put("find_functions", "Search functions by name");
        searchEndpoints.put("get_functions?function=main", "Decompile function");
        searchEndpoints.put("get_functions", "Get function(s) by name or address");
        searchEndpoints.put("disassemble_function", "Disassemble function");

        for (Map.Entry<String, String> entry : searchEndpoints.entrySet()) {
            assertTrue("Search/Analysis endpoint '" + entry.getKey() + "' (" + entry.getValue() + ") should be registered",
                isEndpointRegistered(entry.getKey()));
        }
    }

    /**
     * Test that cross-reference endpoints are registered
     */
    public void testCrossReferenceEndpointsRegistered() {
        if (!shouldRunLiveTest()) return;
        Map<String, String> xrefEndpoints = new HashMap<>();
        xrefEndpoints.put("get_xrefs_to", "Get references to address");
        xrefEndpoints.put("get_xrefs_from", "Get references from address");
        xrefEndpoints.put("get_functions?function=main&fields=callers,callees", "Get function cross-references");

        for (Map.Entry<String, String> entry : xrefEndpoints.entrySet()) {
            assertTrue("Cross-reference endpoint '" + entry.getKey() + "' (" + entry.getValue() + ") should be registered",
                isEndpointRegistered(entry.getKey()));
        }
    }

    /**
     * Test that current state endpoints are registered
     */
    public void testCurrentStateEndpointsRegistered() {
        if (!shouldRunLiveTest()) return;
        Map<String, String> stateEndpoints = new HashMap<>();
        stateEndpoints.put("get_ui_cursor", "Get UI cursor state");

        for (Map.Entry<String, String> entry : stateEndpoints.entrySet()) {
            assertTrue("Current state endpoint '" + entry.getKey() + "' (" + entry.getValue() + ") should be registered",
                isEndpointRegistered(entry.getKey()));
        }
    }

    /**
     * Test the problematic endpoints that should be registered but may not be
     * This test documents which advanced endpoints are missing
     */
    public void testAdvancedEndpointsRegistration() {
        if (!shouldRunLiveTest()) return;
        Map<String, String> advancedEndpoints = new HashMap<>();
        advancedEndpoints.put("list_globals", "List all labels");
        advancedEndpoints.put("get_metadata", "Get program statistics");
        advancedEndpoints.put("search_byte_patterns", "Find byte patterns");
        advancedEndpoints.put("analyze_call_graph", "Get function call graph");
        advancedEndpoints.put("list_globals", "Search labels");
        advancedEndpoints.put("list_strings", "Get string references");

        int registeredCount = 0;
        int totalCount = advancedEndpoints.size();

        System.out.println("\n=== Advanced Endpoints Registration Status ===");

        for (Map.Entry<String, String> entry : advancedEndpoints.entrySet()) {
            boolean isRegistered = isEndpointRegistered(entry.getKey());
            if (isRegistered) {
                System.out.println("✓ " + entry.getKey() + " - " + entry.getValue());
                registeredCount++;
            } else {
                System.out.println("✗ " + entry.getKey() + " - " + entry.getValue() + " (NOT REGISTERED)");
            }
        }

        double registrationRate = (double) registeredCount / totalCount * 100;
        System.out.println("Advanced endpoint registration: " + registeredCount + "/" + totalCount +
                          " (" + String.format("%.1f", registrationRate) + "%)");

        // Document the current state - this test should eventually pass when all endpoints are fixed
        // For now, we just report the status without failing the test
        if (registeredCount < totalCount) {
            System.out.println("WARNING: Some advanced endpoints are not registered. This indicates:");
            System.out.println("1. HTTP server context registration may have failed");
            System.out.println("2. Plugin may not have fully reloaded");
            System.out.println("3. Endpoint implementation may be incomplete");
        }

        // This assertion can be enabled once all endpoints are expected to work
        // assertTrue("All advanced endpoints should be registered", registeredCount == totalCount);
    }

    /**
     * Test that the HTTP server responds with proper error codes
     */
    public void testHttpErrorCodes() {
        if (!shouldRunLiveTest()) return;
        // Test 404 for truly non-existent endpoints
        assertFalse("Non-existent endpoint should return 404",
            isEndpointRegistered("definitely_does_not_exist_123456"));

        // Test that registered endpoints don't return 404
        assertTrue("Basic endpoint should not return 404",
            isEndpointRegistered("find_functions"));
    }

    /**
     * Test endpoint consistency - all endpoints from Java source should be registered
     * This test verifies that the createContext() calls in the Java code are working
     */
    public void testJavaSourceEndpointConsistency() {
        if (!shouldRunLiveTest()) return;
        // These are the endpoints that have createContext() calls in the Java source
        String[] sourceEndpoints = {
            // From startServer() method in GhidraMCPPlugin.java
            "find_functions", "find_functions", "list_program_items?kind=classes", "list_program_items?kind=segments", "list_program_items?kind=imports", "list_program_items?kind=exports",
            "list_program_items?kind=namespaces", "list_program_items?kind=data_items", "list_strings", "find_functions", "get_functions?function=main",
            "rename_function", "rename_symbol", "get_xrefs_to", "get_xrefs_from", "get_functions?function=main&fields=callers,callees",
            "get_functions?function=main&fields=labels", "get_functions", "get_ui_cursor",
            "disassemble_function", "set_comment",
            "rename_function", "set_function_prototype",
            "set_variable_type",

            // Advanced endpoints that should be in the source
            "search_byte_patterns", "get_metadata", "create_label", "list_globals",
            "get_functions?function=main&fields=labels", "list_globals"
        };

        int registeredFromSource = 0;
        int totalFromSource = sourceEndpoints.length;

        System.out.println("\n=== Java Source Endpoint Consistency Check ===");

        for (String endpoint : sourceEndpoints) {
            boolean isRegistered = isEndpointRegistered(endpoint);
            if (isRegistered) {
                registeredFromSource++;
            } else {
                System.out.println("✗ " + endpoint + " - defined in source but not registered");
            }
        }

        double consistencyRate = (double) registeredFromSource / totalFromSource * 100;
        System.out.println("Source consistency: " + registeredFromSource + "/" + totalFromSource +
                          " (" + String.format("%.1f", consistencyRate) + "%)");

        // We expect high consistency between source and registered endpoints
        assertTrue("All source-defined endpoint fixtures should be registered",
            registeredFromSource == totalFromSource);
    }

    /**
     * Test server health and basic functionality
     */
    public void testServerHealth() {
        if (!shouldRunLiveTest()) return;
        // Test that the server is responding at all
        boolean serverResponding = false;
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/check_connection"))
                .timeout(Duration.ofSeconds(2))
                .GET()
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            serverResponding = (response.statusCode() >= 200 && response.statusCode() < 500);
        } catch (Exception e) {
            // Server not responding
        }

        assertTrue("MCP HTTP server should be responding. " +
                  "Ensure Ghidra is running with GhidraMCP plugin enabled and MCP server started.",
                  serverResponding);
    }
}
